package com.ailoveworkout.auth;

import com.ailoveworkout.common.ApiErrors;
import com.ailoveworkout.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

/** 각 요청에서 영속 무효화 상태와 사용자 존재를 확인하며 DB 장애 시 접근을 허용하지 않는다. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserRepository users;
    private final RevokedTokenRepository revoked;
    private final ObjectMapper mapper;

    public JwtAuthenticationFilter(JwtService jwt, UserRepository users,
            RevokedTokenRepository revoked, ObjectMapper mapper) {
        this.jwt = jwt; this.users = users; this.revoked = revoked; this.mapper = mapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return ("GET".equals(request.getMethod()) && (path.equals("/api/v1/health") || path.equals("/api/v1/health/dependencies")))
                || ("POST".equals(request.getMethod()) && (path.equals("/api/v1/auth/signup") || path.equals("/api/v1/auth/login")));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null) {
            try {
                if (!header.startsWith("Bearer ")) throw new IllegalArgumentException();
                CurrentUser user = jwt.decode(header.substring(7));
                if (!users.existsById(user.id()) || revoked.existsById(user.jti())) throw new IllegalArgumentException();
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, List.of()));
                SecurityContextHolder.setContext(context);
            } catch (JwtException | IllegalArgumentException exception) {
                ApiErrors.write(response, mapper, 401, "UNAUTHENTICATED", "인증이 필요하거나 인증 정보가 유효하지 않습니다.");
                return;
            } catch (DataAccessException | org.springframework.transaction.TransactionException exception) {
                ApiErrors.write(response, mapper, 503, "SERVICE_UNAVAILABLE", "인증 저장소를 확인할 수 없습니다. 다시 시도해 주세요.");
                return;
            }
        }
        // 컨트롤러 예외는 이 필터의 인증 오류로 바꾸지 않고 MVC 오류 계약에서 처리한다.
        chain.doFilter(request, response);
    }
}
