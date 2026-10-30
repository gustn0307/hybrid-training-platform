package com.ailoveworkout.auth;

import com.ailoveworkout.common.ApiException;
import com.ailoveworkout.user.User;
import com.ailoveworkout.user.UserRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 계정 생성·로그인·영속 로그아웃을 담당하며 비밀번호와 JWT 원문은 저장하거나 로깅하지 않는다. */
@Service
public class AuthService {
    private final UserRepository users;
    private final RevokedTokenRepository revoked;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserRepository users, RevokedTokenRepository revoked, PasswordEncoder passwords,
            JwtService jwt, Clock clock) {
        this.users = users; this.revoked = revoked; this.passwords = passwords; this.jwt = jwt; this.clock = clock;
        // 없는 계정도 같은 BCrypt 작업을 수행하여 로그인 실패의 시간 차이를 줄인다.
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public User signup(String email, String password, String nickname) {
        if (users.existsByEmail(email)) throw duplicateEmail();
        try {
            return users.saveAndFlush(new User(email, passwords.encode(password), nickname, clock.instant()));
        } catch (DataIntegrityViolationException exception) {
            // 사전 조회 이후 동시에 가입하더라도 DB UNIQUE가 최종적으로 중복을 막는다.
            throw duplicateEmail();
        }
    }

    private ApiException duplicateEmail() {
        return new ApiException(409, "EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다.");
    }

    @Transactional(readOnly = true)
    public String login(String email, String password) {
        User user = users.findByEmail(email).orElse(null);
        boolean valid = passwords.matches(password, user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !valid) throw new ApiException(401, "INVALID_CREDENTIALS", "이메일 또는 비밀번호를 확인해 주세요.");
        return jwt.issue(user.getId());
    }

    @Transactional
    public void logout(CurrentUser user) {
        // 트랜잭션 커밋이 실패하면 컨트롤러까지 예외가 전파되어 204 성공 응답을 반환하지 않는다.
        revoked.revoke(user.jti(), user.id(), user.expiresAt(), clock.instant());
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    @Transactional
    public void removeExpiredRevocations() {
        // 유효한 토큰의 무효화 기록은 건드리지 않고 이미 만료된 행만 정리한다.
        revoked.deleteExpired(clock.instant());
    }
}
