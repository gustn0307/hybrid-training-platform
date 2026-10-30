package com.ailoveworkout.user;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkoutProfileRepository extends JpaRepository<WorkoutProfile, UUID> {}
