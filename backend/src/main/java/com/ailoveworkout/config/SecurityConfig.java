package com.ailoveworkout.config;

import com.ailoveworkout.auth.*;
import com.ailoveworkout.common.ApiErrors;
import com.ailoveworkout.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/** 공개 인증·헬스 경로와 본인 API만 허용하며 미구현 도메인은 계속 차단한다. */
@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwt, UserRepository users,
            RevokedTokenRepository revoked, ObjectMapper mapper) throws Exception {
        // 쿠키·세션 인증을 쓰지 않고 메모리 토큰을 Authorization 헤더로만 보내므로 CSRF를 비활성화한다.
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/api/v1/health", "/api/v1/health/dependencies").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/signup", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me", "/api/v1/users/me/workout-profile",
                                "/api/v1/users/me/workout-profile/options").authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/users/me/workout-profile").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> ApiErrors.write(response, mapper,
                                401, "UNAUTHENTICATED", "인증이 필요하거나 인증 정보가 유효하지 않습니다."))
                        .accessDeniedHandler((request, response, exception) -> ApiErrors.write(response, mapper,
                                403, "FORBIDDEN", "요청한 기능에 접근할 권한이 없습니다.")))
                .addFilterBefore(new JwtAuthenticationFilter(jwt, users, revoked, mapper), UsernamePasswordAuthenticationFilter.class)
                .logout(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .build();
    }
}
