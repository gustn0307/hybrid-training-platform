package com.ailoveworkout.auth;

import com.ailoveworkout.WorkoutApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import static org.junit.jupiter.api.Assertions.*;

/** 별도 PostgreSQL DB에서 실제 HTTP·마이그레이션·재시작을 검증한다. 설정이 없으면 명시적으로 건너뛴다. */
@EnabledIfEnvironmentVariable(named = "AUTH_TEST_DB_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuthUserPostgresTest {
    private ServletWebServerApplicationContext app;
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private final String password = "test-" + UUID.randomUUID();
    private final String key = randomKey();

    private String randomKey() {
        byte[] bytes = new byte[32]; new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
    @BeforeAll void start() {
        // 테스트 전용 DB와 키만 주입한다. 운영 DB에서 실행하거나 스키마를 삭제하지 않는다.
        app = (ServletWebServerApplicationContext) new SpringApplicationBuilder(WorkoutApplication.class).run(
                "--server.port=0", "--spring.datasource.url=" + System.getenv("AUTH_TEST_DB_URL"),
                "--spring.datasource.username=" + System.getenv("AUTH_TEST_DB_USER"),
                "--spring.datasource.password=" + System.getenv("AUTH_TEST_DB_PASSWORD"), "--app.jwt-secret=" + key,
                "--spring.main.banner-mode=off");
    }
    @AfterAll void stop() { if (app != null) app.close(); }
    private record Reply(int status, JsonNode data, java.net.http.HttpHeaders headers) {}
    private Reply call(String method, String path, Object body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + app.getWebServer().getPort() + "/api/v1" + path))
                .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body instanceof String text ? text : json.writeValueAsString(body)));
        var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Reply(response.statusCode(), response.body().isBlank() ? json.nullNode() : json.readTree(response.body()), response.headers());
    }
    private String email() { return "user-" + UUID.randomUUID() + "@example.com"; }
    private String signupAndLogin() throws Exception {
        String email = email();
        assertEquals(201, call("POST", "/auth/signup", Map.of("email", email, "password", password, "nickname", "같은닉네임"), null).status());
        var reply = call("POST", "/auth/login", Map.of("email", email, "password", password), null);
        assertEquals(200, reply.status());
        return reply.data().get("accessToken").asText();
    }
    private Map<String, Object> profile() {
        return Map.of("goal", "FITNESS", "experienceLevel", "BEGINNER", "preferredTypes", List.of("RUNNING"), "equipment", List.of("NONE"));
    }

    @Test void signupNormalizesEmailRejectsDuplicatesAndStoresOnlyHash() throws Exception {
        String email = email();
        var body = Map.of("email", email.toUpperCase(java.util.Locale.ROOT), "password", password, "nickname", "같은닉네임");
        var created = call("POST", "/auth/signup", body, null);
        assertEquals(201, created.status()); assertEquals(email, created.data().get("email").asText());
        assertFalse(created.data().has("password")); assertFalse(created.data().has("passwordHash")); assertFalse(created.data().has("accessToken"));
        assertTrue(created.headers().allValues("Set-Cookie").isEmpty());
        assertEquals(401, call("GET", "/users/me", null, null).status());
        assertEquals(409, call("POST", "/auth/signup", body, null).status());
        assertNotNull(signupAndLogin()); // 닉네임이 같아도 별도 이메일은 가입 가능하다.
        String stored = app.getBean(JdbcTemplate.class).queryForObject("select password_hash from app_user where email = ?", String.class, email);
        assertNotEquals(password, stored); assertTrue(app.getBean(PasswordEncoder.class).matches(password, stored));
    }

    @Test void loginFailuresDoNotRevealAccountExistence() throws Exception {
        String email = email();
        call("POST", "/auth/signup", Map.of("email", email, "password", password, "nickname", "사용자"), null);
        var wrong = call("POST", "/auth/login", Map.of("email", email, "password", "wrong-password"), null);
        var missing = call("POST", "/auth/login", Map.of("email", email(), "password", "wrong-password"), null);
        assertEquals(401, wrong.status()); assertEquals(wrong.status(), missing.status()); assertEquals(wrong.data(), missing.data());
        var valid = call("POST", "/auth/login", Map.of("email", email.toUpperCase(java.util.Locale.ROOT), "password", password), null);
        assertEquals(200, valid.status()); assertEquals(1800, valid.data().get("expiresIn").asInt());
        assertTrue(valid.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
    }

    @Test void firstSetupReadAndPartialUpdateKeepOmittedFields() throws Exception {
        String token = signupAndLogin();
        var unset = call("GET", "/users/me/workout-profile", null, token);
        assertEquals(200, unset.status()); assertFalse(unset.data().get("configured").asBoolean()); assertTrue(unset.data().get("goal").isNull());
        assertEquals(400, call("PATCH", "/users/me/workout-profile", Map.of("goal", "FITNESS"), token).status());
        assertEquals(200, call("PATCH", "/users/me/workout-profile", profile(), token).status());
        var changed = call("PATCH", "/users/me/workout-profile", Map.of("goal", "HABIT"), token);
        assertEquals(200, changed.status()); assertEquals("HABIT", changed.data().get("goal").asText());
        assertEquals("BEGINNER", changed.data().get("experienceLevel").asText());
        assertEquals("RUNNING", changed.data().get("preferredTypes").get(0).asText());
        assertEquals("NONE", changed.data().get("equipment").get(0).asText());
        assertEquals(changed.data(), call("GET", "/users/me/workout-profile", null, token).data());
        var options = call("GET", "/users/me/workout-profile/options", null, token);
        assertEquals(4, options.data().get("goals").size()); assertEquals(2, options.data().get("preferredTypes").size());
    }

    @Test void usersCannotReadOrModifyAnotherProfile() throws Exception {
        String first = signupAndLogin(), second = signupAndLogin();
        call("PATCH", "/users/me/workout-profile", profile(), first);
        UUID firstId = app.getBean(JwtService.class).decode(first).id();
        assertEquals(403, call("GET", "/users/" + firstId, null, second).status());
        assertEquals(403, call("GET", "/users/" + firstId + "/workout-profile", null, second).status());
        assertEquals(403, call("PATCH", "/users/" + firstId + "/workout-profile", profile(), second).status());
        assertEquals(400, call("PATCH", "/users/me/workout-profile", Map.of("userId", firstId.toString(), "goal", "HABIT"), second).status());
        assertFalse(call("GET", "/users/me/workout-profile", null, second).data().get("configured").asBoolean());
        assertEquals("FITNESS", call("GET", "/users/me/workout-profile", null, first).data().get("goal").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"goal\":\"UNKNOWN\"}", "{\"experienceLevel\":\"EXPERT\"}",
            "{\"preferredTypes\":[\"WEIGHT\"]}", "{\"equipment\":[\"BARBELL\"]}",
            "{\"goal\":null}", "{\"experienceLevel\":null}", "{\"preferredTypes\":null}", "{\"equipment\":null}",
            "{\"preferredTypes\":[]}", "{\"equipment\":[]}", "{\"preferredTypes\":[\"RUNNING\",\"RUNNING\"]}",
            "{\"equipment\":[\"MAT\",\"MAT\"]}", "{\"equipment\":[\"NONE\",\"MAT\"]}",
            "{\"preferredTypes\":[null]}", "{\"goal\":0}"})
    void invalidProfilePatchIsAtomic(String body) throws Exception {
        String token = signupAndLogin();
        var before = call("PATCH", "/users/me/workout-profile", profile(), token);
        var invalid = call("PATCH", "/users/me/workout-profile", body, token);
        assertEquals(400, invalid.status()); assertEquals("VALIDATION_ERROR", invalid.data().get("code").asText());
        assertEquals(before.data(), call("GET", "/users/me/workout-profile", null, token).data());
    }

    @Test void expiredForgedAndUnknownUserTokensAreRejected() throws Exception {
        String token = signupAndLogin();
        UUID id = app.getBean(JwtService.class).decode(token).id();
        var claims = JwtClaimsSet.builder().issuer("ai-love-workout").audience(List.of("ai-love-workout-web"))
                .subject(id.toString()).id(UUID.randomUUID().toString()).issuedAt(Instant.now().minusSeconds(1800))
                .expiresAt(Instant.now().minusSeconds(1)).build();
        String expired = app.getBean(JwtEncoder.class).encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        assertEquals(401, call("GET", "/users/me", null, expired).status());
        String[] parts = token.split("\\.");
        assertEquals(401, call("GET", "/users/me", null, parts[0] + ".e30." + parts[2]).status());
        assertEquals(401, call("GET", "/users/me", null, app.getBean(JwtService.class).issue(UUID.randomUUID())).status());
    }

    @Test void logoutRemainsRevokedAfterActualApplicationRestart() throws Exception {
        String revoked = signupAndLogin(), stillValid = signupAndLogin();
        UUID jti = app.getBean(JwtService.class).decode(revoked).jti();
        assertEquals(204, call("POST", "/auth/logout", null, revoked).status());
        assertEquals(401, call("GET", "/users/me", null, revoked).status());
        assertEquals(401, call("POST", "/auth/logout", null, revoked).status());
        assertEquals(1, app.getBean(JdbcTemplate.class).queryForObject("select count(*) from revoked_token where jti = ?", Integer.class, jti));
        app.close(); start(); // 메모리 캐시가 아닌 동일 PostgreSQL·서명키로 새 서버를 시작한다.
        assertEquals(401, call("GET", "/users/me", null, revoked).status());
        assertEquals(200, call("GET", "/users/me", null, stillValid).status());
    }

    @Test void postgresConstraintsRejectInvalidArraysEvenWithoutApi() throws Exception {
        String token = signupAndLogin();
        UUID id = app.getBean(JwtService.class).decode(token).id();
        call("PATCH", "/users/me/workout-profile", profile(), token);
        var jdbc = app.getBean(JdbcTemplate.class);
        for (String values : List.of("ARRAY['NONE','MAT']", "ARRAY['MAT','MAT']", "ARRAY[]::text[]", "ARRAY['BARBELL']", "ARRAY[NULL]::text[]")) {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> jdbc.update("update workout_profile set equipment = " + values + " where user_id = ?", id));
        }
    }

    @Test void passwordAndNicknameLengthsAreEnforced() throws Exception {
        for (String raw : List.of("1".repeat(7), "가".repeat(73))) {
            assertEquals(400, call("POST", "/auth/signup", Map.of("email", email(), "password", raw, "nickname", "이름"), null).status());
        }
        for (String nickname : List.of("한", "이".repeat(21))) {
            assertEquals(400, call("POST", "/auth/signup", Map.of("email", email(), "password", password, "nickname", nickname), null).status());
        }
        String email = email(), unicode = "운".repeat(72);
        assertEquals(201, call("POST", "/auth/signup", Map.of("email", email, "password", unicode, "nickname", "🏃".repeat(20)), null).status());
        assertEquals(200, call("POST", "/auth/login", Map.of("email", email, "password", unicode), null).status());
    }

    static Stream<String> malformedPasswords() {
        char high = (char) 0xD800, low = (char) 0xDC00;
        return Stream.of("abcdefg" + high, "abcdefg" + low,
                "abcdef" + low + high, "abcdef" + high + low + high);
    }

    @ParameterizedTest(name = "invalid Unicode HTTP case {index}")
    @MethodSource("malformedPasswords")
    void signupAndLoginRejectMalformedUnicodeOverHttp(String raw) throws Exception {
        String email = email();
        // String 인코딩 자체의 치환을 피하고 JSON escape를 통해 서버의 UTF-16 검증에 도달시킨다.
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < raw.length(); index++) escaped.append(String.format("\\u%04x", (int) raw.charAt(index)));
        String body = "{\"email\":\"" + email + "\",\"password\":\"" + escaped + "\"";
        var signup = call("POST", "/auth/signup", body + ",\"nickname\":\"검증\"}", null);
        String existingEmail = email();
        assertEquals(201, call("POST", "/auth/signup", Map.of("email", existingEmail,
                "password", "abcdefg?", "nickname", "검증"), null).status());
        String loginBody = "{\"email\":\"" + existingEmail + "\",\"password\":\"" + escaped + "\"}";
        var login = call("POST", "/auth/login", loginBody, null);
        assertEquals(400, signup.status()); assertEquals(400, login.status());
        assertEquals("VALIDATION_ERROR", signup.data().get("code").asText());
        assertTrue(signup.data().get("fieldErrors").has("password"));
        assertTrue(login.data().get("fieldErrors").has("password"));
        assertEquals(200, call("POST", "/auth/login", Map.of("email", existingEmail, "password", "abcdefg?"), null).status());
        assertEquals(0, app.getBean(JdbcTemplate.class).queryForObject("select count(*) from app_user where email = ?", Integer.class, email));
    }

    @Test void normalEmojiAndMixedPasswordsCanSignupAndLogin() throws Exception {
        for (String raw : List.of("🏃".repeat(8), "🏃".repeat(72), "운동일지🏃123")) {
            String email = email();
            assertEquals(201, call("POST", "/auth/signup", Map.of("email", email, "password", raw, "nickname", "검증"), null).status());
            assertEquals(200, call("POST", "/auth/login", Map.of("email", email, "password", raw), null).status());
        }
    }

    @ParameterizedTest(name = "email signup and login case {index}")
    @ValueSource(strings = {"user", "운동"})
    void asciiAndUnicodeEmailsShareNormalizationAndLogoutPolicy(String local) throws Exception {
        String email = local + "-" + UUID.randomUUID() + "@example.com";
        String uppercase = email.toUpperCase(java.util.Locale.ROOT);
        var signup = call("POST", "/auth/signup", Map.of("email", uppercase, "password", password, "nickname", "검증"), null);
        assertEquals(201, signup.status()); assertEquals(email, signup.data().get("email").asText());
        var login = call("POST", "/auth/login", Map.of("email", uppercase, "password", password), null);
        assertEquals(200, login.status());
        String token = login.data().get("accessToken").asText();
        assertEquals(email, call("GET", "/users/me", null, token).data().get("email").asText());
        assertEquals(204, call("POST", "/auth/logout", null, token).status());
        assertEquals(401, call("GET", "/users/me", null, token).status());
    }

    @ParameterizedTest(name = "invalid email HTTP case {index}")
    @ValueSource(strings = {"", " ", "not-an-email", "user@", "@example.com", "user name@example.com"})
    void invalidEmailsAreRejectedForSignupAndLogin(String email) throws Exception {
        assertEquals(400, call("POST", "/auth/signup", Map.of("email", email, "password", password, "nickname", "검증"), null).status());
        assertEquals(400, call("POST", "/auth/login", Map.of("email", email, "password", password), null).status());
    }

    @Test void emailLengthLimitAccepts254AndRejects255Characters() throws Exception {
        String local = "a".repeat(32) + UUID.randomUUID().toString().replace("-", "");
        String domain = "b".repeat(63) + "." + "c".repeat(63) + ".";
        String valid = local + "@" + domain + "d".repeat(61);
        String tooLong = valid + "d";
        assertEquals(254, valid.length());
        assertEquals(201, call("POST", "/auth/signup", Map.of("email", valid, "password", password, "nickname", "검증"), null).status());
        assertEquals(200, call("POST", "/auth/login", Map.of("email", valid, "password", password), null).status());
        assertEquals(400, call("POST", "/auth/signup", Map.of("email", tooLong, "password", password, "nickname", "검증"), null).status());
        assertEquals(400, call("POST", "/auth/login", Map.of("email", tooLong, "password", password), null).status());
    }
}
