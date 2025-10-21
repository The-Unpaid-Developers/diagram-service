package com.project.diagram_service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
        @DisplayName("Main method should start application successfully")
        void testMainMethod() {
            // Given
            String[] args = {};
            
            // When & Then - Test that main method executes without throwing exceptions
            try (MockedStatic<SpringApplication> springAppMock = Mockito.mockStatic(SpringApplication.class)) {
                ConfigurableApplicationContext mockContext = mock(ConfigurableApplicationContext.class);
                springAppMock.when(() -> SpringApplication.run(eq(DiagramServiceApplication.class), eq(args)))
                           .thenReturn(mockContext);
                
                // Execute main method
                assertThatCode(() -> DiagramServiceApplication.main(args))
                    .doesNotThrowAnyException();
                
                // Verify SpringApplication.run was called with correct parameters
                springAppMock.verify(() -> SpringApplication.run(DiagramServiceApplication.class, args));
            }
        }

        @Test
        @DisplayName("Main method should handle various arguments")
        void shouldHandleVariousArguments() {
            // Given
            String[] args = {"--server.port=8082", "--spring.profiles.active=test"};
            
            // When & Then - Test with specific arguments
            try (MockedStatic<SpringApplication> springAppMock = Mockito.mockStatic(SpringApplication.class)) {
                ConfigurableApplicationContext mockContext = mock(ConfigurableApplicationContext.class);
                springAppMock.when(() -> SpringApplication.run(eq(DiagramServiceApplication.class), eq(args)))
                           .thenReturn(mockContext);
                
                // Execute main method
                assertThatCode(() -> DiagramServiceApplication.main(args))
                    .doesNotThrowAnyException();
                
                // Verify SpringApplication.run was called with the specific args
                springAppMock.verify(() -> SpringApplication.run(DiagramServiceApplication.class, args));
            }
        }

        @Test
        @DisplayName("Main method should call SpringApplication.run with correct class")
        void shouldCallSpringApplicationRunWithCorrectClass() {
            // Given
            String[] args = {};
            
            // When & Then - Verify the correct application class is used
            try (MockedStatic<SpringApplication> springAppMock = Mockito.mockStatic(SpringApplication.class)) {
                ConfigurableApplicationContext mockContext = mock(ConfigurableApplicationContext.class);
                springAppMock.when(() -> SpringApplication.run(any(Class.class), any(String[].class)))
                           .thenReturn(mockContext);
                
                // Execute main method
                DiagramServiceApplication.main(args);
                
                // Verify SpringApplication.run was called with DiagramServiceApplication.class
                springAppMock.verify(() -> SpringApplication.run(DiagramServiceApplication.class, args));
            }
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