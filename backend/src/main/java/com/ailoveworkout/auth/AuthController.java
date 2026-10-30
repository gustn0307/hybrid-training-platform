package com.ailoveworkout.auth;

import com.ailoveworkout.common.CharacterLength;
import com.ailoveworkout.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 회원가입은 토큰 없이 완료하고 별도의 로그인으로 인증을 시작한다. */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    public record Signup(
            @NotBlank @Email @CharacterLength(min = 1, max = 254) String email,
            @NotNull @CharacterLength(min = 8, max = 72, message = "비밀번호는 8~72자입니다.") String password,
            @NotNull @CharacterLength(min = 2, max = 20, message = "닉네임은 2~20자입니다.") String nickname) {
        public Signup { if (email != null) email = email.toLowerCase(Locale.ROOT); }
        @Override public String toString() { return "Signup[redacted]"; }
    }
    public record Login(
            @NotBlank @Email @CharacterLength(min = 1, max = 254) String email,
            @NotNull @CharacterLength(min = 8, max = 72, message = "비밀번호는 8~72자입니다.") String password) {
        public Login { if (email != null) email = email.toLowerCase(Locale.ROOT); }
        @Override public String toString() { return "Login[redacted]"; }
    }
    public record Account(UUID id, String email, String nickname) {
        public static Account from(User user) { return new Account(user.getId(), user.getEmail(), user.getNickname()); }
    }
    public record Token(String accessToken, String tokenType, int expiresIn) {
        @Override public String toString() { return "Token[redacted]"; }
    }

    @PostMapping("/signup")
    ResponseEntity<Account> signup(@Valid @RequestBody Signup request) {
        return ResponseEntity.status(201).body(Account.from(auth.signup(request.email(), request.password(), request.nickname())));
    }

    @PostMapping("/login")
    Token login(@Valid @RequestBody Login request) {
        return new Token(auth.login(request.email(), request.password()), "Bearer", 1800);
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal CurrentUser user) {
        auth.logout(user);
        return ResponseEntity.noContent().build();
    }
}
