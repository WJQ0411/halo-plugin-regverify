package run.halo.regverify;

/**
 * Values stored by the plugin settings form.
 *
 * <p>The {@code Setting} resource lives in {@code extensions/settings.yaml} and is
 * persisted into the plugin ConfigMap under the {@link #GROUP} key. All the fields are
 * normalized in the canonical constructor so that a partially written ConfigMap never
 * produces {@code null} values.
 */
public record RegVerifySetting(
    Boolean enabled,
    String buttonText,
    String title,
    String description,
    String inputLabel,
    String inputPlaceholder,
    String submitText,
    String cancelText,
    String successText,
    String failText,
    String unverifiedText,
    String verifyingText,
    String emptyText,
    String rateLimitedText,
    Integer timeoutMs,
    String script
) {

    public static final String GROUP = "basic";

    static final int DEFAULT_TIMEOUT_MS = 2000;
    static final int MIN_TIMEOUT_MS = 100;
    static final int MAX_TIMEOUT_MS = 10_000;

    public RegVerifySetting {
        enabled = Boolean.TRUE.equals(enabled);
        buttonText = text(buttonText, "开始验证");
        title = text(title, "注册验证");
        description = text(description, "请按页面提示完成验证，验证通过后才能继续注册。");
        inputLabel = text(inputLabel, "验证内容");
        inputPlaceholder = text(inputPlaceholder, "请输入验证内容");
        submitText = text(submitText, "验证");
        cancelText = text(cancelText, "取消");
        successText = text(successText, "验证通过，可以继续注册");
        failText = text(failText, "验证未通过，请重试");
        unverifiedText = text(unverifiedText, "尚未验证");
        verifyingText = text(verifyingText, "验证中…");
        emptyText = text(emptyText, "请输入验证内容");
        rateLimitedText = text(rateLimitedText, "操作过于频繁，请稍后再试");
        timeoutMs = clampTimeout(timeoutMs);
        script = script == null ? "" : script;
    }

    /**
     * Settings used when the plugin has never been configured.
     */
    public static RegVerifySetting defaults() {
        return new RegVerifySetting(null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null);
    }

    /**
     * Whether the verification gate should be applied. A blank script is treated as an
     * unconfigured plugin so that installing the plugin does not lock everybody out of
     * the registration page.
     */
    public boolean active() {
        return enabled && script != null && !script.isBlank();
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static int clampTimeout(Integer value) {
        if (value == null) {
            return DEFAULT_TIMEOUT_MS;
        }
        return Math.max(MIN_TIMEOUT_MS, Math.min(MAX_TIMEOUT_MS, value));
    }
}
