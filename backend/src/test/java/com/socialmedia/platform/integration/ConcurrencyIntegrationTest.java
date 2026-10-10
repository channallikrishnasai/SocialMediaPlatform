package com.socialmedia.platform.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.socialmedia.platform.dto.AuthResponse;
import com.socialmedia.platform.dto.PostRequest;
import com.socialmedia.platform.dto.RegisterRequest;
import com.socialmedia.platform.repository.FollowRepository;
import com.socialmedia.platform.repository.LikeRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves that concurrent duplicate follow/like requests from the same user resolve to
 * exactly one row, backstopped by the DB unique constraints on
 * (follower_id, following_id) and (post_id, user_id) rather than in-JVM locking alone.
 */
@AutoConfigureMockMvc
class ConcurrencyIntegrationTest extends BaseIntegrationTest {

    private static final int CONCURRENT_REQUESTS = 10;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private LikeRepository likeRepository;

    @Test
    void concurrentFollowRequests_ResultInExactlyOneFollowRow() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String tokenA = registerAndGetToken("userA" + suffix);
        long userBId = registerAndGetUserId("userB" + suffix);
        long userAId = getUserId(tokenA);

        fireConcurrently(CONCURRENT_REQUESTS, () ->
                mockMvc.perform(post("/api/users/" + userBId + "/follow")
                        .header("Authorization", "Bearer " + tokenA))
        );

        long count = followRepository.countByFollowerIdAndFollowingId(userAId, userBId);
        assertEquals(1, count, "Concurrent duplicate follow requests must yield exactly one Follow row");
    }

    @Test
    void concurrentLikeRequests_ResultInExactlyOneLikeRow() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String token = registerAndGetToken("liker" + suffix);
        long postId = createPost(token, "Post to like concurrently " + suffix);
        long userId = getUserId(token);

        fireConcurrently(CONCURRENT_REQUESTS, () ->
                mockMvc.perform(post("/api/posts/" + postId + "/like")
                        .header("Authorization", "Bearer " + token))
        );

        long count = likeRepository.countByPostIdAndUserId(postId, userId);
        assertEquals(1, count, "Concurrent duplicate like requests must yield exactly one Like row");
    }

    private interface MvcCall {
        ResultActions call() throws Exception;
    }

    private void fireConcurrently(int times, MvcCall call) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(times);
        CountDownLatch ready = new CountDownLatch(times);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(times);

        for (int i = 0; i < times; i++) {
            executor.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    call.call();
                } catch (Exception e) {
                    // Some requests may legitimately fail (e.g. 409 on a losing race) -
                    // the assertion is on the resulting row count, not per-request status.
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();
    }

    private String registerAndGetToken(String username) throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setEmail(username + "@example.com");
        request.setPassword("password123");

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class)
                .getAccessToken();
    }

    private long registerAndGetUserId(String username) throws Exception {
        return getUserId(registerAndGetToken(username));
    }

    private long getUserId(String token) throws Exception {
        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/users/me")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createPost(String token, String content) throws Exception {
        PostRequest request = new PostRequest();
        request.setContent(content);

        MvcResult result = mockMvc.perform(post("/api/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }
}
