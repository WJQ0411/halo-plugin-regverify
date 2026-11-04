package run.halo.regverify;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Buffers the HTML response of the signup page and inserts the plugin fragment before
 * {@code </body>}.
 */
final class SignupPageResponseDecorator extends ServerHttpResponseDecorator {

    private final String injection;

    SignupPageResponseDecorator(ServerHttpResponse delegate, String injection) {
        super(delegate);
        this.injection = injection;
    }

    @Override
    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
        return DataBufferUtils.join(body)
            .flatMap(buffer -> {
                byte[] bytes = read(buffer);
                byte[] result = inject(bytes);
                getHeaders().remove(HttpHeaders.CONTENT_LENGTH);
                return super.writeWith(Mono.just(bufferFactory().wrap(result)));
            })
            .switchIfEmpty(Mono.defer(() -> super.writeWith(Mono.empty())));
    }

    @Override
    public Mono<Void> writeAndFlushWith(
        Publisher<? extends Publisher<? extends DataBuffer>> body) {
        return writeWith(Flux.from(body).flatMap(inner -> Flux.from(inner)));
    }

    private static byte[] read(DataBuffer buffer) {
        try {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return bytes;
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    private byte[] inject(byte[] bytes) {
        var status = getStatusCode();
        if (status != null && (status.value() < 200 || status.value() >= 300)) {
            return bytes;
        }
        var contentType = getHeaders().getContentType();
        if (contentType == null || !MediaType.TEXT_HTML.isCompatibleWith(contentType)) {
            return bytes;
        }
        Charset charset = contentType.getCharset() == null
            ? StandardCharsets.UTF_8
            : contentType.getCharset();
        var html = new String(bytes, charset);
        if (html.contains(SignupPageInjector.MARKER)) {
            return bytes;
        }
        int index = html.toLowerCase(Locale.ROOT).lastIndexOf("</body>");
        if (index < 0) {
            return bytes;
        }
        var injected = html.substring(0, index) + injection + html.substring(index);
        return injected.getBytes(charset);
    }
}
