package com.project.diagram_service.integration;

import com.project.diagram_service.client.CoreServiceClient;
import com.project.diagram_service.dto.BusinessCapabilityDiagramDTO;
import com.project.diagram_service.dto.BusinessCapabilitiesTreeDTO;
import com.project.diagram_service.dto.CommonSolutionReviewDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests specifically focused on business capabilities tree transformation logic.
 * These tests verify that the service correctly transforms raw MongoDB-style data into 
 * the expected hierarchical tree structure, providing confidence in the core business logic.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Business Capabilities Tree Integration Tests")
class BusinessCapabilitiesIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @MockitoBean
    private CoreServiceClient coreServiceClient;

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/api/v1/diagram";
    }

    @Nested
    @DisplayName("Raw Data Transformation Integration Tests")
    class RawDataTransformationTests {

        @Test
        @DisplayName("Should transform complete UNKNOWN hierarchy from raw MongoDB data via HTTP")
        void testTransformUnknownHierarchyFromRawMongoDB() {
            // Given - Raw MongoDB structure exactly as provided
            BusinessCapabilityDiagramDTO rawSystem = createRawMongoDBSystem();
            when(coreServiceClient.getBusinessCapabilities()).thenReturn(Arrays.asList(rawSystem));

            // When - Make HTTP request
            ResponseEntity<BusinessCapabilitiesTreeDTO> response = restTemplate.exchange(
                baseUrl + "/business-capabilities/all",
                HttpMethod.GET,
                null,
                BusinessCapabilitiesTreeDTO.class
            );

            // Then - Verify HTTP response
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();

            // Verify transformation logic via HTTP
            BusinessCapabilitiesTreeDTO tree = response.getBody();
            assertThat(tree.getCapabilities()).hasSize(4); // L1 + L2 + L3 + System

            // Create map for easy node lookup
            Map<String, BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> nodeMap = 
                tree.getCapabilities().stream()
                    .collect(Collectors.toMap(
                        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode::getId,
                        node -> node
                    ));

            // Verify L1: UNKNOWN
            BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l1 = nodeMap.get("l1-unknown");
            assertThat(l1)
                .isNotNull()
                .satisfies(node -> {
                    assertThat(node.getName()).isEqualTo("UNKNOWN");
                    assertThat(node.getLevel()).isEqualTo("L1");
                    assertThat(node.getParentId()).isNull();
                    assertThat(node.getSystemCount()).isEqualTo(1);
                });

            // Verify L2: UNKNOWN
            BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l2 = nodeMap.get("l2-unknown-under-l1-unknown");
            assertThat(l2)
                .isNotNull()
                .satisfies(node -> {
                    assertThat(node.getName()).isEqualTo("UNKNOWN");
                    assertThat(node.getLevel()).isEqualTo("L2");
                    assertThat(node.getParentId()).isEqualTo("l1-unknown");
                    assertThat(node.getSystemCount()).isEqualTo(1);
                });

            // Verify L3: UNKNOWN
            BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l3 = nodeMap.get("l3-unknown-under-l2-unknown-under-l1-unknown");
            assertThat(l3)
                .isNotNull()
                .satisfies(node -> {
                    assertThat(node.getName()).isEqualTo("UNKNOWN");
                    assertThat(node.getLevel()).isEqualTo("L3");
                    assertThat(node.getParentId()).isEqualTo("l2-unknown-under-l1-unknown");
                    assertThat(node.getSystemCount()).isEqualTo(1);
                });

            // Verify System: NextGen Platform
            BusinessCapabilitiesTreeDTO.BusinessCapabilityNode system = nodeMap.get("sys-001-under-l3-unknown-under-l2-unknown-under-l1-unknown");
            assertThat(system)
                .isNotNull()
                .satisfies(node -> {
                    assertThat(node.getId()).isEqualTo("sys-001-under-l3-unknown-under-l2-unknown-under-l1-unknown");
                    assertThat(node.getName()).isEqualTo("NextGen Platform");
                    assertThat(node.getLevel()).isEqualTo("System");
                    assertThat(node.getParentId()).isEqualTo("l3-unknown-under-l2-unknown-under-l1-unknown");
                    assertThat(node.getSystemCount()).isNull();
                });

            verify(coreServiceClient).getBusinessCapabilities();
        }

        @Test
        @DisplayName("Should handle complex multi-system transformation via HTTP")
        void testComplexMultiSystemTransformation() {
            // Given - Multiple systems across different capabilities
            List<BusinessCapabilityDiagramDTO> complexRawData = createComplexRawData();
            when(coreServiceClient.getBusinessCapabilities()).thenReturn(complexRawData);

            // When
            ResponseEntity<BusinessCapabilitiesTreeDTO> response = restTemplate.exchange(
                baseUrl + "/business-capabilities/all",
                HttpMethod.GET,
                null,
                BusinessCapabilitiesTreeDTO.class
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            BusinessCapabilitiesTreeDTO tree = response.getBody();
            assertThat(tree).isNotNull();

            // Should have multiple L1s, L2s, L3s, and systems
            assertThat(tree.getCapabilities()).hasSizeGreaterThanOrEqualTo(8);

            // Verify hierarchy structure
            List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> l1Nodes = tree.getCapabilities().stream()
                .filter(n -> "L1".equals(n.getLevel()))
                .toList();
            
            assertThat(l1Nodes).hasSizeGreaterThanOrEqualTo(2);
            assertThat(l1Nodes)
                .extracting("name")
                .contains("Customer Management", "Product Management");

            // Verify system counts are calculated correctly
            BusinessCapabilitiesTreeDTO.BusinessCapabilityNode customerL1 = l1Nodes.stream()
                .filter(n -> "Customer Management".equals(n.getName()))
                .findFirst()
                .orElseThrow();
            assertThat(customerL1.getSystemCount()).isGreaterThan(0);

            verify(coreServiceClient).getBusinessCapabilities();
        }

        @Test
        @DisplayName("Should handle system appearing in multiple capabilities via HTTP")
        void testSystemInMultipleCapabilities() {
            // Given - One system appearing in multiple business capabilities
            BusinessCapabilityDiagramDTO multiCapabilitySystem = new BusinessCapabilityDiagramDTO();
            multiCapabilitySystem.setSystemCode("sys-001");
            multiCapabilitySystem.setSolutionOverview(createSolutionOverview("Multi-Purpose Platform"));
            
            // System appears in 2 different L3 capabilities
            multiCapabilitySystem.setBusinessCapabilities(Arrays.asList(
                createBusinessCapability("Customer Management", "CRM", "Contact Management"),
                createBusinessCapability("Customer Management", "CRM", "Lead Management")
            ));
            
            when(coreServiceClient.getBusinessCapabilities()).thenReturn(Arrays.asList(multiCapabilitySystem));

            // When
            ResponseEntity<BusinessCapabilitiesTreeDTO> response = restTemplate.exchange(
                baseUrl + "/business-capabilities/all",
                HttpMethod.GET,
                null,
                BusinessCapabilitiesTreeDTO.class
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            BusinessCapabilitiesTreeDTO tree = response.getBody();
            assertThat(tree).isNotNull();

            // System should appear twice (once under each L3)
            List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> systemNodes = tree.getCapabilities().stream()
                .filter(n -> "System".equals(n.getLevel()))
                .toList();
            
            assertThat(systemNodes).hasSize(2);
            assertThat(systemNodes)
                .extracting("id")
                .containsExactlyInAnyOrder("sys-001-under-l3-contact-management-under-l2-crm-under-l1-customer-management", "sys-001-under-l3-lead-management-under-l2-crm-under-l1-customer-management");
            
            assertThat(systemNodes)
                .extracting("parentId")
                .containsExactlyInAnyOrder("l3-contact-management-under-l2-crm-under-l1-customer-management", "l3-lead-management-under-l2-crm-under-l1-customer-management");

            verify(coreServiceClient).getBusinessCapabilities();
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCasesAndErrorHandling {

        @Test
        @DisplayName("Should handle missing solution overview gracefully via HTTP")
        void testMissingSolutionOverview() {
            // Given
            BusinessCapabilityDiagramDTO systemWithoutSolution = new BusinessCapabilityDiagramDTO();
            systemWithoutSolution.setSystemCode("sys-001");
            systemWithoutSolution.setSolutionOverview(null); // Missing solution overview
            systemWithoutSolution.setBusinessCapabilities(Arrays.asList(
                createBusinessCapability("Customer Management", "CRM", "Contact Management")
            ));
            
            when(coreServiceClient.getBusinessCapabilities()).thenReturn(Arrays.asList(systemWithoutSolution));

            // When
            ResponseEntity<BusinessCapabilitiesTreeDTO> response = restTemplate.exchange(
                baseUrl + "/business-capabilities/all",
                HttpMethod.GET,
                null,
                BusinessCapabilitiesTreeDTO.class
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            BusinessCapabilitiesTreeDTO tree = response.getBody();
            
            BusinessCapabilitiesTreeDTO.BusinessCapabilityNode system = tree.getCapabilities().stream()
                .filter(n -> "System".equals(n.getLevel()))
                .findFirst()
                .orElseThrow();
            
            assertThat(system.getId()).isEqualTo("sys-001-under-l3-contact-management-under-l2-crm-under-l1-customer-management");
            assertThat(system.getName()).isEqualTo("Unknown Solution"); // fallback name

            verify(coreServiceClient).getBusinessCapabilities();
        }

        @Test
        @DisplayName("Should handle empty business capabilities list via HTTP")
        void testEmptyBusinessCapabilities() {
            // Given
            when(coreServiceClient.getBusinessCapabilities()).thenReturn(Collections.emptyList());

            // When
            ResponseEntity<BusinessCapabilitiesTreeDTO> response = restTemplate.exchange(
                baseUrl + "/business-capabilities/all",
                HttpMethod.GET,
                null,
                BusinessCapabilitiesTreeDTO.class
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getCapabilities()).isEmpty();

            verify(coreServiceClient).getBusinessCapabilities();
        }

        @Test
        @DisplayName("Should propagate service exceptions correctly via HTTP")
        void testServiceExceptionHandling() {
            // Given
            when(coreServiceClient.getBusinessCapabilities())
                .thenThrow(new RuntimeException("Database connection failed"));

            // When
            ResponseEntity<String> response = restTemplate.exchange(
                baseUrl + "/business-capabilities/all",
                HttpMethod.GET,
                null,
                String.class
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);

            verify(coreServiceClient).getBusinessCapabilities();
        }
    }

    // ========================================
    // Helper Methods for Creating Test Data
    // ========================================

    /**
     * Creates raw MongoDB-style data exactly as provided in user example
     */
    private BusinessCapabilityDiagramDTO createRawMongoDBSystem() {
        BusinessCapabilityDiagramDTO rawSystem = new BusinessCapabilityDiagramDTO();
        rawSystem.setSystemCode("sys-001");
        
        // Create complete solution overview matching MongoDB structure
        CommonSolutionReviewDTO.SolutionOverview overview = new CommonSolutionReviewDTO.SolutionOverview();
        overview.setId("68db858493fdf8d87a3bb4e8");
        
        CommonSolutionReviewDTO.SolutionDetails details = new CommonSolutionReviewDTO.SolutionDetails();
        details.setSolutionName("NextGen Platform");
        details.setProjectName("AlphaLaunch");
        details.setSolutionReviewCode("AWG-2025-001");
        details.setSolutionArchitectName("Jane Doe");
        details.setDeliveryProjectManagerName("John Smith");
        details.setItBusinessPartner("Alice Johnson");
        overview.setSolutionDetails(details);
        
        overview.setReviewedBy(null);
        overview.setReviewType("NEW_BUILD");
        overview.setApprovalStatus("PENDING");
        overview.setReviewStatus("DRAFT");
        overview.setConditions(null);
        overview.setBusinessUnit("UNKNOWN");
        overview.setBusinessDriver("REGULATORY");
        overview.setValueOutcome("test");
        overview.setApplicationUsers(Arrays.asList("EMPLOYEE", "CUSTOMERS"));
        overview.setConcerns(Collections.emptyList());
        rawSystem.setSolutionOverview(overview);
        
        // Create business capability exactly as in MongoDB
        BusinessCapabilityDiagramDTO.BusinessCapability capability = 
            new BusinessCapabilityDiagramDTO.BusinessCapability();
        capability.setId("68db858493fdf8d87a3bb4e9");
        capability.setL1Capability("UNKNOWN");
        capability.setL2Capability("UNKNOWN");
        capability.setL3Capability("UNKNOWN");
        capability.setRemarks(null);
        rawSystem.setBusinessCapabilities(Arrays.asList(capability));
        
        return rawSystem;
    }

    private List<BusinessCapabilityDiagramDTO> createComplexRawData() {
        return Arrays.asList(
            // Customer Management branch
            createRawSystem("sys-001", "CRM System", "Customer Management", "CRM", "Contact Management"),
            createRawSystem("sys-002", "Lead System", "Customer Management", "CRM", "Lead Management"),
            createRawSystem("sys-003", "Support System", "Customer Management", "Customer Service", "Ticket Management"),
            
            // Product Management branch
            createRawSystem("sys-004", "Catalog System", "Product Management", "Product Catalog", "Item Management"),
            createRawSystem("sys-005", "Pricing Engine", "Product Management", "Product Catalog", "Price Management")
        );
    }

    private BusinessCapabilityDiagramDTO createRawSystem(String systemCode, String solutionName,
                                                         String l1, String l2, String l3) {
        BusinessCapabilityDiagramDTO dto = new BusinessCapabilityDiagramDTO();
        dto.setSystemCode(systemCode);
        dto.setSolutionOverview(createSolutionOverview(solutionName));
        dto.setBusinessCapabilities(Arrays.asList(createBusinessCapability(l1, l2, l3)));
        return dto;
    }

    private CommonSolutionReviewDTO.SolutionOverview createSolutionOverview(String solutionName) {
        CommonSolutionReviewDTO.SolutionOverview overview = new CommonSolutionReviewDTO.SolutionOverview();
        overview.setId("68db858493fdf8d87a3bb4e8");
        
        CommonSolutionReviewDTO.SolutionDetails details = new CommonSolutionReviewDTO.SolutionDetails();
        details.setSolutionName(solutionName);
        details.setProjectName("AlphaLaunch");
        details.setSolutionReviewCode("AWG-2025-001");
        details.setSolutionArchitectName("Jane Doe");
        details.setDeliveryProjectManagerName("John Smith");
        details.setItBusinessPartner("Alice Johnson");
        overview.setSolutionDetails(details);
        
        overview.setReviewedBy(null);
        overview.setReviewType("NEW_BUILD");
        overview.setApprovalStatus("PENDING");
        overview.setReviewStatus("DRAFT");
        overview.setConditions(null);
        overview.setBusinessUnit("UNKNOWN");
        overview.setBusinessDriver("REGULATORY");
        overview.setValueOutcome("test");
        overview.setApplicationUsers(Arrays.asList("EMPLOYEE", "CUSTOMERS"));
        overview.setConcerns(Collections.emptyList());
        
        return overview;
    }

    private BusinessCapabilityDiagramDTO.BusinessCapability createBusinessCapability(String l1, String l2, String l3) {
        BusinessCapabilityDiagramDTO.BusinessCapability capability = 
            new BusinessCapabilityDiagramDTO.BusinessCapability();
        capability.setId("68db858493fdf8d87a3bb4e9");
        capability.setL1Capability(l1);
        capability.setL2Capability(l2);
        capability.setL3Capability(l3);
        capability.setRemarks(null);
        return capability;
    }
}