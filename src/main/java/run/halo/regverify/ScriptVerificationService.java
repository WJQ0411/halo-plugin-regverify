package run.halo.regverify;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.plugin.ReactiveSettingFetcher;

/**
 * Loads the plugin settings and runs the verification script off the event loop.
 */
@Component
public class ScriptVerificationService {

    private final ReactiveSettingFetcher settingFetcher;

    public ScriptVerificationService(ReactiveSettingFetcher settingFetcher) {
        this.settingFetcher = settingFetcher;
    }

    /**
     * Reads the settings from the plugin ConfigMap. Missing configuration is returned as
     * the disabled defaults so that a broken or missing ConfigMap keeps the gate closed
     * instead of failing open.
     */
    public Mono<RegVerifySetting> getSetting() {
        return settingFetcher.fetch(RegVerifySetting.GROUP, RegVerifySetting.class)
            .switchIfEmpty(Mono.fromSupplier(RegVerifySetting::defaults))
            .onErrorResume(error -> Mono.fromSupplier(RegVerifySetting::defaults));
    }

    /**
     * Runs the configured script for the given input. The result is {@code valid=false}
     * when the plugin is not active, so callers can always treat it as a gate.
     */
    public Mono<ScriptEvaluator.Result> verify(VerifyInput input) {
        return getSetting().flatMap(setting -> {
            if (!setting.active()) {
                return Mono.just(ScriptEvaluator.Result.fail("注册验证未启用"));
            }
            Map<String, String> bindings = new LinkedHashMap<>();
            bindings.put("input", input.answer());
            bindings.put("username", input.username());
            bindings.put("email", input.email());
            bindings.put("displayName", input.displayName());
            bindings.put("ip", input.ip());
            return Mono.fromCallable(() -> ScriptEvaluator.evaluate(setting.script(),
                    setting.timeoutMs(), bindings))
                .subscribeOn(Schedulers.boundedElastic());
        });
    }
}
