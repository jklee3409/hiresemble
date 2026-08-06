package com.hiresemble.ai.model;

import com.hiresemble.agentrun.domain.model.ModelTier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-owned allowlist of exact OpenAI API model identifiers exposed for cover-letter work.
 *
 * <p>Keep provider product IDs in this catalog instead of scattering literals across API,
 * workflow, and UI layers. Availability is based on the official OpenAI model catalog; account
 * entitlements are still enforced by OpenAI when a request is made.
 */
public final class OpenAiChatModels {

    public static final String GPT_5_6_SOL = "gpt-5.6-sol";
    public static final String GPT_5_6_TERRA = "gpt-5.6-terra";
    public static final String GPT_5_6_LUNA = "gpt-5.6-luna";
    public static final String GPT_5_5 = "gpt-5.5";
    public static final String GPT_5_4 = "gpt-5.4";
    public static final String GPT_5_4_MINI = "gpt-5.4-mini";
    public static final String GPT_5_4_NANO = "gpt-5.4-nano";
    public static final String GPT_5_2 = "gpt-5.2";
    public static final String GPT_5_1 = "gpt-5.1";
    public static final String GPT_5 = "gpt-5";

    public static final String RECOMMENDED = GPT_5_6_TERRA;

    private static final List<Model> COVER_LETTER_MODELS = List.of(
            new Model(GPT_5_6_SOL, "GPT-5.6 Sol", "최고 성능 중심", ModelTier.HIGH_QUALITY, false),
            new Model(GPT_5_6_TERRA, "GPT-5.6 Terra", "품질과 비용의 균형", ModelTier.BALANCED, true),
            new Model(GPT_5_6_LUNA, "GPT-5.6 Luna", "빠르고 경제적인 선택", ModelTier.LOW_COST, false),
            new Model(GPT_5_5, "GPT-5.5", "고성능 이전 세대", ModelTier.HIGH_QUALITY, false),
            new Model(GPT_5_4, "GPT-5.4", "범용 고성능 모델", ModelTier.BALANCED, false),
            new Model(GPT_5_4_MINI, "GPT-5.4 mini", "비용 효율적인 범용 모델", ModelTier.LOW_COST, false),
            new Model(GPT_5_4_NANO, "GPT-5.4 nano", "가장 경제적인 소형 모델", ModelTier.LOW_COST, false),
            new Model(GPT_5_2, "GPT-5.2", "이전 세대 주력 모델", ModelTier.BALANCED, false),
            new Model(GPT_5_1, "GPT-5.1", "이전 세대 범용 모델", ModelTier.BALANCED, false),
            new Model(GPT_5, "GPT-5", "초기 GPT-5 모델", ModelTier.BALANCED, false));

    private static final Map<String, Model> BY_ID;

    static {
        Map<String, Model> values = new LinkedHashMap<>();
        COVER_LETTER_MODELS.forEach(value -> values.put(value.id(), value));
        BY_ID = Map.copyOf(values);
    }

    private OpenAiChatModels() {}

    public static List<Model> coverLetterModels() {
        return COVER_LETTER_MODELS;
    }

    public static boolean supportsCoverLetter(String model) {
        return model != null && BY_ID.containsKey(model);
    }

    public static Model requireCoverLetter(String model) {
        Model selected = model == null ? null : BY_ID.get(model);
        if (selected == null) {
            throw new IllegalArgumentException("unsupported OpenAI cover-letter model");
        }
        return selected;
    }

    public record Model(
            String id,
            String displayName,
            String description,
            ModelTier tier,
            boolean recommended) {}
}
