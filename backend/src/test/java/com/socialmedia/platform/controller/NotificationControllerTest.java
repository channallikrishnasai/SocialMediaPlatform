package com.socialmedia.platform.controller;

import com.socialmedia.platform.dto.NotificationResponse;
import com.socialmedia.platform.dto.PagedResponse;
import com.socialmedia.platform.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Test
    @WithMockUser
    void getUserNotifications_ShouldReturnOk() throws Exception {
        PagedResponse<NotificationResponse> response = PagedResponse.<NotificationResponse>builder()
                .content(Collections.emptyList())
                .build();
        when(notificationService.getUserNotifications(any(), any(Pageable.class))).thenReturn(response);

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void getUnreadNotifications_ShouldReturnOk() throws Exception {
        PagedResponse<NotificationResponse> response = PagedResponse.<NotificationResponse>builder()
                .content(Collections.emptyList())
                .build();
        when(notificationService.getUnreadNotifications(any(), any(Pageable.class))).thenReturn(response);

        mockMvc.perform(get("/api/notifications/unread"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void getUnreadCount_ShouldReturnOk() throws Exception {
        when(notificationService.getUnreadCount(any())).thenReturn(0L);
        mockMvc.perform(get("/api/notifications/unread/count"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void markAsRead_ShouldReturnOk() throws Exception {
        mockMvc.perform(put("/api/notifications/1/read").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void markAllAsRead_ShouldReturnOk() throws Exception {
        mockMvc.perform(put("/api/notifications/read-all").with(csrf()))
                .andExpect(status().isOk());
    }
}
