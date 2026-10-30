package com.ailoveworkout.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** JWT 원문 대신 만료 전 jti만 영속화하여 로그아웃 상태를 재시작과 독립시킨다. */
@Entity
@Table(name = "revoked_token")
public class RevokedToken {
    @Id private UUID jti;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "revoked_at", nullable = false) private Instant revokedAt;
    protected RevokedToken() {}
}
