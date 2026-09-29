package com.task.userauth.admin;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static com.task.userauth.support.ApiTestSupport.login;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminUserControllerIT {

    private static final String URL = "/api/admin/users";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void anonymousGets401() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void regularUserGets403ProblemDetail() throws Exception {
        Cookie user = login(mockMvc, "demo", "Demo12345");

        mockMvc.perform(get(URL).cookie(user))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("You do not have permission to access this resource."));
    }

    @Test
    void adminGetsPagedUsersWithoutPasswords() throws Exception {
        Cookie admin = login(mockMvc, "admin", "Admin12345");

        mockMvc.perform(get(URL).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].username", hasItems("demo", "admin")))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(2)))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void pageSizeIsCapped() throws Exception {
        Cookie admin = login(mockMvc, "admin", "Admin12345");

        mockMvc.perform(get(URL).param("size", "1000").cookie(admin))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void pagingParametersAreApplied() throws Exception {
        Cookie admin = login(mockMvc, "admin", "Admin12345");

        mockMvc.perform(get(URL).param("page", "0").param("size", "1").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].username").value("demo"))
                .andExpect(jsonPath("$.totalPages", greaterThanOrEqualTo(2)));
    }
}
