package run.halo.regverify;

/**
 * Builds the HTML fragment that is appended to Halo's server-rendered signup page.
 *
 * <p>Halo renders the signup page from {@code templates/signup.html} (since 2.20) and
 * does not expose a template extension point for gateway pages, so the plugin injects a
 * stylesheet, a small configuration object and the client script before
 * {@code </body>}. All administrator provided strings are serialized as escaped JSON and
 * inserted with {@code textContent} on the client, so they cannot break out of the script
 * tag or inject markup.
 */
final class SignupPageInjector {

    static final String PLUGIN_NAME = "regverify";
    static final String ASSETS_PATH = "/plugins/" + PLUGIN_NAME + "/assets/res";
    static final String API_PATH = "/apis/api.regverify.halo.run/v1alpha1/verify";
    static final String MARKER = "regverify:start";

    private SignupPageInjector() {
    }

    static String build(RegVerifySetting setting) {
        return build(setting, null);
    }

    /**
     * @param setting current settings
     * @param preVerifiedAnswer answer that Halo's signup endpoint already accepted in this
     *     request; when the endpoint re-renders the signup page with a validation error,
     *     echoing it back lets the visitor keep the verified state without retyping
     */
    static String build(RegVerifySetting setting, String preVerifiedAnswer) {
        var json = new StringBuilder(512)
            .append('{')
            .append(quote("apiPath")).append(':').append(quote(API_PATH)).append(',')
            .append(quote("texts")).append(":{")
            .append(quote("title")).append(':').append(quote(setting.title())).append(',')
            .append(quote("description")).append(':').append(quote(setting.description()))
            .append(',')
            .append(quote("inputLabel")).append(':').append(quote(setting.inputLabel()))
            .append(',')
            .append(quote("inputPlaceholder")).append(':')
            .append(quote(setting.inputPlaceholder())).append(',')
            .append(quote("submit")).append(':').append(quote(setting.submitText())).append(',')
            .append(quote("cancel")).append(':').append(quote(setting.cancelText())).append(',')
            .append(quote("success")).append(':').append(quote(setting.successText()))
            .append(',')
            .append(quote("fail")).append(':').append(quote(setting.failText())).append(',')
            .append(quote("button")).append(':').append(quote(setting.buttonText())).append(',')
            .append(quote("unverified")).append(':').append(quote(setting.unverifiedText()))
            .append(',')
            .append(quote("verifying")).append(':').append(quote(setting.verifyingText()))
            .append(',')
            .append(quote("empty")).append(':').append(quote(setting.emptyText())).append(',')
            .append(quote("rateLimited")).append(':')
            .append(quote(setting.rateLimitedText()))
            .append('}')
            .append(',')
            .append(quote("preVerified"))
            .append(':')
            .append(quote(preVerifiedAnswer == null || preVerifiedAnswer.isBlank()
                ? "" : preVerifiedAnswer))
            .append('}')
            .toString();

        return """
            <!-- %s -->
            <link rel="stylesheet" href="%s/regverify.css">
            <script>window.__REGVERIFY__ = %s;</script>
            <script src="%s/regverify.js" defer></script>
            <!-- regverify:end -->
            """.formatted(MARKER, ASSETS_PATH, json, ASSETS_PATH);
    }

    /**
     * Serializes a string as a JSON literal that is also safe inside an inline
     * {@code <script>} element.
     */
    static String quote(String value) {
        if (value == null) {
            return "null";
        }
        var builder = new StringBuilder(value.length() + 16).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                default -> {
                    if (c < 0x20 || c == '<' || c == '>' || c == '&' || c == '\u2028'
                        || c == '\u2029') {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.append('"').toString();
    }
}
