package com.tone.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

public class PixKeyValidator implements ConstraintValidator<PixKey, String> {

    private static final Pattern CPF = Pattern.compile("^\\d{11}$");
    private static final Pattern CNPJ = Pattern.compile("^\\d{14}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,180}\\.[a-zA-Z]{2,}$");
    private static final Pattern PHONE = Pattern.compile("^\\+55\\d{10,11}$");
    private static final Pattern EVP = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        String trimmed = value.trim();
        if (CPF.matcher(trimmed).matches()) {
            return isValidCpf(trimmed);
        }
        if (CNPJ.matcher(trimmed).matches()) {
            return isValidCnpj(trimmed);
        }
        return EMAIL.matcher(trimmed).matches()
                || PHONE.matcher(trimmed).matches()
                || EVP.matcher(trimmed).matches();
    }

    private static boolean isValidCpf(String digits) {
        if (digits.chars().distinct().count() == 1) return false;
        int d1 = calcMod11(digits, new int[]{10, 9, 8, 7, 6, 5, 4, 3, 2});
        int d2 = calcMod11(digits, new int[]{11, 10, 9, 8, 7, 6, 5, 4, 3, 2});
        return d1 == (digits.charAt(9) - '0') && d2 == (digits.charAt(10) - '0');
    }

    private static boolean isValidCnpj(String digits) {
        if (digits.chars().distinct().count() == 1) return false;
        int d1 = calcMod11(digits, new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        int d2 = calcMod11(digits, new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        return d1 == (digits.charAt(12) - '0') && d2 == (digits.charAt(13) - '0');
    }

    private static int calcMod11(String digits, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (digits.charAt(i) - '0') * weights[i];
        }
        int rem = sum % 11;
        return rem < 2 ? 0 : 11 - rem;
    }
}
