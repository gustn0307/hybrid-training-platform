package com.ailoveworkout.config;

import com.ailoveworkout.auth.PasswordHashing;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

/** JWT 암호 구현은 검증된 JOSE 라이브러리에 맡기고 키가 없거나 약하면 시작을 거절한다. */
@Configuration
@EnableScheduling
public class JwtConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean PasswordEncoder passwordEncoder() { return new PasswordHashing(); }

    @Bean SecretKeySpec jwtKey(@Value("${app.jwt-secret}") String value) {
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(value); }
        catch (IllegalArgumentException exception) { throw new IllegalStateException("JWT_SECRET must be Base64"); }
        if (bytes.length < 32) throw new IllegalStateException("JWT_SECRET must contain at least 32 random bytes");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean JwtEncoder jwtEncoder(SecretKeySpec key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }

    @Bean JwtDecoder jwtDecoder(SecretKeySpec key, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        // 기본 시계 오차 허용으로 만료 토큰의 수명이 늘어나지 않게 0초로 검증한다.
        JwtTimestampValidator time = new JwtTimestampValidator(Duration.ZERO);
        time.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(time,
                new JwtIssuerValidator("ai-love-workout"), jwt -> jwt.getAudience() != null && jwt.getAudience().contains("ai-love-workout-web")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"))));
        return decoder;
    }
}
