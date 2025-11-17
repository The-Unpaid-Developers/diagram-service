package com.project.diagram_service.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.diagram_service.dto.SystemDependencyDTO;
import com.project.diagram_service.dto.BusinessCapabilityDiagramDTO;
import com.project.diagram_service.dto.BusinessCapabilityDTO;
import com.project.diagram_service.dto.CommonSolutionReviewDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@DisplayName("CoreServiceClient Tests")
class CoreServiceClientTest {

    private CoreServiceClient coreServiceClient;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;
    private final String baseUrl = "http://test-core-service.com";

    @BeforeEach
    void setUp() {
        coreServiceClient = new CoreServiceClient(baseUrl);
        // Access the RestTemplate through reflection for testing
        RestTemplate restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.createServer(restTemplate);
        objectMapper = new ObjectMapper();
        
        // Replace the RestTemplate in the client
        try {
            var restTemplateField = CoreServiceClient.class.getDeclaredField("restTemplate");
            restTemplateField.setAccessible(true);
            restTemplateField.set(coreServiceClient, restTemplate);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set up test RestTemplate", e);
        }
    }

    @Test
    @DisplayName("Should successfully retrieve system dependencies")
    void testGetSystemDependencies_Success() throws JsonProcessingException {
        // Given
        List<SystemDependencyDTO> expectedResponse = createMockSystemDependencies();
        String jsonResponse = objectMapper.writeValueAsString(expectedResponse);

        mockServer.expect(requestTo(baseUrl + "/api/v1/solution-review/system-dependencies"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        // When
        List<SystemDependencyDTO> result = coreServiceClient.getSystemDependencies();

        // Then
        assertThat(result)
            .isNotNull()
            .hasSize(2)
            .satisfies(systems -> {
                assertThat(systems.get(0).getSystemCode()).isEqualTo("SYS-001");
                assertThat(systems.get(1).getSystemCode()).isEqualTo("SYS-002");
            });
        mockServer.verify();
    }

    @Test
    @DisplayName("Should handle HTTP error when retrieving system dependencies")
    void testGetSystemDependencies_HttpError() {
        // Given
        mockServer.expect(requestTo(baseUrl + "/api/v1/solution-review/system-dependencies"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        // When & Then
        assertThatThrownBy(() -> coreServiceClient.getSystemDependencies())
                .isInstanceOf(RestClientException.class);
        mockServer.verify();
    }

    @Test
    @DisplayName("Should handle malformed JSON response for system dependencies")
    void testGetSystemDependencies_MalformedJson() {
        // Given
        mockServer.expect(requestTo(baseUrl + "/api/v1/solution-review/system-dependencies"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("invalid-json", MediaType.APPLICATION_JSON));

        // When & Then
        assertThatThrownBy(() -> coreServiceClient.getSystemDependencies())
                .isInstanceOf(RestClientException.class);
        mockServer.verify();
    }

    @Test
    @DisplayName("Should successfully retrieve business capabilities")
    void testGetBusinessCapabilities_Success() throws JsonProcessingException {
        // Given
        List<BusinessCapabilityDiagramDTO> expectedResponse = createMockBusinessCapabilities();
        String jsonResponse = objectMapper.writeValueAsString(expectedResponse);

        mockServer.expect(requestTo(baseUrl + "/api/v1/solution-review/business-capabilities"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        // When
        List<BusinessCapabilityDiagramDTO> result = coreServiceClient.getBusinessCapabilities();

        // Then
        assertThat(result)
            .isNotNull()
            .hasSize(2)
            .satisfies(capabilities -> {
                assertThat(capabilities.get(0).getSystemCode()).isEqualTo("sys-001");
                assertThat(capabilities.get(1).getSystemCode()).isEqualTo("sys-002");
            });
        mockServer.verify();
    }

    @Test
    @DisplayName("Should handle HTTP error when retrieving business capabilities")
    void testGetBusinessCapabilities_HttpError() {
        // Given
        mockServer.expect(requestTo(baseUrl + "/api/v1/solution-review/business-capabilities"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        // When & Then
        assertThatThrownBy(() -> coreServiceClient.getBusinessCapabilities())
                .isInstanceOf(RestClientException.class);
        mockServer.verify();
    }

    @Test
    @DisplayName("Should handle empty response for business capabilities")
    void testGetBusinessCapabilities_EmptyResponse() throws JsonProcessingException {
        // Given
        String jsonResponse = objectMapper.writeValueAsString(Arrays.asList());

        mockServer.expect(requestTo(baseUrl + "/api/v1/solution-review/business-capabilities"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        // When
        List<BusinessCapabilityDiagramDTO> result = coreServiceClient.getBusinessCapabilities();

        // Then
        assertThat(result)
            .isNotNull()
            .isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Should handle network timeout")
    void testGetSystemDependencies_NetworkTimeout() {
        // Given
        mockServer.expect(requestTo(baseUrl + "/api/v1/solution-review/system-dependencies"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError().body("Connection timeout"));

        // When & Then
        assertThatThrownBy(() -> coreServiceClient.getSystemDependencies())
                .isInstanceOf(RestClientException.class);
        mockServer.verify();
    }

    // Helper methods
    private List<SystemDependencyDTO> createMockSystemDependencies() {
        SystemDependencyDTO system1 = new SystemDependencyDTO();
        system1.setSystemCode("SYS-001");
        
        CommonSolutionReviewDTO.SolutionOverview overview1 = new CommonSolutionReviewDTO.SolutionOverview();
        CommonSolutionReviewDTO.SolutionDetails details1 = new CommonSolutionReviewDTO.SolutionDetails();
        details1.setSolutionName("Payment Service");
        details1.setSolutionReviewCode("REV-001");
        details1.setProjectName("Payment Project");
        overview1.setSolutionDetails(details1);
        overview1.setReviewType("ACTIVE");
        overview1.setApprovalStatus("APPROVED");
        overview1.setBusinessUnit("Finance");
        overview1.setBusinessDriver("Process payments efficiently");
        
        system1.setSolutionOverview(overview1);

        SystemDependencyDTO system2 = new SystemDependencyDTO();
        system2.setSystemCode("SYS-002");
        
        CommonSolutionReviewDTO.SolutionOverview overview2 = new CommonSolutionReviewDTO.SolutionOverview();
        CommonSolutionReviewDTO.SolutionDetails details2 = new CommonSolutionReviewDTO.SolutionDetails();
        details2.setSolutionName("User Service");
        details2.setSolutionReviewCode("REV-002");
        details2.setProjectName("User Management Project");
        overview2.setSolutionDetails(details2);
        overview2.setReviewType("ACTIVE");
        overview2.setApprovalStatus("APPROVED");
        overview2.setBusinessUnit("IT");
        overview2.setBusinessDriver("Manage user accounts");
        
        system2.setSolutionOverview(overview2);

        return Arrays.asList(system1, system2);
    }

    private List<BusinessCapabilityDiagramDTO> createMockBusinessCapabilities() {
        BusinessCapabilityDiagramDTO capability1 = new BusinessCapabilityDiagramDTO();
        capability1.setSystemCode("sys-001");
        
        CommonSolutionReviewDTO.SolutionOverview overview1 = new CommonSolutionReviewDTO.SolutionOverview();
        CommonSolutionReviewDTO.SolutionDetails details1 = new CommonSolutionReviewDTO.SolutionDetails();
        details1.setSolutionName("Payment Processing");
        details1.setSolutionReviewCode("REV-001");
        details1.setProjectName("Payment Capability Project");
        overview1.setSolutionDetails(details1);
        overview1.setReviewType("ACTIVE");
        overview1.setApprovalStatus("APPROVED");
        overview1.setBusinessUnit("Finance");
        
        capability1.setSolutionOverview(overview1);

        BusinessCapabilityDiagramDTO capability2 = new BusinessCapabilityDiagramDTO();
        capability2.setSystemCode("sys-002");
        
        CommonSolutionReviewDTO.SolutionOverview overview2 = new CommonSolutionReviewDTO.SolutionOverview();
        CommonSolutionReviewDTO.SolutionDetails details2 = new CommonSolutionReviewDTO.SolutionDetails();
        details2.setSolutionName("User Management");
        details2.setSolutionReviewCode("REV-002");
        details2.setProjectName("User Capability Project");
        overview2.setSolutionDetails(details2);
        overview2.setReviewType("ACTIVE");
        overview2.setApprovalStatus("APPROVED");
        overview2.setBusinessUnit("IT");
        
        capability2.setSolutionOverview(overview2);

        return Arrays.asList(capability1, capability2);
    }

    @Test
    @DisplayName("Should successfully retrieve all business capabilities from dropdown endpoint")
    void testGetAllBusinessCapabilities_Success() throws JsonProcessingException {
        // Given
        List<BusinessCapabilityDTO> expectedResponse = createMockAllBusinessCapabilities();
        String jsonResponse = objectMapper.writeValueAsString(expectedResponse);

        mockServer.expect(requestTo(baseUrl + "/api/v1/dropdowns/business-capabilities"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        // When
        List<BusinessCapabilityDTO> result = coreServiceClient.getAllBusinessCapabilities();

        // Then
        assertThat(result)
            .isNotNull()
            .hasSize(3)
            .satisfies(capabilities -> {
                assertThat(capabilities.get(0).getL1()).isEqualTo("Customer Management");
                assertThat(capabilities.get(0).getL2()).isEqualTo("CRM");
                assertThat(capabilities.get(0).getL3()).isEqualTo("Contact Management");
                
                assertThat(capabilities.get(1).getL1()).isEqualTo("Customer Management");
                assertThat(capabilities.get(1).getL2()).isEqualTo("CRM");
                assertThat(capabilities.get(1).getL3()).isEqualTo("Lead Management");
                
                assertThat(capabilities.get(2).getL1()).isEqualTo("Finance");
                assertThat(capabilities.get(2).getL2()).isEqualTo("Accounting");
                assertThat(capabilities.get(2).getL3()).isEqualTo("General Ledger");
            });
        mockServer.verify();
    }

    @Test
    @DisplayName("Should handle HTTP error when retrieving all business capabilities")
    void testGetAllBusinessCapabilities_HttpError() {
        // Given
        mockServer.expect(requestTo(baseUrl + "/api/v1/dropdowns/business-capabilities"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        // When & Then
        assertThatThrownBy(() -> coreServiceClient.getAllBusinessCapabilities())
                .isInstanceOf(RestClientException.class);
        mockServer.verify();
    }

    @Test
    @DisplayName("Should handle empty response for all business capabilities")
    void testGetAllBusinessCapabilities_EmptyResponse() throws JsonProcessingException {
        // Given
        String jsonResponse = objectMapper.writeValueAsString(Arrays.asList());

        mockServer.expect(requestTo(baseUrl + "/api/v1/dropdowns/business-capabilities"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(jsonResponse, MediaType.APPLICATION_JSON));

        // When
        List<BusinessCapabilityDTO> result = coreServiceClient.getAllBusinessCapabilities();

        // Then
        assertThat(result)
            .isNotNull()
            .isEmpty();
        mockServer.verify();
    }

    private List<BusinessCapabilityDTO> createMockAllBusinessCapabilities() {
        BusinessCapabilityDTO cap1 = new BusinessCapabilityDTO();
        cap1.setL1("Customer Management");
        cap1.setL2("CRM");
        cap1.setL3("Contact Management");

        BusinessCapabilityDTO cap2 = new BusinessCapabilityDTO();
        cap2.setL1("Customer Management");
        cap2.setL2("CRM");
        cap2.setL3("Lead Management");

        BusinessCapabilityDTO cap3 = new BusinessCapabilityDTO();
        cap3.setL1("Finance");
        cap3.setL2("Accounting");
        cap3.setL3("General Ledger");

        return Arrays.asList(cap1, cap2, cap3);
    }
}