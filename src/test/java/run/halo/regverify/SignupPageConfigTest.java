package run.halo.regverify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

class SignupPageConfigTest {

    @Test
    void shouldEmbedParseableConfiguration() {
        var setting = new RegVerifySetting(true, "按钮", "标题 <b>", "说明\"引号\"",
            "输入", "占位", "提交", "取消", "成功", "失败", "未验证", "验证中", "空", "频繁",
            2000, "input === 'x'");
        var html = SignupPageInjector.build(setting, "</script>");

        var prefix = "window.__REGVERIFY__ = ";
        int start = html.indexOf(prefix) + prefix.length();
        int end = html.indexOf(";</script>", start);
        assertTrue(start > prefix.length() && end > start, "config script must be present");
        var config = html.substring(start, end);

        var context = Context.enter();
        try {
            var scope = context.initStandardObjects();
            var parsed = context.evaluateString(scope, "(" + config + ")", "config", 1, null);
            assertTrue(parsed instanceof Scriptable);
            var object = (Scriptable) parsed;
            assertEquals(SignupPageInjector.API_PATH,
                ScriptableObject.getProperty(object, "apiPath"));
            assertEquals("</script>", ScriptableObject.getProperty(object, "preVerified"));
            var texts = (Scriptable) ScriptableObject.getProperty(object, "texts");
            assertEquals("标题 <b>", ScriptableObject.getProperty(texts, "title"));
        } finally {
            Context.exit();
        }
    }
}
