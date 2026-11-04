package run.halo.regverify;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SignupPageInjectorTest {

    @Test
    void shouldEscapeStringsForInlineScript() {
        var quoted = SignupPageInjector.quote("</script><img src=x onerror=alert(1)>");
        assertFalse(quoted.contains("<"));
        assertFalse(quoted.contains(">"));
        assertFalse(quoted.contains("</script"));
        assertTrue(quoted.contains("\\u003c"));
    }

    @Test
    void shouldEchoPreVerifiedAnswerSafely() {
        var setting = new RegVerifySetting(true, "验证", "标题", "说明", "输入", "占位",
            "提交", "取消", "成功", "失败", "未验证", "验证中", "空", "频繁", 2000, "true");
        var html = SignupPageInjector.build(setting, "</script><script>alert(1)</script>");
        assertFalse(html.contains("</script><script>alert(1)"));
        assertTrue(html.contains("\\u003c/script\\u003e"));
    }

    @Test
    void shouldBuildInjectionWithConfiguredTexts() {
        var setting = new RegVerifySetting(true, "验证", "标题", "说明", "输入", "占位",
            "提交", "取消", "成功", "失败", "未验证", "验证中", "空", "频繁", 2000,
            "input === 'abc'");
        var html = SignupPageInjector.build(setting);
        assertTrue(html.contains(SignupPageInjector.API_PATH));
        assertTrue(html.contains("regverify.js"));
        assertTrue(html.contains("regverify.css"));
        assertTrue(html.contains("\"title\":\"标题\""));
        assertFalse(html.contains("input === 'abc'"), "script must never reach the browser");
    }
}
