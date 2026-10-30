package com.ailoveworkout.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** 계정·인증 정보를 보관한다. API에는 별도 DTO만 반환하여 해시가 직렬화되지 않게 한다. */
@Entity
@Table(name = "app_user")
public class User {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 254) private String email;
    @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
    @Column(nullable = false, length = 20) private String nickname;
    @Column(length = 64) private String timezone;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected User() {}
    public User(String email, String passwordHash, String nickname, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.createdAt = createdAt;
    }
    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getNickname() { return nickname; }
}
