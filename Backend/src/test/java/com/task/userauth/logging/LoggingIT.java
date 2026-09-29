package com.task.userauth.logging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;

import static com.task.userauth.support.ApiTestSupport.jsonPost;
import static com.task.userauth.support.ApiTestSupport.login;
import static com.task.userauth.support.ApiTestSupport.loginJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Checks the AOP logging, the request-ID correlation and the enriched error bodies end to end. */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class LoggingIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void controllerAndServiceCallsAreLoggedWithoutSecrets(CapturedOutput output) throws Exception {
        String token = login(mockMvc, "demo", "Demo12345").getValue();

        assertThat(output.getOut())
                .contains("-> login(LoginRequest[username=demo, password=****])") // AuthController, DEBUG
                .contains("<- login")                                              // controller timing, INFO
                .contains("-> issue(UsernamePasswordAuthenticationToken)")        // TokenService, type only
                .doesNotContain("Demo12345")
                .doesNotContain(token);
    }

    @Test
    void logLinesCarryTheRequestIdFromTheHeader(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/api/users/me").header(RequestIdFilter.HEADER, "trace-abc-123"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(RequestIdFilter.HEADER, "trace-abc-123"))
                .andExpect(jsonPath("$.requestId").value("trace-abc-123"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        assertThat(output.getOut()).contains("[trace-abc-123]");
    }

    @Test
    void unsafeIncomingRequestIdIsReplaced() throws Exception {
        mockMvc.perform(get("/api/users/me").header(RequestIdFilter.HEADER, "evil\r\nFAKE LOG LINE"))
                .andExpect(header().string(RequestIdFilter.HEADER,
                        org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}")));
    }

    @Test
    void newlinesInArgumentsCannotForgeLogLines(CapturedOutput output) throws Exception {
        mockMvc.perform(jsonPost("/api/auth/login", loginJson("x\\nINFO forged entry", "Whatever123")))
                .andExpect(status().isUnauthorized());

        assertThat(output.getOut()).contains("x_INFO forged entry").doesNotContain("x\nINFO forged entry");
    }

    @Test
    void mvcLevelErrorsAreEnrichedToo() throws Exception {
        mockMvc.perform(jsonPost("/api/auth/login", "{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }
}
