package com.ailoveworkout.auth;

import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 승인된 문자 수를 유지하면서 BCrypt의 72바이트 잘림·거절을 피한다. */
public final class PasswordHashing implements PasswordEncoder {
    private static final String PREFIX = "{bcrypt-sha384}";
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    private String prepare(CharSequence raw) {
        try {
            // 구분 문자열은 다른 서비스의 단순 SHA 해시와 전처리 결과가 같아지는 것을 피한다.
            // DTO 검증을 거치지 않아도 손실성 치환으로 다른 비밀번호가 같은 입력이 되지 않게 한다.
            var encoded = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap("AI Love Workout password v1:" + raw));
            byte[] bytes = new byte[encoded.remaining()];
            encoded.get(bytes);
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-384").digest(bytes));
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Invalid Unicode password");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Required password digest is unavailable");
        }
    }

    public String encode(CharSequence raw) { return PREFIX + bcrypt.encode(prepare(raw)); }
    public boolean matches(CharSequence raw, String encoded) {
        try {
            return encoded != null && encoded.startsWith(PREFIX)
                    && bcrypt.matches(prepare(raw), encoded.substring(PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            // 인증 비교에서는 잘못된 인코딩 입력을 일치하지 않는 비밀번호로 처리한다.
            return false;
        }
    }
}
