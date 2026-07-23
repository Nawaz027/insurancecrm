package com.example.insurancecrm.controller;

import com.example.insurancecrm.domain.User;
import com.example.insurancecrm.enums.Role;
import com.example.insurancecrm.repository.UserRepository;
import com.example.insurancecrm.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.Map;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Covers a gap where deactivating a user left their already-issued access token usable until it
 *  naturally expired — JwtAuthFilter only checked token revocation/inactivity, never user.active,
 *  so a deactivated agent could keep hitting the API right up until they tried to log back in. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class UserDeactivationRevokesAccessIT {

    @Autowired private WebApplicationContext webApplicationContext;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
    private static final String ADMIN_EMAIL = "deact-admin@test.com";
    private static final String AGENT_EMAIL = "deact-agent@test.com";
    private static final String PASSWORD = "Password@123";

    private String adminToken;
    private String agentAccessToken;
    private String agentRefreshToken;
    private String agentId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        cleanUp();
        User admin = userRepository.save(User.builder().name("Admin").email(ADMIN_EMAIL)
                .password(passwordEncoder.encode(PASSWORD)).role(Role.ADMIN).active(true)
                .createdAt(LocalDateTime.now()).build());
        User agent = userRepository.save(User.builder().name("Agent").email(AGENT_EMAIL)
                .password(passwordEncoder.encode(PASSWORD)).role(Role.AGENT).active(true)
                .createdAt(LocalDateTime.now()).build());

        agentId = agent.getId();
        adminToken = jwtUtil.generateAccessToken(admin.getEmail(), admin.getId(), "ADMIN");
        agentAccessToken = jwtUtil.generateAccessToken(agent.getEmail(), agent.getId(), "AGENT");
        agentRefreshToken = jwtUtil.generateRefreshToken(agent.getEmail(), agent.getId(), "AGENT");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    private void cleanUp() {
        userRepository.findByEmail(ADMIN_EMAIL).ifPresent(userRepository::delete);
        userRepository.findByEmail(AGENT_EMAIL).ifPresent(userRepository::delete);
    }

    private ResultActions probeAgentAccessToken() throws Exception {
        // GET /api/customers only needs a valid authenticated principal, any role — good for
        // proving whether a token is still accepted at all.
        return mockMvc.perform(get("/api/customers").header("Authorization", "Bearer " + agentAccessToken));
    }

    @Test
    void deactivate_revokesAgentAccessTokenImmediately_noReLoginRequired() throws Exception {
        probeAgentAccessToken().andExpect(status().isOk());

        mockMvc.perform(delete("/api/users/" + agentId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        probeAgentAccessToken().andExpect(status().isUnauthorized());
    }

    @Test
    void deactivate_alsoRevokesAgentRefreshToken() throws Exception {
        mockMvc.perform(delete("/api/users/" + agentId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", agentRefreshToken))))
                .andExpect(status().isForbidden());
    }
}
