package com.socialmedia.platform.controller;

import com.socialmedia.platform.security.JwtAuthenticationFilter;
import com.socialmedia.platform.security.JwtTokenProvider;
import com.socialmedia.platform.security.CustomUserDetailsService;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

@AutoConfigureMockMvc(addFilters = false)
public abstract class BaseControllerTest {
    @MockBean
    protected JwtTokenProvider tokenProvider;

    @MockBean
    protected CustomUserDetailsService customUserDetailsService;
}
