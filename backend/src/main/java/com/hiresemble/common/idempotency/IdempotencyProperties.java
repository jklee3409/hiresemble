package com.hiresemble.common.idempotency;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "hiresemble.idempotency")
public class IdempotencyProperties {

    private int currentHashKeyVersion = 1;
    private Map<Integer, String> hmacKeys = new HashMap<>();
    private Duration ttl = Duration.ofHours(24);

    public int getCurrentHashKeyVersion() {
        return currentHashKeyVersion;
    }

    public void setCurrentHashKeyVersion(int currentHashKeyVersion) {
        this.currentHashKeyVersion = currentHashKeyVersion;
    }

    public Map<Integer, String> getHmacKeys() {
        return hmacKeys;
    }

    public void setHmacKeys(Map<Integer, String> hmacKeys) {
        this.hmacKeys = new HashMap<>(hmacKeys);
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public String keyFor(int version) {
        String key = hmacKeys.get(version);
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "An idempotency HMAC key must be configured before an idempotent endpoint is enabled");
        }
        return key;
    }
}
