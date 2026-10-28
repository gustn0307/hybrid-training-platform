package com.ailoveworkout.health;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 프로세스 응답과 실제 의존 서비스 연결 상태를 별도 API로 제공한다. */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {
    private final DependencyHealthService dependencies;

    public HealthController(DependencyHealthService dependencies) {
        this.dependencies = dependencies;
    }

    /** DB·AI 준비 여부와 구분하여 Spring Boot가 요청에 응답하는지만 확인한다. */
    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "backend");
    }

    /** DB 또는 AI가 준비되지 않았다면 정상 준비 상태로 안내하지 않고 503을 반환한다. */
    @GetMapping("/dependencies")
    public ResponseEntity<DependencyHealthService.Report> dependencies() {
        var report = dependencies.check();
        return ResponseEntity.status("UP".equals(report.status()) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(report);
    }
}
