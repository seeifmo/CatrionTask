package com.task.userauth.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static com.task.userauth.support.ApiTestSupport.jsonPost;
import static com.task.userauth.support.ApiTestSupport.loginJson;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Own application context with tiny budgets, so the limits are hit quickly. Each test uses its own IP. */
@SpringBootTest(properties = {
        "app.rate-limit.auth.capacity=3",
        "app.rate-limit.auth.period=1m",
        "app.rate-limit.api.capacity=5",
        "app.rate-limit.api.period=1m"})
@AutoConfigureMockMvc
class RateLimitIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginIsLimitedPerIpAndReturns429WithRetryAfter() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(badLogin().with(from("10.0.0.1")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("X-RateLimit-Remaining", String.valueOf(2 - i)));
        }

        mockMvc.perform(badLogin().with(from("10.0.0.1")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.detail", startsWith("Too many requests.")))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void theLimitIsBlockedEvenForCorrectCredentials() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(badLogin().with(from("10.0.0.2")));
        }
        mockMvc.perform(jsonPost("/api/auth/login", loginJson("demo", "Demo12345")).with(from("10.0.0.2")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void otherClientsAreNotAffected() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(badLogin().with(from("10.0.0.3")));
        }
        mockMvc.perform(badLogin().with(from("10.0.0.4")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authAndGeneralApiBudgetsAreSeparate() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(badLogin().with(from("10.0.0.5")));
        }
        // Auth budget exhausted, but ordinary API calls from the same IP still go through.
        mockMvc.perform(get("/api/users/me").with(from("10.0.0.5")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void generalApiIsLimitedToo() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/users/me").with(from("10.0.0.6")))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(get("/api/users/me").with(from("10.0.0.6")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void healthCheckIsNeverLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/actuator/health").with(from("10.0.0.7")))
                    .andExpect(status().isOk());
        }
    }

    private static MockHttpServletRequestBuilder badLogin() {
        return jsonPost("/api/auth/login", loginJson("demo", "wrong-password1"));
    }

    private static RequestPostProcessor from(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
