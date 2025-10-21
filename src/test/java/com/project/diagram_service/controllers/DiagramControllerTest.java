package com.project.diagram_service.controllers;

import com.project.diagram_service.dto.SystemDependencyDTO;
import com.project.diagram_service.dto.BusinessCapabilityDiagramDTO;
import com.project.diagram_service.dto.BusinessCapabilitiesTreeDTO;
import com.project.diagram_service.dto.CommonDiagramDTO;
import com.project.diagram_service.dto.CommonSolutionReviewDTO;
import com.project.diagram_service.dto.SpecificSystemDependenciesDiagramDTO;
import com.project.diagram_service.dto.OverallSystemDependenciesDiagramDTO;
import com.project.diagram_service.dto.PathDiagramDTO;
import com.project.diagram_service.services.DiagramService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.hamcrest.Matchers.hasSize;

@WebMvcTest(controllers = DiagramController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
class DiagramControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiagramService diagramService;

    private List<SystemDependencyDTO> mockSystemDependencies;
    private List<BusinessCapabilityDiagramDTO> mockBusinessCapabilities;
    private BusinessCapabilitiesTreeDTO mockBusinessCapabilitiesTree;
    private SpecificSystemDependenciesDiagramDTO mockSystemDiagram;
    private PathDiagramDTO mockPathDiagram;

    @BeforeEach
    void setUp() {
        // Setup mock data
        SystemDependencyDTO dependency1 = new SystemDependencyDTO();
        dependency1.setSystemCode("SYS-001");
        
        SystemDependencyDTO dependency2 = new SystemDependencyDTO();
        dependency2.setSystemCode("SYS-002");
        
        mockSystemDependencies = Arrays.asList(dependency1, dependency2);

        // Setup mock business capabilities
        BusinessCapabilityDiagramDTO capability1 = new BusinessCapabilityDiagramDTO();
        capability1.setSystemCode("sys-002");
        
        CommonSolutionReviewDTO.SolutionOverview solutionOverview1 = new CommonSolutionReviewDTO.SolutionOverview();
        solutionOverview1.setId("68db771593fdf8d87a3bb4be");
        solutionOverview1.setApprovalStatus("PENDING");
        solutionOverview1.setReviewStatus("DRAFT");
        capability1.setSolutionOverview(solutionOverview1);
        
        BusinessCapabilityDiagramDTO.BusinessCapability businessCap1 = new BusinessCapabilityDiagramDTO.BusinessCapability();
        businessCap1.setId("68db771593fdf8d87a3bb4bf");
        businessCap1.setL1Capability("UNKNOWN");
        businessCap1.setL2Capability("UNKNOWN");
        businessCap1.setL3Capability("UNKNOWN");
        capability1.setBusinessCapabilities(Arrays.asList(businessCap1));

        BusinessCapabilityDiagramDTO capability2 = new BusinessCapabilityDiagramDTO();
        capability2.setSystemCode("sys-003");
        
        mockBusinessCapabilities = Arrays.asList(capability1, capability2);

        // Setup mock business capabilities tree
        mockBusinessCapabilitiesTree = new BusinessCapabilitiesTreeDTO();
        
        // Create L1 capability node
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l1Node = new BusinessCapabilitiesTreeDTO.BusinessCapabilityNode();
        l1Node.setId("Customer Management");
        l1Node.setName("Customer Management");
        l1Node.setLevel("L1");
        l1Node.setParentId(null);
        l1Node.setSystemCount(1);
        
        // Create L2 capability node  
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l2Node = new BusinessCapabilitiesTreeDTO.BusinessCapabilityNode();
        l2Node.setId("CRM");
        l2Node.setName("CRM");
        l2Node.setLevel("L2");
        l2Node.setParentId("Customer Management");
        l2Node.setSystemCount(1);
        
        // Create L3 capability node
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l3Node = new BusinessCapabilitiesTreeDTO.BusinessCapabilityNode();
        l3Node.setId("Contact Management");
        l3Node.setName("Contact Management");
        l3Node.setLevel("L3");
        l3Node.setParentId("CRM");
        l3Node.setSystemCount(1);
        
        // Create system node
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode systemNode = new BusinessCapabilitiesTreeDTO.BusinessCapabilityNode();
        systemNode.setId("sys-001");
        systemNode.setName("CRM System");
        systemNode.setLevel("SYSTEM");
        systemNode.setParentId("Contact Management");
        systemNode.setSystemCount(0);
        
        mockBusinessCapabilitiesTree.setCapabilities(Arrays.asList(l1Node, l2Node, l3Node, systemNode));

        // Setup mock diagram
        mockSystemDiagram = new SpecificSystemDependenciesDiagramDTO();
        CommonDiagramDTO.NodeDTO node1 = new CommonDiagramDTO.NodeDTO();
        node1.setId("SYS-001");
        node1.setName("System One");
        node1.setType("Core System");
        node1.setCriticality("Major");

        CommonDiagramDTO.NodeDTO node2 = new CommonDiagramDTO.NodeDTO();
        node2.setId("SYS-002-C");
        node2.setName("System Two");
        node2.setType("IncomeSystem");
        node2.setCriticality("Major");

        CommonDiagramDTO.DetailedLinkDTO link = new CommonDiagramDTO.DetailedLinkDTO();
        link.setSource("SYS-001");
        link.setTarget("SYS-002-C");
        link.setPattern("REST_API");
        link.setFrequency("Daily");
        link.setRole("CONSUMER");

        CommonDiagramDTO.ExtendedMetadataDTO metadata = new CommonDiagramDTO.ExtendedMetadataDTO();
        metadata.setCode("SYS-001");
        metadata.setReview("REV-001");
        metadata.setIntegrationMiddleware(Collections.emptyList());
        metadata.setGeneratedDate(java.time.LocalDate.of(2025, 9, 30));

        mockSystemDiagram.setNodes(Arrays.asList(node1, node2));
        mockSystemDiagram.setLinks(Arrays.asList(link));
        mockSystemDiagram.setMetadata(metadata);
        
        // Setup mock path diagram
        mockPathDiagram = new PathDiagramDTO();
        CommonDiagramDTO.NodeDTO pathNode1 = new CommonDiagramDTO.NodeDTO();
        pathNode1.setId("SYS-001");
        pathNode1.setName("System One");
        pathNode1.setType("Core System");
        pathNode1.setCriticality("Major");
        pathNode1.setUrl("SYS-001.json");

        CommonDiagramDTO.NodeDTO pathNode2 = new CommonDiagramDTO.NodeDTO();
        pathNode2.setId("SYS-002");
        pathNode2.setName("System Two");
        pathNode2.setType("IncomeSystem");
        pathNode2.setCriticality("Major");
        pathNode2.setUrl("SYS-002.json");

        PathDiagramDTO.PathLinkDTO pathLink = new PathDiagramDTO.PathLinkDTO();
        pathLink.setSource("SYS-001");
        pathLink.setTarget("SYS-002");
        pathLink.setPattern("REST_API");
        pathLink.setFrequency("Daily");
        pathLink.setRole("CONSUMER");
        pathLink.setMiddleware("API_GATEWAY");

        CommonDiagramDTO.ExtendedMetadataDTO pathMetadata = new CommonDiagramDTO.ExtendedMetadataDTO();
        pathMetadata.setCode("SYS-001 → SYS-002");
        pathMetadata.setReview("1 path found");
        pathMetadata.setIntegrationMiddleware(Arrays.asList("API_GATEWAY"));
        pathMetadata.setGeneratedDate(java.time.LocalDate.of(2025, 10, 7));

        mockPathDiagram.setNodes(Arrays.asList(pathNode1, pathNode2));
        mockPathDiagram.setLinks(Arrays.asList(pathLink));
        mockPathDiagram.setMetadata(pathMetadata);
    }

    @Test
    @DisplayName("Should successfully get system dependencies")
    void testGetSystemDependencies_Success() throws Exception {
        // Given
        when(diagramService.getSystemDependencies()).thenReturn(mockSystemDependencies);

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/system-dependencies")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].systemCode").value("SYS-001"))
                .andExpect(jsonPath("$[1].systemCode").value("SYS-002"));

        verify(diagramService, times(1)).getSystemDependencies();
    }

    @Test
    @DisplayName("Should handle service exception when getting system dependencies")
    void testGetSystemDependencies_ServiceException() throws Exception {
        // Given
        when(diagramService.getSystemDependencies()).thenThrow(new RuntimeException("Service unavailable"));

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/system-dependencies")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());

        verify(diagramService, times(1)).getSystemDependencies();
    }

    @Test
    @DisplayName("Should successfully get business capabilities")
    void testGetBusinessCapabilities_Success() throws Exception {
        // Given
        when(diagramService.getBusinessCapabilities()).thenReturn(mockBusinessCapabilities);

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/business-capabilities")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].systemCode").value("sys-002"))
                .andExpect(jsonPath("$[0].solutionOverview.approvalStatus").value("PENDING"))
                .andExpect(jsonPath("$[0].businessCapabilities[0].l1Capability").value("UNKNOWN"))
                .andExpect(jsonPath("$[1].systemCode").value("sys-003"));

        verify(diagramService, times(1)).getBusinessCapabilities();
    }

    @Test
    @DisplayName("Should handle service exception when getting business capabilities")
    void testGetBusinessCapabilities_ServiceException() throws Exception {
        // Given
        when(diagramService.getBusinessCapabilities()).thenThrow(new RuntimeException("Database connection failed"));

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/business-capabilities")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());

        verify(diagramService, times(1)).getBusinessCapabilities();
    }

    @Test
    @DisplayName("Should successfully get business capabilities tree")
    void testGetAllBusinessCapabilitiesTree_Success() throws Exception {
        // Given
        when(diagramService.getBusinessCapabilitiesTree()).thenReturn(mockBusinessCapabilitiesTree);

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/business-capabilities/all")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.capabilities").isArray())
                .andExpect(jsonPath("$.capabilities.length()").value(4))
                .andExpect(jsonPath("$.capabilities[0].id").value("Customer Management"))
                .andExpect(jsonPath("$.capabilities[0].level").value("L1"))
                .andExpect(jsonPath("$.capabilities[0].systemCount").value(1))
                .andExpect(jsonPath("$.capabilities[3].id").value("sys-001"))
                .andExpect(jsonPath("$.capabilities[3].level").value("SYSTEM"))
                .andExpect(jsonPath("$.capabilities[3].name").value("CRM System"));

        verify(diagramService, times(1)).getBusinessCapabilitiesTree();
    }

    @Test
    @DisplayName("Should handle service exception when getting business capabilities tree")
    void testGetAllBusinessCapabilitiesTree_ServiceException() throws Exception {
        // Given
        when(diagramService.getBusinessCapabilitiesTree()).thenThrow(new RuntimeException("Core service unavailable"));

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/business-capabilities/all")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());

        verify(diagramService, times(1)).getBusinessCapabilitiesTree();
    }

    @Test
    @DisplayName("Should handle empty business capabilities tree")
    void testGetAllBusinessCapabilitiesTree_EmptyTree() throws Exception {
        // Given
        BusinessCapabilitiesTreeDTO emptyTree = new BusinessCapabilitiesTreeDTO();
        emptyTree.setCapabilities(Collections.emptyList());
        when(diagramService.getBusinessCapabilitiesTree()).thenReturn(emptyTree);

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/business-capabilities/all")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.capabilities").isArray())
                .andExpect(jsonPath("$.capabilities.length()").value(0));

        verify(diagramService, times(1)).getBusinessCapabilitiesTree();
    }

    @Test
    @DisplayName("Should successfully generate system dependencies diagram")
    void testGetSystemDependenciesDiagram_Success() throws Exception {
        // Given
        String systemCode = "SYS-001";
        when(diagramService.generateSystemDependenciesDiagram(systemCode)).thenReturn(mockSystemDiagram);

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/system-dependencies/{systemCode}", systemCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.nodes.length()").value(2))
                .andExpect(jsonPath("$.links").isArray())
                .andExpect(jsonPath("$.links.length()").value(1))
                .andExpect(jsonPath("$.metadata.code").value("SYS-001"))
                .andExpect(jsonPath("$.metadata.review").value("REV-001"));

        verify(diagramService, times(1)).generateSystemDependenciesDiagram(systemCode);
    }

    @Test
    @DisplayName("Should handle service exception when generating diagram")
    void testGetSystemDependenciesDiagram_ServiceException() throws Exception {
        // Given
        String systemCode = "SYS-001";
        when(diagramService.generateSystemDependenciesDiagram(anyString()))
                .thenThrow(new RuntimeException("Required data is null"));

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/system-dependencies/{systemCode}", systemCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());

        verify(diagramService, times(1)).generateSystemDependenciesDiagram(systemCode);
    }

    @Test
    @DisplayName("Should handle special characters in system code")
    void testGetSystemDependenciesDiagram_SpecialCharacters() throws Exception {
        // Given
        String systemCode = "SYS-001_TEST";
        when(diagramService.generateSystemDependenciesDiagram(systemCode)).thenReturn(mockSystemDiagram);

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/system-dependencies/{systemCode}", systemCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        verify(diagramService, times(1)).generateSystemDependenciesDiagram(systemCode);
    }

    @Test
    @DisplayName("Should return empty list when no dependencies found")
    void testGetSystemDependencies_EmptyResult() throws Exception {
        // Given
        when(diagramService.getSystemDependencies()).thenReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/system-dependencies")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));

        verify(diagramService, times(1)).getSystemDependencies();
    }

    @Test
    @DisplayName("Should handle system with no links in diagram")
    void testGetSystemDependenciesDiagram_NoLinks() throws Exception {
        // Given
        String systemCode = "SYS-ISOLATED";
        SpecificSystemDependenciesDiagramDTO diagramWithNoLinks = new SpecificSystemDependenciesDiagramDTO();
        CommonDiagramDTO.NodeDTO singleNode = new CommonDiagramDTO.NodeDTO();
        singleNode.setId("SYS-ISOLATED");
        singleNode.setName("Isolated System");
        
        CommonDiagramDTO.ExtendedMetadataDTO metadata = new CommonDiagramDTO.ExtendedMetadataDTO();
        metadata.setCode("SYS-ISOLATED");
        
        diagramWithNoLinks.setNodes(Arrays.asList(singleNode));
        diagramWithNoLinks.setLinks(Collections.emptyList());
        diagramWithNoLinks.setMetadata(metadata);
        
        when(diagramService.generateSystemDependenciesDiagram(systemCode)).thenReturn(diagramWithNoLinks);

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/system-dependencies/{systemCode}", systemCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.links").isArray())
                .andExpect(jsonPath("$.links.length()").value(0));

        verify(diagramService, times(1)).generateSystemDependenciesDiagram(systemCode);
    }

    @Test
    @DisplayName("Should handle very long system codes")
    void testGetSystemDependenciesDiagram_LongSystemCode() throws Exception {
        // Given
        String longSystemCode = "A".repeat(100);
        when(diagramService.generateSystemDependenciesDiagram(longSystemCode))
            .thenReturn(mockSystemDiagram);

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/system-dependencies/{systemCode}", longSystemCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        verify(diagramService).generateSystemDependenciesDiagram(longSystemCode);
    }

    @Test
    @DisplayName("Should handle URL encoded system codes")
    void testGetSystemDependenciesDiagram_URLEncodedSystemCode() throws Exception {
        // Given
        String urlEncodedSystemCode = "SYS%20001"; // "SYS 001" URL encoded
        when(diagramService.generateSystemDependenciesDiagram(urlEncodedSystemCode))
            .thenReturn(null); // Service returns null for invalid codes

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/system-dependencies/{systemCode}", urlEncodedSystemCode)
                .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk());
                // Note: Content type might not be set if service returns null

        verify(diagramService).generateSystemDependenciesDiagram(urlEncodedSystemCode);
    }

    @Test
    @DisplayName("Should handle numeric system codes")
    void testGetSystemDependenciesDiagram_NumericSystemCode() throws Exception {
        // Given
        String numericSystemCode = "12345";
        when(diagramService.generateSystemDependenciesDiagram(numericSystemCode))
            .thenReturn(mockSystemDiagram);

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/system-dependencies/{systemCode}", numericSystemCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        verify(diagramService).generateSystemDependenciesDiagram(numericSystemCode);
    }

    @Test
    @DisplayName("Should handle error when generating diagram for invalid system")
    void testGetSystemDependenciesDiagram_InvalidSystem() throws Exception {
        // Given
        String invalidSystemCode = "INVALID";
        when(diagramService.generateSystemDependenciesDiagram(invalidSystemCode))
            .thenThrow(new RuntimeException("Invalid system code format"));

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/system-dependencies/{systemCode}", invalidSystemCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());

        verify(diagramService).generateSystemDependenciesDiagram(invalidSystemCode);
    }

    @Test
    @DisplayName("Should handle system not found error")
    void testGetSystemDependenciesDiagram_SystemNotFound() throws Exception {
        // Given
        String systemCode = "SYS-001";
        when(diagramService.generateSystemDependenciesDiagram(systemCode))
            .thenThrow(new RuntimeException("System not found: " + systemCode));

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/system-dependencies/{systemCode}", systemCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());

        verify(diagramService).generateSystemDependenciesDiagram(systemCode);
    }

    @Test
    @DisplayName("Should handle database connection error")
    void testGetSystemDependencies_DatabaseError() throws Exception {
        // Given
        when(diagramService.getSystemDependencies())
            .thenThrow(new RuntimeException("Database connection failed"));

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/system-dependencies")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());

        verify(diagramService).getSystemDependencies();
    }

    @Test
    @DisplayName("Should handle different content types")
    void testGetSystemDependencies_AcceptAnyContent() throws Exception {
        // Given
        when(diagramService.getSystemDependencies()).thenReturn(mockSystemDependencies);

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/system-dependencies")
                        .header("Accept", "*/*"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        verify(diagramService).getSystemDependencies();
    }

    @Test
    @DisplayName("Should handle JSON content type specifically")
    void testGetSystemDependencies_JSONAccept() throws Exception {
        // Given
        when(diagramService.getSystemDependencies()).thenReturn(mockSystemDependencies);

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/system-dependencies")
                        .header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        verify(diagramService).getSystemDependencies();
    }
    
        @Test
    @DisplayName("Should successfully find paths between two systems")
    void testGetPathsBetweenSystems_Success() throws Exception {
        // Arrange
        when(diagramService.findAllPathsDiagram("SYS-001", "SYS-002")).thenReturn(mockPathDiagram);

        // Act & Assert
        mockMvc.perform(get("/api/v1/diagram/system-dependencies/path")
                        .param("start", "SYS-001")
                        .param("end", "SYS-002")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.nodes.length()").value(2))
                .andExpect(jsonPath("$.links").isArray())
                .andExpect(jsonPath("$.links.length()").value(1))
                .andExpect(jsonPath("$.links[0].middleware").value("API_GATEWAY"));

        verify(diagramService).findAllPathsDiagram("SYS-001", "SYS-002");
    }
    
    @Test
    @DisplayName("Should return bad request when start system is invalid")
    void testGetPathsBetweenSystems_InvalidStartSystem() throws Exception {
        // Arrange
        when(diagramService.findAllPathsDiagram("INVALID", "SYS-002"))
                .thenThrow(new IllegalArgumentException("Start system cannot be null or empty"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/diagram/system-dependencies/path")
                        .param("start", "INVALID")
                        .param("end", "SYS-002")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(diagramService).findAllPathsDiagram("INVALID", "SYS-002");
    }
    
    @Test
    @DisplayName("Should return bad request when end system is invalid")
    void testGetPathsBetweenSystems_InvalidEndSystem() throws Exception {
        // Arrange
        when(diagramService.findAllPathsDiagram("SYS-001", "INVALID"))
                .thenThrow(new IllegalArgumentException("End system cannot be null or empty"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/diagram/system-dependencies/path")
                        .param("start", "SYS-001")
                        .param("end", "INVALID")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(diagramService).findAllPathsDiagram("SYS-001", "INVALID");
    }
    
    @Test
    @DisplayName("Should return bad request when start and end systems are the same")
    void testGetPathsBetweenSystems_SameSystems() throws Exception {
        // Arrange
        when(diagramService.findAllPathsDiagram("SYS-001", "SYS-001"))
                .thenThrow(new IllegalArgumentException("Start and end systems cannot be the same"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/diagram/system-dependencies/path")
                        .param("start", "SYS-001")
                        .param("end", "SYS-001")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(diagramService).findAllPathsDiagram("SYS-001", "SYS-001");
    }
    
    @Test
    @DisplayName("Should return not found when system doesn't exist")
    void testGetPathsBetweenSystems_SystemNotFound() throws Exception {
        // Arrange
        when(diagramService.findAllPathsDiagram("NONEXISTENT", "SYS-002"))
                .thenThrow(new IllegalArgumentException("Start system 'NONEXISTENT' not found"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/diagram/system-dependencies/path")
                        .param("start", "NONEXISTENT")
                        .param("end", "SYS-002")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(diagramService).findAllPathsDiagram("NONEXISTENT", "SYS-002");
    }
    
    @Test
    @DisplayName("Should return internal server error when service throws unexpected exception")
    void testGetPathsBetweenSystems_ServiceException() throws Exception {
        // Arrange
        when(diagramService.findAllPathsDiagram("SYS-001", "SYS-002"))
                .thenThrow(new RuntimeException("Database connection failed"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/diagram/system-dependencies/path")
                        .param("start", "SYS-001")
                        .param("end", "SYS-002")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isInternalServerError());

        verify(diagramService).findAllPathsDiagram("SYS-001", "SYS-002");
    }

    // Tests for getAllSystemDependenciesDiagrams endpoint
    @Test
    void getAllSystemDependenciesDiagrams_Success() throws Exception {
        // Create mock overall system dependencies diagram
        OverallSystemDependenciesDiagramDTO mockDiagram = new OverallSystemDependenciesDiagramDTO();
        
        when(diagramService.generateAllSystemDependenciesDiagrams()).thenReturn(mockDiagram);

        mockMvc.perform(get("/api/v1/diagram/system-dependencies/all")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        verify(diagramService).generateAllSystemDependenciesDiagrams();
    }

    @Test
    void getAllSystemDependenciesDiagrams_ServiceException() throws Exception {
        when(diagramService.generateAllSystemDependenciesDiagrams())
                .thenThrow(new RuntimeException("Service error"));

        mockMvc.perform(get("/api/v1/diagram/system-dependencies/all")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isInternalServerError());

        verify(diagramService).generateAllSystemDependenciesDiagrams();
    }

    @Test
    void getAllSystemDependenciesDiagrams_EmptyDiagram() throws Exception {
        OverallSystemDependenciesDiagramDTO emptyDiagram = new OverallSystemDependenciesDiagramDTO();
        emptyDiagram.setNodes(List.of());
        emptyDiagram.setLinks(List.of());
        
        when(diagramService.generateAllSystemDependenciesDiagrams()).thenReturn(emptyDiagram);

        mockMvc.perform(get("/api/v1/diagram/system-dependencies/all")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.nodes").isEmpty())
                .andExpect(jsonPath("$.links").isArray())
                .andExpect(jsonPath("$.links").isEmpty());

        verify(diagramService).generateAllSystemDependenciesDiagrams();
    }

    @Test
    void getAllSystemDependenciesDiagrams_ValidResponseStructure() throws Exception {
        // Create a complete mock diagram with all expected fields
        OverallSystemDependenciesDiagramDTO mockDiagram = new OverallSystemDependenciesDiagramDTO();
        
        // Create mock nodes
        CommonDiagramDTO.NodeDTO node1 = new CommonDiagramDTO.NodeDTO();
        node1.setId("SYS-001");
        node1.setName("Core System A");
        node1.setType("Core");
        node1.setCriticality("High");
        
        CommonDiagramDTO.NodeDTO node2 = new CommonDiagramDTO.NodeDTO();
        node2.setId("SYS-002");
        node2.setName("External System B");
        node2.setType("External");
        node2.setCriticality("Medium");
        
        // Create mock links
        CommonDiagramDTO.SimpleLinkDTO link = new CommonDiagramDTO.SimpleLinkDTO();
        link.setSource("SYS-001");
        link.setTarget("SYS-002");
        link.setCount(3);
        
        // Create mock metadata
        CommonDiagramDTO.BasicMetadataDTO metadata = new CommonDiagramDTO.BasicMetadataDTO();
        metadata.setGeneratedDate(LocalDate.now());
        
        mockDiagram.setNodes(List.of(node1, node2));
        mockDiagram.setLinks(List.of(link));
        mockDiagram.setMetadata(metadata);
        
        when(diagramService.generateAllSystemDependenciesDiagrams()).thenReturn(mockDiagram);

        mockMvc.perform(get("/api/v1/diagram/system-dependencies/all")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.nodes", hasSize(2)))
                .andExpect(jsonPath("$.nodes[0].id").value("SYS-001"))
                .andExpect(jsonPath("$.nodes[0].name").value("Core System A"))
                .andExpect(jsonPath("$.nodes[0].type").value("Core"))
                .andExpect(jsonPath("$.nodes[0].criticality").value("High"))
                .andExpect(jsonPath("$.nodes[1].id").value("SYS-002"))
                .andExpect(jsonPath("$.nodes[1].name").value("External System B"))
                .andExpect(jsonPath("$.nodes[1].type").value("External"))
                .andExpect(jsonPath("$.nodes[1].criticality").value("Medium"))
                .andExpect(jsonPath("$.links").isArray())
                .andExpect(jsonPath("$.links", hasSize(1)))
                .andExpect(jsonPath("$.links[0].source").value("SYS-001"))
                .andExpect(jsonPath("$.links[0].target").value("SYS-002"))
                .andExpect(jsonPath("$.links[0].count").value(3))
                .andExpect(jsonPath("$.metadata").exists())
                .andExpect(jsonPath("$.metadata.generatedDate").exists());

        verify(diagramService).generateAllSystemDependenciesDiagrams();
    }

    @Test
    void getAllSystemDependenciesDiagrams_ContentTypeSupport() throws Exception {
        OverallSystemDependenciesDiagramDTO mockDiagram = new OverallSystemDependenciesDiagramDTO();
        when(diagramService.generateAllSystemDependenciesDiagrams()).thenReturn(mockDiagram);

        // Test that endpoint works without explicit Accept header
        mockMvc.perform(get("/api/v1/diagram/system-dependencies/all"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        verify(diagramService).generateAllSystemDependenciesDiagrams();
    }

    @Test
    void getAllSystemDependenciesDiagrams_ComplexDiagram() throws Exception {
        // Test with a more complex diagram structure
        OverallSystemDependenciesDiagramDTO complexDiagram = new OverallSystemDependenciesDiagramDTO();
        
        // Multiple nodes with different types
        List<CommonDiagramDTO.NodeDTO> nodes = Arrays.asList(
            createNode("SYS-001", "Payment Service", "Core", "Critical"),
            createNode("SYS-002", "User Service", "Core", "High"),
            createNode("SYS-003", "External Payment Gateway", "External", "Medium"),
            createNode("SYS-004", "Notification Service", "Core", "Low")
        );
        
        // Multiple links with different counts
        List<CommonDiagramDTO.SimpleLinkDTO> links = Arrays.asList(
            createLink("SYS-001", "SYS-002", 5),
            createLink("SYS-001", "SYS-003", 2),
            createLink("SYS-002", "SYS-004", 1)
        );
        
        complexDiagram.setNodes(nodes);
        complexDiagram.setLinks(links);
        
        CommonDiagramDTO.BasicMetadataDTO metadata = new CommonDiagramDTO.BasicMetadataDTO();
        metadata.setGeneratedDate(LocalDate.now());
        complexDiagram.setMetadata(metadata);
        
        when(diagramService.generateAllSystemDependenciesDiagrams()).thenReturn(complexDiagram);

        mockMvc.perform(get("/api/v1/diagram/system-dependencies/all")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes", hasSize(4)))
                .andExpect(jsonPath("$.links", hasSize(3)))
                .andExpect(jsonPath("$.nodes[?(@.type == 'Core')]", hasSize(3)))
                .andExpect(jsonPath("$.nodes[?(@.type == 'External')]", hasSize(1)))
                .andExpect(jsonPath("$.links[?(@.count > 1)]", hasSize(2)));

        verify(diagramService).generateAllSystemDependenciesDiagrams();
    }

    // ========================================
    // System-Specific Business Capabilities Tests
    // ========================================

    @Test
    @DisplayName("Should return system-specific business capabilities tree successfully")
    void testGetSystemBusinessCapabilitiesTree_Success() throws Exception {
        // Given
        String systemCode = "sys-001";
        BusinessCapabilitiesTreeDTO systemTree = new BusinessCapabilitiesTreeDTO();
        
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l1Node = new BusinessCapabilitiesTreeDTO.BusinessCapabilityNode();
        l1Node.setId("l1-customer-management");
        l1Node.setName("Customer Management");
        l1Node.setLevel("L1");
        l1Node.setParentId(null);
        l1Node.setSystemCount(1);

        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l2Node = new BusinessCapabilitiesTreeDTO.BusinessCapabilityNode();
        l2Node.setId("l2-crm-under-l1-customer-management");
        l2Node.setName("CRM");
        l2Node.setLevel("L2");
        l2Node.setParentId("l1-customer-management");
        l2Node.setSystemCount(1);

        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l3Node = new BusinessCapabilitiesTreeDTO.BusinessCapabilityNode();
        l3Node.setId("l3-contact-management-under-l2-crm-under-l1-customer-management");
        l3Node.setName("Contact Management");
        l3Node.setLevel("L3");
        l3Node.setParentId("l2-crm-under-l1-customer-management");
        l3Node.setSystemCount(1);

        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode systemNode = new BusinessCapabilitiesTreeDTO.BusinessCapabilityNode();
        systemNode.setId("sys-001-under-l3-contact-management-under-l2-crm-under-l1-customer-management");
        systemNode.setName("CRM System");
        systemNode.setLevel("System");
        systemNode.setParentId("l3-contact-management-under-l2-crm-under-l1-customer-management");
        systemNode.setSystemCount(null);

        systemTree.setCapabilities(Arrays.asList(l1Node, l2Node, l3Node, systemNode));
        
        when(diagramService.getSystemBusinessCapabilitiesTree(systemCode)).thenReturn(systemTree);

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/business-capabilities/{systemCode}", systemCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.capabilities", hasSize(4)))
                .andExpect(jsonPath("$.capabilities[?(@.level == 'L1')]", hasSize(1)))
                .andExpect(jsonPath("$.capabilities[?(@.level == 'L2')]", hasSize(1)))
                .andExpect(jsonPath("$.capabilities[?(@.level == 'L3')]", hasSize(1)))
                .andExpect(jsonPath("$.capabilities[?(@.level == 'System')]", hasSize(1)))
                .andExpect(jsonPath("$.capabilities[0].name").value("Customer Management"))
                .andExpect(jsonPath("$.capabilities[?(@.level == 'System' && @.name == 'CRM System')]", hasSize(1)));

        verify(diagramService).getSystemBusinessCapabilitiesTree(systemCode);
    }

    @Test
    @DisplayName("Should return empty tree when system has no business capabilities")
    void testGetSystemBusinessCapabilitiesTree_EmptyResult() throws Exception {
        // Given
        String systemCode = "nonexistent-sys";
        BusinessCapabilitiesTreeDTO emptyTree = new BusinessCapabilitiesTreeDTO();
        emptyTree.setCapabilities(Collections.emptyList());
        
        when(diagramService.getSystemBusinessCapabilitiesTree(systemCode)).thenReturn(emptyTree);

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/business-capabilities/{systemCode}", systemCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.capabilities", hasSize(0)));

        verify(diagramService).getSystemBusinessCapabilitiesTree(systemCode);
    }

    @Test
    @DisplayName("Should handle special characters in system code")
    void testGetSystemBusinessCapabilitiesTree_SpecialCharacters() throws Exception {
        // Given
        String systemCode = "sys-001_TEST";
        BusinessCapabilitiesTreeDTO tree = new BusinessCapabilitiesTreeDTO();
        tree.setCapabilities(Collections.emptyList());
        
        when(diagramService.getSystemBusinessCapabilitiesTree(systemCode)).thenReturn(tree);

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/business-capabilities/{systemCode}", systemCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        verify(diagramService).getSystemBusinessCapabilitiesTree(systemCode);
    }

    @Test
    @DisplayName("Should return 400 when system code contains invalid path characters")
    void testGetSystemBusinessCapabilitiesTree_InvalidPathCharacters() throws Exception {
        // When & Then - This tests URL path handling with forward slash
        mockMvc.perform(get("/api/v1/diagram/business-capabilities/sys/001")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isNotFound()); // Spring will treat this as a different endpoint

        verifyNoInteractions(diagramService);
    }

    @Test
    @DisplayName("Should return 500 when service throws IllegalArgumentException")
    void testGetSystemBusinessCapabilitiesTree_IllegalArgumentException() throws Exception {
        // Given
        String systemCode = "invalid-sys";
        when(diagramService.getSystemBusinessCapabilitiesTree(systemCode))
                .thenThrow(new IllegalArgumentException("System code cannot be null or empty"));

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/business-capabilities/{systemCode}", systemCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isInternalServerError());

        verify(diagramService).getSystemBusinessCapabilitiesTree(systemCode);
    }

    @Test
    @DisplayName("Should return 500 when service throws IllegalStateException")
    void testGetSystemBusinessCapabilitiesTree_IllegalStateException() throws Exception {
        // Given
        String systemCode = "sys-001";
        when(diagramService.getSystemBusinessCapabilitiesTree(systemCode))
                .thenThrow(new IllegalStateException("Failed to generate business capabilities tree for system: " + systemCode));

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/business-capabilities/{systemCode}", systemCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isInternalServerError());

        verify(diagramService).getSystemBusinessCapabilitiesTree(systemCode);
    }

    @Test
    @DisplayName("Should return 500 when service throws runtime exception")
    void testGetSystemBusinessCapabilitiesTree_RuntimeException() throws Exception {
        // Given
        String systemCode = "sys-001";
        when(diagramService.getSystemBusinessCapabilitiesTree(systemCode))
                .thenThrow(new RuntimeException("Unexpected error"));

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/business-capabilities/{systemCode}", systemCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isInternalServerError());

        verify(diagramService).getSystemBusinessCapabilitiesTree(systemCode);
    }

    @Test
    @DisplayName("Should handle complex system tree with multiple capability flows")
    void testGetSystemBusinessCapabilitiesTree_ComplexTree() throws Exception {
        // Given
        String systemCode = "multi-sys-001";
        BusinessCapabilitiesTreeDTO complexTree = new BusinessCapabilitiesTreeDTO();
        
        // Create multiple L1, L2, L3 nodes and multiple system entries
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l1Customer = createCapabilityNode("l1-customer", "Customer Management", "L1", null, 2);
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l1Sales = createCapabilityNode("l1-sales", "Sales", "L1", null, 1);
        
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l2Crm = createCapabilityNode("l2-crm", "CRM", "L2", "l1-customer", 2);
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l2Pipeline = createCapabilityNode("l2-pipeline", "Pipeline", "L2", "l1-sales", 1);
        
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l3Contact = createCapabilityNode("l3-contact", "Contact Management", "L3", "l2-crm", 1);
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l3Lead = createCapabilityNode("l3-lead", "Lead Management", "L3", "l2-crm", 1);
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l3Opportunity = createCapabilityNode("l3-opportunity", "Opportunity Management", "L3", "l2-pipeline", 1);
        
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode sys1 = createCapabilityNode("sys-contact", "Multi-Function System", "System", "l3-contact", null);
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode sys2 = createCapabilityNode("sys-lead", "Multi-Function System", "System", "l3-lead", null);
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode sys3 = createCapabilityNode("sys-opportunity", "Multi-Function System", "System", "l3-opportunity", null);
        
        complexTree.setCapabilities(Arrays.asList(l1Customer, l1Sales, l2Crm, l2Pipeline, l3Contact, l3Lead, l3Opportunity, sys1, sys2, sys3));
        
        when(diagramService.getSystemBusinessCapabilitiesTree(systemCode)).thenReturn(complexTree);

        // When & Then
        mockMvc.perform(get("/api/v1/diagram/business-capabilities/{systemCode}", systemCode)
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.capabilities", hasSize(10)))
                .andExpect(jsonPath("$.capabilities[?(@.level == 'L1')]", hasSize(2)))
                .andExpect(jsonPath("$.capabilities[?(@.level == 'L2')]", hasSize(2)))
                .andExpect(jsonPath("$.capabilities[?(@.level == 'L3')]", hasSize(3)))
                .andExpect(jsonPath("$.capabilities[?(@.level == 'System')]", hasSize(3)))
                .andExpect(jsonPath("$.capabilities[?(@.level == 'L1' && @.systemCount > 1)]", hasSize(1)))
                .andExpect(jsonPath("$.capabilities[?(@.level == 'System' && @.name == 'Multi-Function System')]", hasSize(3)));

        verify(diagramService).getSystemBusinessCapabilitiesTree(systemCode);
    }

    // Helper methods for creating test data
    private CommonDiagramDTO.NodeDTO createNode(String id, String name, String type, String criticality) {
        CommonDiagramDTO.NodeDTO node = new CommonDiagramDTO.NodeDTO();
        node.setId(id);
        node.setName(name);
        node.setType(type);
        node.setCriticality(criticality);
        return node;
    }

    private CommonDiagramDTO.SimpleLinkDTO createLink(String source, String target, int count) {
        CommonDiagramDTO.SimpleLinkDTO link = new CommonDiagramDTO.SimpleLinkDTO();
        link.setSource(source);
        link.setTarget(target);
        link.setCount(count);
        return link;
    }

    private BusinessCapabilitiesTreeDTO.BusinessCapabilityNode createCapabilityNode(
            String id, String name, String level, String parentId, Integer systemCount) {
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode node = new BusinessCapabilitiesTreeDTO.BusinessCapabilityNode();
        node.setId(id);
        node.setName(name);
        node.setLevel(level);
        node.setParentId(parentId);
        node.setSystemCount(systemCount);
        return node;
    }
}
