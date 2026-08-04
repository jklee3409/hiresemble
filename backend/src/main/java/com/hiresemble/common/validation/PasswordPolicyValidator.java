package com.hiresemble.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordPolicyValidator implements ConstraintValidator<PasswordPolicy, String> {

    private static final int MINIMUM_CODE_POINTS = 10;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        boolean hasLetter = false;
        boolean hasNumber = false;
        boolean hasSpecialCharacter = false;
        int codePointCount = 0;

        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            codePointCount++;

            if (Character.isLetter(codePoint)) {
                hasLetter = true;
            } else if (Character.isDigit(codePoint)) {
                hasNumber = true;
            } else if (isPunctuationOrSymbol(codePoint)) {
                hasSpecialCharacter = true;
            }
        }

        return codePointCount >= MINIMUM_CODE_POINTS
                && hasLetter
                && hasNumber
                && hasSpecialCharacter;
    }

    private boolean isPunctuationOrSymbol(int codePoint) {
        return switch (Character.getType(codePoint)) {
            case Character.CONNECTOR_PUNCTUATION,
                    Character.DASH_PUNCTUATION,
                    Character.START_PUNCTUATION,
                    Character.END_PUNCTUATION,
                    Character.INITIAL_QUOTE_PUNCTUATION,
                    Character.FINAL_QUOTE_PUNCTUATION,
                    Character.OTHER_PUNCTUATION,
                    Character.MATH_SYMBOL,
                    Character.CURRENCY_SYMBOL,
                    Character.MODIFIER_SYMBOL,
                    Character.OTHER_SYMBOL -> true;
            default -> false;
        };
    }
}
