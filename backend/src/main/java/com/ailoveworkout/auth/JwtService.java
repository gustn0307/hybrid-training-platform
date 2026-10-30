package com.ailoveworkout.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

/** Access Token만 발급하며 토큰이나 인증 클레임을 로그에 남기지 않는다. */
@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final Clock clock;
    public JwtService(JwtEncoder encoder, JwtDecoder decoder, Clock clock) {
        this.encoder = encoder; this.decoder = decoder; this.clock = clock;
    }

    public String issue(UUID userId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer("ai-love-workout")
                .audience(List.of("ai-love-workout-web")).subject(userId.toString())
                .id(UUID.randomUUID().toString()).issuedAt(now).expiresAt(now.plusSeconds(1800)).build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
    }

    public CurrentUser decode(String token) {
        Jwt jwt = decoder.decode(token);
        Instant now = clock.instant();
        // 발급 서버의 필수 클레임까지 검증한다. 서명만 맞는 불완전한 토큰도 인증하지 않는다.
        if (jwt.getSubject() == null || jwt.getId() == null || jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(now)
                || jwt.getIssuedAt() == null || jwt.getIssuedAt().isAfter(now)
                || jwt.getExpiresAt().isAfter(jwt.getIssuedAt().plusSeconds(1800))) {
            throw new BadJwtException("Invalid token claims");
        }
        return new CurrentUser(UUID.fromString(jwt.getSubject()), UUID.fromString(jwt.getId()), jwt.getExpiresAt());
    }
}
