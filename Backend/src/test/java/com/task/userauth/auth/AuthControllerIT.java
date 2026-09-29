package com.task.userauth.auth;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.task.userauth.support.ApiTestSupport.jsonPost;
import static com.task.userauth.support.ApiTestSupport.loginJson;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginWithValidCredentialsSetsHttpOnlyCookieAndNoBody() throws Exception {
        mockMvc.perform(jsonPost("/api/auth/login", loginJson("demo", "Demo12345")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().string("Set-Cookie", allOf(
                        containsString("access_token="),
                        containsString("HttpOnly"),
                        containsString("SameSite=Strict"),
                        containsString("Path=/api"))));
    }

    @Test
    void loginIsCaseInsensitiveOnUsername() throws Exception {
        mockMvc.perform(jsonPost("/api/auth/login", loginJson("DEMO", "Demo12345")))
                .andExpect(status().isNoContent());
    }

    @Test
    void loginWithWrongPasswordReturns401ProblemDetail() throws Exception {
        mockMvc.perform(jsonPost("/api/auth/login", loginJson("demo", "wrong-password1")))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid username or password."))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void loginWithUnknownUserReturnsSameMessageAsWrongPassword() throws Exception {
        mockMvc.perform(jsonPost("/api/auth/login", loginJson("nobody", "Whatever123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password."));
    }

    @Test
    void loginWithBlankFieldsReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(jsonPost("/api/auth/login", loginJson("", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void loginWithStaleCookieStillWorks() throws Exception {
        mockMvc.perform(jsonPost("/api/auth/login", loginJson("demo", "Demo12345"))
                        .cookie(new Cookie("access_token", "expired.or.garbage")))
                .andExpect(status().isNoContent());
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("demo", "Demo12345")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Missing or invalid CSRF token."));
    }

    @Test
    void registerCreatesUserWithoutExposingPassword() throws Exception {
        mockMvc.perform(jsonPost("/api/auth/register", registerJson("NewUser", "New.User@Example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.email").value("new.user@example.com"))
                .andExpect(jsonPath("$.fullName").value("New User"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(content().string(not(containsString("password"))));

        mockMvc.perform(jsonPost("/api/auth/login", loginJson("newuser", "Passw0rd!")))
                .andExpect(status().isNoContent());
    }

    @Test
    void registerWithTakenUsernameReturns409() throws Exception {
        mockMvc.perform(jsonPost("/api/auth/register", registerJson("demo", "other@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.username").value("Username is already taken."));
    }

    @Test
    void registerWithTakenEmailReturns409() throws Exception {
        mockMvc.perform(jsonPost("/api/auth/register", registerJson("someoneelse", "DEMO@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.email").value("Email is already registered."));
    }

    @Test
    void registerWithInvalidInputReturns400WithFieldErrors() throws Exception {
        String body = """
                {"username":"a","email":"not-an-email","fullName":"","password":"short"}""";
        mockMvc.perform(jsonPost("/api/auth/register", body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void registerRejectsPasswordWithoutDigit() throws Exception {
        String body = """
                {"username":"nodigit","email":"nodigit@example.com","fullName":"No Digit","password":"onlyletters"}""";
        mockMvc.perform(jsonPost("/api/auth/register", body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("must contain at least one letter and one digit"));
    }

    @Test
    void logoutClearsCookie() throws Exception {
        mockMvc.perform(jsonPost("/api/auth/logout", ""))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", allOf(
                        containsString("access_token="),
                        containsString("Max-Age=0"),
                        containsString("HttpOnly"))));
    }

    @Test
    void malformedJsonReturns400ProblemDetail() throws Exception {
        mockMvc.perform(jsonPost("/api/auth/login", "{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private static String registerJson(String username, String email) {
        return """
                {"username":"%s","email":"%s","fullName":"New User","password":"Passw0rd!"}"""
                .formatted(username, email);
    }
}
