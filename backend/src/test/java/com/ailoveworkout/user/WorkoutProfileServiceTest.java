package com.ailoveworkout.user;

import com.ailoveworkout.common.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static com.ailoveworkout.user.WorkoutOptions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** FR-USER-004의 누락 유지·원자적 실패와 장비 조합을 검증한다. 실제 DB 검증과 구분한다. */
class WorkoutProfileServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final WorkoutProfileRepository profiles = mock(WorkoutProfileRepository.class);
    private final WorkoutProfileService service = new WorkoutProfileService(users, profiles);
    private final User user = new User("test@example.com", "unused", "테스트", Instant.now());

    @BeforeEach void setup() {
        when(users.findForUpdateById(user.getId())).thenReturn(Optional.of(user));
        when(profiles.findById(user.getId())).thenReturn(Optional.empty());
    }
    private WorkoutProfileService.Patch full() {
        var patch = new WorkoutProfileService.Patch();
        patch.goal = Goal.FITNESS; patch.experienceLevel = Experience.BEGINNER;
        patch.preferredTypes = List.of(TrainingType.RUNNING); patch.equipment = List.of(Equipment.NONE);
        return patch;
    }
    @Test void missingProfileIsNormalAndFirstSetupRequiresAllFields() {
        assertFalse(service.get(user.getId()).configured());
        var patch = full(); patch.equipment = null;
        assertThrows(ApiException.class, () -> service.update(user.getId(), patch));
        verify(profiles, never()).save(any());
        assertTrue(service.update(user.getId(), full()).configured());
    }
    @Test void partialUpdateKeepsOmittedValues() {
        var profile = new WorkoutProfile(user.getId());
        profile.update("FITNESS", "BEGINNER", new String[]{"RUNNING"}, new String[]{"NONE"});
        when(profiles.findById(user.getId())).thenReturn(Optional.of(profile));
        var patch = new WorkoutProfileService.Patch(); patch.goal = Goal.HABIT;
        var updated = service.update(user.getId(), patch);
        assertEquals("HABIT", updated.goal()); assertEquals("BEGINNER", updated.experienceLevel());
        assertEquals(List.of("RUNNING"), updated.preferredTypes()); assertEquals(List.of("NONE"), updated.equipment());
    }
    @Test void duplicateEmptyAndMixedEquipmentAreRejected() {
        var patch = full(); patch.equipment = List.of(Equipment.NONE, Equipment.MAT);
        assertThrows(ApiException.class, () -> service.update(user.getId(), patch));
        patch.equipment = List.of(Equipment.MAT, Equipment.MAT);
        assertThrows(ApiException.class, () -> service.update(user.getId(), patch));
        patch.equipment = List.of();
        assertThrows(ApiException.class, () -> service.update(user.getId(), patch));
        var duplicate = full(); duplicate.preferredTypes = List.of(TrainingType.RUNNING, TrainingType.RUNNING);
        assertThrows(ApiException.class, () -> service.update(user.getId(), duplicate));
    }
    @Test void invalidUpdateDoesNotPartiallyChangeExistingProfile() {
        var profile = new WorkoutProfile(user.getId());
        profile.update("FITNESS", "BEGINNER", new String[]{"RUNNING"}, new String[]{"NONE"});
        when(profiles.findById(user.getId())).thenReturn(Optional.of(profile));
        var patch = full(); patch.goal = Goal.HABIT; patch.equipment = List.of(Equipment.NONE, Equipment.MAT);
        assertThrows(ApiException.class, () -> service.update(user.getId(), patch));
        assertEquals("FITNESS", profile.getGoal());
        assertThrows(ApiException.class, () -> service.update(user.getId(), new WorkoutProfileService.Patch()));
    }
}
