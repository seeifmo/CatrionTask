package com.task.userauth.support;

import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Builds requests the way the Angular client sends them: JSON body plus double-submit CSRF cookie/header. */
public final class ApiTestSupport {

    public static final String AUTH_COOKIE = "access_token";
    private static final String CSRF_VALUE = "test-csrf-token";

    private ApiTestSupport() {
    }

    public static MockHttpServletRequestBuilder jsonPost(String url, String body) {
        return post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .cookie(new Cookie("XSRF-TOKEN", CSRF_VALUE))
                .header("X-XSRF-TOKEN", CSRF_VALUE);
    }

    public static String loginJson(String username, String password) {
        return """
                {"username":"%s","password":"%s"}""".formatted(username, password);
    }

    /** Logs in and returns the auth cookie the server set. */
    public static Cookie login(MockMvc mockMvc, String username, String password) throws Exception {
        return mockMvc.perform(jsonPost("/api/auth/login", loginJson(username, password)))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie(AUTH_COOKIE);
    }
}
