package run.halo.regverify;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

/**
 * Public endpoint used by the signup page to check an answer against the configured
 * script.
 *
 * <p>The endpoint is reachable anonymously through the
 * {@code regverify-anonymous-verify} role template, and is rate limited per client IP.
 */
@Component
public class RegVerifyEndpoint implements CustomEndpoint {

    private static final String GROUP = "api.regverify.halo.run";
    private static final String VERSION = "v1alpha1";

    private final ScriptVerificationService verificationService;
    private final VerificationRateLimiter rateLimiter;

    public RegVerifyEndpoint(ScriptVerificationService verificationService,
        VerificationRateLimiter rateLimiter) {
        this.verificationService = verificationService;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .POST("/verify", this::verify)
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion(GROUP, VERSION);
    }

    private Mono<ServerResponse> verify(ServerRequest request) {
        var ip = ClientIp.resolve(request.exchange().getRequest());
        return request.bodyToMono(VerifyRequest.class)
            .switchIfEmpty(Mono.fromSupplier(VerifyRequest::empty))
            .onErrorResume(error -> Mono.fromSupplier(VerifyRequest::empty))
            .flatMap(body -> {
                if (body.answer() == null || body.answer().isBlank()) {
                    return response(HttpStatus.BAD_REQUEST,
                        new VerifyResponse(false, "请输入验证内容"));
                }
                if (!rateLimiter.tryAcquire("verify", ip)) {
                    return response(HttpStatus.TOO_MANY_REQUESTS,
                        new VerifyResponse(false, "操作过于频繁，请稍后再试"));
                }
                return verificationService
                    .verify(new VerifyInput(body.answer(), body.username(), body.email(),
                        body.displayName(), ip))
                    .flatMap(result -> response(HttpStatus.OK,
                        new VerifyResponse(result.valid(), result.message())));
            });
    }

    private Mono<ServerResponse> response(HttpStatus status, VerifyResponse body) {
        return ServerResponse.status(status)
            .contentType(APPLICATION_JSON)
            .bodyValue(body);
    }

    /**
     * Request body of {@code POST /verify}. Missing fields are treated as empty strings.
     */
    public record VerifyRequest(
        String answer,
        String username,
        String email,
        String displayName
    ) {

        static VerifyRequest empty() {
            return new VerifyRequest("", "", "", "");
        }
    }

    /**
     * Response body of {@code POST /verify}.
     *
     * @param valid whether the script accepted the answer
     * @param message optional reason shown to the visitor
     */
    public record VerifyResponse(boolean valid, String message) {
    }
}
