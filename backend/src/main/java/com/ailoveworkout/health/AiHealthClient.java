package com.ailoveworkout.health;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** 내부 FastAPI의 헬스 응답을 확인한다. 외부 LLM 호출 성공 여부를 검사하는 클라이언트는 아니다. */
@Component
public class AiHealthClient {
    private final RestClient client;

    public AiHealthClient(RestClient aiRestClient) {
        this.client = aiRestClient;
    }

    /** 다른 서비스의 UP 응답을 정상으로 오인하지 않도록 서비스 식별자까지 확인한다. */
    public boolean isHealthy() {
        try {
            var response = client.get().uri("/health").retrieve().body(AiHealth.class);
            return response != null && "UP".equals(response.status())
                    && "ai-service".equals(response.service());
        } catch (RuntimeException exception) {
            // 예외 상세에는 비공개 연결 설정이 포함될 수 있으므로 정상 여부만 전달한다.
            return false;
        }
    }

    private record AiHealth(String status, String service) { }
}
