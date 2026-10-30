package com.ailoveworkout.user;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 프로필 입력 코드와 한국어 표시명의 기준이다. React는 이 목록을 API로 받아 사용한다. */
public final class WorkoutOptions {
    private WorkoutOptions() {}
    public interface Labeled { String label(); }
    public enum Goal implements Labeled {
        FITNESS("체력 향상"), STRENGTH("근력 향상"), RUNNING("러닝 능력 향상"), HABIT("운동 습관 형성");
        private final String label;
        Goal(String label) { this.label = label; }
        public String label() { return label; }
    }
    public enum Experience implements Labeled {
        BEGINNER("초급"), INTERMEDIATE("중급"), ADVANCED("상급");
        private final String label;
        Experience(String label) { this.label = label; }
        public String label() { return label; }
    }
    public enum TrainingType implements Labeled {
        RUNNING("러닝"), CALISTHENICS("맨몸운동");
        private final String label;
        TrainingType(String label) { this.label = label; }
        public String label() { return label; }
    }
    public enum Equipment implements Labeled {
        NONE("장비 없음"), MAT("매트"), PULL_UP_BAR("철봉"), DUMBBELL("덤벨");
        private final String label;
        Equipment(String label) { this.label = label; }
        public String label() { return label; }
    }
    public record Option(String value, String label) {}
    private static <T extends Enum<T> & Labeled> List<Option> values(T[] values) {
        return Arrays.stream(values).map(value -> new Option(value.name(), value.label())).toList();
    }
    public static Map<String, List<Option>> all() {
        return Map.of("goals", values(Goal.values()), "experienceLevels", values(Experience.values()),
                "preferredTypes", values(TrainingType.values()), "equipment", values(Equipment.values()));
    }
}
