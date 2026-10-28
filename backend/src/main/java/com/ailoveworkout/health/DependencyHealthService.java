package com.ailoveworkout.health;

import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.stereotype.Service;

/** DB와 내부 AI 연결을 함께 점검하여 프로세스 응답과 의존 서비스 준비 상태를 구분한다. */
@Service
public class DependencyHealthService {
    private final DataSource dataSource;
    private final AiHealthClient aiClient;

    public DependencyHealthService(DataSource dataSource, AiHealthClient aiClient) {
        this.dataSource = dataSource;
        this.aiClient = aiClient;
    }

    /** 두 연결이 모두 정상일 때만 전체 상태를 UP으로 판정한다. */
    public Report check() {
        boolean database = databaseAvailable();
        boolean ai = aiClient.isHealthy();
        return new Report(database && ai ? "UP" : "DOWN", database ? "UP" : "DOWN", ai ? "UP" : "DOWN");
    }

    private boolean databaseAvailable() {
        // 풀에서 빌린 연결은 점검 성공·실패와 관계없이 반환해야 한다.
        try (var connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException exception) {
            // 접속 예외의 상세 정보 대신 상태만 반환하여 연결 설정 노출을 피한다.
            return false;
        }
    }

    public record Report(String status, String database, String aiService) { }
}
