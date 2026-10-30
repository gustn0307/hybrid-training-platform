package com.ailoveworkout.auth;

import java.time.Instant;
import java.util.UUID;

/** 검증된 JWT에서만 생성하며 클라이언트 본문의 userId로 대체하지 않는다. */
public record CurrentUser(UUID id, UUID jti, Instant expiresAt) {}
