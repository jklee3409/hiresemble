package com.hiresemble.job.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.job.application.port.JobPageFetchException;
import com.hiresemble.job.application.port.JobPageFetchGateway.PageClassification;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class SecureJobPageFetchAdapterTest {

    private static final InetAddress PUBLIC = address("93.184.216.34");

    @Test
    void normalHtmlAndRedirectAreFetchedWithDnsValidationAtEveryHop() {
        Queue<SecureJobPageFetchAdapter.TransportResponse> responses = new ArrayDeque<>();
        responses.add(response(
                302,
                Map.of("Location", List.of("https://jobs.test/final")),
                ""));
        responses.add(html(200, jobHtml()));
        AtomicInteger dnsChecks = new AtomicInteger();
        SecureJobPageFetchAdapter adapter = adapter(
                host -> {
                    dnsChecks.incrementAndGet();
                    return List.of(PUBLIC);
                },
                (uri, addresses, deadline) -> responses.remove());

        var fetched = adapter.fetch(URI.create("https://example.test/start"));

        assertThat(fetched.finalUri()).isEqualTo(URI.create("https://jobs.test/final"));
        assertThat(fetched.classification()).isEqualTo(PageClassification.FETCHED);
        assertThat(fetched.html()).contains("Backend Platform Engineer");
        assertThat(dnsChecks).hasValue(2);
    }

    @Test
    void loopbackAndRedirectToPrivateAddressAreRejectedBeforeTransport() {
        AtomicInteger calls = new AtomicInteger();
        SecureJobPageFetchAdapter loopback = adapter(
                host -> List.of(address("127.0.0.1")),
                (uri, addresses, deadline) -> {
                    calls.incrementAndGet();
                    return html(200, jobHtml());
                });
        assertFetchFailure(loopback, "https://loopback.test/jobs", "JOB_PAGE_URL_UNSAFE", false);
        assertThat(calls).hasValue(0);

        SecureJobPageFetchAdapter redirected = adapter(
                host -> host.equals("private.test")
                        ? List.of(address("10.0.0.7"))
                        : List.of(PUBLIC),
                (uri, addresses, deadline) -> {
                    calls.incrementAndGet();
                    return response(
                            302,
                            Map.of("Location", List.of("http://private.test/admin")),
                            "");
                });
        assertFetchFailure(
                redirected, "https://public.test/jobs", "JOB_PAGE_URL_UNSAFE", false);
        assertThat(calls).hasValue(1);
    }

    @Test
    void timeoutSizeAndContentTypeFailuresAreSafelyClassified() {
        SecureJobPageFetchAdapter timeout = adapter(
                host -> List.of(PUBLIC),
                (uri, addresses, deadline) -> {
                    throw new HttpTimeoutException("test timeout");
                });
        assertFetchFailure(timeout, "https://example.test/slow", "JOB_PAGE_TIMEOUT", true);

        byte[] oversized = new byte[1025];
        SecureJobPageFetchAdapter tooLarge = adapter(
                host -> List.of(PUBLIC),
                (uri, addresses, deadline) -> response(
                        200,
                        Map.of("Content-Type", List.of("text/html")),
                        new ByteArrayInputStream(oversized)));
        assertFetchFailure(
                tooLarge, "https://example.test/large", "JOB_PAGE_RESPONSE_TOO_LARGE", false);

        byte[] compressed = gzip(new byte[2048]);
        SecureJobPageFetchAdapter decompressedTooLarge = adapter(
                host -> List.of(PUBLIC),
                (uri, addresses, deadline) -> response(
                        200,
                        Map.of(
                                "Content-Type", List.of("text/html"),
                                "Content-Encoding", List.of("gzip"),
                                "Content-Length", List.of(Integer.toString(compressed.length))),
                        new ByteArrayInputStream(compressed)));
        assertFetchFailure(
                decompressedTooLarge,
                "https://example.test/compressed-large",
                "JOB_PAGE_RESPONSE_TOO_LARGE",
                false);

        SecureJobPageFetchAdapter wrongType = adapter(
                host -> List.of(PUBLIC),
                (uri, addresses, deadline) -> response(
                        200,
                        Map.of("Content-Type", List.of("application/json")),
                        "{}"));
        assertFetchFailure(
                wrongType,
                "https://example.test/not-html",
                "JOB_PAGE_CONTENT_TYPE_INVALID",
                false);
    }

    @Test
    void remoteStatusesSeparateManualInputFromRetryableTechnicalFailure() {
        SecureJobPageFetchAdapter forbidden = fixed(response(403, Map.of(), ""));
        assertThat(forbidden.fetch(URI.create("https://example.test/blocked")).classification())
                .isEqualTo(PageClassification.BOT_BLOCKED);

        assertFetchFailure(
                fixed(response(429, Map.of(), "")),
                "https://example.test/rate",
                "JOB_PAGE_REMOTE_TEMPORARY_FAILURE",
                true);
        assertFetchFailure(
                fixed(response(503, Map.of(), "")),
                "https://example.test/down",
                "JOB_PAGE_REMOTE_TEMPORARY_FAILURE",
                true);
        assertFetchFailure(
                fixed(response(404, Map.of(), "")),
                "https://example.test/missing",
                "JOB_PAGE_REMOTE_REJECTED",
                false);
    }

    @Test
    void loginEmptyAndJavascriptShellPagesRequireManualInput() {
        assertThat(fixed(html(
                                200,
                                "<html><title>Sign in</title><body><form>"
                                        + "<input type=password>Sign in to continue</form></body></html>"))
                        .fetch(URI.create("https://example.test/login"))
                        .classification())
                .isEqualTo(PageClassification.LOGIN_REQUIRED);
        assertThat(fixed(html(200, "<html><body>Empty</body></html>"))
                        .fetch(URI.create("https://example.test/empty"))
                        .classification())
                .isEqualTo(PageClassification.EMPTY);
        assertThat(fixed(html(
                                200,
                                "<html><body>Enable JavaScript"
                                        + "<script src=a></script><script src=b></script></body></html>"))
                        .fetch(URI.create("https://example.test/js"))
                        .classification())
                .isEqualTo(PageClassification.JAVASCRIPT_REQUIRED);
    }

    @Test
    void validatedAddressIsPinnedForEveryRedirectAndHostHeaderUsesOriginalAuthority()
            throws Exception {
        InetAddress redirectedAddress = address("93.184.216.35");
        CapturingSocket firstSocket = socketResponse(
                302,
                Map.of("Location", List.of("http://final.test/final")),
                "");
        CapturingSocket secondSocket = socketResponse(
                200,
                Map.of("Content-Type", List.of("text/html; charset=UTF-8")),
                jobHtml());
        Queue<CapturingSocket> sockets =
                new ArrayDeque<>(List.of(firstSocket, secondSocket));
        List<InetAddress> connectedAddresses = new ArrayList<>();
        JobPageFetchProperties properties = properties(Duration.ofMillis(500));
        SecureJobPageFetchAdapter.JdkPinnedHttpTransport transport =
                new SecureJobPageFetchAdapter.JdkPinnedHttpTransport(
                        properties.getConnectTimeout(),
                        (address, port, connectTimeout, deadline) -> {
                            connectedAddresses.add(address);
                            return sockets.remove();
                        },
                        (SSLSocketFactory) SSLSocketFactory.getDefault());
        SecureJobPageFetchAdapter adapter = new SecureJobPageFetchAdapter(
                properties,
                host -> List.of(host.equals("final.test") ? redirectedAddress : PUBLIC),
                transport);

        var fetched = adapter.fetch(URI.create("http://start.test:8080/jobs"));

        assertThat(fetched.finalUri()).isEqualTo(URI.create("http://final.test/final"));
        assertThat(connectedAddresses).containsExactly(PUBLIC, redirectedAddress);
        assertThat(firstSocket.requestText()).contains("\r\nHost: start.test:8080\r\n");
        assertThat(secondSocket.requestText()).contains("\r\nHost: final.test\r\n");
    }

    @Test
    void ipv6HostHeaderIsBracketedAndTlsKeepsOriginalHostnameVerificationAndSni()
            throws Exception {
        InetAddress publicIpv6 = address("2001:4860:4860::8888");
        CapturingSocket ipv6Socket = socketResponse(
                200,
                Map.of("Content-Type", List.of("text/html; charset=UTF-8")),
                jobHtml());
        JobPageFetchProperties properties = properties(Duration.ofMillis(500));
        SecureJobPageFetchAdapter.JdkPinnedHttpTransport transport =
                new SecureJobPageFetchAdapter.JdkPinnedHttpTransport(
                        properties.getConnectTimeout(),
                        (address, port, connectTimeout, deadline) -> ipv6Socket,
                        (SSLSocketFactory) SSLSocketFactory.getDefault());
        SecureJobPageFetchAdapter adapter = new SecureJobPageFetchAdapter(
                properties, host -> List.of(publicIpv6), transport);

        adapter.fetch(URI.create("http://[2001:4860:4860::8888]:8080/jobs"));

        assertThat(ipv6Socket.requestText())
                .contains("\r\nHost: [2001:4860:4860::8888]:8080\r\n");

        SSLParameters dnsParameters =
                SecureJobPageFetchAdapter.JdkPinnedHttpTransport.tlsParameters(
                        "jobs.example.com", new SSLParameters());
        assertThat(dnsParameters.getEndpointIdentificationAlgorithm()).isEqualTo("HTTPS");
        assertThat(dnsParameters.getServerNames())
                .singleElement()
                .isInstanceOfSatisfying(SNIHostName.class, name ->
                        assertThat(name.getAsciiName()).isEqualTo("jobs.example.com"));

        SSLParameters ipParameters =
                SecureJobPageFetchAdapter.JdkPinnedHttpTransport.tlsParameters(
                        "2001:4860:4860::8888", new SSLParameters());
        assertThat(ipParameters.getEndpointIdentificationAlgorithm()).isEqualTo("HTTPS");
        assertThat(ipParameters.getServerNames()).isEmpty();
    }

    @Test
    void responseDeadlineIsAbsoluteAcrossSlowBodyReads() {
        AtomicInteger reads = new AtomicInteger();
        byte[] body = jobHtml().getBytes(StandardCharsets.UTF_8);
        InputStream slowBody = new SlowDripInputStream(body, reads, Duration.ofMillis(20));
        JobPageFetchProperties properties = properties(Duration.ofMillis(60));
        SecureJobPageFetchAdapter adapter = new SecureJobPageFetchAdapter(
                properties,
                host -> List.of(PUBLIC),
                (uri, addresses, deadline) -> response(
                        200,
                        Map.of("Content-Type", List.of("text/html; charset=UTF-8")),
                        slowBody));

        assertFetchFailure(
                adapter, "https://example.test/slow-body", "JOB_PAGE_TIMEOUT", true);
        assertThat(reads.get()).isLessThan(body.length);
    }

    private SecureJobPageFetchAdapter fixed(
            SecureJobPageFetchAdapter.TransportResponse response) {
        return adapter(host -> List.of(PUBLIC), (uri, addresses, deadline) -> response);
    }

    private SecureJobPageFetchAdapter adapter(
            SecureJobPageFetchAdapter.DnsResolver dns,
            SecureJobPageFetchAdapter.HttpTransport transport) {
        return new SecureJobPageFetchAdapter(
                properties(Duration.ofMillis(100)), dns, transport);
    }

    private JobPageFetchProperties properties(Duration responseTimeout) {
        JobPageFetchProperties properties = new JobPageFetchProperties();
        properties.setConnectTimeout(Duration.ofMillis(100));
        properties.setResponseTimeout(responseTimeout);
        properties.setMaxRedirects(3);
        properties.setMaxResponseBytes(1024);
        return properties;
    }

    private void assertFetchFailure(
            SecureJobPageFetchAdapter adapter,
            String url,
            String code,
            boolean retryable) {
        assertThatThrownBy(() -> adapter.fetch(URI.create(url)))
                .isInstanceOfSatisfying(JobPageFetchException.class, failure -> {
                    assertThat(failure.safeErrorCode()).isEqualTo(code);
                    assertThat(failure.retryable()).isEqualTo(retryable);
                });
    }

    private static SecureJobPageFetchAdapter.TransportResponse html(
            int status, String html) {
        return response(
                status,
                Map.of("Content-Type", List.of("text/html; charset=UTF-8")),
                html);
    }

    private static SecureJobPageFetchAdapter.TransportResponse response(
            int status, Map<String, List<String>> headers, String body) {
        return response(
                status,
                headers,
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    private static SecureJobPageFetchAdapter.TransportResponse response(
            int status, Map<String, List<String>> headers, InputStream body) {
        return new SecureJobPageFetchAdapter.TransportResponse(status, headers, body);
    }

    private static CapturingSocket socketResponse(
            int status, Map<String, List<String>> headers, String body) {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        StringBuilder response = new StringBuilder("HTTP/1.1 ")
                .append(status)
                .append(" Test\r\n");
        headers.forEach((name, values) -> values.forEach(value ->
                response.append(name).append(": ").append(value).append("\r\n")));
        response.append("Content-Length: ")
                .append(bodyBytes.length)
                .append("\r\n\r\n");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            bytes.write(response.toString().getBytes(StandardCharsets.US_ASCII));
            bytes.write(bodyBytes);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        return new CapturingSocket(bytes.toByteArray());
    }

    private static InetAddress address(String value) {
        try {
            return InetAddress.getByName(value);
        } catch (java.net.UnknownHostException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] gzip(byte[] value) {
        try {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
                gzip.write(value);
            }
            return output.toByteArray();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String jobHtml() {
        return """
                <html><head><title>Backend Platform Engineer</title></head>
                <body><main>Build reliable Java and Spring services for our recruiting platform.
                Collaborate with product engineers and operate production systems.</main></body></html>
                """;
    }

    private static final class CapturingSocket extends Socket {

        private final InputStream input;
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();

        private CapturingSocket(byte[] response) {
            this.input = new ByteArrayInputStream(response);
        }

        @Override
        public InputStream getInputStream() {
            return input;
        }

        @Override
        public OutputStream getOutputStream() {
            return output;
        }

        @Override
        public synchronized void setSoTimeout(int timeout) {
            // The deterministic in-memory response never blocks.
        }

        private String requestText() {
            return output.toString(StandardCharsets.US_ASCII);
        }
    }

    private static final class SlowDripInputStream extends InputStream {

        private final byte[] content;
        private final AtomicInteger reads;
        private final long delayMillis;
        private int offset;

        private SlowDripInputStream(
                byte[] content, AtomicInteger reads, Duration delay) {
            this.content = content;
            this.reads = reads;
            this.delayMillis = delay.toMillis();
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int read = read(one, 0, 1);
            return read < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] target, int targetOffset, int length)
                throws IOException {
            if (offset >= content.length) {
                return -1;
            }
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("slow body interrupted", exception);
            }
            reads.incrementAndGet();
            target[targetOffset] = content[offset++];
            return 1;
        }
    }
}
