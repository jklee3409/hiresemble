package com.hiresemble.githubsource.infrastructure;

import com.hiresemble.common.exception.BusinessException;
import com.hiresemble.common.exception.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "hiresemble.github.private-enabled", havingValue = "true")
public final class GitHubAppJwtSigner {

    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private final long appId;
    private final PrivateKey privateKey;
    private final Clock clock;

    @Autowired
    public GitHubAppJwtSigner(GitHubProperties properties, Clock clock) {
        this(properties.getApp().getAppId(), properties.getApp().getPrivateKey(), clock);
    }

    GitHubAppJwtSigner(long appId, String privateKeyPem, Clock clock) {
        this.appId = appId;
        this.privateKey = parse(privateKeyPem);
        this.clock = clock;
    }

    public String create() {
        long now = clock.instant().getEpochSecond();
        String header = encode("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String payload = encode("{\"iat\":" + (now - 60) + ",\"exp\":" + (now + 540)
                + ",\"iss\":" + appId + "}");
        String signingInput = header + "." + payload;
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + BASE64_URL.encodeToString(signature.sign());
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.GITHUB_UPSTREAM_AUTHENTICATION_FAILED, exception);
        }
    }

    private String encode(String value) {
        return BASE64_URL.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static PrivateKey parse(String pem) {
        try {
            String normalized = pem.replace("\\n", "\n").trim();
            boolean pkcs1 = normalized.contains("BEGIN RSA PRIVATE KEY");
            String body = normalized
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = Base64.getDecoder().decode(body);
            byte[] pkcs8 = pkcs1 ? wrapPkcs1(decoded) : decoded;
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (Exception exception) {
            throw new IllegalStateException("GitHub App private key is invalid", exception);
        }
    }

    private static byte[] wrapPkcs1(byte[] pkcs1) {
        byte[] version = new byte[] {0x02, 0x01, 0x00};
        byte[] rsaAlgorithm = new byte[] {
            0x30, 0x0d, 0x06, 0x09, 0x2a, (byte) 0x86, 0x48, (byte) 0x86,
            (byte) 0xf7, 0x0d, 0x01, 0x01, 0x01, 0x05, 0x00
        };
        byte[] octet = der((byte) 0x04, pkcs1);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(version);
        body.writeBytes(rsaAlgorithm);
        body.writeBytes(octet);
        return der((byte) 0x30, body.toByteArray());
    }

    private static byte[] der(byte tag, byte[] value) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(tag);
        if (value.length < 128) {
            output.write(value.length);
        } else {
            int lengthBytes = 0;
            int remaining = value.length;
            while (remaining > 0) {
                lengthBytes++;
                remaining >>>= 8;
            }
            output.write(0x80 | lengthBytes);
            for (int shift = (lengthBytes - 1) * 8; shift >= 0; shift -= 8) {
                output.write((value.length >>> shift) & 0xff);
            }
        }
        output.writeBytes(value);
        return output.toByteArray();
    }
}
