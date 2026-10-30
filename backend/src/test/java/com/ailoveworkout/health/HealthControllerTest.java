package com.ailoveworkout.health;

import com.ailoveworkout.config.SecurityConfig;
import com.ailoveworkout.auth.JwtService;
import com.ailoveworkout.auth.RevokedTokenRepository;
import com.ailoveworkout.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// MVC 테스트에서도 기본 계정 생성을 제외하여 테스트용 비밀번호 로그를 방지한다.
@WebMvcTest(controllers = HealthController.class,
        excludeAutoConfiguration = UserDetailsServiceAutoConfiguration.class)
@Import(SecurityConfig.class)
class HealthControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean DependencyHealthService dependencies;
    @MockitoBean JwtService jwt;
    @MockitoBean UserRepository users;
    @MockitoBean RevokedTokenRepository revoked;

    @Test
    void livenessIsPublic() throws Exception {
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("backend"));
    }

    @Test
    void dependenciesRequireBothServices() throws Exception {
        // 모킹된 상태에 대한 HTTP 계약 검증이며 실제 DB 연결을 증명하는 테스트는 아니다.
        when(dependencies.check()).thenReturn(new DependencyHealthService.Report("UP", "UP", "UP"));
        mvc.perform(get("/api/v1/health/dependencies")).andExpect(status().isOk());
        when(dependencies.check()).thenReturn(new DependencyHealthService.Report("DOWN", "DOWN", "UP"));
        mvc.perform(get("/api/v1/health/dependencies")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.database").value("DOWN"));
    }

    @Test
    void unrelatedEndpointsAreDenied() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void healthCannotBeUsedForWrites() throws Exception {
        mvc.perform(post("/api/v1/health")).andExpect(status().isUnauthorized());
    }
}
