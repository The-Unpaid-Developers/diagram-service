package com.project.diagram_service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.diagram_service.client.CoreServiceClient;
import com.project.diagram_service.dto.SystemDependencyDTO;
import com.project.diagram_service.dto.BusinessCapabilityDiagramDTO;
import com.project.diagram_service.dto.SpecificSystemDependenciesDiagramDTO;
import com.project.diagram_service.dto.OverallSystemDependenciesDiagramDTO;
import com.project.diagram_service.dto.PathDiagramDTO;
import com.project.diagram_service.dto.CommonSolutionReviewDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.StopWatch;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Diagram Service Integration Tests")
class DiagramServiceIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CoreServiceClient coreServiceClient;

    private String baseUrl;
    private List<SystemDependencyDTO> mockSystemDependencies;
    private List<BusinessCapabilityDiagramDTO> mockBusinessCapabilities;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/v1/diagram";
        // Reset all mocks to ensure test independence
        reset(coreServiceClient);
        setupMockData();
    }

    private void setupMockData() {
        // Create mock system dependencies
        SystemDependencyDTO system1 = createSystemDependency("SYS-001", "Payment Service", "REV-001");
        SystemDependencyDTO system2 = createSystemDependency("SYS-002", "User Service", "REV-002");
        SystemDependencyDTO system3 = createSystemDependency("SYS-003", "External Gateway", "REV-003");

        // Add integration flows
        SystemDependencyDTO.IntegrationFlow flow1 = createIntegrationFlow(
            "SYS-002", "CONSUMER", "REST_API", "Daily", null
        );
        system1.setIntegrationFlows(Collections.singletonList(flow1));

        SystemDependencyDTO.IntegrationFlow flow2 = createIntegrationFlow(
            "SYS-003", "PRODUCER", "MESSAGE_QUEUE", "Hourly", "RABBITMQ"
        );
        system2.setIntegrationFlows(Collections.singletonList(flow2));

        mockSystemDependencies = Arrays.asList(system1, system2, system3);

        // Create mock business capabilities
        BusinessCapabilityDiagramDTO capability1 = createBusinessCapability("sys-001", "Payment Processing");
        BusinessCapabilityDiagramDTO capability2 = createBusinessCapability("sys-002", "User Management");

        mockBusinessCapabilities = Arrays.asList(capability1, capability2);
    }

    @Nested
    @DisplayName("Basic Functionality Tests")
    class BasicFunctionalityTests {

        @Test
        @DisplayName("Health endpoint should return healthy status")
        void testHealthEndpoint() {
            // When
            ResponseEntity<String> response = restTemplate.getForEntity("/health", String.class);

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo("healthy");
        }

        @Test
        @DisplayName("GET /system-dependencies should return all system dependencies")
        void testGetSystemDependencies_Success() {
            // Given
            when(coreServiceClient.getSystemDependencies()).thenReturn(mockSystemDependencies);

            // When
            ResponseEntity<List<SystemDependencyDTO>> response = restTemplate.exchange(
                baseUrl + "/system-dependencies",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<SystemDependencyDTO>>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(3);
            assertThat(response.getBody().get(0).getSystemCode()).isEqualTo("SYS-001");
            verify(coreServiceClient, times(1)).getSystemDependencies();
        }

        @Test
        @DisplayName("GET /business-capabilities should return all business capabilities")
        void testGetBusinessCapabilities_Success() {
            // Given
            when(coreServiceClient.getBusinessCapabilities()).thenReturn(mockBusinessCapabilities);

            // When
            ResponseEntity<List<BusinessCapabilityDiagramDTO>> response = restTemplate.exchange(
                baseUrl + "/business-capabilities",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<BusinessCapabilityDiagramDTO>>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).hasSize(2);
            assertThat(response.getBody().get(0).getSystemCode()).isEqualTo("sys-001");
            verify(coreServiceClient, times(1)).getBusinessCapabilities();
        }

        @Test
        @DisplayName("GET /system-dependencies/{systemCode} should return specific system diagram")
        void testGetSystemDependenciesDiagram_Success() {
            // Given
            String systemCode = "SYS-001";
            when(coreServiceClient.getSystemDependencies()).thenReturn(mockSystemDependencies);

            // When
            ResponseEntity<SpecificSystemDependenciesDiagramDTO> response = restTemplate.getForEntity(
                baseUrl + "/system-dependencies/" + systemCode,
                SpecificSystemDependenciesDiagramDTO.class
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getNodes()).isNotEmpty();
            assertThat(response.getBody().getMetadata()).isNotNull();
            assertThat(response.getBody().getMetadata().getCode()).isEqualTo(systemCode);
        }

        @Test
        @DisplayName("GET /system-dependencies/all should return overall system diagram")
        void testGetAllSystemDependenciesDiagrams_Success() {
            // Given
            when(coreServiceClient.getSystemDependencies()).thenReturn(mockSystemDependencies);

            // When
            ResponseEntity<OverallSystemDependenciesDiagramDTO> response = restTemplate.getForEntity(
                baseUrl + "/system-dependencies/all",
                OverallSystemDependenciesDiagramDTO.class
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getNodes()).isNotNull();
            assertThat(response.getBody().getLinks()).isNotNull();
            assertThat(response.getBody().getMetadata()).isNotNull();
        }

        @Test
        @DisplayName("GET /system-dependencies/path should return path diagram between systems")
        void testFindPathsBetweenSystems_Success() {
            // Given
            String startSystem = "SYS-001";
            String endSystem = "SYS-002";
            when(coreServiceClient.getSystemDependencies()).thenReturn(mockSystemDependencies);

            // When
            ResponseEntity<PathDiagramDTO> response = restTemplate.getForEntity(
                baseUrl + "/system-dependencies/path?start=" + startSystem + "&end=" + endSystem,
                PathDiagramDTO.class
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getNodes()).isNotNull();
            assertThat(response.getBody().getLinks()).isNotNull();
            assertThat(response.getBody().getMetadata()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle service errors gracefully")
        void testServiceError() {
            // Given
            when(coreServiceClient.getSystemDependencies()).thenThrow(new RuntimeException("Core service unavailable"));

            // When
            ResponseEntity<List<SystemDependencyDTO>> response = restTemplate.exchange(
                baseUrl + "/system-dependencies",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<SystemDependencyDTO>>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            verify(coreServiceClient, times(1)).getSystemDependencies();
        }

        @Test
        @DisplayName("Should handle null responses from core service")
        void testNullResponseHandling() {
            // Given
            when(coreServiceClient.getSystemDependencies()).thenReturn(null);

            // When
            ResponseEntity<List<SystemDependencyDTO>> response = restTemplate.exchange(
                baseUrl + "/system-dependencies",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<SystemDependencyDTO>>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("Should handle empty responses gracefully")
        void testEmptyResponseHandling() {
            // Given
            when(coreServiceClient.getSystemDependencies()).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<List<SystemDependencyDTO>> response = restTemplate.exchange(
                baseUrl + "/system-dependencies",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<SystemDependencyDTO>>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("Should handle invalid system codes")
        void testInvalidSystemCode() {
            // Given
            String invalidSystemCode = "INVALID-SYS";
            when(coreServiceClient.getSystemDependencies()).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<SpecificSystemDependenciesDiagramDTO> response = restTemplate.getForEntity(
                baseUrl + "/system-dependencies/" + invalidSystemCode,
                SpecificSystemDependenciesDiagramDTO.class
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        }

        @Test
        @DisplayName("Should handle invalid path parameters")
        void testInvalidPathParameters() {
            // When
            ResponseEntity<PathDiagramDTO> response = restTemplate.getForEntity(
                baseUrl + "/system-dependencies/path?start=INVALID&end=SYS-002",
                PathDiagramDTO.class
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Should handle missing query parameters")
        void testMissingParameters() {
            // When - Missing end parameter
            ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/system-dependencies/path?start=SYS-001",
                String.class
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("Performance Tests")
    class PerformanceTests {

        @Test
        @DisplayName("Should handle normal datasets efficiently")
        void testNormalDatasetPerformance() {
            // Given - Normal dataset (50 systems)
            List<SystemDependencyDTO> dataset = createMockSystemDependencies(50);
            when(coreServiceClient.getSystemDependencies()).thenReturn(dataset);

            // When
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            
            ResponseEntity<List<SystemDependencyDTO>> response = restTemplate.exchange(
                baseUrl + "/system-dependencies",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<SystemDependencyDTO>>() {}
            );
            
            stopWatch.stop();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(50);
            assertThat(stopWatch.getTotalTimeMillis()).isLessThan(1000); // Less than 1 second
        }

        @Test
        @DisplayName("Should handle large datasets within reasonable time")
        void testLargeDatasetPerformance() {
            // Given - Large dataset (500 systems)
            List<SystemDependencyDTO> largeDataset = createMockSystemDependencies(500);
            when(coreServiceClient.getSystemDependencies()).thenReturn(largeDataset);

            // When
            StopWatch stopWatch = new StopWatch();
            stopWatch.start();
            
            ResponseEntity<List<SystemDependencyDTO>> response = restTemplate.exchange(
                baseUrl + "/system-dependencies",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<SystemDependencyDTO>>() {}
            );
            
            stopWatch.stop();

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).hasSize(500);
            assertThat(stopWatch.getTotalTimeMillis()).isLessThan(3000); // Less than 3 seconds
        }

        @Test
        @DisplayName("Should handle concurrent requests properly")
        void testConcurrentRequests() throws InterruptedException {
            // Given
            when(coreServiceClient.getSystemDependencies()).thenReturn(mockSystemDependencies);

            // When - Make multiple concurrent requests
            Thread[] threads = new Thread[5];
            ResponseEntity<?>[] responses = new ResponseEntity<?>[5];

            for (int i = 0; i < 5; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    responses[index] = restTemplate.exchange(
                        baseUrl + "/system-dependencies",
                        HttpMethod.GET,
                        null,
                        new ParameterizedTypeReference<List<SystemDependencyDTO>>() {}
                    );
                });
                threads[i].start();
            }

            // Wait for all threads to complete
            for (Thread thread : threads) {
                thread.join(5000); // 5 second timeout per thread
            }

            // Then - All requests should complete successfully
            for (ResponseEntity<?> response : responses) {
                assertThat(response).isNotNull();
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            }
        }
    }

    @Nested
    @DisplayName("JSON and API Contract Tests")
    class JsonAndApiContractTests {

        @Test
        @DisplayName("Should validate JSON serialization/deserialization")
        void testJsonSerializationDeserialization() throws Exception {
            // Given
            when(coreServiceClient.getSystemDependencies()).thenReturn(mockSystemDependencies);

            // When
            ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/system-dependencies",
                String.class
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            
            // Verify JSON can be deserialized back to objects
            List<SystemDependencyDTO> deserializedList = objectMapper.readValue(
                response.getBody(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, SystemDependencyDTO.class)
            );
            
            assertThat(deserializedList).hasSize(3);
            assertThat(deserializedList.get(0).getSystemCode()).isEqualTo("SYS-001");
        }

        @Test
        @DisplayName("Should validate HTTP headers and content type")
        void testHttpHeadersAndContentType() {
            // Given
            when(coreServiceClient.getSystemDependencies()).thenReturn(mockSystemDependencies);

            // When
            ResponseEntity<List<SystemDependencyDTO>> response = restTemplate.exchange(
                baseUrl + "/system-dependencies",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<List<SystemDependencyDTO>>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getHeaders().getContentType().toString()).contains("application/json");
            assertThat(response.getBody()).isNotNull();
        }
    }

    // Helper methods for creating test data
    private SystemDependencyDTO createSystemDependency(String systemCode, String name, String reviewCode) {
        SystemDependencyDTO dto = new SystemDependencyDTO();
        dto.setSystemCode(systemCode);
        
        CommonSolutionReviewDTO.SolutionOverview overview = new CommonSolutionReviewDTO.SolutionOverview();
        CommonSolutionReviewDTO.SolutionDetails details = new CommonSolutionReviewDTO.SolutionDetails();
        details.setSolutionName(name);
        details.setSolutionReviewCode(reviewCode);
        details.setProjectName("Test Project");
        
        overview.setSolutionDetails(details);
        overview.setReviewType("ACTIVE");
        overview.setApprovalStatus("APPROVED");
        overview.setBusinessUnit("Test Unit");
        overview.setBusinessDriver("Test business driver");
        
        dto.setSolutionOverview(overview);
        dto.setIntegrationFlows(Collections.emptyList());
        
        return dto;
    }

    private SystemDependencyDTO.IntegrationFlow createIntegrationFlow(
            String counterpartSystemCode, String role, String pattern, String frequency, String middleware) {
        SystemDependencyDTO.IntegrationFlow flow = new SystemDependencyDTO.IntegrationFlow();
        flow.setCounterpartSystemCode(counterpartSystemCode);
        flow.setCounterpartSystemRole(role);
        flow.setIntegrationMethod(pattern);
        flow.setFrequency(frequency);
        flow.setMiddleware(middleware);
        flow.setComponentName("Test Component");
        flow.setPurpose("Test integration purpose");
        return flow;
    }

    private BusinessCapabilityDiagramDTO createBusinessCapability(String systemCode, String name) {
        BusinessCapabilityDiagramDTO dto = new BusinessCapabilityDiagramDTO();
        dto.setSystemCode(systemCode);
        
        CommonSolutionReviewDTO.SolutionOverview overview = new CommonSolutionReviewDTO.SolutionOverview();
        CommonSolutionReviewDTO.SolutionDetails details = new CommonSolutionReviewDTO.SolutionDetails();
        details.setSolutionName(name);
        details.setSolutionReviewCode("REV-" + systemCode.toUpperCase());
        details.setProjectName("Test Business Capability Project");
        
        overview.setSolutionDetails(details);
        overview.setReviewType("ACTIVE");
        overview.setApprovalStatus("APPROVED");
        overview.setBusinessUnit("Test Unit");
        
        dto.setSolutionOverview(overview);
        
        return dto;
    }

    private List<SystemDependencyDTO> createMockSystemDependencies(int count) {
        return java.util.stream.IntStream.range(1, count + 1)
            .mapToObj(i -> createSystemDependency(
                "SYS-" + String.format("%03d", i),
                "System " + i,
                "REV-" + String.format("%03d", i)
            ))
            .toList();
    }
}