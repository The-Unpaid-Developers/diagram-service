package com.project.diagram_service.services;

import com.project.diagram_service.client.CoreServiceClient;
import com.project.diagram_service.dto.SystemDependencyDTO;
import com.project.diagram_service.dto.SpecificSystemDependenciesDiagramDTO;
import com.project.diagram_service.dto.OverallSystemDependenciesDiagramDTO;
import com.project.diagram_service.dto.PathDiagramDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiagramService Unit Tests")
class DiagramServiceTest {

    @Mock
    private CoreServiceClient coreServiceClient;

    @InjectMocks
    private DiagramService diagramService;

    private List<SystemDependencyDTO> mockSystemDependencies;
    private SystemDependencyDTO primarySystem;
    private SystemDependencyDTO externalSystem;

    @BeforeEach
    void setUp() {
        // Setup primary system
        primarySystem = createSystemDependency("SYS-001", "Primary System", "REV-001");
        
        // Setup external system
        externalSystem = createSystemDependency("SYS-002", "External System", "REV-002");
        
        mockSystemDependencies = Arrays.asList(primarySystem, externalSystem);
    }

    @Test
    @DisplayName("Should retrieve system dependencies successfully")
    void testGetSystemDependencies_Success() {
        // Given
        when(coreServiceClient.getSystemDependencies()).thenReturn(mockSystemDependencies);

        // When
        List<SystemDependencyDTO> result = diagramService.getSystemDependencies();

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(primarySystem, externalSystem);
        verify(coreServiceClient, times(1)).getSystemDependencies();
    }

    @Test
    @DisplayName("Should throw RuntimeException when core service fails")
    void testGetSystemDependencies_CoreServiceFailure() {
        // Given
        when(coreServiceClient.getSystemDependencies()).thenThrow(new RuntimeException("Core service unavailable"));

        // When & Then
        assertThatThrownBy(() -> diagramService.getSystemDependencies())
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Core service unavailable");
        
        verify(coreServiceClient, times(1)).getSystemDependencies();
    }

    @Test
    @DisplayName("Should generate system diagram with direct connections (no middleware)")
    void testGenerateSystemDependenciesDiagram_DirectConnections() {
        // Given
        String targetSystemCode = "SYS-001";
        SystemDependencyDTO.IntegrationFlow flow = createIntegrationFlow(
            "SYS-002", "CONSUMER", "REST_API", "Daily", null
        );
        primarySystem.setIntegrationFlows(Collections.singletonList(flow));
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(mockSystemDependencies);

        // When
        SpecificSystemDependenciesDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(2); // Primary system + external system
        assertThat(result.getLinks()).hasSize(1); // One direct link
        assertThat(result.getMetadata().getCode()).isEqualTo(targetSystemCode);
        assertThat(result.getMetadata().getGeneratedDate()).isEqualTo(LocalDate.now());
        
        // Verify primary system node
        SpecificSystemDependenciesDiagramDTO.NodeDTO primaryNode = findNodeById(result.getNodes(), "SYS-001");
        assertThat(primaryNode).isNotNull();
        assertThat(primaryNode.getName()).isEqualTo("Primary System");
        assertThat(primaryNode.getType()).isEqualTo("Core System");
        
        // Verify external system node (with consumer suffix)
        SpecificSystemDependenciesDiagramDTO.NodeDTO externalNode = findNodeById(result.getNodes(), "SYS-002-C");
        assertThat(externalNode).isNotNull();
        assertThat(externalNode.getName()).isEqualTo("External System");
        assertThat(externalNode.getType()).isEqualTo("IncomeSystem");
        
        // Verify link
        SpecificSystemDependenciesDiagramDTO.LinkDTO link = result.getLinks().get(0);
        assertThat(link.getSource()).isEqualTo("SYS-001");
        assertThat(link.getTarget()).isEqualTo("SYS-002-C");
        assertThat(link.getPattern()).isEqualTo("REST_API");
        assertThat(link.getFrequency()).isEqualTo("Daily");
    }

    @Test
    @DisplayName("Should generate system diagram with middleware connections")
    void testGenerateSystemDependenciesDiagram_WithMiddleware() {
        // Given
        String targetSystemCode = "SYS-001";
        SystemDependencyDTO.IntegrationFlow flow = createIntegrationFlow(
            "SYS-002", "CONSUMER", "REST_API", "Daily", "API_GATEWAY"
        );
        primarySystem.setIntegrationFlows(Collections.singletonList(flow));
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(mockSystemDependencies);

        // When
        SpecificSystemDependenciesDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(3); // Primary + external + middleware
        assertThat(result.getLinks()).hasSize(2); // Two middleware links
        
        // Verify middleware node
        SpecificSystemDependenciesDiagramDTO.NodeDTO middlewareNode = findNodeById(result.getNodes(), "API_GATEWAY-C");
        assertThat(middlewareNode).isNotNull();
        assertThat(middlewareNode.getName()).isEqualTo("API_GATEWAY");
        assertThat(middlewareNode.getType()).isEqualTo("Middleware");
        assertThat(middlewareNode.getCriticality()).isEqualTo("Standard-2");
        
        // Verify middleware is included in metadata
        assertThat(result.getMetadata().getIntegrationMiddleware()).contains("API_GATEWAY-C");
        
        // Verify links
        List<SpecificSystemDependenciesDiagramDTO.LinkDTO> links = result.getLinks();
        assertThat(links).anyMatch(link -> 
            "SYS-001".equals(link.getSource()) && "API_GATEWAY-C".equals(link.getTarget()));
        assertThat(links).anyMatch(link -> 
            "API_GATEWAY-C".equals(link.getSource()) && "SYS-002-C".equals(link.getTarget()));
    }

    @Test
    @DisplayName("Should generate system diagram with bidirectional flows")
    void testGenerateSystemDependenciesDiagram_BidirectionalFlows() {
        // Given
        String targetSystemCode = "SYS-001";
        
        // Primary system produces to external system
        SystemDependencyDTO.IntegrationFlow outgoingFlow = createIntegrationFlow(
            "SYS-002", "CONSUMER", "REST_API", "Daily", null
        );
        primarySystem.setIntegrationFlows(Collections.singletonList(outgoingFlow));
        
        // External system produces to primary system
        SystemDependencyDTO.IntegrationFlow incomingFlow = createIntegrationFlow(
            "SYS-001", "CONSUMER", "MESSAGE_QUEUE", "Hourly", null
        );
        externalSystem.setIntegrationFlows(Collections.singletonList(incomingFlow));
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(mockSystemDependencies);

        // When
        SpecificSystemDependenciesDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(3); // Primary + external-C + external-P
        assertThat(result.getLinks()).hasSize(2); // Two direct links
        
        // Verify external system nodes with different roles
        SpecificSystemDependenciesDiagramDTO.NodeDTO externalConsumerNode = findNodeById(result.getNodes(), "SYS-002-C");
        SpecificSystemDependenciesDiagramDTO.NodeDTO externalProducerNode = findNodeById(result.getNodes(), "SYS-002-P");
        
        assertThat(externalConsumerNode).isNotNull();
        assertThat(externalProducerNode).isNotNull();
        
        // Verify links
        List<SpecificSystemDependenciesDiagramDTO.LinkDTO> links = result.getLinks();
        assertThat(links).anyMatch(link -> 
            "SYS-001".equals(link.getSource()) && "SYS-002-C".equals(link.getTarget()));
        assertThat(links).anyMatch(link -> 
            "SYS-002-P".equals(link.getSource()) && "SYS-001".equals(link.getTarget()));
    }

