package com.hiresemble.job.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hiresemble.common.exception.BusinessException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JobUrlCanonicalizerTest {

    private final JobUrlCanonicalizer canonicalizer = new JobUrlCanonicalizer();

    @Test
    void canonicalEquivalencesCoverHostPortFragmentQueryTrackingAndDotSegments() {
        Map<String, String> equivalentPairs = Map.of(
                "HTTPS://EXAMPLE.COM/jobs/42", "https://example.com/jobs/42",
                "https://example.com:443/jobs/42", "https://example.com/jobs/42",
                "https://example.com/jobs/42#apply", "https://example.com/jobs/42",
                "https://example.com/jobs/42?b=2&a=1", "https://example.com/jobs/42?a=1&b=2",
                "https://example.com/jobs/42?utm_source=x&a=1&fbclid=y",
                        "https://example.com/jobs/42?a=1",
                "https://example.com/a/./b/../jobs//42", "https://example.com/a/jobs/42");

        equivalentPairs.forEach((left, right) ->
                assertThat(canonicalizer.canonicalize(left))
                        .isEqualTo(canonicalizer.canonicalize(right)));
    }

    @Test
    void idnaHostIsAsciiAndDifferentResourcesRemainDifferent() {
        assertThat(canonicalizer.canonicalize("https://예시.테스트/채용"))
                .startsWith("https://xn--")
                .doesNotContain("#");
        assertThat(canonicalizer.canonicalize("https://example.com/jobs/one"))
                .isNotEqualTo(canonicalizer.canonicalize("https://example.com/jobs/two"));
    }

    @Test
    void reservedEscapesRemainDistinctWhileUnreservedEscapesAreNormalized() {
        String encodedSlash =
                canonicalizer.canonicalize("https://example.com/jobs/a%2fb");
        String literalSlash =
                canonicalizer.canonicalize("https://example.com/jobs/a/b");
        assertThat(encodedSlash)
                .isEqualTo("https://example.com/jobs/a%2Fb")
                .isNotEqualTo(literalSlash);

        String literalPlus =
                canonicalizer.canonicalize("https://example.com/jobs?q=+");
        String encodedSpace =
                canonicalizer.canonicalize("https://example.com/jobs?q=%20");
        assertThat(literalPlus)
                .isEqualTo("https://example.com/jobs?q=+")
                .isNotEqualTo(encodedSpace);
        assertThat(encodedSpace).isEqualTo("https://example.com/jobs?q=%20");

        assertThat(canonicalizer.canonicalize(
                        "https://example.com/%7ejobs?b=%7e&a=%2f"))
                .isEqualTo("https://example.com/~jobs?a=%2F&b=~");
    }

    @Test
    void onlyAbsoluteHttpUrlsWithoutCredentialsAreAccepted() {
        assertThatThrownBy(() -> canonicalizer.canonicalize("file:///etc/passwd"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> canonicalizer.canonicalize("/jobs/42"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> canonicalizer.canonicalize("https://user:secret@example.com/jobs"))
                .isInstanceOf(BusinessException.class);
    }
}
