package com.hiresemble.ai.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KoreanUserFacingTextPolicyTest {

    @Test
    void detectsKoreanUserFacingProseWithoutRejectingTechnicalTerms() {
        assertThat(KoreanUserFacingTextPolicy.containsKorean("Spring API 개발 경험")).isTrue();
        assertThat(KoreanUserFacingTextPolicy.containsKorean("Java experience")).isFalse();
        assertThat(KoreanUserFacingTextPolicy.containsKorean(null)).isFalse();
    }
}
