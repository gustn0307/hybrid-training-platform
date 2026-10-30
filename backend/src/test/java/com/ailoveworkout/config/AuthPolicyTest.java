package com.ailoveworkout.config;

import com.ailoveworkout.auth.*;
import jakarta.validation.Validation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import static org.junit.jupiter.api.Assertions.*;

/** 실제 키는 사용하지 않고 매번 생성한 메모리 키로 토큰 시간·서명·문자 수 정책을 검증한다. */
class AuthPolicyTest {
    private final JwtConfig config = new JwtConfig();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-30T10:00:00Z"), ZoneOffset.UTC);
    private final javax.crypto.spec.SecretKeySpec key = key();
    private final JwtEncoder encoder = config.jwtEncoder(key);
    private final JwtDecoder decoder = config.jwtDecoder(key, clock);
    private final JwtService jwt = new JwtService(encoder, decoder, clock);

    private javax.crypto.spec.SecretKeySpec key() {
        byte[] bytes = new byte[32]; new java.security.SecureRandom().nextBytes(bytes);
        return config.jwtKey(java.util.Base64.getEncoder().encodeToString(bytes));
    }

    @Test void tokenHasUniqueJtiAndThirtyMinuteLifetime() {
        UUID id = UUID.randomUUID();
        String first = jwt.issue(id);
        var decoded = decoder.decode(first);
        assertEquals(id, jwt.decode(first).id());
        assertEquals(1800, java.time.Duration.between(decoded.getIssuedAt(), decoded.getExpiresAt()).getSeconds());
        assertNotEquals(decoded.getId(), decoder.decode(jwt.issue(id)).getId());
    }

    @Test void expiredAndForgedTokensAreRejected() {
        var claims = JwtClaimsSet.builder().issuer("ai-love-workout").audience(List.of("ai-love-workout-web"))
                .subject(UUID.randomUUID().toString()).id(UUID.randomUUID().toString())
                .issuedAt(clock.instant().minusSeconds(1800)).expiresAt(clock.instant().minusSeconds(1)).build();
        String expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        assertThrows(JwtException.class, () -> jwt.decode(expired));
        String token = jwt.issue(UUID.randomUUID());
        String[] parts = token.split("\\.");
        // 서명만 바꾸는 대신 다른 키로 서명하여 확실한 위조 토큰을 만든다.
        JwtService attacker = new JwtService(config.jwtEncoder(key()), decoder, clock);
        assertThrows(JwtException.class, () -> jwt.decode(attacker.issue(UUID.randomUUID())));
        assertThrows(JwtException.class, () -> jwt.decode(parts[0] + ".e30." + parts[2]));
    }

    @Test void missingOrWrongIdentityClaimsAreRejected() {
        var claims = JwtClaimsSet.builder().issuer("ai-love-workout").audience(List.of("ai-love-workout-web"))
                .subject("not-a-user-uuid").issuedAt(clock.instant()).expiresAt(clock.instant().plusSeconds(1800)).build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        assertThrows(JwtException.class, () -> jwt.decode(token));
    }

    @Test void passwordHashingSupportsSeventyTwoUnicodeCharactersWithoutTruncation() {
        var passwords = new PasswordHashing();
        String raw = "운".repeat(72);
        String hash = passwords.encode(raw);
        assertNotEquals(raw, hash);
        assertTrue(hash.startsWith("{bcrypt-sha384}$2"));
        assertTrue(passwords.matches(raw, hash));
        assertFalse(passwords.matches("운".repeat(71) + "동", hash));
        assertNotEquals(hash, passwords.encode(raw));
        assertTrue(passwords.matches(" ".repeat(8), passwords.encode(" ".repeat(8))));
    }

    @Test void validationCountsCodePointsAndDoesNotRequirePasswordCombinations() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(new AuthController.Signup("USER@example.com", "가".repeat(72), "🏃".repeat(20))).isEmpty());
            assertTrue(validator.validate(new AuthController.Signup("user@example.com", " ".repeat(8), "중복허용")).isEmpty());
            assertFalse(validator.validate(new AuthController.Signup("user@example.com", "가".repeat(73), "이름")).isEmpty());
            assertEquals("user@example.com", new AuthController.Login("USER@example.com", "12345678").email());
        }
    }

    @Test void missingOrShortSigningKeyFailsFast() {
        assertThrows(IllegalStateException.class, () -> config.jwtKey(""));
        assertThrows(IllegalStateException.class, () -> config.jwtKey("invalid-base64!"));
    }

    static Stream<String> malformedPasswords() {
        char high = (char) 0xD800, low = (char) 0xDC00;
        return Stream.of("abcdefg" + high, "abcdefg" + low,
                "abcdef" + low + high, "abcdef" + high + low + high);
    }

    // 표시명에 비밀번호 원문을 포함하지 않으며 정상 pair 뒤의 잘못된 문자도 검사한다.
    @ParameterizedTest(name = "invalid Unicode DTO case {index}")
    @MethodSource("malformedPasswords")
    void signupAndLoginRejectMalformedUnicode(String raw) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertFalse(validator.validate(new AuthController.Signup("user@example.com", raw, "이름")).isEmpty());
            assertFalse(validator.validate(new AuthController.Login("user@example.com", raw)).isEmpty());
        }
    }

    @ParameterizedTest(name = "invalid Unicode hashing case {index}")
    @MethodSource("malformedPasswords")
    void hashingRejectsMalformedUnicodeWithoutAliases(String raw) {
        var passwords = new PasswordHashing();
        assertThrows(IllegalArgumentException.class, () -> passwords.encode(raw));
        assertFalse(passwords.matches(raw, passwords.encode("abcdefg?")));
    }

    static Stream<Arguments> passwordBoundaries() {
        return Stream.of("p", "운", "🏃").flatMap(character -> Stream.of(7, 8, 72, 73)
                .map(length -> Arguments.of(character.repeat(length), length == 8 || length == 72)));
    }

    @ParameterizedTest(name = "Unicode password boundary case {index}")
    @MethodSource("passwordBoundaries")
    void signupAndLoginUseTheSameUnicodeLengthBoundaries(String raw, boolean valid) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertEquals(valid, validator.validate(new AuthController.Signup("user@example.com", raw, "이름")).isEmpty());
            assertEquals(valid, validator.validate(new AuthController.Login("user@example.com", raw)).isEmpty());
            if (valid) {
                var passwords = new PasswordHashing();
                assertTrue(passwords.matches(raw, passwords.encode(raw)));
            }
        }
    }

    @Test void validPasswordsKeepCompatibilityWithPreviousHashFormat() throws Exception {
        // 수정 전 인코딩으로 만든 해시를 직접 검증해 기존 계정의 호환성을 확인한다.
        var passwords = new PasswordHashing();
        var bcrypt = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        for (String raw : List.of("abcdefgh", "운".repeat(8), "🏃".repeat(8), "운동일지🏃123")) {
            byte[] previousBytes = ("AI Love Workout password v1:" + raw).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String previousDigest = java.util.Base64.getEncoder().encodeToString(
                    java.security.MessageDigest.getInstance("SHA-384").digest(previousBytes));
            String previousHash = "{bcrypt-sha384}" + bcrypt.encode(previousDigest);
            assertTrue(passwords.matches(raw, previousHash));
        }
    }
}
