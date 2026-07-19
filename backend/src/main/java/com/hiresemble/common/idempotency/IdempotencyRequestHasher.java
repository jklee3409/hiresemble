package com.hiresemble.common.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyRequestHasher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final IdempotencyProperties properties;

    public IdempotencyRequestHasher(IdempotencyProperties properties) {
        this.properties = properties;
    }

    public int currentVersion() {
        return properties.getCurrentHashKeyVersion();
    }

    public String hash(int version, IdempotencyScope scope, byte[] canonicalRequest) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.keyFor(version).getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            mac.update(scope.httpMethod().getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '|');
            mac.update(scope.routeScope().getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '|');
            mac.update(canonicalRequest);
            return HexFormat.of().formatHex(mac.doFinal());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable", exception);
        }
    }
}
