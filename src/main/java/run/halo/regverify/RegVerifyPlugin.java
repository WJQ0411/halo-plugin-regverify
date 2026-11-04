package run.halo.regverify;

import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * Plugin entry point.
 *
 * <p>All the runtime behaviour lives in
 * {@link SignupVerificationWebFilter} (page injection and signup enforcement) and
 * {@link RegVerifyEndpoint} (the public verification API).
 */
@Component
public class RegVerifyPlugin extends BasePlugin {

    public RegVerifyPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }
}
