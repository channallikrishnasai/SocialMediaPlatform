package com.socialmedia.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmedia.platform.dto.CommentRequest;
import com.socialmedia.platform.dto.PagedResponse;
import com.socialmedia.platform.dto.PostRequest;
import com.socialmedia.platform.dto.PostResponse;
import com.socialmedia.platform.service.PostService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
class PostControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostService postService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser
    void createPost_ShouldReturnCreated() throws Exception {
        PostRequest request = new PostRequest();
        request.setContent("Test content");

        mockMvc.perform(post("/api/posts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser
    void getPostById_ShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/posts/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void getUserPosts_ShouldReturnOk() throws Exception {
        PagedResponse<PostResponse> response = PagedResponse.<PostResponse>builder()
                .content(Collections.emptyList())
                .build();
        when(postService.getUserPosts(eq(1L), any(Pageable.class), any())).thenReturn(response);

        mockMvc.perform(get("/api/posts/user/1"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void updatePost_ShouldReturnOk() throws Exception {
        PostRequest request = new PostRequest();
        request.setContent("Updated content");

        mockMvc.perform(put("/api/posts/1")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void deletePost_ShouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/api/posts/1").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void likePost_ShouldReturnOk() throws Exception {
        mockMvc.perform(post("/api/posts/1/like").with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void unlikePost_ShouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/api/posts/1/like").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void addComment_ShouldReturnCreated() throws Exception {
        CommentRequest request = new CommentRequest();
        request.setContent("Test comment");

        mockMvc.perform(post("/api/posts/1/comments")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser
    void getPostComments_ShouldReturnOk() throws Exception {
        PagedResponse<com.socialmedia.platform.dto.CommentResponse> response = PagedResponse.<com.socialmedia.platform.dto.CommentResponse>builder()
                .content(Collections.emptyList())
                .build();
        when(postService.getPostComments(eq(1L), any(Pageable.class), any())).thenReturn(response);

        mockMvc.perform(get("/api/posts/1/comments"))
                .andExpect(status().isOk());
    }
}