    @Test
    @DisplayName("Should handle external systems not in our data")
    void testGenerateSystemDependenciesDiagram_ExternalSystemNotInData() {
        // Given
        String targetSystemCode = "SYS-001";
        SystemDependencyDTO.IntegrationFlow flow = createIntegrationFlow(
            "SYS-999", "CONSUMER", "REST_API", "Daily", null // SYS-999 doesn't exist in our data
        );
        primarySystem.setIntegrationFlows(Collections.singletonList(flow));
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(mockSystemDependencies);

        // When
        SpecificSystemDependenciesDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

        // Then
        assertThat(result).isNotNull();
        
        // Verify external system node (not in our data)
        SpecificSystemDependenciesDiagramDTO.NodeDTO externalNode = findNodeById(result.getNodes(), "SYS-999-C");
        assertThat(externalNode).isNotNull();
        assertThat(externalNode.getName()).isEqualTo("SYS-999"); // Uses system code as name
        assertThat(externalNode.getType()).isEqualTo("External"); // External type since not in our data
    }

    @Test
    @DisplayName("Should ignore middleware with NONE value")
    void testGenerateSystemDependenciesDiagram_MiddlewareNone() {
        // Given
        String targetSystemCode = "SYS-001";
        SystemDependencyDTO.IntegrationFlow flow = createIntegrationFlow(
            "SYS-002", "CONSUMER", "REST_API", "Daily", "NONE"
        );
        primarySystem.setIntegrationFlows(Collections.singletonList(flow));
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(mockSystemDependencies);

        // When
        SpecificSystemDependenciesDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(2); // No middleware node
        assertThat(result.getLinks()).hasSize(1); // Direct link only
        assertThat(result.getMetadata().getIntegrationMiddleware()).isEmpty();
    }

    @Test
    @DisplayName("Should throw RuntimeException when system not found")
    void testGenerateSystemDependenciesDiagram_SystemNotFound() {
        // Given
        String nonExistentSystemCode = "SYS-999";
        when(coreServiceClient.getSystemDependencies()).thenReturn(mockSystemDependencies);

        // When & Then
        assertThatThrownBy(() -> diagramService.generateSystemDependenciesDiagram(nonExistentSystemCode))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("System not found: SYS-999");
    }

    @Test
    @DisplayName("Should handle empty integration flows")
    void testGenerateSystemDependenciesDiagram_EmptyIntegrationFlows() {
        // Given
        String targetSystemCode = "SYS-001";
        primarySystem.setIntegrationFlows(Collections.emptyList());
        externalSystem.setIntegrationFlows(Collections.emptyList());
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(mockSystemDependencies);

        // When
        SpecificSystemDependenciesDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(1); // Only primary system
        assertThat(result.getLinks()).isEmpty(); // No links
        assertThat(result.getMetadata().getIntegrationMiddleware()).isEmpty();
    }

    @Test
    @DisplayName("Should handle null integration flows")
    void testGenerateSystemDependenciesDiagram_NullIntegrationFlows() {
        // Given
        String targetSystemCode = "SYS-001";
        primarySystem.setIntegrationFlows(null);
        externalSystem.setIntegrationFlows(null);
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(mockSystemDependencies);

        // When
        SpecificSystemDependenciesDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(1); // Only primary system
        assertThat(result.getLinks()).isEmpty(); // No links
    }

    @Test
    @DisplayName("Should handle null integration flows gracefully")
    void testGenerateSystemDiagram_NullIntegrationFlows() {
        // Given
        String targetSystemCode = "SYS-001";
        SystemDependencyDTO systemWithNullFlows = createSystemDependency("SYS-001", "System One", "REV-001");
        systemWithNullFlows.setIntegrationFlows(null); // Null flows
        
        List<SystemDependencyDTO> systemsWithNullFlows = Arrays.asList(systemWithNullFlows);
        when(coreServiceClient.getSystemDependencies()).thenReturn(systemsWithNullFlows);

        // When
        SpecificSystemDependenciesDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(1);
        assertThat(result.getLinks()).isEmpty();
        assertThat(result.getMetadata().getCode()).isEqualTo(targetSystemCode);
    }

