package run.halo.regverify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScriptEvaluatorTest {

    private Map<String, ?> bindings(String input) {
        return Map.of(
            "input", input,
            "username", "alice",
            "email", "alice@example.com",
            "displayName", "Alice",
            "ip", "127.0.0.1"
        );
    }

    @Test
    void shouldPassWhenScriptReturnsTrue() {
        var result = ScriptEvaluator.evaluate("input === 'abc'", 1000, bindings("abc"));
        assertTrue(result.valid());
    }

    @Test
    void shouldFailWhenScriptReturnsFalse() {
        var result = ScriptEvaluator.evaluate("input === 'abc'", 1000, bindings("nope"));
        assertFalse(result.valid());
        assertNotNull(result.message());
    }

    @Test
    void shouldPassWithStringTrue() {
        assertTrue(ScriptEvaluator.evaluate("'true'", 1000, bindings("")).valid());
    }

    @Test
    void shouldFailWithEmptyString() {
        assertFalse(ScriptEvaluator.evaluate("''", 1000, bindings("")).valid());
    }

    @Test
    void shouldUseReturnedStringAsMessage() {
        var result = ScriptEvaluator.evaluate("'答案不对'", 1000, bindings(""));
        assertFalse(result.valid());
        assertEquals("答案不对", result.message());
    }

    @Test
    void shouldPassWithNonZeroNumber() {
        assertTrue(ScriptEvaluator.evaluate("1", 1000, bindings("")).valid());
        assertFalse(ScriptEvaluator.evaluate("0", 1000, bindings("")).valid());
    }

    @Test
    void shouldFailOnSyntaxError() {
        assertFalse(ScriptEvaluator.evaluate("function (", 1000, bindings("")).valid());
    }

    @Test
    void shouldFailOnRuntimeError() {
        assertFalse(ScriptEvaluator.evaluate("missingFunction()", 1000, bindings("")).valid());
    }

    @Test
    void shouldNotExposeJavaClasses() {
        var result = ScriptEvaluator.evaluate(
            "java.lang.System.exit(0); true", 1000, bindings(""));
        assertFalse(result.valid());

        var packages = ScriptEvaluator.evaluate(
            "Packages.java.lang.System.exit(0); true", 1000, bindings(""));
        assertFalse(packages.valid());
    }

    @Test
    void shouldExposeBindingsToScript() {
        var result = ScriptEvaluator.evaluate(
            "input === 'abc' && username === 'alice' && email.indexOf('@') > 0",
            1000, bindings("abc"));
        assertTrue(result.valid());
    }

    @Test
    void shouldStopInfiniteLoops() {
        var start = System.nanoTime();
        var result = ScriptEvaluator.evaluate("while (true) {}", 300, bindings(""));
        var elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertFalse(result.valid());
        assertTrue(elapsed.toSeconds() < 5, "script should be interrupted quickly");
    }

    @Test
    void shouldRejectOversizedInput() {
        var result = ScriptEvaluator.evaluate("true", 1000, bindings("x".repeat(2001)));
        assertFalse(result.valid());
    }

    @Test
    void shouldRejectBlankScript() {
        assertFalse(ScriptEvaluator.evaluate("  ", 1000, bindings("")).valid());
    }
}
