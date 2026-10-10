package com.socialmedia.platform.controller;

import com.socialmedia.platform.dto.UserResponse;
import com.socialmedia.platform.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Test
    @WithMockUser
    void getCurrentUser_ShouldReturnOk() throws Exception {
        when(userService.getCurrentUser(any())).thenReturn(UserResponse.builder().build());
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void getUserById_ShouldReturnOk() throws Exception {
        when(userService.getUserById(eq(1L), any())).thenReturn(UserResponse.builder().build());
        mockMvc.perform(get("/api/users/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void getUserByUsername_ShouldReturnOk() throws Exception {
        when(userService.getUserByUsername(eq("testuser"), any())).thenReturn(UserResponse.builder().build());
        mockMvc.perform(get("/api/users/username/testuser"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void updateProfile_ShouldReturnOk() throws Exception {
        when(userService.updateProfile(eq(1L), any(), any(), any())).thenReturn(UserResponse.builder().build());
        mockMvc.perform(put("/api/users/1")
                .with(csrf())
                .param("bio", "New bio"))
                .andExpect(status().isOk());
    }
}
