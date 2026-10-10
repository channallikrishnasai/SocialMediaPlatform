package com.socialmedia.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmedia.platform.dto.AuthResponse;
import com.socialmedia.platform.dto.PostRequest;
import com.socialmedia.platform.dto.RegisterRequest;
import com.socialmedia.platform.repository.PostRepository;
import com.socialmedia.platform.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PostIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createAndFetchPostFlow() throws Exception {
        String timestamp = String.valueOf(System.currentTimeMillis());
        // 1. Register a user to get token
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername("user" + timestamp);
        registerRequest.setEmail("user" + timestamp + "@example.com");
        registerRequest.setPassword("password123");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseContent = result.getResponse().getContentAsString();
        AuthResponse authResponse = objectMapper.readValue(responseContent, AuthResponse.class);
        String token = "Bearer " + authResponse.getAccessToken();

        // 2. Create a post
        PostRequest postRequest = new PostRequest();
        postRequest.setContent("Hello Integration Test " + timestamp);

        mockMvc.perform(post("/api/posts")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(postRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Hello Integration Test " + timestamp));

        // 3. Fetch feed
        mockMvc.perform(get("/api/feed")
                .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("Hello Integration Test " + timestamp));
    }
}