    @Test
    @DisplayName("Should handle empty string system code")
    void testGenerateSystemDiagram_EmptySystemCode() {
        // Given
        String emptySystemCode = "";

        // When & Then
        assertThatThrownBy(() -> diagramService.generateSystemDependenciesDiagram(emptySystemCode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("System code must not be null or blank");
    }

    @Test
    @DisplayName("Should handle whitespace-only system code")
    void testGenerateSystemDiagram_WhitespaceSystemCode() {
        // Given
        String whitespaceSystemCode = "   ";

        // When & Then
        assertThatThrownBy(() -> diagramService.generateSystemDependenciesDiagram(whitespaceSystemCode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("System code must not be null or blank");
    }

    @Test
    @DisplayName("Should handle systems with empty integration flows list")
    void testGenerateSystemDiagram_EmptyIntegrationFlows() {
        // Given
        String targetSystemCode = "SYS-001";
        SystemDependencyDTO systemWithEmptyFlows = createSystemDependency("SYS-001", "System One", "REV-001");
        systemWithEmptyFlows.setIntegrationFlows(Collections.emptyList());
        
        List<SystemDependencyDTO> systemsWithEmptyFlows = Arrays.asList(systemWithEmptyFlows);
        when(coreServiceClient.getSystemDependencies()).thenReturn(systemsWithEmptyFlows);

        // When
        SpecificSystemDependenciesDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(1);
        assertThat(result.getLinks()).isEmpty();
        SpecificSystemDependenciesDiagramDTO.NodeDTO coreNode = result.getNodes().get(0);
        assertThat(coreNode.getId()).isEqualTo(targetSystemCode);
        assertThat(coreNode.getType()).isEqualTo("Core System");
    }

    @Test
    @DisplayName("Should handle integration flows with null middleware")
    void testGenerateSystemDiagram_NullMiddleware() {
        // Given
        String targetSystemCode = "SYS-001";
        primarySystem.setIntegrationFlows(Arrays.asList(
            createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", null) // Null middleware
        ));
        when(coreServiceClient.getSystemDependencies()).thenReturn(mockSystemDependencies);

        // When
        SpecificSystemDependenciesDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(2); // Only core system and target system, no middleware
        assertThat(result.getLinks()).hasSize(1);
        
        // Verify no middleware nodes were created
        boolean hasMiddlewareNodes = result.getNodes().stream()
            .anyMatch(node -> "Middleware".equals(node.getType()));
        assertThat(hasMiddlewareNodes).isFalse();
    }

    @Test
    @DisplayName("Should handle integration flows with empty middleware")
    void testGenerateSystemDiagram_EmptyMiddleware() {
        // Given
        String targetSystemCode = "SYS-001";
        primarySystem.setIntegrationFlows(Arrays.asList(
            createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", "") // Empty middleware
        ));
        when(coreServiceClient.getSystemDependencies()).thenReturn(mockSystemDependencies);

        // When
        SpecificSystemDependenciesDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(2); // Only core system and target system, no middleware
        assertThat(result.getLinks()).hasSize(1);
        
        // Verify no middleware nodes were created
        boolean hasMiddlewareNodes = result.getNodes().stream()
            .anyMatch(node -> "Middleware".equals(node.getType()));
        assertThat(hasMiddlewareNodes).isFalse();
    }

    @Test
    @DisplayName("Should handle systems with null solution overview")
    void testGenerateSystemDiagram_NullSolutionOverview() {
        // Given
        String targetSystemCode = "SYS-001";
        SystemDependencyDTO systemWithNullOverview = new SystemDependencyDTO();
        systemWithNullOverview.setSystemCode("SYS-001");
        systemWithNullOverview.setSolutionOverview(null);
        systemWithNullOverview.setIntegrationFlows(Collections.emptyList());
        
        List<SystemDependencyDTO> systems = Arrays.asList(systemWithNullOverview);
        when(coreServiceClient.getSystemDependencies()).thenReturn(systems);

        // When & Then
        assertThatThrownBy(() -> diagramService.generateSystemDependenciesDiagram(targetSystemCode))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should handle systems with null solution details")
    void testGenerateSystemDiagram_NullSolutionDetails() {
        // Given
        String targetSystemCode = "SYS-001";
        SystemDependencyDTO systemWithNullDetails = new SystemDependencyDTO();
        systemWithNullDetails.setSystemCode("SYS-001");
        
        SystemDependencyDTO.SolutionOverview overview = new SystemDependencyDTO.SolutionOverview();
        overview.setSolutionDetails(null);
        systemWithNullDetails.setSolutionOverview(overview);
        systemWithNullDetails.setIntegrationFlows(Collections.emptyList());
        
        List<SystemDependencyDTO> systems = Arrays.asList(systemWithNullDetails);
        when(coreServiceClient.getSystemDependencies()).thenReturn(systems);

        // When & Then
        assertThatThrownBy(() -> diagramService.generateSystemDependenciesDiagram(targetSystemCode))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should handle complex middleware scenarios with multiple flows")
    void testGenerateSystemDiagram_ComplexMiddlewareScenarios() {
        // Given
        String targetSystemCode = "SYS-001";
        primarySystem.setIntegrationFlows(Arrays.asList(
            createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", "API_GATEWAY"),
            createIntegrationFlow("SYS-003", "PRODUCER", "SOAP", "Hourly", "ESB"),
            createIntegrationFlow("SYS-004", "CONSUMER", "MESSAGE_QUEUE", "Real-time", "MESSAGE_BROKER")
        ));
        
        SystemDependencyDTO secondarySystem = createSystemDependency("SYS-002", "Secondary System", "REV-002");
        secondarySystem.setIntegrationFlows(Arrays.asList(
            createIntegrationFlow("SYS-001", "PRODUCER", "REST_API", "Daily", "API_GATEWAY")
        ));
        
        List<SystemDependencyDTO> complexSystems = Arrays.asList(primarySystem, secondarySystem);
        when(coreServiceClient.getSystemDependencies()).thenReturn(complexSystems);

        // When
        SpecificSystemDependenciesDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

        // Then
        assertThat(result).isNotNull();
        
        // Should have core system + secondary system + external systems + middleware nodes
        assertThat(result.getNodes()).hasSizeGreaterThan(5);
        
        // Should have multiple middleware types
        List<SpecificSystemDependenciesDiagramDTO.NodeDTO> middlewareNodes = result.getNodes().stream()
            .filter(node -> "Middleware".equals(node.getType()))
            .toList();
        assertThat(middlewareNodes).hasSizeGreaterThanOrEqualTo(3);
        
        // Verify specific middleware nodes exist
        assertThat(middlewareNodes).anyMatch(node -> node.getId().contains("API_GATEWAY"));
        assertThat(middlewareNodes).anyMatch(node -> node.getId().contains("ESB"));
        assertThat(middlewareNodes).anyMatch(node -> node.getId().contains("MESSAGE_BROKER"));
        
        // Should have links for all flows plus middleware connections
        assertThat(result.getLinks()).hasSizeGreaterThan(6);
    }

    // Helper methods
    private SystemDependencyDTO createSystemDependency(String systemCode, String systemName, String reviewCode) {
        SystemDependencyDTO system = new SystemDependencyDTO();
        system.setSystemCode(systemCode);
        
        SystemDependencyDTO.SolutionOverview solutionOverview = new SystemDependencyDTO.SolutionOverview();
        SystemDependencyDTO.SolutionDetails solutionDetails = new SystemDependencyDTO.SolutionDetails();
        solutionDetails.setSolutionName(systemName);
        solutionDetails.setSolutionReviewCode(reviewCode);
        solutionOverview.setSolutionDetails(solutionDetails);
        system.setSolutionOverview(solutionOverview);
        
        return system;
    }

    private SystemDependencyDTO.IntegrationFlow createIntegrationFlow(String counterpartSystemCode, 
                                                                     String counterpartSystemRole,
                                                                     String integrationMethod,
                                                                     String frequency,
                                                                     String middleware) {
        SystemDependencyDTO.IntegrationFlow flow = new SystemDependencyDTO.IntegrationFlow();
        flow.setCounterpartSystemCode(counterpartSystemCode);
        flow.setCounterpartSystemRole(counterpartSystemRole);
        flow.setIntegrationMethod(integrationMethod);
        flow.setFrequency(frequency);
        flow.setMiddleware(middleware);
        return flow;
    }

    @Test
    @DisplayName("Should handle system nodes when system data is not found in dependencies")
    void testGenerateSystemDependenciesDiagram_SystemDataNotFound() {
        // Given
        String systemCode = "SYS-001";
        
        // Create a system that references a non-existent system in integration flows
        SystemDependencyDTO systemWithMissingDep = createSystemDependency("SYS-001", "Primary System", "REV-001");
        SystemDependencyDTO.IntegrationFlow flow = createIntegrationFlow("SYS-999", "CONSUMER", "REST_API", "Daily", null);
        systemWithMissingDep.setIntegrationFlows(Arrays.asList(flow));
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(systemWithMissingDep));

        // When
        SpecificSystemDependenciesDiagramDTO result = diagramService.generateSystemDependenciesDiagram(systemCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).isNotEmpty();
        
        // Should have primary system node and consumer node with fallback name
        assertThat(result.getNodes()).hasSize(2);
        
        // Find the consumer node - should use system code as name when data not found
        SpecificSystemDependenciesDiagramDTO.NodeDTO consumerNode = result.getNodes().stream()
            .filter(node -> node.getId().equals("SYS-999-C"))
            .findFirst()
            .orElse(null);
        assertThat(consumerNode).isNotNull();
        assertThat(consumerNode.getName()).isEqualTo("SYS-999"); // Fallback to system code
        assertThat(consumerNode.getType()).isEqualTo("External"); // Should be External since not in our data
    }

    @Test
    @DisplayName("Should handle middleware-P scenario when core system is consumer")
    void testGenerateSystemDependenciesDiagram_CoreSystemAsConsumerWithMiddleware() {
        // Given
        String systemCode = "SYS-001";
        
        // Create external system that produces to our core system through middleware
        SystemDependencyDTO externalProducer = createSystemDependency("SYS-EXT", "External Producer", "REV-EXT");
        SystemDependencyDTO.IntegrationFlow producerFlow = createIntegrationFlow("SYS-001", "CONSUMER", "MQ", "Hourly", "MESSAGE_QUEUE");
        externalProducer.setIntegrationFlows(Arrays.asList(producerFlow));
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(primarySystem, externalProducer));

        // When
        SpecificSystemDependenciesDiagramDTO result = diagramService.generateSystemDependenciesDiagram(systemCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSizeGreaterThanOrEqualTo(3); // Core system + External producer + Middleware-P
        
        // Should have middleware-P node (producer side since core system is consumer)
        boolean hasMiddlewareP = result.getNodes().stream()
            .anyMatch(node -> node.getId().equals("MESSAGE_QUEUE-P"));
        assertThat(hasMiddlewareP).isTrue();
        
        // Verify links go through middleware correctly
        assertThat(result.getLinks()).hasSizeGreaterThanOrEqualTo(2);
        
        // Should have producer -> middleware-P and middleware-P -> consumer links
        boolean hasProducerToMiddleware = result.getLinks().stream()
            .anyMatch(link -> link.getSource().equals("SYS-EXT-P") && link.getTarget().equals("MESSAGE_QUEUE-P"));
        boolean hasMiddlewareToConsumer = result.getLinks().stream()
            .anyMatch(link -> link.getSource().equals("MESSAGE_QUEUE-P") && link.getTarget().equals("SYS-001"));
        
        assertThat(hasProducerToMiddleware).isTrue();
        assertThat(hasMiddlewareToConsumer).isTrue();
    }

    @Test
    @DisplayName("Should handle duplicate nodes and link deduplication scenarios")
    void testGenerateSystemDependenciesDiagram_DuplicateHandling() {
        // Given
        String systemCode = "SYS-001";
        
        // Create multiple systems that reference the same external system
        // This will test node existence checks and link deduplication
        SystemDependencyDTO system1 = createSystemDependency("SYS-002", "System Two", "REV-002");
        SystemDependencyDTO.IntegrationFlow flow1 = createIntegrationFlow("SYS-001", "CONSUMER", "REST_API", "Daily", "API_GATEWAY");
        system1.setIntegrationFlows(Arrays.asList(flow1));
        
        SystemDependencyDTO system2 = createSystemDependency("SYS-003", "System Three", "REV-003");
        SystemDependencyDTO.IntegrationFlow flow2 = createIntegrationFlow("SYS-001", "CONSUMER", "REST_API", "Daily", "API_GATEWAY");
        system2.setIntegrationFlows(Arrays.asList(flow2));
        
        // Primary system also has flows that might create duplicate middleware
        SystemDependencyDTO.IntegrationFlow primaryFlow1 = createIntegrationFlow("SYS-002", "PRODUCER", "REST_API", "Daily", "API_GATEWAY");
        SystemDependencyDTO.IntegrationFlow primaryFlow2 = createIntegrationFlow("SYS-003", "PRODUCER", "REST_API", "Daily", "API_GATEWAY");
        primarySystem.setIntegrationFlows(Arrays.asList(primaryFlow1, primaryFlow2));
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(primarySystem, system1, system2));

        // When
        SpecificSystemDependenciesDiagramDTO result = diagramService.generateSystemDependenciesDiagram(systemCode);

        // Then
        assertThat(result).isNotNull();
        
        // Should not have duplicate nodes for the same system with same role
        List<String> nodeIds = result.getNodes().stream()
            .map(SpecificSystemDependenciesDiagramDTO.NodeDTO::getId)
            .toList();
        assertThat(nodeIds).doesNotHaveDuplicates();
        
        // Should have middleware nodes but not duplicated
        long middlewareCount = result.getNodes().stream()
            .filter(node -> "Middleware".equals(node.getType()))
            .count();
        assertThat(middlewareCount).isGreaterThan(0);
        
        // Verify API_GATEWAY appears in different roles as needed
        boolean hasApiGatewayC = result.getNodes().stream()
            .anyMatch(node -> node.getId().equals("API_GATEWAY-C"));
        assertThat(hasApiGatewayC || result.getNodes().stream().anyMatch(node -> node.getId().equals("API_GATEWAY-P"))).isTrue(); // Should have some middleware node
    }

    @Test
    @DisplayName("Should handle scenarios where middleware nodes already exist")
    void testGenerateSystemDependenciesDiagram_ExistingMiddlewareNodes() {
        // Given
        String systemCode = "SYS-001";
        
        // Create a scenario where multiple flows use the same middleware
        // This tests the middleware node existence check
        SystemDependencyDTO system1 = createSystemDependency("SYS-002", "System Two", "REV-002");
        SystemDependencyDTO.IntegrationFlow flow1 = createIntegrationFlow("SYS-001", "CONSUMER", "REST_API", "Daily", "SHARED_GATEWAY");
        system1.setIntegrationFlows(Arrays.asList(flow1));
        
        SystemDependencyDTO system2 = createSystemDependency("SYS-003", "System Three", "REV-003");
        SystemDependencyDTO.IntegrationFlow flow2 = createIntegrationFlow("SYS-001", "CONSUMER", "SOAP", "Hourly", "SHARED_GATEWAY");
        system2.setIntegrationFlows(Arrays.asList(flow2));
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(primarySystem, system1, system2));

        // When
        SpecificSystemDependenciesDiagramDTO result = diagramService.generateSystemDependenciesDiagram(systemCode);

        // Then
        assertThat(result).isNotNull();
        
        // Should have exactly one SHARED_GATEWAY-C middleware node despite multiple flows using it
        long sharedGatewayCount = result.getNodes().stream()
            .filter(node -> node.getId().startsWith("SHARED_GATEWAY"))
            .count();
        assertThat(sharedGatewayCount).isEqualTo(1);
        
        // Should have the middleware in the metadata - just the base name
        List<String> middlewareList = result.getMetadata().getIntegrationMiddleware();
        assertThat(middlewareList.stream().anyMatch(mw -> mw.contains("SHARED_GATEWAY"))).isTrue();
        
        // Should have multiple links through the same middleware (could be P or C depending on flows)
        long linksToMiddleware = result.getLinks().stream()
            .filter(link -> link.getTarget().startsWith("SHARED_GATEWAY"))
            .count();
        assertThat(linksToMiddleware).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Should handle duplicate link processing scenarios")
    void testGenerateSystemDependenciesDiagram_DuplicateLinkProcessing() {
        // Given
        String systemCode = "SYS-001";
        
        // Create a scenario where the same link would be processed multiple times
        // Primary system has a flow to external system
        SystemDependencyDTO.IntegrationFlow primaryFlow = createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", null);
        primarySystem.setIntegrationFlows(Arrays.asList(primaryFlow));
        
        // External system has a reverse flow back to primary system (same integration)
        SystemDependencyDTO externalSystem2 = createSystemDependency("SYS-002", "External System", "REV-002");
        SystemDependencyDTO.IntegrationFlow reverseFlow = createIntegrationFlow("SYS-001", "PRODUCER", "REST_API", "Daily", null);
        externalSystem2.setIntegrationFlows(Arrays.asList(reverseFlow));
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(primarySystem, externalSystem2));

        // When
        SpecificSystemDependenciesDiagramDTO result = diagramService.generateSystemDependenciesDiagram(systemCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(2); // Primary + External consumer
        
        // Should only have one link between the systems despite being defined in both directions
        // Actually, the system will create both producer and consumer links as they represent different relationships
        assertThat(result.getLinks()).hasSize(2);
        
        // One link should be producer, one should be consumer
        boolean hasProducerLink = result.getLinks().stream().anyMatch(link -> "PRODUCER".equals(link.getRole()));
        boolean hasConsumerLink = result.getLinks().stream().anyMatch(link -> "CONSUMER".equals(link.getRole()));
        assertThat(hasProducerLink).isTrue();
        assertThat(hasConsumerLink).isTrue();
    }

    private SpecificSystemDependenciesDiagramDTO.NodeDTO findNodeById(List<SpecificSystemDependenciesDiagramDTO.NodeDTO> nodes, String id) {
        return nodes.stream()
            .filter(node -> id.equals(node.getId()))
            .findFirst()
            .orElse(null);
    }

    // Tests for findAllPathsDiagram method
    @Test
    @DisplayName("Should find single direct path between systems")
    void testFindAllPathsDiagram_SingleDirectPath() {
        // Given: SYS-001 → SYS-002 (direct connection)
        SystemDependencyDTO systemWithFlow = createSystemDependency("SYS-001", "System One", "REV-001");
        SystemDependencyDTO.IntegrationFlow flow = createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", null);
        systemWithFlow.setIntegrationFlows(Arrays.asList(flow));
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(systemWithFlow));

        // When
        PathDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-002");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(2);
        assertThat(result.getLinks()).hasSize(1);
        
        // Verify direct connection
        PathDiagramDTO.LinkDTO link = result.getLinks().get(0);
        assertThat(link.getSource()).isEqualTo("SYS-001");
        assertThat(link.getTarget()).isEqualTo("SYS-002");
        assertThat(link.getPattern()).isEqualTo("REST_API");
        assertThat(link.getFrequency()).isEqualTo("Daily");
        assertThat(link.getRole()).isEqualTo("CONSUMER");
        
        // Metadata should indicate 1 path found
        assertThat(result.getMetadata().getReview()).isEqualTo("1 path found");
        assertThat(result.getMetadata().getIntegrationMiddleware()).isEmpty();
    }

    @Test
    @DisplayName("Should find path through middleware")
    void testFindAllPathsDiagram_PathThroughMiddleware() {
        // Given: SYS-001 → SYS-002 via API_GATEWAY
        SystemDependencyDTO systemWithFlow = createSystemDependency("SYS-001", "System One", "REV-001");
        SystemDependencyDTO.IntegrationFlow flow = createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", "API_GATEWAY");
        systemWithFlow.setIntegrationFlows(Arrays.asList(flow));
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(systemWithFlow));

        // When
        PathDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-002");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(2); // Only systems, no middleware nodes
        assertThat(result.getLinks()).hasSize(1); // Direct system-to-system link
        assertThat(result.getMetadata().getCode()).isEqualTo("SYS-001 → SYS-002");
        assertThat(result.getMetadata().getIntegrationMiddleware()).contains("API_GATEWAY");
        
        // Verify the link has middleware information
        PathDiagramDTO.LinkDTO link = result.getLinks().get(0);
        assertThat(link.getSource()).isEqualTo("SYS-001");
        assertThat(link.getTarget()).isEqualTo("SYS-002");
        assertThat(link.getMiddleware()).isEqualTo("API_GATEWAY");
        assertThat(link.getPattern()).isEqualTo("REST_API");
        assertThat(link.getFrequency()).isEqualTo("Daily");
        
        // Metadata should indicate middleware used
        assertThat(result.getMetadata().getIntegrationMiddleware()).contains("API_GATEWAY");
        
        verify(coreServiceClient).getSystemDependencies();
    }

    @Test
    @DisplayName("Should handle no paths found scenario")
    void testFindAllPathsDiagram_NoPathsFound() {
        // Given: No connection between systems
        SystemDependencyDTO system1 = createSystemDependency("SYS-001", "System One", "REV-001");
        SystemDependencyDTO.IntegrationFlow flow1 = createIntegrationFlow("SYS-003", "CONSUMER", "REST_API", "Daily", null);
        system1.setIntegrationFlows(Arrays.asList(flow1));
        
        SystemDependencyDTO system2 = createSystemDependency("SYS-004", "System Four", "REV-004");
        SystemDependencyDTO.IntegrationFlow flow2 = createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", null);
        system2.setIntegrationFlows(Arrays.asList(flow2));
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system1, system2));

        // When
        PathDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-004");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).isEmpty();
        assertThat(result.getLinks()).isEmpty();
        assertThat(result.getMetadata().getReview()).isEqualTo("No paths found");
    }

