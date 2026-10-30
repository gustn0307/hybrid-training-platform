package com.ailoveworkout.auth;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, UUID> {
    // 동시에 도착한 로그아웃은 중복 저장만 피한다. 이후 요청의 인증은 필터에서 거절한다.
    @Modifying
    @Query(value = "insert into revoked_token(jti, user_id, expires_at, revoked_at) values (:jti, :userId, :expiresAt, :now) on conflict (jti) do nothing", nativeQuery = true)
    void revoke(UUID jti, UUID userId, Instant expiresAt, Instant now);

    @Modifying
    @Query("delete from RevokedToken t where t.expiresAt <= :now")
    int deleteExpired(Instant now);
}
