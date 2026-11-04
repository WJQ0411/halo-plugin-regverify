package run.halo.regverify;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;

/**
 * Resolves the client IP address for rate limiting. Mirrors the behaviour of Halo's own
 * {@code IpAddressUtils}, which is not part of the plugin API.
 */
final class ClientIp {

    static final String UNKNOWN = "unknown";

    private static final String[] IP_HEADER_NAMES = {
        "X-Forwarded-For",
        "X-Real-IP",
        "Proxy-Client-IP",
        "WL-Proxy-Client-IP",
        "CF-Connecting-IP",
    };

    private ClientIp() {
    }

    static String resolve(ServerHttpRequest request) {
        for (String header : IP_HEADER_NAMES) {
            var value = request.getHeaders().getFirst(header);
            if (!StringUtils.hasText(value) || UNKNOWN.equalsIgnoreCase(value)) {
                continue;
            }
            for (String candidate : value.trim().split("[,;]")) {
                if (StringUtils.hasText(candidate) && !UNKNOWN.equalsIgnoreCase(candidate)) {
                    return candidate.trim();
                }
            }
        }
        var remoteAddress = request.getRemoteAddress();
        if (remoteAddress == null || remoteAddress.isUnresolved()) {
            return UNKNOWN;
        }
        return remoteAddress.getAddress().getHostAddress();
    }
}
