package com.ailoveworkout.user;

import com.ailoveworkout.auth.AuthController.Account;
import com.ailoveworkout.common.ApiException;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.ailoveworkout.user.WorkoutOptions.*;

/** 본인 프로필의 미설정·최초 설정·부분 수정을 하나의 트랜잭션으로 처리한다. */
@Service
public class WorkoutProfileService {
    private final UserRepository users;
    private final WorkoutProfileRepository profiles;
    public WorkoutProfileService(UserRepository users, WorkoutProfileRepository profiles) {
        this.users = users; this.profiles = profiles;
    }

    /** 누락은 유지, 명시적 null은 오류로 구분한다. enum 역직렬화로 미지원 코드를 차단한다. */
    public static class Patch {
        @JsonSetter(nulls = Nulls.FAIL) public Goal goal;
        @JsonSetter(nulls = Nulls.FAIL) public Experience experienceLevel;
        @JsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.FAIL) public List<TrainingType> preferredTypes;
        @JsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.FAIL) public List<Equipment> equipment;
    }
    public record Profile(boolean configured, String goal, String experienceLevel,
            List<String> preferredTypes, List<String> equipment) {
        static Profile from(WorkoutProfile profile) {
            return profile == null ? new Profile(false, null, null, null, null)
                    : new Profile(true, profile.getGoal(), profile.getExperienceLevel(), profile.getPreferredTypes(), profile.getEquipment());
        }
    }

    @Transactional(readOnly = true)
    public Account account(UUID userId) {
        return Account.from(users.findById(userId).orElseThrow(() -> new ApiException(401, "UNAUTHENTICATED", "인증이 필요합니다.")));
    }

    @Transactional(readOnly = true)
    public Profile get(UUID userId) { return Profile.from(profiles.findById(userId).orElse(null)); }

    @Transactional
    public Profile update(UUID userId, Patch patch) {
        users.findForUpdateById(userId).orElseThrow(() -> new ApiException(401, "UNAUTHENTICATED", "인증이 필요합니다."));
        WorkoutProfile profile = profiles.findById(userId).orElse(null);
        if (patch.goal == null && patch.experienceLevel == null && patch.preferredTypes == null && patch.equipment == null)
            throw ApiException.invalid("하나 이상의 변경 항목이 필요합니다.");
        if (profile == null && (patch.goal == null || patch.experienceLevel == null || patch.preferredTypes == null || patch.equipment == null))
            throw ApiException.invalid("최초 설정에는 목표·경험·운동 유형·장비가 모두 필요합니다.");
        if (patch.preferredTypes != null) requireSelection(patch.preferredTypes);
        if (patch.equipment != null) {
            requireSelection(patch.equipment);
            if (patch.equipment.contains(Equipment.NONE) && patch.equipment.size() > 1)
                throw ApiException.invalid("장비 없음은 다른 장비와 함께 선택할 수 없습니다.");
        }
        boolean first = profile == null;
        if (first) profile = new WorkoutProfile(userId);
        // 전체 검증 이후에만 값을 적용하므로 실패 시 일부 필드만 변경되지 않는다.
        profile.update(patch.goal == null ? profile.getGoal() : patch.goal.name(),
                patch.experienceLevel == null ? profile.getExperienceLevel() : patch.experienceLevel.name(),
                patch.preferredTypes == null ? profile.getPreferredTypes().toArray(String[]::new)
                        : patch.preferredTypes.stream().map(Enum::name).toArray(String[]::new),
                patch.equipment == null ? profile.getEquipment().toArray(String[]::new)
                        : patch.equipment.stream().map(Enum::name).toArray(String[]::new));
        if (first) profiles.save(profile);
        return Profile.from(profile);
    }

    private void requireSelection(List<?> values) {
        if (values.isEmpty() || values.stream().anyMatch(java.util.Objects::isNull) || new HashSet<>(values).size() != values.size())
            throw ApiException.invalid("선택값은 하나 이상이어야 하며 중복과 null은 허용하지 않습니다.");
    }
}
