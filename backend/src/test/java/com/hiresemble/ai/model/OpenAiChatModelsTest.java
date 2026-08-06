package com.hiresemble.ai.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class OpenAiChatModelsTest {

    @Test
    void exposesExactAllowlistedIdsWithOneRecommendedModel() {
        assertThat(OpenAiChatModels.coverLetterModels())
                .extracting(OpenAiChatModels.Model::id)
                .containsExactly(
                        "gpt-5.6-sol",
                        "gpt-5.6-terra",
                        "gpt-5.6-luna",
                        "gpt-5.5",
                        "gpt-5.4",
                        "gpt-5.4-mini",
                        "gpt-5.4-nano",
                        "gpt-5.2",
                        "gpt-5.1",
                        "gpt-5");
        assertThat(OpenAiChatModels.coverLetterModels())
                .filteredOn(OpenAiChatModels.Model::recommended)
                .extracting(OpenAiChatModels.Model::id)
                .containsExactly(OpenAiChatModels.RECOMMENDED);
        assertThat(OpenAiChatModels.coverLetterModels())
                .extracting(OpenAiChatModels.Model::id)
                .doesNotContainAnyElementsOf(Set.of("gpt-4.5-preview"));
    }

    @Test
    void rejectsUnknownOrDeprecatedModelIds() {
        assertThatThrownBy(() -> OpenAiChatModels.requireCoverLetter("gpt-4.5-preview"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OpenAiChatModels.requireCoverLetter("gpt-5.6-terra "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