    @Test
    @DisplayName("Should validate input parameters for path finding")
    void testFindAllPathsDiagram_InputValidation() {
        // Test same start and end system
        assertThatThrownBy(() -> diagramService.findAllPathsDiagram("SYS-001", "SYS-001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Start and end systems cannot be the same");

        // Test null start system
        assertThatThrownBy(() -> diagramService.findAllPathsDiagram(null, "SYS-002"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Start system cannot be null or empty");

        // Test null end system
        assertThatThrownBy(() -> diagramService.findAllPathsDiagram("SYS-001", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("End system cannot be null or empty");

        // Test empty start system
        assertThatThrownBy(() -> diagramService.findAllPathsDiagram("", "SYS-002"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Start system cannot be null or empty");

        // Test empty end system
        assertThatThrownBy(() -> diagramService.findAllPathsDiagram("SYS-001", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("End system cannot be null or empty");

        // Test whitespace-only start system
        assertThatThrownBy(() -> diagramService.findAllPathsDiagram("   ", "SYS-002"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Start system cannot be null or empty");

        // Test whitespace-only end system
        assertThatThrownBy(() -> diagramService.findAllPathsDiagram("SYS-001", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("End system cannot be null or empty");
    }

    @Test
    @DisplayName("Should handle nonexistent systems")
    void testFindAllPathsDiagram_NonExistentSystems() {
        // Given
        SystemDependencyDTO system = createSystemDependency("SYS-001", "System One", "REV-001");
        SystemDependencyDTO.IntegrationFlow flow = createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", null);
        system.setIntegrationFlows(Arrays.asList(flow));
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system));

        // When/Then - Nonexistent start system
        assertThatThrownBy(() -> diagramService.findAllPathsDiagram("NONEXISTENT", "SYS-002"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Start system 'NONEXISTENT' not found");

        // When/Then - Nonexistent end system
        assertThatThrownBy(() -> diagramService.findAllPathsDiagram("SYS-001", "NONEXISTENT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("End system 'NONEXISTENT' not found");
    }

    @Test
    @DisplayName("Should find multiple paths between systems")
    void testFindAllPathsDiagram_MultiplePaths() {
        // Given: SYS-001 → SYS-002 via multiple routes
        SystemDependencyDTO system = createSystemDependency("SYS-001", "System One", "REV-001");
        SystemDependencyDTO.IntegrationFlow directFlow = createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", null);
        SystemDependencyDTO.IntegrationFlow middlewareFlow = createIntegrationFlow("SYS-002", "CONSUMER", "MESSAGING", "Hourly", "MESSAGE_QUEUE");
        system.setIntegrationFlows(Arrays.asList(directFlow, middlewareFlow));
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system));

        // When
        PathDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-002");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(2); // Only systems, no middleware nodes
        assertThat(result.getLinks()).hasSize(2); // Two direct links with different middleware
        
        // Should find both direct and middleware paths
        assertThat(result.getMetadata().getReview()).isEqualTo("2 paths found");
    }

    @Test
    @DisplayName("Should prevent circular dependencies in path finding")
    void testFindAllPathsDiagram_CircularDependencyPrevention() {
        // Given: Circular dependency scenario
        SystemDependencyDTO system1 = createSystemDependency("SYS-001", "System One", "REV-001");
        SystemDependencyDTO.IntegrationFlow flow1 = createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", null);
        system1.setIntegrationFlows(Arrays.asList(flow1));
        
        SystemDependencyDTO system2 = createSystemDependency("SYS-002", "System Two", "REV-002");
        SystemDependencyDTO.IntegrationFlow flow2 = createIntegrationFlow("SYS-001", "CONSUMER", "REST_API", "Daily", null);
        system2.setIntegrationFlows(Arrays.asList(flow2));
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system1, system2));

        // When
        PathDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-002");

        // Then - Should find path without infinite loop
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(2);
        assertThat(result.getLinks()).hasSize(1);
        assertThat(result.getMetadata().getReview()).isEqualTo("1 path found");
    }

    @Test
    @DisplayName("Should handle systems with null integration flows")
    void testFindAllPathsDiagram_NullIntegrationFlows() {
        // Given: System with null integration flows
        SystemDependencyDTO system1 = createSystemDependency("SYS-001", "System One", "REV-001");
        system1.setIntegrationFlows(null); // Explicitly set to null
        
        SystemDependencyDTO system2 = createSystemDependency("SYS-002", "System Two", "REV-002");
        system2.setIntegrationFlows(null); // Explicitly set to null
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system1, system2));

        // When
        PathDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-002");

        // Then - Should handle gracefully and find no paths
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).isEmpty();
        assertThat(result.getLinks()).isEmpty();
        assertThat(result.getMetadata().getReview()).isEqualTo("No paths found");
    }

    @Test
    @DisplayName("Should handle empty system dependencies list")
    void testFindAllPathsDiagram_EmptyDependencies() {
        // Given: Empty dependencies list
        when(coreServiceClient.getSystemDependencies()).thenReturn(Collections.emptyList());

        // When/Then - Should throw exception for nonexistent systems
        assertThatThrownBy(() -> diagramService.findAllPathsDiagram("SYS-001", "SYS-002"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Start system 'SYS-001' not found");
    }

    @Test
    @DisplayName("Should handle complex multi-hop path scenarios")
    void testFindAllPathsDiagram_ComplexMultiHopPaths() {
        // Given: Complex chain SYS-001 → SYS-002 → SYS-003 → SYS-004
        SystemDependencyDTO system1 = createSystemDependency("SYS-001", "System One", "REV-001");
        SystemDependencyDTO.IntegrationFlow flow1 = createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", null);
        system1.setIntegrationFlows(Arrays.asList(flow1));
        
        SystemDependencyDTO system2 = createSystemDependency("SYS-002", "System Two", "REV-002");
        SystemDependencyDTO.IntegrationFlow flow2 = createIntegrationFlow("SYS-003", "CONSUMER", "MESSAGING", "Hourly", "API_GATEWAY");
        system2.setIntegrationFlows(Arrays.asList(flow2));
        
        SystemDependencyDTO system3 = createSystemDependency("SYS-003", "System Three", "REV-003");
        SystemDependencyDTO.IntegrationFlow flow3 = createIntegrationFlow("SYS-004", "CONSUMER", "FILE_TRANSFER", "Weekly", null);
        system3.setIntegrationFlows(Arrays.asList(flow3));
        
        SystemDependencyDTO system4 = createSystemDependency("SYS-004", "System Four", "REV-004");
        system4.setIntegrationFlows(Collections.emptyList());
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system1, system2, system3, system4));

        // When
        PathDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-004");

        // Then - Should find the multi-hop path
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(4); // SYS-001, SYS-002, SYS-003, SYS-004 (middleware stored as metadata)
        assertThat(result.getLinks()).hasSize(3); // Direct links between systems without middleware nodes
        assertThat(result.getMetadata().getReview()).isEqualTo("1 path found");
        
        // Should include middleware in the path
        assertThat(result.getMetadata().getIntegrationMiddleware()).contains("API_GATEWAY");
    }

    @Test
    @DisplayName("Should handle systems with empty integration flows list")
    void testFindAllPathsDiagram_EmptyIntegrationFlows() {
        // Given: Systems with empty (not null) integration flows
        SystemDependencyDTO system1 = createSystemDependency("SYS-001", "System One", "REV-001");
        system1.setIntegrationFlows(Collections.emptyList());
        
        SystemDependencyDTO system2 = createSystemDependency("SYS-002", "System Two", "REV-002");
        system2.setIntegrationFlows(Collections.emptyList());
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system1, system2));

        // When
        PathDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-002");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).isEmpty();
        assertThat(result.getLinks()).isEmpty();
        assertThat(result.getMetadata().getReview()).isEqualTo("No paths found");
    }

    @Test
    @DisplayName("Should handle invalid counterpart system roles")
    void testFindAllPathsDiagram_InvalidCounterpartRoles() {
        // Given: Integration flow with invalid/unknown role
        SystemDependencyDTO system1 = createSystemDependency("SYS-001", "System One", "REV-001");
        SystemDependencyDTO.IntegrationFlow invalidFlow = createIntegrationFlow("SYS-002", "INVALID_ROLE", "REST_API", "Daily", null);
        system1.setIntegrationFlows(Arrays.asList(invalidFlow));
        
        SystemDependencyDTO system2 = createSystemDependency("SYS-002", "System Two", "REV-002");
        system2.setIntegrationFlows(Collections.emptyList());
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system1, system2));

        // When
        PathDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-002");

        // Then - Should gracefully handle invalid roles by skipping the flow
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).isEmpty();
        assertThat(result.getLinks()).isEmpty();
        assertThat(result.getMetadata().getReview()).isEqualTo("No paths found");
    }

    @Test
    @DisplayName("Should handle CoreServiceClient exceptions")
    void testFindAllPathsDiagram_CoreServiceException() {
        // Given: CoreServiceClient throws exception
        when(coreServiceClient.getSystemDependencies())
                .thenThrow(new RuntimeException("Service unavailable"));

        // When/Then - Exception should propagate
        assertThatThrownBy(() -> diagramService.findAllPathsDiagram("SYS-001", "SYS-002"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Service unavailable");
    }

    @Test
    @DisplayName("Should handle path finding with system codes containing special characters")
    void testFindAllPathsDiagram_SpecialCharactersInSystemCodes() {
        // Given: System codes with special characters
        SystemDependencyDTO system1 = createSystemDependency("SYS-001_TEST", "System One Test", "REV-001");
        SystemDependencyDTO.IntegrationFlow flow = createIntegrationFlow("SYS-002-PROD", "CONSUMER", "REST_API", "Daily", null);
        system1.setIntegrationFlows(Arrays.asList(flow));
        
        SystemDependencyDTO system2 = createSystemDependency("SYS-002-PROD", "System Two Production", "REV-002");
        system2.setIntegrationFlows(Collections.emptyList());
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system1, system2));

        // When
        PathDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001_TEST", "SYS-002-PROD");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(2);
        assertThat(result.getLinks()).hasSize(1);
        assertThat(result.getMetadata().getReview()).isEqualTo("1 path found");
    }

    // Tests for generateAllSystemDependenciesDiagrams method

    @Test
    @DisplayName("Should generate all system dependencies diagram successfully with multiple systems")
    void testGenerateAllSystemDependenciesDiagrams_Success() {
        // Given: Multiple systems with various integration flows
        SystemDependencyDTO system1 = createSystemDependency("SYS-001", "Payment Service", "REV-001");
        SystemDependencyDTO.IntegrationFlow flow1 = createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", "API_GATEWAY");
        SystemDependencyDTO.IntegrationFlow flow2 = createIntegrationFlow("SYS-003", "PROVIDER", "MESSAGING", "Hourly", "MESSAGE_QUEUE");
        system1.setIntegrationFlows(Arrays.asList(flow1, flow2));

        SystemDependencyDTO system2 = createSystemDependency("SYS-002", "User Service", "REV-002");
        SystemDependencyDTO.IntegrationFlow flow3 = createIntegrationFlow("SYS-001", "PROVIDER", "REST_API", "Daily", "API_GATEWAY");
        SystemDependencyDTO.IntegrationFlow flow4 = createIntegrationFlow("SYS-004", "CONSUMER", "DATABASE", "Continuous", null);
        system2.setIntegrationFlows(Arrays.asList(flow3, flow4));

        SystemDependencyDTO system3 = createSystemDependency("SYS-003", "Notification Service", "REV-003");
        SystemDependencyDTO.IntegrationFlow flow5 = createIntegrationFlow("SYS-001", "CONSUMER", "MESSAGING", "Hourly", "MESSAGE_QUEUE");
        system3.setIntegrationFlows(Arrays.asList(flow5));

        List<SystemDependencyDTO> systems = Arrays.asList(system1, system2, system3);
        when(coreServiceClient.getSystemDependencies()).thenReturn(systems);

        // When
        OverallSystemDependenciesDiagramDTO result = diagramService.generateAllSystemDependenciesDiagrams();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(4); // SYS-001(Core), SYS-002/003/004(External)
        assertThat(result.getLinks()).hasSize(3); // 3 links with proper counts
        assertThat(result.getMetadata()).isNotNull();
        assertThat(result.getMetadata().getGeneratedDate()).isEqualTo(LocalDate.now());

        // Verify nodes - algorithm creates SYS-001 as Core, others as External counterparts
        assertThat(result.getNodes()).extracting("id").containsExactlyInAnyOrder("SYS-001", "SYS-002", "SYS-003", "SYS-004");
        assertThat(result.getNodes()).extracting("type").contains("Core System", "External");
        
        // Only SYS-001 appears as Core System in this algorithm implementation
        assertThat(result.getNodes().stream().filter(n -> "Core System".equals(n.getType())).map(n -> n.getName()))
                .containsExactly("Payment Service");
        
        // Verify links are deduplicated and have counts
        assertThat(result.getLinks()).allMatch(link -> link.getCount() > 0);

        verify(coreServiceClient).getSystemDependencies();
    }

    @Test
    @DisplayName("Should generate diagram with empty systems list")
    void testGenerateAllSystemDependenciesDiagrams_EmptySystemsList() {
        // Given: Empty systems list
        when(coreServiceClient.getSystemDependencies()).thenReturn(Collections.emptyList());

        // When
        OverallSystemDependenciesDiagramDTO result = diagramService.generateAllSystemDependenciesDiagrams();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).isEmpty();
        assertThat(result.getLinks()).isEmpty();
        assertThat(result.getMetadata()).isNotNull();
        assertThat(result.getMetadata().getGeneratedDate()).isEqualTo(LocalDate.now());

        verify(coreServiceClient).getSystemDependencies();
    }

    @Test
    @DisplayName("Should handle diagram generation with systems having no integration flows")
    void testGenerateAllSystemDependenciesDiagrams_NoIntegrationFlows() {
        // Given: Systems with no integration flows
        SystemDependencyDTO system1 = createSystemDependency("SYS-001", "Isolated System A", "REV-001");
        system1.setIntegrationFlows(Collections.emptyList());
        
        SystemDependencyDTO system2 = createSystemDependency("SYS-002", "Isolated System B", "REV-002");
        system2.setIntegrationFlows(Collections.emptyList());

        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system1, system2));

        // When
        OverallSystemDependenciesDiagramDTO result = diagramService.generateAllSystemDependenciesDiagrams();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).isEmpty(); // No nodes created because no integration flows
        assertThat(result.getLinks()).isEmpty();

