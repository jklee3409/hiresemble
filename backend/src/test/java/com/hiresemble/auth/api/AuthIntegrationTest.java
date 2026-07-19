package com.hiresemble.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.hiresemble.common.security.RequestIdFilter;
import com.hiresemble.common.exception.GlobalExceptionHandler;
import com.hiresemble.support.PostgresIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@Import(AuthIntegrationTest.FailureFixtureConfiguration.class)
class AuthIntegrationTest extends PostgresIntegrationTest {

    private static final Set<String> ERROR_FIELDS = Set.of(
            "timestamp", "status", "code", "message", "fieldErrors", "requestId");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void csrfBootstrapCreatesAnonymousSessionAndIgnoresExternalRequestId() throws Exception {
        String untrusted = UUID.randomUUID().toString();

        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf")
                        .header(RequestIdFilter.HEADER_NAME, untrusted))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.parameterName").value("_csrf"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(header().exists(RequestIdFilter.HEADER_NAME))
                .andReturn();

        assertThat(result.getResponse().getCookie("SESSION")).isNotNull();
        assertThat(result.getResponse().getHeader("Set-Cookie"))
                .contains("HttpOnly")
                .containsIgnoringCase("SameSite=Lax")
                .doesNotContain("Secure");
        assertThat(result.getResponse().getHeader(RequestIdFilter.HEADER_NAME)).isNotEqualTo(untrusted);
        assertThatCodeIsUuid(result.getResponse().getHeader(RequestIdFilter.HEADER_NAME));
    }

    @Test
    void signupNormalizesEmailStoresBcryptAndCreatesProfileAndRotatedSession() throws Exception {
        CsrfSession anonymous = csrfSession();
        String rawPassword = "password-123";

        MvcResult result = signup(
                anonymous, "Case.User@Example.COM", rawPassword, "  Display Name  ", 201);
        JsonNode body = json(result);
        Cookie authenticatedCookie = requiredSessionCookie(result);

        assertThat(authenticatedCookie.getValue()).isNotEqualTo(anonymous.cookie().getValue());
        assertThat(body.at("/user/email").asText()).isEqualTo("case.user@example.com");
        assertThat(body.at("/user/displayName").asText()).isEqualTo("Display Name");
        assertThat(body.at("/csrf/token").asText()).isNotEqualTo(anonymous.token());
        assertThat(body.has("statusCode")).isFalse();
        assertThat(body.has("data")).isFalse();

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, "case.user@example.com");
        assertThat(passwordHash)
                .startsWith("$2")
                .contains("$12$")
                .doesNotContain(rawPassword);
        UUID userId = UUID.fromString(body.at("/user/id").asText());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM user_profiles WHERE user_id = ?", Long.class, userId))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT role || ':' || status FROM users WHERE id = ?", String.class, userId))
                .isEqualTo("USER:ACTIVE");
        assertThat(jdbcTemplate.queryForObject("""
                        SELECT default_quality_mode || ':' || high_quality_enabled || ':'
                               || daily_budget_usd || ':' || active || ':' || version
                        FROM user_ai_preferences WHERE user_id = ?
                        """, String.class, userId))
                .isEqualTo("ECONOMY:false:1.000000:true:0");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT principal_name FROM spring_session", String.class))
                .isEqualTo(userId.toString())
                .doesNotContain("case.user@example.com");

        mockMvc.perform(get("/api/v1/auth/me").cookie(authenticatedCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("case.user@example.com"));
    }

    @Test
    void signupRollsBackUserWhenProfileCreationFails() throws Exception {
        jdbcTemplate.execute("""
                CREATE FUNCTION reject_user_profile_fixture() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'test-only profile insert failure';
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_user_profile_fixture_trigger
                BEFORE INSERT ON user_profiles
                FOR EACH ROW EXECUTE FUNCTION reject_user_profile_fixture()
                """);
        try {
            MvcResult result = signup(
                    csrfSession(), "rollback@example.com", "password-123", "Rollback", 500);

            assertThat(json(result).get("code").asText()).isEqualTo("INTERNAL_ERROR");
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class))
                    .isZero();
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT count(*) FROM user_profiles", Long.class))
                    .isZero();
        } finally {
            jdbcTemplate.execute(
                    "DROP TRIGGER IF EXISTS reject_user_profile_fixture_trigger ON user_profiles");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_user_profile_fixture()");
        }
    }

    @Test
    void signupRollsBackUserProfileSessionAndPreferenceWhenPreferenceCreationFails()
            throws Exception {
        CsrfSession anonymous = csrfSession();
        jdbcTemplate.execute("""
                CREATE FUNCTION reject_ai_preference_fixture() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'test-only AI preference insert failure';
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_ai_preference_fixture_trigger
                BEFORE INSERT ON user_ai_preferences
                FOR EACH ROW EXECUTE FUNCTION reject_ai_preference_fixture()
                """);
        try {
            MvcResult result = signup(
                    anonymous,
                    "preference-failure@example.com",
                    "password-123",
                    "Preference Failure",
                    500);

            assertThat(json(result).get("code").asText()).isEqualTo("INTERNAL_ERROR");
            assertThat(result.getResponse().getContentAsString())
                    .doesNotContain("AI preference insert failure");
            assertFailedSignupLeftOnlySafeAnonymousSession(anonymous);
        } finally {
            jdbcTemplate.execute(
                    "DROP TRIGGER IF EXISTS reject_ai_preference_fixture_trigger "
                            + "ON user_ai_preferences");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_ai_preference_fixture()");
        }
    }

    @Test
    void sessionPersistenceFailureRollsBackUserAndProfileWithoutRequestEndAuthenticationReplay()
            throws Exception {
        CsrfSession anonymous = csrfSession();
        jdbcTemplate.execute("CREATE SEQUENCE security_context_failure_once_fixture_seq");
        jdbcTemplate.execute("""
                CREATE FUNCTION reject_security_context_fixture() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.attribute_name = 'SPRING_SECURITY_CONTEXT'
                            AND nextval('security_context_failure_once_fixture_seq') = 1 THEN
                        RAISE EXCEPTION 'test-only security context persistence failure';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER reject_security_context_fixture_trigger
                BEFORE INSERT OR UPDATE ON spring_session_attributes
                FOR EACH ROW EXECUTE FUNCTION reject_security_context_fixture()
                """);
        try {
            MvcResult result = signup(
                    anonymous,
                    "session-failure@example.com",
                    "password-123",
                    "Session Failure",
                    500);

            assertThat(json(result).get("code").asText()).isEqualTo("INTERNAL_ERROR");
            assertExactErrorFields(json(result));
            assertThat(result.getResponse().getContentAsString())
                    .doesNotContain("security context persistence failure");
            assertFailedSignupLeftOnlySafeAnonymousSession(anonymous);
            assertThat(jdbcTemplate.queryForObject(
                            "SELECT last_value FROM security_context_failure_once_fixture_seq",
                            Long.class))
                    .as("request-end must not retry the transient authenticated Session write")
                    .isEqualTo(1L);
        } finally {
            jdbcTemplate.execute(
                    "DROP TRIGGER IF EXISTS reject_security_context_fixture_trigger "
                            + "ON spring_session_attributes");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_security_context_fixture()");
            jdbcTemplate.execute("DROP SEQUENCE IF EXISTS security_context_failure_once_fixture_seq");
        }
    }

    @Test
    void databaseCommitFailureAfterSessionMutationLeavesNoDanglingAuthenticatedSession()
            throws Exception {
        CsrfSession anonymous = csrfSession();
        jdbcTemplate.execute("""
                CREATE FUNCTION reject_signup_commit_fixture() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    RAISE EXCEPTION 'test-only deferred signup commit failure';
                END;
                $$
                """);
        jdbcTemplate.execute("""
                CREATE CONSTRAINT TRIGGER reject_signup_commit_fixture_trigger
                AFTER INSERT ON users
                DEFERRABLE INITIALLY DEFERRED
                FOR EACH ROW EXECUTE FUNCTION reject_signup_commit_fixture()
                """);
        try {
            MvcResult result = signup(
                    anonymous,
                    "commit-failure@example.com",
                    "password-123",
                    "Commit Failure",
                    500);

            assertThat(json(result).get("code").asText()).isEqualTo("INTERNAL_ERROR");
            assertExactErrorFields(json(result));
            assertThat(result.getResponse().getContentAsString())
                    .doesNotContain("deferred signup commit failure");
            assertFailedSignupLeftOnlySafeAnonymousSession(anonymous);
            Cookie rotatedButRolledBack = result.getResponse().getCookie("SESSION");
            if (rotatedButRolledBack != null && !rotatedButRolledBack.getValue().isBlank()) {
                mockMvc.perform(get("/api/v1/auth/me").cookie(rotatedButRolledBack))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
            }
        } finally {
            jdbcTemplate.execute(
                    "DROP TRIGGER IF EXISTS reject_signup_commit_fixture_trigger ON users");
            jdbcTemplate.execute("DROP FUNCTION IF EXISTS reject_signup_commit_fixture()");
        }
    }

    @Test
    void duplicateEmailUsesNormalizedUniqueContract() throws Exception {
        signup(csrfSession(), "duplicate@example.com", "password-123", "First", 201);

        MvcResult duplicate = signup(
                csrfSession(), "DUPLICATE@EXAMPLE.COM", "different-123", "Second", 409);

        assertThat(json(duplicate).get("code").asText()).isEqualTo("EMAIL_ALREADY_REGISTERED");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class)).isEqualTo(1L);
        assertExactErrorFields(json(duplicate));
    }

    @Test
    void signupAndLoginValidateUtf8ByteBoundariesWithoutExposingPasswords() throws Exception {
        CsrfSession tooShortSession = csrfSession();
        String nineBytes = "가가가";
        MvcResult tooShort = signup(tooShortSession, "short@example.com", nineBytes, "Short", 400);
        assertThat(tooShort.getResponse().getContentAsString()).doesNotContain(nineBytes);
        assertThat(json(tooShort).at("/fieldErrors/0/field").asText()).isEqualTo("password");

        signup(csrfSession(), "ten@example.com", "가가가a", "Ten", 201);
        String seventyTwoBytes = "가".repeat(24);
        signup(csrfSession(), "seventy-two@example.com", seventyTwoBytes, "Seventy Two", 201);
        login(csrfSession(), "seventy-two@example.com", seventyTwoBytes, 200);

        String seventyThreeBytes = "가".repeat(24) + "a";
        MvcResult tooLong = signup(
                csrfSession(), "long@example.com", seventyThreeBytes, "Long", 400);
        assertThat(tooLong.getResponse().getContentAsString()).doesNotContain(seventyThreeBytes);
        MvcResult loginTooLong =
                login(csrfSession(), "seventy-two@example.com", seventyThreeBytes, 400);
        assertThat(loginTooLong.getResponse().getContentAsString())
                .doesNotContain(seventyThreeBytes);

        CsrfSession loginCsrf = csrfSession();
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(loginCsrf.cookie())
                        .header("X-CSRF-TOKEN", loginCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing@example.com","password":"a"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void signupRejectsPathSeparatorsAndControlCharactersInDisplayName() throws Exception {
        MvcResult pathSeparator = signup(
                csrfSession(), "path-name@example.com", "password-123", "Unsafe/Name", 400);
        MvcResult controlCharacter = signup(
                csrfSession(), "control-name@example.com", "password-123", "Unsafe\nName", 400);

        assertThat(json(pathSeparator).at("/fieldErrors/0/field").asText())
                .isEqualTo("displayName");
        assertThat(json(controlCharacter).at("/fieldErrors/0/field").asText())
                .isEqualTo("displayName");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class)).isZero();
    }

    @Test
    void loginHidesEmailExistenceAndRotatesTheAnonymousSession() throws Exception {
        signup(csrfSession(), "login@example.com", "password-123", "Login", 201);

        CsrfSession loginCsrf = csrfSession();
        MvcResult success = login(loginCsrf, "LOGIN@EXAMPLE.COM", "password-123", 200);
        Cookie loginCookie = requiredSessionCookie(success);
        assertThat(loginCookie.getValue()).isNotEqualTo(loginCsrf.cookie().getValue());

        JsonNode missing = json(login(csrfSession(), "missing@example.com", "password-123", 401));
        JsonNode wrong = json(login(csrfSession(), "login@example.com", "wrong-password", 401));
        assertThat(missing.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(wrong.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(missing.get("message").asText()).isEqualTo(wrong.get("message").asText());

        mockMvc.perform(get("/api/v1/auth/me").cookie(loginCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("login@example.com"));
    }

    @Test
    void missingCsrfRejectsSignupLoginAndLogoutWithTheCommonContract() throws Exception {
        MvcResult signup = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson("no-csrf@example.com", "password-123", "No CSRF")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
                .andReturn();
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"none@example.com","password":"password-123"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
                .andReturn();

        AuthenticatedSession authenticated = authenticated("logout-csrf@example.com", "Logout CSRF");
        MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout").cookie(authenticated.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"))
                .andReturn();

        assertExactErrorFields(json(signup));
        assertExactErrorFields(json(login));
        assertExactErrorFields(json(logout));
    }

    @Test
    void logoutInvalidatesTheSessionAndMeRequiresAuthentication() throws Exception {
        MvcResult unauthenticated = mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andReturn();
        assertExactErrorFields(json(unauthenticated));

        AuthenticatedSession authenticated = authenticated("logout@example.com", "Logout");
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(authenticated.cookie())
                        .header("X-CSRF-TOKEN", authenticated.csrfToken()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(get("/api/v1/auth/me").cookie(authenticated.cookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM spring_session", Long.class))
                .isZero();
    }

    @Test
    void twoIndependentSessionsCanOnlyProjectTheirOwnCurrentUser() throws Exception {
        AuthenticatedSession first = authenticated("first@example.com", "First");
        AuthenticatedSession second = authenticated("second@example.com", "Second");

        mockMvc.perform(get("/api/v1/auth/me").cookie(first.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first.userId().toString()))
                .andExpect(jsonPath("$.email").value("first@example.com"));
        mockMvc.perform(get("/api/v1/auth/me").cookie(second.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(second.userId().toString()))
                .andExpect(jsonPath("$.email").value("second@example.com"));
        assertThat(first.cookie().getValue()).isNotEqualTo(second.cookie().getValue());
    }

    @Test
    void malformedValidationSecurityAndUnexpectedFailuresUseTheSameSafeFieldSet()
            throws Exception {
        CsrfSession csrf = csrfSession();
        MvcResult malformed = mockMvc.perform(post("/api/v1/auth/signup")
                        .cookie(csrf.cookie())
                        .header("X-CSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andReturn();
        MvcResult validation = signup(csrfSession(), "bad", "secret-value", "", 400);
        MvcResult security = mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        AuthenticatedSession authenticated = authenticated("failure@example.com", "Failure");
        MvcResult unexpected = mockMvc.perform(get("/api/v1/test/failure")
                        .cookie(authenticated.cookie()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn();
        MvcResult invalidParameter = mockMvc.perform(get("/api/v1/test/parameter")
                        .cookie(authenticated.cookie())
                        .queryParam("number", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andReturn();

        assertExactErrorFields(json(malformed));
        assertExactErrorFields(json(validation));
        assertExactErrorFields(json(security));
        assertExactErrorFields(json(unexpected));
        assertExactErrorFields(json(invalidParameter));
        assertThat(validation.getResponse().getContentAsString()).doesNotContain("secret-value");
        assertThat(unexpected.getResponse().getContentAsString())
                .doesNotContain("password=fixture-secret")
                .doesNotContain("IllegalStateException")
                .doesNotContain("AuthIntegrationTest");
    }

    @Test
    void requestIdHeaderBodyAndMdcLogUseTheSameServerGeneratedUuid() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestIdFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            String untrusted = UUID.randomUUID().toString();
            MvcResult result = mockMvc.perform(get("/api/v1/auth/me")
                            .header(RequestIdFilter.HEADER_NAME, untrusted))
                    .andExpect(status().isUnauthorized())
                    .andReturn();
            String headerRequestId = result.getResponse().getHeader(RequestIdFilter.HEADER_NAME);
            String bodyRequestId = json(result).get("requestId").asText();

            assertThat(headerRequestId).isEqualTo(bodyRequestId).isNotEqualTo(untrusted);
            assertThatCodeIsUuid(headerRequestId);
            assertThat(appender.list)
                    .anySatisfy(event -> assertThat(event.getMDCPropertyMap())
                            .containsEntry(RequestIdFilter.MDC_KEY, headerRequestId));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void unexpectedFailureLogsOnlySafeTypeAndRequestCorrelation() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            AuthenticatedSession authenticated = authenticated("safe-log@example.com", "Safe Log");
            mockMvc.perform(get("/api/v1/test/failure").cookie(authenticated.cookie()))
                    .andExpect(status().isInternalServerError());

            assertThat(appender.list)
                    .anySatisfy(event -> {
                        assertThat(event.getFormattedMessage())
                                .contains("java.lang.IllegalStateException")
                                .doesNotContain("fixture-secret")
                                .doesNotContain("SQL select private_data");
                        assertThat(event.getMDCPropertyMap()).containsKey(RequestIdFilter.MDC_KEY);
                        assertThat(event.getThrowableProxy()).isNull();
                    });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private AuthenticatedSession authenticated(String email, String displayName) throws Exception {
        MvcResult result = signup(csrfSession(), email, "password-123", displayName, 201);
        JsonNode body = json(result);
        return new AuthenticatedSession(
                requiredSessionCookie(result),
                body.at("/csrf/token").asText(),
                UUID.fromString(body.at("/user/id").asText()));
    }

    private MvcResult signup(
            CsrfSession csrf, String email, String password, String displayName, int expectedStatus)
            throws Exception {
        return mockMvc.perform(post("/api/v1/auth/signup")
                        .cookie(csrf.cookie())
                        .header("X-CSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupJson(email, password, displayName)))
                .andExpect(status().is(expectedStatus))
                .andExpect(header().exists(RequestIdFilter.HEADER_NAME))
                .andReturn();
    }

    private MvcResult login(CsrfSession csrf, String email, String password, int expectedStatus)
            throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest(email, password));
        return mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-CSRF-TOKEN", csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(expectedStatus))
                .andReturn();
    }

    private String signupJson(String email, String password, String displayName) throws Exception {
        return objectMapper.writeValueAsString(
                new SignupRequest(email, password, displayName, true, true));
    }

    private CsrfSession csrfSession() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();
        return new CsrfSession(
                requiredSessionCookie(result), json(result).get("token").asText());
    }

    private Cookie requiredSessionCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie("SESSION");
        assertThat(cookie).as("Spring Session cookie").isNotNull();
        return cookie;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void assertFailedSignupLeftOnlySafeAnonymousSession(CsrfSession anonymous)
            throws Exception {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM users", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM user_profiles", Long.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM user_ai_preferences", Long.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM spring_session", Long.class))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM spring_session WHERE principal_name IS NOT NULL",
                        Long.class))
                .isZero();
        assertThat(jdbcTemplate.queryForObject(
                        """
                        SELECT count(*) FROM spring_session_attributes
                        WHERE attribute_name = 'SPRING_SECURITY_CONTEXT'
                        """,
                        Long.class))
                .isZero();
        mockMvc.perform(get("/api/v1/auth/me").cookie(anonymous.cookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    private void assertExactErrorFields(JsonNode error) {
        Set<String> names = new LinkedHashSet<>();
        names.addAll(error.propertyNames());
        assertThat(names).containsExactlyInAnyOrderElementsOf(ERROR_FIELDS);
        assertThat(error.get("fieldErrors").isArray()).isTrue();
    }

    private void assertThatCodeIsUuid(String value) {
        assertThat(value).isNotNull();
        assertThat(UUID.fromString(value)).isNotNull();
    }

    private record CsrfSession(Cookie cookie, String token) {}

    private record AuthenticatedSession(Cookie cookie, String csrfToken, UUID userId) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class FailureFixtureConfiguration {
        @Bean
        FailureFixtureController failureFixtureController() {
            return new FailureFixtureController();
        }
    }

    @RestController
    static class FailureFixtureController {
        @GetMapping("/api/v1/test/failure")
        void fail() {
            throw new IllegalStateException("password=fixture-secret SQL select private_data");
        }

        @GetMapping("/api/v1/test/parameter")
        int parameter(@RequestParam int number) {
            return number;
        }
    }
}
