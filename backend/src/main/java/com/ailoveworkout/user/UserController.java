package com.ailoveworkout.user;

import com.ailoveworkout.auth.AuthController.Account;
import com.ailoveworkout.auth.CurrentUser;
import com.ailoveworkout.user.WorkoutProfileService.Patch;
import com.ailoveworkout.user.WorkoutProfileService.Profile;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** URL·본문의 userId 대신 검증된 인증 주체에서 본인 식별자를 가져온다. */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {
    private final WorkoutProfileService profiles;
    public UserController(WorkoutProfileService profiles) { this.profiles = profiles; }
    @GetMapping Account account(@AuthenticationPrincipal CurrentUser user) { return profiles.account(user.id()); }
    @GetMapping("/workout-profile") Profile profile(@AuthenticationPrincipal CurrentUser user) { return profiles.get(user.id()); }
    @PatchMapping("/workout-profile") Profile update(@AuthenticationPrincipal CurrentUser user, @RequestBody Patch request) {
        return profiles.update(user.id(), request);
    }
    @GetMapping("/workout-profile/options") Map<String, List<WorkoutOptions.Option>> options() { return WorkoutOptions.all(); }
}
