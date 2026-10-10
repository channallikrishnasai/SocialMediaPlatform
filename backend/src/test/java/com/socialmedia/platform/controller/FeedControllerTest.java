package com.socialmedia.platform.controller;

import com.socialmedia.platform.dto.PagedResponse;
import com.socialmedia.platform.dto.PostResponse;
import com.socialmedia.platform.service.FeedService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FeedController.class)
class FeedControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FeedService feedService;

    @Test
    @WithMockUser
    void getPersonalizedFeed_ShouldReturnOk() throws Exception {
        PagedResponse<PostResponse> response = PagedResponse.<PostResponse>builder()
                .content(Collections.emptyList())
                .build();
        when(feedService.getPersonalizedFeed(any(), any(Pageable.class))).thenReturn(response);

        mockMvc.perform(get("/api/feed")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
