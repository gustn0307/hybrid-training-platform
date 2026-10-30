package com.ailoveworkout.auth;

import com.ailoveworkout.common.ApiErrors;
import com.ailoveworkout.config.SecurityConfig;
import com.ailoveworkout.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 저장소 장애를 인증 성공·로그아웃 성공으로 바꾸지 않는 HTTP 계약을 검증한다. */
@WebMvcTest(controllers = AuthController.class, excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import({SecurityConfig.class, ApiErrors.class})
class SecurityFailureTest {
    @Autowired MockMvc mvc;
    @MockitoBean AuthService auth;
    @MockitoBean JwtService jwt;
    @MockitoBean UserRepository users;
    @MockitoBean RevokedTokenRepository revoked;
    private final CurrentUser user = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), Instant.now().plusSeconds(1800));

    @BeforeEach void setup() {
        when(jwt.decode("test-token")).thenReturn(user);
        when(users.existsById(user.id())).thenReturn(true);
    }
    @Test void failedLogoutCannotReturnSuccess() throws Exception {
        doThrow(new DataAccessResourceFailureException("unavailable")).when(auth).logout(any());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer test-token"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }
    @Test void revocationLookupFailureDeniesAccess() throws Exception {
        when(revoked.existsById(user.jti())).thenThrow(new DataAccessResourceFailureException("unavailable"));
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer test-token"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
        verifyNoInteractions(auth);
    }
    @Test void unknownUserAndRevokedTokenAreUnauthenticated() throws Exception {
        when(users.existsById(user.id())).thenReturn(false);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer test-token"))
                .andExpect(status().isUnauthorized());
        when(users.existsById(user.id())).thenReturn(true);
        when(revoked.existsById(user.jti())).thenReturn(true);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer test-token"))
                .andExpect(status().isUnauthorized());
    }
    @Test void transactionStartFailureAlsoDeniesAccess() throws Exception {
        // 커넥션 획득 전 트랜잭션 시작 실패도 필터 바깥으로 누출하지 않고 같은 503 계약을 쓴다.
        when(users.existsById(user.id())).thenThrow(new org.springframework.transaction.CannotCreateTransactionException("unavailable"));
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer test-token"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
        verifyNoInteractions(auth);
    }
    @Test void authenticatedUnknownRoutesAreForbidden() throws Exception {
        mvc.perform(get("/api/v1/users/another-user/workout-profile").header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
