package run.halo.regverify;

import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import run.halo.app.security.AdditionalWebFilter;

/**
 * Injects the verification entry into Halo's server-rendered signup page and rejects
 * signup submissions that do not carry a valid answer.
 *
 * <p>The browser-side check is only a convenience; the POST filter re-runs the script
 * with the submitted answer so an attacker cannot bypass the gate by posting the form
 * directly.
 */
@Component
public class SignupVerificationWebFilter implements AdditionalWebFilter {

    private static final Logger log = LoggerFactory.getLogger(SignupVerificationWebFilter.class);

    private static final String SIGNUP_PATH = "/signup";
    private static final String ANSWER_FIELD = "regverifyAnswer";
    private static final URI FAILURE_REDIRECT = URI.create("/signup?regverify=failed");

    private final ScriptVerificationService verificationService;
    private final VerificationRateLimiter rateLimiter;

    public SignupVerificationWebFilter(ScriptVerificationService verificationService,
        VerificationRateLimiter rateLimiter) {
        this.verificationService = verificationService;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public int getOrder() {
        // Run before the security filters so that form data can be inspected safely.
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        if (!SIGNUP_PATH.equals(request.getPath().value())) {
            return chain.filter(exchange);
        }
        if (HttpMethod.GET.equals(request.getMethod())) {
            return handleSignupPage(exchange, chain);
        }
        if (HttpMethod.POST.equals(request.getMethod())) {
            return handleSignupSubmission(exchange, chain);
        }
        return chain.filter(exchange);
    }

    private Mono<Void> handleSignupPage(ServerWebExchange exchange, WebFilterChain chain) {
        return verificationService.getSetting().flatMap(setting -> {
            if (!setting.active()) {
                return chain.filter(exchange);
            }
            var decorated = new SignupPageResponseDecorator(exchange.getResponse(),
                SignupPageInjector.build(setting));
            return chain.filter(exchange.mutate().response(decorated).build());
        });
    }

    private Mono<Void> handleSignupSubmission(ServerWebExchange exchange,
        WebFilterChain chain) {
        return verificationService.getSetting()
            .flatMap(setting -> {
                if (!setting.active()) {
                    return chain.filter(exchange);
                }
                return validate(exchange)
                    .flatMap(validation -> {
                        if (!validation.allowed()) {
                            return reject(exchange);
                        }
                        var response = exchange.getResponse();
                        var decorated = new SignupPageResponseDecorator(response,
                            SignupPageInjector.build(setting, validation.answer()));
                        var mutated = exchange.mutate().response(decorated).build();
                        return chain.filter(mutated);
                    });
            });
    }

    private record Validation(boolean allowed, String answer) {

        static Validation rejected() {
            return new Validation(false, "");
        }
    }

    /**
     * Validates the submitted answer. Any error is treated as a rejection (fail closed).
     */
    private Mono<Validation> validate(ServerWebExchange exchange) {
        return exchange.getFormData()
            .flatMap(form -> {
                var ip = ClientIp.resolve(exchange.getRequest());
                if (!rateLimiter.tryAcquire("signup", ip)) {
                    log.info("Rejected signup from {}: too many attempts", ip);
                    return Mono.just(Validation.rejected());
                }
                var answer = form.getFirst(ANSWER_FIELD);
                var input = new VerifyInput(
                    answer,
                    form.getFirst("username"),
                    form.getFirst("email"),
                    form.getFirst("displayName"),
                    ip
                );
                return verificationService.verify(input)
                    .doOnNext(result -> {
                        if (!result.valid()) {
                            log.info("Rejected signup from {}: {}", ip, result.message());
                        }
                    })
                    .map(result -> new Validation(result.valid(), answer == null ? "" : answer));
            })
            .onErrorResume(error -> {
                log.warn("Failed to validate signup submission, rejecting it", error);
                return Mono.just(Validation.rejected());
            });
    }

    private Mono<Void> reject(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        if (response.isCommitted()) {
            return response.setComplete();
        }
        response.setStatusCode(HttpStatus.SEE_OTHER);
        response.getHeaders().setLocation(FAILURE_REDIRECT);
        return response.setComplete();
    }
}
