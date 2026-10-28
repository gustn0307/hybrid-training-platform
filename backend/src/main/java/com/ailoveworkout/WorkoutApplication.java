package com.ailoveworkout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * 공개 API 서버의 진입점이다. 기본 계정 자동 생성을 제외하여 임의 비밀번호가 로그에 남지 않게 한다.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class WorkoutApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkoutApplication.class, args);
    }
}
