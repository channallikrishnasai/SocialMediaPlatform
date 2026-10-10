package com.socialmedia.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.socialmedia.platform.dto.PagedResponse;
import com.socialmedia.platform.dto.PostResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedisSerializationTest {

    @Test
    void testSerializationWithDefaultTyping() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        PagedResponse<PostResponse> pagedResponse = PagedResponse.<PostResponse>builder()
                .content(List.of(PostResponse.builder().id(1L).content("test").build()))
                .pageNumber(0)
                .pageSize(10)
                .totalElements(1)
                .totalPages(1)
                .last(true)
                .build();

        byte[] serialized = serializer.serialize(pagedResponse);
        Object deserialized = serializer.deserialize(serialized);

        assertThat(deserialized).isInstanceOf(PagedResponse.class);
        PagedResponse<PostResponse> result = (PagedResponse<PostResponse>) deserialized;
        assertThat(result.getContent().get(0)).isInstanceOf(PostResponse.class);
        assertThat(result.getContent().get(0).getContent()).isEqualTo("test");
    }
}