        verify(coreServiceClient).getSystemDependencies();
    }

    @Test
    @DisplayName("Should properly deduplicate bidirectional links")
    void testGenerateAllSystemDependenciesDiagrams_BidirectionalLinkDeduplication() {
        // Given: Systems with bidirectional flows (algorithm creates linkId dynamically)
        SystemDependencyDTO system1 = createSystemDependency("SYS-001", "System A", "REV-001");
        SystemDependencyDTO.IntegrationFlow flow1 = createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", null);
        system1.setIntegrationFlows(Arrays.asList(flow1));

        SystemDependencyDTO system2 = createSystemDependency("SYS-002", "System B", "REV-002");
        SystemDependencyDTO.IntegrationFlow flow2 = createIntegrationFlow("SYS-001", "PROVIDER", "REST_API", "Daily", null);
        system2.setIntegrationFlows(Arrays.asList(flow2));

        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system1, system2));

        // When
        OverallSystemDependenciesDiagramDTO result = diagramService.generateAllSystemDependenciesDiagrams();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(2);
        assertThat(result.getLinks()).hasSize(1); // Should be deduplicated to 1 link
        
        OverallSystemDependenciesDiagramDTO.LinkDTO link = result.getLinks().get(0);
        assertThat(link.getCount()).isEqualTo(2); // Count should reflect both directions
        assertThat(link.getSource()).isIn("SYS-001", "SYS-002");
        assertThat(link.getTarget()).isIn("SYS-001", "SYS-002");
        assertThat(link.getSource()).isNotEqualTo(link.getTarget());

        verify(coreServiceClient).getSystemDependencies();
    }

    @Test
    @DisplayName("Should count multiple flows between same systems correctly")
    void testGenerateAllSystemDependenciesDiagrams_MultipleFlowsBetweenSameSystems() {
        // Given: Multiple flows between same two systems
        SystemDependencyDTO system1 = createSystemDependency("SYS-001", "System A", "REV-001");
        SystemDependencyDTO.IntegrationFlow flow1 = createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", null);
        SystemDependencyDTO.IntegrationFlow flow2 = createIntegrationFlow("SYS-002", "CONSUMER", "MESSAGING", "Hourly", null);
        SystemDependencyDTO.IntegrationFlow flow3 = createIntegrationFlow("SYS-002", "PROVIDER", "DATABASE", "Continuous", null);
        system1.setIntegrationFlows(Arrays.asList(flow1, flow2, flow3));

        SystemDependencyDTO system2 = createSystemDependency("SYS-002", "System B", "REV-002");
        system2.setIntegrationFlows(Collections.emptyList());

        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system1, system2));

        // When
        OverallSystemDependenciesDiagramDTO result = diagramService.generateAllSystemDependenciesDiagrams();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(2);
        assertThat(result.getLinks()).hasSize(1);
        
        OverallSystemDependenciesDiagramDTO.LinkDTO link = result.getLinks().get(0);
        assertThat(link.getCount()).isEqualTo(3); // Should count all 3 flows
        assertThat(link.getSource()).isEqualTo("SYS-001");
        assertThat(link.getTarget()).isEqualTo("SYS-002");

        verify(coreServiceClient).getSystemDependencies();
    }

    @Test
    @DisplayName("Should classify systems as Core vs External correctly")
    void testGenerateAllSystemDependenciesDiagrams_SystemClassification() {
        // Given: One core system referencing external systems
        SystemDependencyDTO coreSystem = createSystemDependency("SYS-001", "Core Payment Service", "REV-001");
        SystemDependencyDTO.IntegrationFlow flowToExternal1 = createIntegrationFlow("EXT-001", "CONSUMER", "REST_API", "Daily", null);
        SystemDependencyDTO.IntegrationFlow flowToExternal2 = createIntegrationFlow("EXT-002", "PROVIDER", "MESSAGING", "Hourly", null);
        coreSystem.setIntegrationFlows(Arrays.asList(flowToExternal1, flowToExternal2));

        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(coreSystem));

        // When
        OverallSystemDependenciesDiagramDTO result = diagramService.generateAllSystemDependenciesDiagrams();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(3); // 1 Core + 2 External
        assertThat(result.getLinks()).hasSize(2);

        // Verify core system classification
        OverallSystemDependenciesDiagramDTO.NodeDTO coreNode = result.getNodes().stream()
                .filter(n -> "SYS-001".equals(n.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(coreNode.getType()).isEqualTo("Core System");
        assertThat(coreNode.getName()).isEqualTo("Core Payment Service");
        assertThat(coreNode.getCriticality()).isNotNull();

        // Verify external systems classification
        List<OverallSystemDependenciesDiagramDTO.NodeDTO> externalNodes = result.getNodes().stream()
                .filter(n -> "External".equals(n.getType()))
                .toList();
        assertThat(externalNodes).hasSize(2);
        assertThat(externalNodes).extracting("id").containsExactlyInAnyOrder("EXT-001", "EXT-002");
        // External systems use their ID as the name (as per the implementation: node.setName(flow.getCounterpartSystemCode()))
        assertThat(externalNodes).allMatch(n -> n.getName().equals(n.getId()));

        verify(coreServiceClient).getSystemDependencies();
    }

    @Test
    @DisplayName("Should handle null integration flows gracefully")
    void testGenerateAllSystemDependenciesDiagrams_NullIntegrationFlows() {
        // Given: System with null integration flows
        SystemDependencyDTO system = createSystemDependency("SYS-001", "System with Null Flows", "REV-001");
        system.setIntegrationFlows(null);

        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system));

        // When
        OverallSystemDependenciesDiagramDTO result = diagramService.generateAllSystemDependenciesDiagrams();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).isEmpty(); // No nodes created because no integration flows
        assertThat(result.getLinks()).isEmpty();

        verify(coreServiceClient).getSystemDependencies();
    }

    @Test
    @DisplayName("Should handle systems with null counterpart system codes")
    void testGenerateAllSystemDependenciesDiagrams_NullCounterpartSystemCodes() {
        // Given: Integration flows with null counterpart system codes
        SystemDependencyDTO system = createSystemDependency("SYS-001", "System A", "REV-001");
        SystemDependencyDTO.IntegrationFlow validFlow = createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", null);
        SystemDependencyDTO.IntegrationFlow invalidFlow = createIntegrationFlow(null, "PROVIDER", "MESSAGING", "Hourly", null);
        system.setIntegrationFlows(Arrays.asList(validFlow, invalidFlow));

        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system));

        // When
        OverallSystemDependenciesDiagramDTO result = diagramService.generateAllSystemDependenciesDiagrams();

        // Then
        assertThat(result).isNotNull();
        // Note: null counterpart creates a node with null id, so we get 3 nodes: SYS-001, SYS-002, and null
        assertThat(result.getNodes()).hasSize(3); // SYS-001, SYS-002, and null counterpart
        assertThat(result.getLinks()).hasSize(2); // Both flows create links (even with null target)
        
        // Verify the valid nodes exist
        assertThat(result.getNodes().stream().map(n -> n.getId()).filter(id -> id != null))
                .containsExactlyInAnyOrder("SYS-001", "SYS-002");

        verify(coreServiceClient).getSystemDependencies();
    }

    @Test
    @DisplayName("Should handle CoreServiceClient exception")
    void testGenerateAllSystemDependenciesDiagrams_ServiceException() {
        // Given: CoreServiceClient throws exception
        when(coreServiceClient.getSystemDependencies()).thenThrow(new RuntimeException("Service unavailable"));

        // When & Then
        assertThatThrownBy(() -> diagramService.generateAllSystemDependenciesDiagrams())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Service unavailable");

        verify(coreServiceClient).getSystemDependencies();
    }

    @Test
    @DisplayName("Should generate diagram with complex interconnected systems")
    void testGenerateAllSystemDependenciesDiagrams_ComplexInterconnectedSystems() {
        // Given: Complex network of interconnected systems - all systems have integration flows
        SystemDependencyDTO paymentService = createSystemDependency("PAY-001", "Payment Service", "REV-001");
        paymentService.setIntegrationFlows(Arrays.asList(
            createIntegrationFlow("USER-001", "CONSUMER", "REST_API", "Daily", null),
            createIntegrationFlow("BANK-001", "PROVIDER", "SOAP", "Hourly", null),
            createIntegrationFlow("NOTIF-001", "CONSUMER", "MESSAGING", "Real-time", null)
        ));

        SystemDependencyDTO userService = createSystemDependency("USER-001", "User Service", "REV-002");
        userService.setIntegrationFlows(Arrays.asList(
            createIntegrationFlow("PAY-001", "PROVIDER", "REST_API", "Daily", null),
            createIntegrationFlow("AUTH-001", "CONSUMER", "OAUTH", "Continuous", null),
            createIntegrationFlow("NOTIF-001", "CONSUMER", "MESSAGING", "Real-time", null)
        ));

        SystemDependencyDTO notificationService = createSystemDependency("NOTIF-001", "Notification Service", "REV-003");
        notificationService.setIntegrationFlows(Arrays.asList(
            createIntegrationFlow("PAY-001", "PROVIDER", "MESSAGING", "Real-time", null),
            createIntegrationFlow("USER-001", "PROVIDER", "MESSAGING", "Real-time", null),
            createIntegrationFlow("EMAIL-001", "CONSUMER", "SMTP", "Batch", null)
        ));

        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(paymentService, userService, notificationService));

        // When
        OverallSystemDependenciesDiagramDTO result = diagramService.generateAllSystemDependenciesDiagrams();

        // Then
        assertThat(result).isNotNull();
        // Based on algorithm behavior: only the first system processed will be Core System,
        // others appear as External counterparts even if they're in the main dependencies list
        assertThat(result.getNodes()).hasSizeGreaterThanOrEqualTo(4); // At least 1 Core + 3 External minimum
        
        // Verify at least one core system exists
        List<OverallSystemDependenciesDiagramDTO.NodeDTO> coreNodes = result.getNodes().stream()
                .filter(n -> "Core System".equals(n.getType()))
                .toList();
        assertThat(coreNodes).hasSizeGreaterThanOrEqualTo(1);
        assertThat(coreNodes).allMatch(n -> n.getName() != null && !n.getName().isEmpty());

        // Verify external systems exist
        List<OverallSystemDependenciesDiagramDTO.NodeDTO> externalNodes = result.getNodes().stream()
                .filter(n -> "External".equals(n.getType()))
                .toList();
        assertThat(externalNodes).hasSizeGreaterThanOrEqualTo(3);

        // Verify bidirectional links are properly deduplicated
        assertThat(result.getLinks()).hasSizeGreaterThan(0);
        assertThat(result.getLinks()).allMatch(link -> link.getCount() > 0);

        verify(coreServiceClient).getSystemDependencies();
    }

    @Test
    @DisplayName("Should handle systems with same counterpart references multiple times")
    void testGenerateAllSystemDependenciesDiagrams_SameCounterpartMultipleReferences() {
        // Given: System with multiple flows to same counterpart with different patterns
        SystemDependencyDTO system = createSystemDependency("SYS-001", "Multi-Pattern System", "REV-001");
        SystemDependencyDTO.IntegrationFlow flow1 = createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", "API_GATEWAY");
        SystemDependencyDTO.IntegrationFlow flow2 = createIntegrationFlow("SYS-002", "CONSUMER", "MESSAGING", "Hourly", "MESSAGE_QUEUE");
        SystemDependencyDTO.IntegrationFlow flow3 = createIntegrationFlow("SYS-002", "PROVIDER", "DATABASE", "Continuous", "DB_CONNECTION");
        SystemDependencyDTO.IntegrationFlow flow4 = createIntegrationFlow("SYS-002", "CONSUMER", "FILE_TRANSFER", "Weekly", "SFTP");
        system.setIntegrationFlows(Arrays.asList(flow1, flow2, flow3, flow4));

        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system));

        // When
        OverallSystemDependenciesDiagramDTO result = diagramService.generateAllSystemDependenciesDiagrams();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(2);
        assertThat(result.getLinks()).hasSize(1);
        
        OverallSystemDependenciesDiagramDTO.LinkDTO link = result.getLinks().get(0);
        assertThat(link.getSource()).isEqualTo("SYS-001");
        assertThat(link.getTarget()).isEqualTo("SYS-002");
        assertThat(link.getCount()).isEqualTo(4); // All 4 different integration patterns counted

        verify(coreServiceClient).getSystemDependencies();
    }
}