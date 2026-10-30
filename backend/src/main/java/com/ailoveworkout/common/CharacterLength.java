package com.ailoveworkout.common;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/** UTF-16의 유효성을 확인하고 Unicode code point로 승인된 문자 수 제한을 적용한다. */
@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
@Constraint(validatedBy = CharacterLength.Validator.class)
public @interface CharacterLength {
    int min();
    int max();
    String message() default "문자 수가 허용 범위를 벗어났습니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<CharacterLength, String> {
        private int min;
        private int max;
        public void initialize(CharacterLength rule) { min = rule.min(); max = rule.max(); }
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) return true;
            // 짝이 없는 surrogate는 UTF-8 변환 시 다른 문자로 치환될 수 있으므로 먼저 거부한다.
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                if (Character.isHighSurrogate(character)) {
                    if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1)))
                        return rejectMalformedUnicode(context);
                    index++;
                } else if (Character.isLowSurrogate(character)) {
                    return rejectMalformedUnicode(context);
                }
            }
            int length = value.codePointCount(0, value.length());
            return length >= min && length <= max;
        }

        private boolean rejectMalformedUnicode(ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("짝이 맞지 않는 Unicode 문자는 사용할 수 없습니다.")
                    .addConstraintViolation();
            return false;
        }
    }
}
