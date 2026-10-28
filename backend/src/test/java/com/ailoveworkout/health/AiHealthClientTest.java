package com.ailoveworkout.health;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** 실제 네트워크 대신 응답을 제어하여 내부 AI 계약과 장애 판정을 검증한다. */
class AiHealthClientTest {
    @Test
    void validatesTheInternalAiContract() {
        var builder = RestClient.builder().baseUrl("http://ai-service:8000");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new AiHealthClient(builder.build());
        server.expect(requestTo("http://ai-service:8000/health"))
                .andRespond(withSuccess("{\"status\":\"UP\",\"service\":\"ai-service\"}", MediaType.APPLICATION_JSON));
        assertTrue(client.isHealthy());
        server.verify();
    }

    @Test
    void anUnrelatedOrFailedServiceIsNotHealthy() {
        var builder = RestClient.builder().baseUrl("http://ai-service:8000");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new AiHealthClient(builder.build());
        server.expect(requestTo("http://ai-service:8000/health"))
                .andRespond(withSuccess("{\"status\":\"UP\",\"service\":\"other\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://ai-service:8000/health")).andRespond(withServerError());
        assertFalse(client.isHealthy());
        assertFalse(client.isHealthy());
        server.verify();
    }
}
