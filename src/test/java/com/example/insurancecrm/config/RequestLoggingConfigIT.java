package com.example.insurancecrm.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.enums.Role;
import com.example.insurancecrm.repository.UserRepository;
import com.example.insurancecrm.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Regression coverage for the plaintext-password-in-logs incident: CommonsRequestLoggingFilter
 * logs full request bodies at DEBUG, which previously included raw passwords/refresh tokens on
 * every login. Asserts the filter is skipped for credential-carrying routes, and still runs
 * normally elsewhere (so the fix doesn't just silently disable logging altogether).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class RequestLoggingConfigIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private CredentialRedactingRequestLoggingFilter requestLoggingFilter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
    private ListAppender<ILoggingEvent> appender;

    private static final String ADMIN_EMAIL = "rlc-admin@test.com";
    private static final String PASSWORD = "Password@123";
    private String adminToken;

    @BeforeEach
    void setUp() {
        // webAppContextSetup doesn't auto-wire plain Filter beans (only springSecurity()'s chain) —
        // the logging filter has to be added explicitly or it never runs during these requests.
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(requestLoggingFilter).apply(springSecurity()).build();
        cleanUp();
        User admin = userRepository.save(User.builder().name("Admin").email(ADMIN_EMAIL)
                .password(passwordEncoder.encode(PASSWORD)).role(Role.ADMIN).active(true)
                .createdAt(LocalDateTime.now()).build());
        adminToken = jwtUtil.generateAccessToken(admin.getEmail(), admin.getId(), "ADMIN");

        appender = new ListAppender<>();
        appender.start();
        // The filter only logs at DEBUG; test config doesn't set that (see application.yaml under
        // src/test/resources, which shadows the main one and omits the `logging.level` block), so
        // it's forced here rather than relying on it — otherwise every assertion below would pass
        // vacuously whether or not the filter actually ran.
        logger().setLevel(Level.DEBUG);
        logger().addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger().detachAppender(appender);
        logger().setLevel(null);
        cleanUp();
    }

    private void cleanUp() {
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail("rlc-created@test.com").ifPresent(userRepository::delete);
    }

    private Logger logger() {
        return (Logger) LoggerFactory.getLogger(CredentialRedactingRequestLoggingFilter.class);
    }

    private boolean anyLoggedMessageContains(String needle) {
        return appender.list.stream().anyMatch(e -> e.getFormattedMessage().contains(needle));
    }

    @Test
    void login_payloadIsNeverLogged() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", ADMIN_EMAIL, "password", PASSWORD))));

        assertThat(anyLoggedMessageContains(PASSWORD)).isFalse();
        assertThat(appender.list).isEmpty();
    }

    @Test
    void createUser_payloadIsNeverLogged() throws Exception {
        String rawPassword = "BrandNewPassword@1";
        mockMvc.perform(post("/api/users").header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "name", "Created", "email", "rlc-created@test.com",
                        "password", rawPassword, "role", "AGENT"))));

        assertThat(anyLoggedMessageContains(rawPassword)).isFalse();
        assertThat(appender.list).isEmpty();
    }

    @Test
    void updateUser_payloadIsNeverLogged() throws Exception {
        User target = userRepository.save(User.builder().name("Target").email("rlc-created@test.com")
                .password(passwordEncoder.encode("pw")).role(Role.AGENT).active(true)
                .createdAt(LocalDateTime.now()).build());
        String newPassword = "UpdatedPassword@1";

        mockMvc.perform(put("/api/users/" + target.getId()).header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "name", "Target", "email", "rlc-created@test.com",
                        "password", newPassword, "role", "AGENT"))));

        assertThat(anyLoggedMessageContains(newPassword)).isFalse();
        assertThat(appender.list).isEmpty();
    }

    @Test
    void nonCredentialRoute_isStillLogged() throws Exception {
        // Proves the fix scopes to credential-carrying routes rather than disabling the filter entirely.
        mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + adminToken));

        assertThat(anyLoggedMessageContains("/api/users")).isTrue();
    }
}
