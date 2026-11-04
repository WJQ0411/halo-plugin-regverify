package run.halo.regverify;

import java.util.Map;
import org.mozilla.javascript.ClassShutter;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates the administrator-provided JavaScript with Rhino.
 *
 * <p>The script runs in a small sandbox:
 * <ul>
 *   <li>Java classes are not visible to scripts (a {@link ClassShutter} denying every
 *       class is installed), so no network or file access is possible.</li>
 *   <li>Execution is time bounded with the Rhino instruction observer, so an infinite
 *       loop cannot block the signup endpoint.</li>
 *   <li>Script and input lengths are bounded before evaluation.</li>
 * </ul>
 *
 * <p>This class is intentionally free of Spring dependencies so it can be unit tested
 * directly.
 */
public final class ScriptEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ScriptEvaluator.class);

    private static final int MAX_SCRIPT_LENGTH = 20_000;
    private static final int MAX_INPUT_LENGTH = 2_000;
    private static final int MAX_MESSAGE_LENGTH = 300;
    private static final int INSTRUCTION_OBSERVER_THRESHOLD = 10_000;
    private static final String DEADLINE_KEY = "regverify.deadline";
    private static final String DEFAULT_FAILURE = "验证未通过";

    private static final SandboxContextFactory CONTEXT_FACTORY = new SandboxContextFactory();

    private ScriptEvaluator() {
    }

    /**
     * Result of an evaluation.
     *
     * @param valid whether the input passed the script
     * @param message optional failure reason that can be shown to the visitor
     */
    public record Result(boolean valid, String message) {

        public static Result pass() {
            return new Result(true, null);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }

    /**
     * Evaluates {@code script} with the given bindings.
     *
     * @param script JavaScript source, must already be non-blank
     * @param timeoutMs maximum execution time in milliseconds
     * @param bindings variables exposed to the script (values are converted to strings)
     * @return never {@code null}
     */
    public static Result evaluate(String script, long timeoutMs, Map<String, ?> bindings) {
        if (script == null || script.isBlank()) {
            return Result.fail("验证脚本未配置");
        }
        if (script.length() > MAX_SCRIPT_LENGTH) {
            return Result.fail("验证脚本过长");
        }
        if (bindings != null) {
            for (Object value : bindings.values()) {
                if (value instanceof CharSequence text && text.length() > MAX_INPUT_LENGTH) {
                    return Result.fail("输入内容过长");
                }
            }
        }

        var start = System.nanoTime();
        var context = CONTEXT_FACTORY.enterContext();
        try {
            context.setLanguageVersion(Context.VERSION_ES6);
            context.setInstructionObserverThreshold(INSTRUCTION_OBSERVER_THRESHOLD);
            var scope = context.initStandardObjects();
            if (bindings != null) {
                bindings.forEach((name, value) -> ScriptableObject.putProperty(scope, name,
                    value == null ? "" : value.toString()));
            }
            context.putThreadLocal(DEADLINE_KEY, start + Math.max(1, timeoutMs) * 1_000_000L);

            Object result;
            try {
                result = context.evaluateString(scope, script, "regverify-script", 1, null);
            } catch (ScriptTimeoutError e) {
                log.warn("Verification script exceeded the {} ms budget", timeoutMs);
                return Result.fail("验证脚本执行超时");
            } catch (RhinoException e) {
                log.warn("Verification script failed: {} (line {})", e.getMessage(), e.lineNumber());
                return Result.fail(DEFAULT_FAILURE);
            } catch (StackOverflowError e) {
                log.warn("Verification script exceeded the maximum call depth");
                return Result.fail(DEFAULT_FAILURE);
            }
            return interpret(result);
        } finally {
            Context.exit();
        }
    }

    private static Result interpret(Object result) {
        if (result instanceof Boolean value) {
            return value ? Result.pass() : Result.fail(DEFAULT_FAILURE);
        }
        if (result instanceof CharSequence text) {
            var message = text.toString().trim();
            if (message.isEmpty()) {
                return Result.fail(DEFAULT_FAILURE);
            }
            if ("true".equalsIgnoreCase(message)) {
                return Result.pass();
            }
            return Result.fail(truncate(message, MAX_MESSAGE_LENGTH));
        }
        if (result instanceof Number number) {
            return number.doubleValue() != 0 ? Result.pass() : Result.fail(DEFAULT_FAILURE);
        }
        return Result.fail(DEFAULT_FAILURE);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Thrown from the instruction observer; an {@link Error} so scripts cannot catch it. */
    private static final class ScriptTimeoutError extends Error {

        private ScriptTimeoutError() {
            super("Verification script execution timed out", null, false, false);
        }
    }

    private static final class SandboxContextFactory extends ContextFactory {

        private static final ClassShutter DENY_ALL = className -> false;

        @Override
        @SuppressWarnings("deprecation")
        protected Context makeContext() {
            var context = super.makeContext();
            context.setLanguageVersion(Context.VERSION_ES6);
            // Interpreted mode is required for the instruction observer to be called.
            // (Rhino 1.9 always interprets; the call keeps this explicit across upgrades.)
            context.setOptimizationLevel(-1);
            context.setInstructionObserverThreshold(INSTRUCTION_OBSERVER_THRESHOLD);
            context.setClassShutter(DENY_ALL);
            return context;
        }

        @Override
        protected void observeInstructionCount(Context context, int instructionCount) {
            if (context.getThreadLocal(DEADLINE_KEY) instanceof Long deadline
                && System.nanoTime() > deadline) {
                throw new ScriptTimeoutError();
            }
        }
    }
}
