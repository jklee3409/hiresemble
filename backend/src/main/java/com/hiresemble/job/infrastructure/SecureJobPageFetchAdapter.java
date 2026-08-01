package com.hiresemble.job.infrastructure;

import com.hiresemble.job.application.port.JobPageFetchException;
import com.hiresemble.job.application.port.JobImageFetchGateway;
import com.hiresemble.job.application.port.JobImageFetchGateway.ImageAsset;
import com.hiresemble.job.application.port.JobImageFetchGateway.ImageCandidate;
import com.hiresemble.job.application.port.JobPageFetchGateway;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Iterator;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public final class SecureJobPageFetchAdapter implements JobPageFetchGateway, JobImageFetchGateway {

    private static final List<String> HTML_TYPES =
            List.of("text/html", "application/xhtml+xml");
    private final JobPageFetchProperties properties;
    private final DnsResolver dnsResolver;
    private final HttpTransport transport;
    private final HtmlCharsetDecoder charsetDecoder = new HtmlCharsetDecoder();

    SecureJobPageFetchAdapter(JobPageFetchProperties properties) {
        this(
                properties,
                host -> List.of(InetAddress.getAllByName(host)),
                new JdkPinnedHttpTransport(
                        properties.getConnectTimeout(),
                        SecureJobPageFetchAdapter::connectSocket,
                        (SSLSocketFactory) SSLSocketFactory.getDefault()));
    }

    SecureJobPageFetchAdapter(
            JobPageFetchProperties properties,
            DnsResolver dnsResolver,
            HttpTransport transport) {
        properties.validate();
        this.properties = properties;
        this.dnsResolver = dnsResolver;
        this.transport = transport;
    }

    @Override
    public FetchResult fetch(URI requestedUri) {
        URI current = requestedUri;
        ResponseDeadline deadline =
                ResponseDeadline.start(properties.getResponseTimeout());
        for (int redirect = 0; redirect <= properties.getMaxRedirects(); redirect++) {
            List<InetAddress> validatedAddresses = validateUriAndDns(current);
            TransportResponse response;
            try {
                response = transport.get(current, validatedAddresses, deadline);
            } catch (HttpTimeoutException exception) {
                throw failure("JOB_PAGE_TIMEOUT", true, exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw failure("JOB_PAGE_FETCH_INTERRUPTED", true, exception);
            } catch (IOException exception) {
                throw failure("JOB_PAGE_NETWORK_ERROR", true, exception);
            }
            int status = response.status();
            if (isRedirect(status)) {
                close(response.body());
                if (redirect == properties.getMaxRedirects()) {
                    throw failure("JOB_PAGE_REDIRECT_LIMIT", false, null);
                }
                String location = header(response.headers(), "location")
                        .orElseThrow(() -> failure("JOB_PAGE_REDIRECT_INVALID", false, null));
                try {
                    current = current.resolve(location);
                } catch (IllegalArgumentException exception) {
                    throw failure("JOB_PAGE_REDIRECT_INVALID", false, exception);
                }
                continue;
            }
            if (status == 403) {
                close(response.body());
                return new FetchResult(current, PageClassification.BOT_BLOCKED, null, status, null);
            }
            if (status == 429 || status >= 500) {
                close(response.body());
                throw failure("JOB_PAGE_REMOTE_TEMPORARY_FAILURE", true, null);
            }
            if (status < 200 || status >= 300) {
                close(response.body());
                throw failure("JOB_PAGE_REMOTE_REJECTED", false, null);
            }
            String contentType = header(response.headers(), "content-type").orElse("");
            if (HTML_TYPES.stream().noneMatch(type ->
                    contentType.toLowerCase(Locale.ROOT).startsWith(type))) {
                close(response.body());
                throw failure("JOB_PAGE_CONTENT_TYPE_INVALID", false, null);
            }
            long declaredLength = header(response.headers(), "content-length")
                    .flatMap(this::longValue)
                    .orElse(-1L);
            if (declaredLength > properties.getMaxResponseBytes()) {
                close(response.body());
                throw failure("JOB_PAGE_RESPONSE_TOO_LARGE", false, null);
            }
            byte[] content = readLimited(
                    decodedBody(response),
                    properties.getMaxResponseBytes(),
                    deadline);
            HtmlCharsetDecoder.DecodedHtml decoded = charsetDecoder.decode(content, contentType);
            String html = decoded.html();
            PageClassification classification = classify(html, current);
            return new FetchResult(
                    current,
                    classification,
                    classification == PageClassification.FETCHED ? html : null,
                    status,
                    decoded.metadata());
        }
        throw failure("JOB_PAGE_REDIRECT_LIMIT", false, null);
    }

    @Override
    public ImageAsset fetch(ImageCandidate candidate, Duration remainingDeadline) {
        if (candidate == null || candidate.imageRef() == null || candidate.uri() == null) {
            throw failure("JOB_IMAGE_REQUEST_INVALID", false, null);
        }
        if (remainingDeadline == null || remainingDeadline.isZero() || remainingDeadline.isNegative()) {
            throw failure("JOB_IMAGE_TIMEOUT", true, null);
        }
        URI current = candidate.uri();
        Duration fetchBudget = remainingDeadline.compareTo(properties.getImageResponseTimeout()) < 0
                ? remainingDeadline
                : properties.getImageResponseTimeout();
        ResponseDeadline deadline = ResponseDeadline.start(fetchBudget);
        for (int redirect = 0; redirect <= properties.getMaxRedirects(); redirect++) {
            List<InetAddress> validatedAddresses = validateUriAndDns(current);
            TransportResponse response;
            try {
                response = transport.get(current, validatedAddresses, deadline);
            } catch (HttpTimeoutException | SocketTimeoutException exception) {
                throw failure("JOB_IMAGE_TIMEOUT", true, exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw failure("JOB_IMAGE_FETCH_INTERRUPTED", true, exception);
            } catch (IOException exception) {
                throw failure("JOB_IMAGE_NETWORK_ERROR", true, exception);
            }
            int status = response.status();
            if (isRedirect(status)) {
                close(response.body());
                if (redirect == properties.getMaxRedirects()) {
                    throw failure("JOB_IMAGE_REDIRECT_LIMIT", false, null);
                }
                String location = header(response.headers(), "location")
                        .orElseThrow(() -> failure("JOB_IMAGE_REDIRECT_INVALID", false, null));
                try {
                    current = current.resolve(location);
                } catch (IllegalArgumentException exception) {
                    throw failure("JOB_IMAGE_REDIRECT_INVALID", false, exception);
                }
                continue;
            }
            if (status == 429 || status >= 500) {
                close(response.body());
                throw failure("JOB_IMAGE_REMOTE_TEMPORARY_FAILURE", true, null);
            }
            if (status < 200 || status >= 300) {
                close(response.body());
                throw failure("JOB_IMAGE_REMOTE_REJECTED", false, null);
            }
            String contentType = header(response.headers(), "content-type")
                    .orElse("")
                    .split(";", 2)[0]
                    .strip()
                    .toLowerCase(Locale.ROOT);
            if (!contentType.equals("image/jpeg")
                    && !contentType.equals("image/png")
                    && !contentType.equals("image/webp")) {
                close(response.body());
                throw failure("JOB_IMAGE_CONTENT_TYPE_INVALID", false, null);
            }
            long declaredLength = header(response.headers(), "content-length")
                    .flatMap(this::longValue)
                    .orElse(-1L);
            if (declaredLength > properties.getMaxImageBytes()) {
                close(response.body());
                throw failure("JOB_IMAGE_TOO_LARGE", false, null);
            }
            byte[] bytes;
            try {
                bytes = readLimited(decodedBody(response), properties.getMaxImageBytes(), deadline);
            } catch (JobPageFetchException failure) {
                throw remapImageFailure(failure);
            }
            if (!magicMatches(contentType, bytes)) {
                throw failure("JOB_IMAGE_MAGIC_MISMATCH", false, null);
            }
            if (contentType.equals("image/webp") && animatedWebp(bytes)) {
                throw failure("JOB_IMAGE_ANIMATION_UNSUPPORTED", false, null);
            }
            ImageDimensions dimensions = decodeDimensions(bytes);
            return new ImageAsset(
                    candidate.imageRef(),
                    contentType,
                    bytes,
                    dimensions.width(),
                    dimensions.height(),
                    sha256(bytes));
        }
        throw failure("JOB_IMAGE_REDIRECT_LIMIT", false, null);
    }

    private List<InetAddress> validateUriAndDns(URI uri) {
        if (uri == null
                || !uri.isAbsolute()
                || uri.getHost() == null
                || uri.getRawUserInfo() != null
                || uri.getRawAuthority() == null
                || uri.getRawAuthority().contains("@")
                || uri.getPort() == 0
                || (!"http".equalsIgnoreCase(uri.getScheme())
                        && !"https".equalsIgnoreCase(uri.getScheme()))) {
            throw failure("JOB_PAGE_URL_UNSAFE", false, null);
        }
        List<InetAddress> addresses;
        try {
            addresses = dnsResolver.resolve(tlsHost(uri));
        } catch (IOException exception) {
            throw failure("JOB_PAGE_DNS_FAILURE", true, exception);
        }
        if (addresses == null
                || addresses.isEmpty()
                || addresses.stream().anyMatch(address -> address == null || blocked(address))) {
            throw failure(
                    addresses == null || addresses.isEmpty()
                            ? "JOB_PAGE_DNS_FAILURE"
                            : "JOB_PAGE_URL_UNSAFE",
                    addresses == null || addresses.isEmpty(),
                    null);
        }
        return List.copyOf(addresses);
    }

    private boolean blocked(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 0
                    || first == 10
                    || first == 127
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 169 && second == 254)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && second == 168)
                    || first >= 224;
        }
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return (first & 0xfe) == 0xfc
                || (first == 0xfe && (second & 0xc0) == 0x80)
                || first == 0xff;
    }

    private PageClassification classify(String html, URI baseUri) {
        Document document = Jsoup.parse(html, baseUri.toASCIIString());
        String text = document.text().strip();
        String lower = (document.title() + " " + text).toLowerCase(Locale.ROOT);
        boolean password = !document.select("input[type=password]").isEmpty();
        if (password
                || lower.contains("sign in")
                || lower.contains("log in")
                || lower.contains("로그인")) {
            return PageClassification.LOGIN_REQUIRED;
        }
        if (lower.contains("captcha")
                || lower.contains("verify you are human")
                || lower.contains("access denied")
                || lower.contains("cloudflare")
                || lower.contains("bot detection")) {
            return PageClassification.BOT_BLOCKED;
        }
        String lowerHtml = html.toLowerCase(Locale.ROOT);
        boolean imageCandidates = !document.select(
                        "img[src],img[srcset],img[data-src],img[data-original],picture source[srcset],[style*=background-image]")
                .isEmpty();
        if (lower.contains("enable javascript")
                || lower.contains("javascript is required")
                || (text.length() < 80
                        && lowerHtml.contains("<script")
                        && document.select("script").size() >= 2
                        && !imageCandidates)) {
            return PageClassification.JAVASCRIPT_REQUIRED;
        }
        if (text.length() < 40 && !imageCandidates) {
            return PageClassification.EMPTY;
        }
        return PageClassification.FETCHED;
    }

    private InputStream decodedBody(TransportResponse response) {
        String encoding = header(response.headers(), "content-encoding")
                .orElse("")
                .toLowerCase(Locale.ROOT)
                .trim();
        try {
            return switch (encoding) {
                case "", "identity" -> response.body();
                case "gzip" -> new GZIPInputStream(response.body());
                case "deflate" -> new InflaterInputStream(response.body());
                default -> throw failure("JOB_PAGE_CONTENT_ENCODING_INVALID", false, null);
            };
        } catch (HttpTimeoutException exception) {
            close(response.body());
            throw failure("JOB_PAGE_TIMEOUT", true, exception);
        } catch (IOException exception) {
            close(response.body());
            throw failure("JOB_PAGE_CONTENT_ENCODING_INVALID", false, exception);
        }
    }

    private byte[] readLimited(
            InputStream input, int maximum, ResponseDeadline deadline) {
        try (input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while (true) {
                deadline.requireRemaining();
                read = input.read(buffer);
                deadline.requireRemaining();
                if (read < 0) {
                    return output.toByteArray();
                }
                total += read;
                if (total > maximum) {
                    throw failure("JOB_PAGE_RESPONSE_TOO_LARGE", false, null);
                }
                output.write(buffer, 0, read);
            }
        } catch (JobPageFetchException exception) {
            throw exception;
        } catch (HttpTimeoutException | SocketTimeoutException exception) {
            throw failure("JOB_PAGE_TIMEOUT", true, exception);
        } catch (IOException exception) {
            throw failure("JOB_PAGE_NETWORK_ERROR", true, exception);
        }
    }

    private boolean magicMatches(String contentType, byte[] bytes) {
        if (contentType.equals("image/png")) {
            return bytes.length >= 8
                    && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N'
                    && bytes[3] == 'G' && bytes[4] == 0x0d && bytes[5] == 0x0a
                    && bytes[6] == 0x1a && bytes[7] == 0x0a;
        }
        if (contentType.equals("image/webp")) {
            return bytes.length >= 20
                    && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                    && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P'
                    && unsignedLittleEndianInt(bytes, 4) == bytes.length - 8L;
        }
        return bytes.length >= 4
                && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8
                && (bytes[bytes.length - 2] & 0xff) == 0xff
                && (bytes[bytes.length - 1] & 0xff) == 0xd9;
    }

    private ImageDimensions decodeDimensions(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw failure("JOB_IMAGE_DECODE_INVALID", false, null);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw failure("JOB_IMAGE_DECODE_INVALID", false, null);
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0
                        || (long) width * height > properties.getMaxImagePixels()) {
                    throw failure("JOB_IMAGE_DIMENSIONS_INVALID", false, null);
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null || decoded.getWidth() != width || decoded.getHeight() != height) {
                    throw failure("JOB_IMAGE_DECODE_INVALID", false, null);
                }
                return new ImageDimensions(width, height);
            } finally {
                reader.dispose();
            }
        } catch (JobPageFetchException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw failure("JOB_IMAGE_DECODE_INVALID", false, exception);
        }
    }

    private boolean animatedWebp(byte[] bytes) {
        int offset = 12;
        while (offset + 8 <= bytes.length) {
            String chunk = new String(bytes, offset, 4, StandardCharsets.US_ASCII);
            long size = unsignedLittleEndianInt(bytes, offset + 4);
            long next = (long) offset + 8L + size + (size & 1L);
            if (next > bytes.length || next <= offset) return true;
            if ("ANIM".equals(chunk) || "ANMF".equals(chunk)) return true;
            offset = (int) next;
        }
        return offset != bytes.length;
    }

    private long unsignedLittleEndianInt(byte[] value, int offset) {
        if (offset < 0 || offset + 4 > value.length) return -1L;
        return (value[offset] & 0xffL)
                | ((value[offset + 1] & 0xffL) << 8)
                | ((value[offset + 2] & 0xffL) << 16)
                | ((value[offset + 3] & 0xffL) << 24);
    }

    private JobPageFetchException remapImageFailure(JobPageFetchException failure) {
        String code = switch (failure.safeErrorCode()) {
            case "JOB_PAGE_TIMEOUT" -> "JOB_IMAGE_TIMEOUT";
            case "JOB_PAGE_RESPONSE_TOO_LARGE" -> "JOB_IMAGE_TOO_LARGE";
            case "JOB_PAGE_CONTENT_ENCODING_INVALID" -> "JOB_IMAGE_CONTENT_ENCODING_INVALID";
            default -> "JOB_IMAGE_NETWORK_ERROR";
        };
        return new JobPageFetchException(code, failure.retryable(), failure);
    }

    private String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private Optional<String> header(Map<String, List<String>> headers, String name) {
        return headers.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .flatMap(entry -> entry.getValue().stream())
                .findFirst();
    }

    private Optional<Long> longValue(String value) {
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private void close(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // The response has already been rejected and no body is retained.
        }
    }

    private JobPageFetchException failure(String code, boolean retryable, Throwable cause) {
        return new JobPageFetchException(code, retryable, cause);
    }

    private static Socket connectSocket(
            InetAddress address,
            int port,
            Duration timeout,
            ResponseDeadline deadline)
            throws IOException {
        deadline.requireRemaining();
        Socket socket = new Socket();
        try {
            socket.connect(
                    new InetSocketAddress(address, port),
                    boundedMillis(Math.min(toNanos(timeout), deadline.remainingNanos())));
            return socket;
        } catch (IOException failure) {
            try {
                socket.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private static int boundedMillis(long nanos) throws HttpTimeoutException {
        if (nanos <= 0) {
            throw new HttpTimeoutException("job page response deadline exceeded");
        }
        long millis = Math.max(1L, (nanos + 999_999L) / 1_000_000L);
        return (int) Math.min(Integer.MAX_VALUE, millis);
    }

    private static long toNanos(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static String tlsHost(URI uri) {
        String host = uri.getHost();
        if (host != null && host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        return host;
    }

    @FunctionalInterface
    interface DnsResolver {
        List<InetAddress> resolve(String host) throws IOException;
    }

    @FunctionalInterface
    interface HttpTransport {
        TransportResponse get(
                URI uri,
                List<InetAddress> validatedAddresses,
                ResponseDeadline deadline)
                throws IOException, InterruptedException;
    }

    @FunctionalInterface
    interface SocketConnector {
        Socket connect(
                InetAddress address,
                int port,
                Duration connectTimeout,
                ResponseDeadline deadline)
                throws IOException;
    }

    record TransportResponse(
            int status,
            Map<String, List<String>> headers,
            InputStream body) {}

    static final class ResponseDeadline {

        private final long startedNanos;
        private final long timeoutNanos;

        private ResponseDeadline(long startedNanos, long timeoutNanos) {
            this.startedNanos = startedNanos;
            this.timeoutNanos = timeoutNanos;
        }

        static ResponseDeadline start(Duration timeout) {
            return new ResponseDeadline(System.nanoTime(), toNanos(timeout));
        }

        long remainingNanos() {
            long elapsed = System.nanoTime() - startedNanos;
            return timeoutNanos - Math.max(0L, elapsed);
        }

        void requireRemaining() throws HttpTimeoutException {
            if (remainingNanos() <= 0) {
                throw new HttpTimeoutException("job page response deadline exceeded");
            }
        }

        int remainingMillis() throws HttpTimeoutException {
            return boundedMillis(remainingNanos());
        }
    }

    static final class JdkPinnedHttpTransport implements HttpTransport {

        private static final int MAX_HEADER_BYTES = 64 * 1024;
        private static final int MAX_HEADER_COUNT = 100;
        private final Duration connectTimeout;
        private final SocketConnector connector;
        private final SSLSocketFactory sslSocketFactory;

        JdkPinnedHttpTransport(
                Duration connectTimeout,
                SocketConnector connector,
                SSLSocketFactory sslSocketFactory) {
            this.connectTimeout = connectTimeout;
            this.connector = connector;
            this.sslSocketFactory = sslSocketFactory;
        }

        @Override
        public TransportResponse get(
                URI uri,
                List<InetAddress> validatedAddresses,
                ResponseDeadline deadline)
                throws IOException {
            if (validatedAddresses == null || validatedAddresses.isEmpty()) {
                throw new IOException("validated DNS addresses are required");
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            String host = tlsHost(uri);
            int port = uri.getPort() < 0
                    ? ("https".equals(scheme) ? 443 : 80)
                    : uri.getPort();
            Socket socket = connect(validatedAddresses, port, deadline);
            try {
                if ("https".equals(scheme)) {
                    socket = secure(socket, host, port, deadline);
                }
                DeadlineInputStream input =
                        new DeadlineInputStream(socket.getInputStream(), socket, deadline);
                writeRequest(socket.getOutputStream(), uri, host, port, deadline);
                HeaderBlock headerBlock = readHeaders(input, deadline);
                InputStream body = responseBody(
                        headerBlock.status(), headerBlock.headers(), input, socket);
                return new TransportResponse(
                        headerBlock.status(), headerBlock.headers(), body);
            } catch (SocketTimeoutException timeout) {
                closeSocket(socket, timeout);
                throw timeout(timeout);
            } catch (IOException failure) {
                closeSocket(socket, failure);
                throw failure;
            } catch (RuntimeException failure) {
                closeSocket(socket, failure);
                throw failure;
            }
        }

        private Socket connect(
                List<InetAddress> validatedAddresses,
                int port,
                ResponseDeadline responseDeadline)
                throws IOException {
            ResponseDeadline connectionDeadline =
                    ResponseDeadline.start(connectTimeout);
            IOException lastFailure = null;
            for (InetAddress address : validatedAddresses) {
                responseDeadline.requireRemaining();
                connectionDeadline.requireRemaining();
                long remaining = Math.min(
                        responseDeadline.remainingNanos(),
                        connectionDeadline.remainingNanos());
                try {
                    return connector.connect(
                            address,
                            port,
                            Duration.ofNanos(remaining),
                            responseDeadline);
                } catch (SocketTimeoutException failure) {
                    lastFailure = timeout(failure);
                } catch (IOException failure) {
                    lastFailure = failure;
                }
            }
            if (lastFailure instanceof HttpTimeoutException timeout) {
                throw timeout;
            }
            if (connectionDeadline.remainingNanos() <= 0) {
                throw new HttpTimeoutException("job page connection timeout");
            }
            throw lastFailure == null
                    ? new IOException("no validated address could be connected")
                    : lastFailure;
        }

        private Socket secure(
                Socket rawSocket,
                String host,
                int port,
                ResponseDeadline deadline)
                throws IOException {
            SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(
                    rawSocket, host, port, true);
            sslSocket.setSSLParameters(tlsParameters(host, sslSocket.getSSLParameters()));
            sslSocket.setSoTimeout(deadline.remainingMillis());
            sslSocket.startHandshake();
            deadline.requireRemaining();
            return sslSocket;
        }

        static SSLParameters tlsParameters(String host, SSLParameters parameters) {
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            if (isIpLiteral(host)) {
                parameters.setServerNames(List.of());
            } else {
                parameters.setServerNames(List.of(new SNIHostName(host)));
            }
            return parameters;
        }

        private void writeRequest(
                OutputStream output,
                URI uri,
                String host,
                int port,
                ResponseDeadline deadline)
                throws IOException {
            deadline.requireRemaining();
            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            String target = uri.getRawQuery() == null
                    ? path
                    : path + "?" + uri.getRawQuery();
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            boolean defaultPort = ("http".equals(scheme) && port == 80)
                    || ("https".equals(scheme) && port == 443);
            String hostValue = host.indexOf(':') >= 0 ? "[" + host + "]" : host;
            if (!defaultPort) {
                hostValue += ":" + port;
            }
            String request = "GET " + target + " HTTP/1.1\r\n"
                    + "Host: " + hostValue + "\r\n"
                    + "Accept: text/html,application/xhtml+xml,image/jpeg,image/png,image/webp\r\n"
                    + "Accept-Encoding: gzip, deflate\r\n"
                    + "User-Agent: HiresembleJobFetcher/1.0\r\n"
                    + "Connection: close\r\n\r\n";
            output.write(request.getBytes(StandardCharsets.US_ASCII));
            output.flush();
            deadline.requireRemaining();
        }

        private HeaderBlock readHeaders(
                DeadlineInputStream input, ResponseDeadline deadline)
                throws IOException {
            HeaderBudget budget = new HeaderBudget(MAX_HEADER_BYTES);
            String statusLine = readLine(input, budget, 8 * 1024);
            if (statusLine == null || !statusLine.startsWith("HTTP/1.")) {
                throw new IOException("invalid HTTP status line");
            }
            String[] statusParts = statusLine.split(" ", 3);
            if (statusParts.length < 2
                    || statusParts[1].length() != 3
                    || !statusParts[1].chars().allMatch(Character::isDigit)) {
                throw new IOException("invalid HTTP status");
            }
            int status = Integer.parseInt(statusParts[1]);
            Map<String, List<String>> mutableHeaders = new LinkedHashMap<>();
            int count = 0;
            while (true) {
                deadline.requireRemaining();
                String line = readLine(input, budget, 8 * 1024);
                if (line == null) {
                    throw new IOException("unexpected EOF in HTTP headers");
                }
                if (line.isEmpty()) {
                    break;
                }
                if (++count > MAX_HEADER_COUNT || line.charAt(0) == ' ' || line.charAt(0) == '\t') {
                    throw new IOException("invalid HTTP headers");
                }
                int separator = line.indexOf(':');
                if (separator <= 0) {
                    throw new IOException("invalid HTTP header");
                }
                String name = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
                String value = line.substring(separator + 1).trim();
                if (!validHeaderName(name) || containsControl(value)) {
                    throw new IOException("invalid HTTP header");
                }
                mutableHeaders
                        .computeIfAbsent(name, ignored -> new ArrayList<>())
                        .add(value);
            }
            Map<String, List<String>> headers = new LinkedHashMap<>();
            mutableHeaders.forEach((name, values) ->
                    headers.put(name, List.copyOf(values)));
            return new HeaderBlock(status, Map.copyOf(headers));
        }

        private InputStream responseBody(
                int status,
                Map<String, List<String>> headers,
                DeadlineInputStream input,
                Socket socket)
                throws IOException {
            if ((status >= 100 && status < 200) || status == 204 || status == 304) {
                socket.close();
                return new ByteArrayInputStream(new byte[0]);
            }
            String transferEncoding = first(headers, "transfer-encoding").orElse("");
            if (!transferEncoding.isBlank()) {
                if (!"chunked".equalsIgnoreCase(transferEncoding.trim())) {
                    throw new IOException("unsupported HTTP transfer encoding");
                }
                return new ChunkedInputStream(input);
            }
            long contentLength = contentLength(headers);
            return contentLength >= 0
                    ? new FixedLengthInputStream(input, contentLength)
                    : input;
        }

        private long contentLength(Map<String, List<String>> headers)
                throws IOException {
            List<String> values = headers.getOrDefault("content-length", List.of());
            if (values.isEmpty()) {
                return -1;
            }
            long parsed = -1;
            for (String value : values) {
                long current;
                try {
                    current = Long.parseLong(value);
                } catch (NumberFormatException failure) {
                    throw new IOException("invalid content length", failure);
                }
                if (current < 0 || (parsed >= 0 && parsed != current)) {
                    throw new IOException("ambiguous content length");
                }
                parsed = current;
            }
            return parsed;
        }

        private static Optional<String> first(
                Map<String, List<String>> headers, String name) {
            return headers.getOrDefault(name, List.of()).stream().findFirst();
        }

        private String readLine(
                InputStream input, HeaderBudget budget, int lineLimit)
                throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            while (true) {
                int value = input.read();
                if (value < 0) {
                    return line.size() == 0
                            ? null
                            : line.toString(StandardCharsets.US_ASCII);
                }
                budget.consume();
                if (value == '\n') {
                    byte[] bytes = line.toByteArray();
                    int length = bytes.length;
                    if (length > 0 && bytes[length - 1] == '\r') {
                        length--;
                    }
                    return new String(bytes, 0, length, StandardCharsets.US_ASCII);
                }
                if (line.size() >= lineLimit) {
                    throw new IOException("HTTP header line too large");
                }
                line.write(value);
            }
        }

        private boolean validHeaderName(String name) {
            return !name.isBlank()
                    && name.chars().allMatch(value ->
                            value > 32 && value < 127 && "()<>@,;:\\\"/[]?={} \t"
                                    .indexOf(value) < 0);
        }

        private boolean containsControl(String value) {
            return value.chars().anyMatch(character ->
                    character == '\r'
                            || character == '\n'
                            || (character < 32 && character != '\t')
                            || character == 127);
        }

        private static boolean isIpLiteral(String host) {
            if (host.indexOf(':') >= 0) {
                return true;
            }
            String[] parts = host.split("\\.", -1);
            if (parts.length != 4) {
                return false;
            }
            for (String part : parts) {
                if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit)) {
                    return false;
                }
                if (Integer.parseInt(part) > 255) {
                    return false;
                }
            }
            return true;
        }

        private static HttpTimeoutException timeout(Throwable cause) {
            HttpTimeoutException timeout =
                    new HttpTimeoutException("job page response deadline exceeded");
            timeout.initCause(cause);
            return timeout;
        }

        private static void closeSocket(Socket socket, Throwable failure) {
            try {
                socket.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
        }

        private record HeaderBlock(int status, Map<String, List<String>> headers) {}

        private static final class HeaderBudget {

            private int remaining;

            private HeaderBudget(int remaining) {
                this.remaining = remaining;
            }

            private void consume() throws IOException {
                if (--remaining < 0) {
                    throw new IOException("HTTP headers too large");
                }
            }
        }
    }

    private record ImageDimensions(int width, int height) {}

    private static final class DeadlineInputStream extends FilterInputStream {

        private final Socket socket;
        private final ResponseDeadline deadline;

        private DeadlineInputStream(
                InputStream delegate, Socket socket, ResponseDeadline deadline) {
            super(delegate);
            this.socket = socket;
            this.deadline = deadline;
        }

        @Override
        public int read() throws IOException {
            configureTimeout();
            try {
                int value = super.read();
                deadline.requireRemaining();
                return value;
            } catch (SocketTimeoutException timeout) {
                throw JdkPinnedHttpTransport.timeout(timeout);
            }
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            configureTimeout();
            try {
                int read = super.read(target, offset, length);
                deadline.requireRemaining();
                return read;
            } catch (SocketTimeoutException timeout) {
                throw JdkPinnedHttpTransport.timeout(timeout);
            }
        }

        private void configureTimeout() throws IOException {
            deadline.requireRemaining();
            socket.setSoTimeout(deadline.remainingMillis());
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private static final class FixedLengthInputStream extends FilterInputStream {

        private long remaining;

        private FixedLengthInputStream(InputStream delegate, long remaining) {
            super(delegate);
            this.remaining = remaining;
        }

        @Override
        public int read() throws IOException {
            if (remaining == 0) {
                return -1;
            }
            int value = super.read();
            if (value < 0) {
                throw new IOException("unexpected EOF in HTTP body");
            }
            remaining--;
            return value;
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            if (remaining == 0) {
                return -1;
            }
            int requested = (int) Math.min(length, remaining);
            int read = super.read(target, offset, requested);
            if (read < 0) {
                throw new IOException("unexpected EOF in HTTP body");
            }
            remaining -= read;
            return read;
        }
    }

    private static final class ChunkedInputStream extends FilterInputStream {

        private long chunkRemaining;
        private boolean complete;

        private ChunkedInputStream(InputStream delegate) {
            super(delegate);
        }

        @Override
        public int read() throws IOException {
            byte[] single = new byte[1];
            int read = read(single, 0, 1);
            return read < 0 ? -1 : single[0] & 0xff;
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            if (complete) {
                return -1;
            }
            if (chunkRemaining == 0) {
                beginChunk();
                if (complete) {
                    return -1;
                }
            }
            int requested = (int) Math.min(length, chunkRemaining);
            int read = in.read(target, offset, requested);
            if (read < 0) {
                throw new IOException("unexpected EOF in chunked body");
            }
            chunkRemaining -= read;
            if (chunkRemaining == 0) {
                requireCrlf();
            }
            return read;
        }

        private void beginChunk() throws IOException {
            String line = readChunkLine(1024);
            int extension = line.indexOf(';');
            String size = (extension < 0 ? line : line.substring(0, extension)).trim();
            try {
                chunkRemaining = Long.parseLong(size, 16);
            } catch (NumberFormatException failure) {
                throw new IOException("invalid chunk size", failure);
            }
            if (chunkRemaining < 0) {
                throw new IOException("invalid chunk size");
            }
            if (chunkRemaining == 0) {
                int trailerBytes = 0;
                while (true) {
                    String trailer = readChunkLine(8 * 1024);
                    trailerBytes += trailer.length();
                    if (trailerBytes > 64 * 1024) {
                        throw new IOException("chunk trailers too large");
                    }
                    if (trailer.isEmpty()) {
                        complete = true;
                        return;
                    }
                }
            }
        }

        private String readChunkLine(int maximum) throws IOException {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            while (line.size() <= maximum) {
                int value = in.read();
                if (value < 0) {
                    throw new IOException("unexpected EOF in chunked body");
                }
                if (value == '\n') {
                    byte[] bytes = line.toByteArray();
                    int length = bytes.length;
                    if (length == 0 || bytes[length - 1] != '\r') {
                        throw new IOException("invalid chunk delimiter");
                    }
                    return new String(
                            bytes, 0, length - 1, StandardCharsets.US_ASCII);
                }
                line.write(value);
            }
            throw new IOException("chunk line too large");
        }

        private void requireCrlf() throws IOException {
            if (in.read() != '\r' || in.read() != '\n') {
                throw new IOException("invalid chunk delimiter");
            }
        }
    }
}
