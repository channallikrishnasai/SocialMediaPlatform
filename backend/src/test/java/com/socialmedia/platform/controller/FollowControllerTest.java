package com.socialmedia.platform.controller;

import com.socialmedia.platform.dto.PagedResponse;
import com.socialmedia.platform.dto.UserResponse;
import com.socialmedia.platform.service.FollowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FollowController.class)
class FollowControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FollowService followService;

    @Test
    @WithMockUser
    void followUser_ShouldReturnOk() throws Exception {
        mockMvc.perform(post("/api/users/1/follow").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void unfollowUser_ShouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/api/users/1/follow").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void getFollowers_ShouldReturnOk() throws Exception {
        PagedResponse<UserResponse> response = PagedResponse.<UserResponse>builder()
                .content(Collections.emptyList())
                .build();
        when(followService.getFollowers(eq(1L), any(Pageable.class), any())).thenReturn(response);

        mockMvc.perform(get("/api/users/1/followers"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void getFollowing_ShouldReturnOk() throws Exception {
        PagedResponse<UserResponse> response = PagedResponse.<UserResponse>builder()
                .content(Collections.emptyList())
                .build();
        when(followService.getFollowing(eq(1L), any(Pageable.class), any())).thenReturn(response);

        mockMvc.perform(get("/api/users/1/following"))
                .andExpect(status().isOk());
    }
}
