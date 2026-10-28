package com.ailoveworkout.health;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 모킹된 JDBC 연결의 반환과 실패 시 상세 정보 없는 상태 보고를 검증한다. */
class DependencyHealthServiceTest {
    @Test
    void checksAndClosesARealConnectionHandle() throws Exception {
        var dataSource = mock(DataSource.class);
        var connection = mock(Connection.class);
        var ai = mock(AiHealthClient.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        when(ai.isHealthy()).thenReturn(true);
        var report = new DependencyHealthService(dataSource, ai).check();
        assertEquals("UP", report.status());
        verify(connection).close();
    }

    @Test
    void failuresAreReportedWithoutConnectionDetails() throws Exception {
        var dataSource = mock(DataSource.class);
        var ai = mock(AiHealthClient.class);
        when(dataSource.getConnection()).thenThrow(new SQLException("private connection details"));
        var report = new DependencyHealthService(dataSource, ai).check();
        assertEquals(new DependencyHealthService.Report("DOWN", "DOWN", "DOWN"), report);
    }
}
