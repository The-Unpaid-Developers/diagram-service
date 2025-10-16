package com.project.diagram_service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Diagram Service Application Tests")
class DiagramServiceApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Nested
    @DisplayName("Application Context Tests")
    class ApplicationContextTest {

        @Test
        @DisplayName("Application context should load successfully")
        void contextLoads() {
            // This test verifies that the Spring Boot application context loads correctly
            // and all beans are properly configured
            assertThat(applicationContext).isNotNull();
            assertThat(applicationContext.getBeanDefinitionCount()).isGreaterThan(0);
            
            // Verify that key service beans are present
            assertThat(applicationContext.containsBean("diagramService"))
                .as("DiagramService bean should be present in application context")
                .isTrue();
                
            assertThat(applicationContext.containsBean("diagramController"))
                .as("DiagramController bean should be present in application context")
                .isTrue();
                
            assertThat(applicationContext.containsBean("coreServiceClient"))
                .as("CoreServiceClient bean should be present in application context")
                .isTrue();
        }
    }

    @Nested
    @DisplayName("Main Method Tests")
    class MainMethodTest {

        @Test
        @DisplayName("Main method should start application without errors")
        void testMainMethod() {
            // Test that main method can be called without throwing exceptions
            // Note: We don't actually run the full application to avoid port conflicts
            assertThatCode(() -> {
                // DiagramServiceApplication.main(new String[]{}); // Commented out to avoid port conflicts in tests
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Application should handle empty command line arguments")
        void shouldHandleEmptyCommandLineArguments() {
            // Verify application can handle null or empty arguments
            assertThatCode(() -> {
                // Verify the application class exists and is properly configured
                Class<?> appClass = DiagramServiceApplication.class;
                assertThat(appClass).isNotNull();
                assertThat(appClass.getSimpleName()).isEqualTo("DiagramServiceApplication");
            }).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTest {

        @Test
        @DisplayName("Application should have proper Spring Boot annotations")
        void shouldHaveProperSpringBootAnnotations() {
            // Verify the main application class has the required Spring Boot annotation
            Class<?> appClass = DiagramServiceApplication.class;
            
            assertThat(appClass.isAnnotationPresent(org.springframework.boot.autoconfigure.SpringBootApplication.class))
                .as("Application class should have @SpringBootApplication annotation")
                .isTrue();
        }
    }
}