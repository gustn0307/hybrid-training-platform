package com.ailoveworkout.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** MVC와 Security 필터가 같은 오류 구조를 사용하며 요청값·예외 상세는 반환하지 않는다. */
@RestControllerAdvice
public class ApiErrors {
    public record Body(String code, String message, Map<String, String> fieldErrors) {}

    public static void write(HttpServletResponse response, ObjectMapper mapper,
            int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getWriter(), new Body(code, message, Map.of()));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Body> application(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(new Body(exception.code(), exception.getMessage(), Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Body> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new Body("VALIDATION_ERROR", "입력값을 확인해 주세요.", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Body> unreadable() {
        return ResponseEntity.badRequest().body(new Body("VALIDATION_ERROR",
                "요청 형식과 허용값을 확인해 주세요. null과 알 수 없는 필드는 허용하지 않습니다.", Map.of()));
    }

    @ExceptionHandler({DataAccessException.class, org.springframework.transaction.TransactionException.class})
    ResponseEntity<Body> database() {
        return ResponseEntity.status(503).body(new Body("SERVICE_UNAVAILABLE", "저장소에 연결할 수 없습니다. 다시 시도해 주세요.", Map.of()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Body> denied() {
        return ResponseEntity.status(403).body(new Body("FORBIDDEN", "요청한 기능에 접근할 권한이 없습니다.", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Body> unexpected() {
        return ResponseEntity.internalServerError().body(new Body("INTERNAL_ERROR", "요청을 처리하지 못했습니다.", Map.of()));
    }
}
