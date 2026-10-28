package com.ailoveworkout.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Spring Boot에서 내부 AI 서비스의 헬스를 확인할 HTTP 클라이언트를 구성한다. */
@Configuration
public class AiClientConfig {
    @Bean
    RestClient aiRestClient(RestClient.Builder builder, @Value("${app.ai-service-url}") String baseUrl) {
        var factory = new SimpleClientHttpRequestFactory();
        // AI 서비스 장애가 연결 점검 요청을 오래 붙잡지 않도록 연결·응답 대기 시간을 제한한다.
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));
        return builder.baseUrl(baseUrl).requestFactory(factory).build();
    }
}
