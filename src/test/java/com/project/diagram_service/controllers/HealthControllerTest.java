package com.project.diagram_service.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = HealthController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
@ActiveProfiles("test")
@DisplayName("HealthController Tests")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should return OK status for health check")
    void shouldReturnOkStatusForHealthCheck() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("healthy"));
    }

    @Test
    @DisplayName("Should handle health check endpoint with different HTTP methods")
    void shouldHandleHealthCheckWithDifferentMethods() throws Exception {
        // GET should work
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("healthy"));

        // POST should return method not allowed
        mockMvc.perform(post("/health"))
                .andExpect(status().isMethodNotAllowed());

        // PUT should return method not allowed
        mockMvc.perform(put("/health"))
                .andExpect(status().isMethodNotAllowed());

        // DELETE should return method not allowed
        mockMvc.perform(delete("/health"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("Should return plain text content type")
    void shouldReturnPlainTextContentType() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/plain;charset=UTF-8"))
                .andExpect(content().string("healthy"));
    }
}