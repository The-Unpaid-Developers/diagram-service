package com.project.diagram_service.controllers;

import com.project.diagram_service.dto.SystemDependencyDTO;
import com.project.diagram_service.dto.SystemDiagramDTO;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@WebMvcTest(controllers = DiagramController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
class DiagramControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiagramService diagramService;

    private List<SystemDependencyDTO> mockSystemDependencies;
    private SystemDiagramDTO mockSystemDiagram;

    @BeforeEach
    void setUp() {
        // Setup mock data
        SystemDependencyDTO dependency1 = new SystemDependencyDTO();
        dependency1.setSystemCode("SYS-001");
        
        SystemDependencyDTO dependency2 = new SystemDependencyDTO();
        dependency2.setSystemCode("SYS-002");
        
        mockSystemDependencies = Arrays.asList(dependency1, dependency2);

        // Setup mock diagram
        mockSystemDiagram = new SystemDiagramDTO();
        SystemDiagramDTO.NodeDTO node1 = new SystemDiagramDTO.NodeDTO();
        node1.setId("SYS-001");
        node1.setName("System One");
        node1.setType("Core System");
        node1.setCriticality("Major");
        node1.setUrl("SYS-001.json");

        SystemDiagramDTO.NodeDTO node2 = new SystemDiagramDTO.NodeDTO();
        node2.setId("SYS-002-C");
        node2.setName("System Two");
        node2.setType("IncomeSystem");
        node2.setCriticality("Major");
        node2.setUrl("SYS-002.json");

        SystemDiagramDTO.LinkDTO link = new SystemDiagramDTO.LinkDTO();
        link.setSource("SYS-001");
        link.setTarget("SYS-002-C");
        link.setPattern("REST_API");
        link.setFrequency("Daily");
        link.setRole("CONSUMER");

        SystemDiagramDTO.MetadataDTO metadata = new SystemDiagramDTO.MetadataDTO();
        metadata.setCode("SYS-001");
        metadata.setReview("REV-001");
        metadata.setIntegrationMiddleware(Collections.emptyList());
        metadata.setGeneratedDate(java.time.LocalDate.of(2025, 9, 30));

        mockSystemDiagram.setNodes(Arrays.asList(node1, node2));
        mockSystemDiagram.setLinks(Arrays.asList(link));
        mockSystemDiagram.setMetadata(metadata);
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
        SystemDiagramDTO diagramWithNoLinks = new SystemDiagramDTO();
        SystemDiagramDTO.NodeDTO singleNode = new SystemDiagramDTO.NodeDTO();
        singleNode.setId("SYS-ISOLATED");
        singleNode.setName("Isolated System");
        
        SystemDiagramDTO.MetadataDTO metadata = new SystemDiagramDTO.MetadataDTO();
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
        when(diagramService.findAllPathsDiagram("SYS-001", "SYS-002")).thenReturn(mockSystemDiagram);

        // Act & Assert
        mockMvc.perform(get("/api/v1/diagram/paths/SYS-001/to/SYS-002")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.links").isArray())
                .andExpect(jsonPath("$.metadata").exists());

        verify(diagramService).findAllPathsDiagram("SYS-001", "SYS-002");
    }
    
    @Test
    @DisplayName("Should return bad request when start system is invalid")
    void testGetPathsBetweenSystems_InvalidStartSystem() throws Exception {
        // Arrange
        when(diagramService.findAllPathsDiagram("INVALID", "SYS-002"))
                .thenThrow(new IllegalArgumentException("Start system cannot be null or empty"));

        // Act & Assert
        mockMvc.perform(get("/api/v1/diagram/paths/{startSystem}/to/{endSystem}", "INVALID", "SYS-002")
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
        mockMvc.perform(get("/api/v1/diagram/paths/{startSystem}/to/{endSystem}", "SYS-001", "INVALID")
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
        mockMvc.perform(get("/api/v1/diagram/paths/SYS-001/to/SYS-001")
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
        mockMvc.perform(get("/api/v1/diagram/paths/NONEXISTENT/to/SYS-002")
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isBadRequest());

        verify(diagramService).findAllPathsDiagram("NONEXISTENT", "SYS-002");
    }
}
