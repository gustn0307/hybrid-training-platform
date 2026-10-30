package com.ailoveworkout.user;

import jakarta.persistence.*;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 계획 입력에 쓰이는 현재 운동 정보이며 기존 후보·확정 계획을 참조하거나 갱신하지 않는다. */
@Entity
@Table(name = "workout_profile")
public class WorkoutProfile {
    @Id @Column(name = "user_id") private UUID userId;
    @Column(nullable = false, length = 32) private String goal;
    @Column(name = "experience_level", nullable = false, length = 32) private String experienceLevel;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "preferred_types", nullable = false, columnDefinition = "text[]") private String[] preferredTypes;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]") private String[] equipment;

    protected WorkoutProfile() {}
    public WorkoutProfile(UUID userId) { this.userId = userId; }
    public String getGoal() { return goal; }
    public String getExperienceLevel() { return experienceLevel; }
    public List<String> getPreferredTypes() { return List.of(preferredTypes); }
    public List<String> getEquipment() { return List.of(equipment); }
    public void update(String goal, String experienceLevel, String[] preferredTypes, String[] equipment) {
        this.goal = goal; this.experienceLevel = experienceLevel;
        this.preferredTypes = preferredTypes.clone(); this.equipment = equipment.clone();
    }
}
