package com.enterpriseflow.common.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fast, database-free test of the error contract. Standalone MockMvc loads only the given
 * controller and advice, not the whole application context.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FakeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void notFoundIsMappedTo404ProblemDetail() throws Exception {
        mockMvc.perform(get("/fake/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Resource not found"))
                .andExpect(jsonPath("$.detail").value("Project with id '42' was not found."));
    }

    @Test
    void validationErrorsIncludeFieldDetails() throws Exception {
        mockMvc.perform(post("/fake/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation error"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void accessDeniedInControllerLayerIs403NotServerError() throws Exception {
        mockMvc.perform(get("/fake/forbidden"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unexpectedErrorsDoNotLeakInternalMessages() throws Exception {
        mockMvc.perform(get("/fake/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."));
    }

    @RestController
    static class FakeController {

        @GetMapping("/fake/missing")
        void missing() {
            throw new ResourceNotFoundException("Project", 42);
        }

        @PostMapping("/fake/validated")
        void validated(@Valid @RequestBody FakeRequest request) {
        }

        @GetMapping("/fake/forbidden")
        void forbidden() {
            throw new AccessDeniedException("denied");
        }

        @GetMapping("/fake/boom")
        void boom() {
            throw new IllegalStateException("SELECT * FROM secret_table failed");
        }
    }

    record FakeRequest(@NotBlank String name) {
    }
}
