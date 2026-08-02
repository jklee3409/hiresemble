package com.hiresemble.ai.validation;

import java.util.regex.Pattern;

/** Minimum language invariant for AI prose rendered directly to Korean users. */
public final class KoreanUserFacingTextPolicy {

    private static final Pattern HANGUL_SYLLABLE = Pattern.compile("[가-힣]");

    private KoreanUserFacingTextPolicy() {}

    public static boolean containsKorean(String value) {
        return value != null && HANGUL_SYLLABLE.matcher(value).find();
    }
}
