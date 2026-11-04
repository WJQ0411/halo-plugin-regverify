package run.halo.regverify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RegVerifySettingTest {

    @Test
    void shouldNormalizeMissingValues() {
        var setting = RegVerifySetting.defaults();
        assertFalse(setting.enabled());
        assertEquals(2000, setting.timeoutMs());
        assertEquals("开始验证", setting.buttonText());
        assertEquals("", setting.script());
        assertFalse(setting.active());
    }

    @Test
    void shouldClampTimeout() {
        var tooSmall = new RegVerifySetting(true, null, null, null, null, null, null, null,
            null, null, null, null, null, null, -5, "return true");
        var tooLarge = new RegVerifySetting(true, null, null, null, null, null, null, null,
            null, null, null, null, null, null, 999_999, "return true");
        assertEquals(100, tooSmall.timeoutMs());
        assertEquals(10_000, tooLarge.timeoutMs());
    }

    @Test
    void shouldOnlyBeActiveWithEnabledAndScript() {
        assertFalse(setting(false, "return true").active());
        assertFalse(setting(true, "   ").active());
        assertTrue(setting(true, "return true").active());
    }

    private static RegVerifySetting setting(boolean enabled, String script) {
        return new RegVerifySetting(enabled, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, script);
    }
}
