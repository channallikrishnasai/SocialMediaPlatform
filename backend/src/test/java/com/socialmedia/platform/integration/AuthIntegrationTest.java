package com.socialmedia.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmedia.platform.dto.AuthRequest;
import com.socialmedia.platform.dto.RegisterRequest;
import com.socialmedia.platform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
    }

    @Test
    void registerAndLoginFlow() throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis());
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("ituser" + timestamp);
        registerRequest.setEmail("it" + timestamp + "@example.com");
        registerRequest.setPassword("password123");

        // Register
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.username").value("ituser" + timestamp));

        // Login
        AuthRequest loginRequest = new AuthRequest();
        loginRequest.setEmail("it" + timestamp + "@example.com");
        loginRequest.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }
}
