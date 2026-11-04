package run.halo.regverify;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class SignupPageResponseDecoratorTest {

    private static final String INJECTION = "<!-- regverify:start --><script>test()</script>"
        + "<!-- regverify:end -->";

    @Test
    void shouldInjectBeforeBodyEnd() {
        var response = new MockServerHttpResponse();
        response.getHeaders().setContentType(MediaType.TEXT_HTML);
        var decorator = new SignupPageResponseDecorator(response, INJECTION);

        var body = "<html><body><form id=\"signup-form\"></form></body></html>";
        decorator.writeWith(Mono.just(buffer(response, body))).block();

        var result = response.getBodyAsString().block();
        assertTrue(result != null && result.contains(INJECTION + "</body>"));
        assertTrue(response.getHeaders().getContentLength() == -1);
    }

    @Test
    void shouldInjectForFlushableResponses() {
        var response = new MockServerHttpResponse();
        response.getHeaders().setContentType(MediaType.TEXT_HTML);
        var decorator = new SignupPageResponseDecorator(response, INJECTION);

        var body = "<html><body>hello</body></html>";
        decorator.writeAndFlushWith(Flux.just(Flux.just(buffer(response, body)))).block();

        var result = response.getBodyAsString().block();
        assertTrue(result != null && result.contains(INJECTION));
    }

    @Test
    void shouldNotInjectTwice() {
        var response = new MockServerHttpResponse();
        response.getHeaders().setContentType(MediaType.TEXT_HTML);
        var decorator = new SignupPageResponseDecorator(response, INJECTION);

        var body = "<html><body>" + INJECTION + "</body></html>";
        decorator.writeWith(Mono.just(buffer(response, body))).block();

        var result = response.getBodyAsString().block();
        assertTrue(result != null
            && result.indexOf(SignupPageInjector.MARKER) == result.lastIndexOf(SignupPageInjector.MARKER));
    }

    @Test
    void shouldIgnoreNonHtmlResponses() {
        var response = new MockServerHttpResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var decorator = new SignupPageResponseDecorator(response, INJECTION);

        decorator.writeWith(Mono.just(buffer(response, "{\"ok\":true}"))).block();
        assertTrue("{\"ok\":true}".equals(response.getBodyAsString().block()));
    }

    private static DataBuffer buffer(MockServerHttpResponse response, String value) {
        return response.bufferFactory().wrap(value.getBytes(StandardCharsets.UTF_8));
    }
}
