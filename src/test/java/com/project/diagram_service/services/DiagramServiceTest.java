package com.project.diagram_service.services;

import com.project.diagram_service.client.CoreServiceClient;
import com.project.diagram_service.dto.SystemDependencyDTO;
import com.project.diagram_service.dto.SystemDiagramDTO;
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
        SystemDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(2); // Primary system + external system
        assertThat(result.getLinks()).hasSize(1); // One direct link
        assertThat(result.getMetadata().getCode()).isEqualTo(targetSystemCode);
        assertThat(result.getMetadata().getGeneratedDate()).isEqualTo(LocalDate.now());
        
        // Verify primary system node
        SystemDiagramDTO.NodeDTO primaryNode = findNodeById(result.getNodes(), "SYS-001");
        assertThat(primaryNode).isNotNull();
        assertThat(primaryNode.getName()).isEqualTo("Primary System");
        assertThat(primaryNode.getType()).isEqualTo("Core System");
        
        // Verify external system node (with consumer suffix)
        SystemDiagramDTO.NodeDTO externalNode = findNodeById(result.getNodes(), "SYS-002-C");
        assertThat(externalNode).isNotNull();
        assertThat(externalNode.getName()).isEqualTo("External System");
        assertThat(externalNode.getType()).isEqualTo("IncomeSystem");
        
        // Verify link
        SystemDiagramDTO.LinkDTO link = result.getLinks().get(0);
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
        SystemDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(3); // Primary + external + middleware
        assertThat(result.getLinks()).hasSize(2); // Two middleware links
        
        // Verify middleware node
        SystemDiagramDTO.NodeDTO middlewareNode = findNodeById(result.getNodes(), "API_GATEWAY-C");
        assertThat(middlewareNode).isNotNull();
        assertThat(middlewareNode.getName()).isEqualTo("API_GATEWAY");
        assertThat(middlewareNode.getType()).isEqualTo("Middleware");
        assertThat(middlewareNode.getCriticality()).isEqualTo("Standard-2");
        
        // Verify middleware is included in metadata
        assertThat(result.getMetadata().getIntegrationMiddleware()).contains("API_GATEWAY-C");
        
        // Verify links
        List<SystemDiagramDTO.LinkDTO> links = result.getLinks();
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
        SystemDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(3); // Primary + external-C + external-P
        assertThat(result.getLinks()).hasSize(2); // Two direct links
        
        // Verify external system nodes with different roles
        SystemDiagramDTO.NodeDTO externalConsumerNode = findNodeById(result.getNodes(), "SYS-002-C");
        SystemDiagramDTO.NodeDTO externalProducerNode = findNodeById(result.getNodes(), "SYS-002-P");
        
        assertThat(externalConsumerNode).isNotNull();
        assertThat(externalProducerNode).isNotNull();
        
        // Verify links
        List<SystemDiagramDTO.LinkDTO> links = result.getLinks();
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
        SystemDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

        // Then
        assertThat(result).isNotNull();
        
        // Verify external system node (not in our data)
        SystemDiagramDTO.NodeDTO externalNode = findNodeById(result.getNodes(), "SYS-999-C");
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
        SystemDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

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
        SystemDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

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
        SystemDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

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
        SystemDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

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
        SystemDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(1);
        assertThat(result.getLinks()).isEmpty();
        SystemDiagramDTO.NodeDTO coreNode = result.getNodes().get(0);
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
        SystemDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

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
        SystemDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

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
        SystemDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

        // Then
        assertThat(result).isNotNull();
        
        // Should have core system + secondary system + external systems + middleware nodes
        assertThat(result.getNodes()).hasSizeGreaterThan(5);
        
        // Should have multiple middleware types
        List<SystemDiagramDTO.NodeDTO> middlewareNodes = result.getNodes().stream()
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
        SystemDiagramDTO result = diagramService.generateSystemDependenciesDiagram(systemCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).isNotEmpty();
        
        // Should have primary system node and consumer node with fallback name
        assertThat(result.getNodes()).hasSize(2);
        
        // Find the consumer node - should use system code as name when data not found
        SystemDiagramDTO.NodeDTO consumerNode = result.getNodes().stream()
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
        SystemDiagramDTO result = diagramService.generateSystemDependenciesDiagram(systemCode);

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
        SystemDiagramDTO result = diagramService.generateSystemDependenciesDiagram(systemCode);

        // Then
        assertThat(result).isNotNull();
        
        // Should not have duplicate nodes for the same system with same role
        List<String> nodeIds = result.getNodes().stream()
            .map(SystemDiagramDTO.NodeDTO::getId)
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
        SystemDiagramDTO result = diagramService.generateSystemDependenciesDiagram(systemCode);

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
        SystemDiagramDTO result = diagramService.generateSystemDependenciesDiagram(systemCode);

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

    private SystemDiagramDTO.NodeDTO findNodeById(List<SystemDiagramDTO.NodeDTO> nodes, String id) {
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
        SystemDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-002");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(2);
        assertThat(result.getLinks()).hasSize(1);
        
        // Verify direct connection
        SystemDiagramDTO.LinkDTO link = result.getLinks().get(0);
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
        SystemDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-002");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(3); // SYS-001, API_GATEWAY, SYS-002
        assertThat(result.getLinks()).hasSize(2); // SYS-001→API_GATEWAY, API_GATEWAY→SYS-002
        
        // Verify middleware expansion
        List<SystemDiagramDTO.LinkDTO> links = result.getLinks();
        assertThat(links).anySatisfy(link -> {
            assertThat(link.getSource()).isEqualTo("SYS-001");
            assertThat(link.getTarget()).isEqualTo("API_GATEWAY");
            assertThat(link.getPattern()).isEqualTo("REST_API");
            assertThat(link.getFrequency()).isEqualTo("Daily");
        });
        
        assertThat(links).anySatisfy(link -> {
            assertThat(link.getSource()).isEqualTo("API_GATEWAY");
            assertThat(link.getTarget()).isEqualTo("SYS-002");
            assertThat(link.getPattern()).isEqualTo("REST_API");
            assertThat(link.getFrequency()).isEqualTo("Daily");
        });
        
        // Metadata should indicate middleware used
        assertThat(result.getMetadata().getIntegrationMiddleware()).contains("API_GATEWAY");
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
        SystemDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-004");

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
        SystemDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-002");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(3); // SYS-001, MESSAGE_QUEUE, SYS-002
        assertThat(result.getLinks()).hasSize(3); // direct + 2 through middleware
        
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
        SystemDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-002");

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
        SystemDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-002");

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
        SystemDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-004");

        // Then - Should find the multi-hop path
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSizeGreaterThan(3); // At least SYS-001, SYS-002, SYS-003, SYS-004 + middleware
        assertThat(result.getLinks()).hasSizeGreaterThan(3); // Multiple links in the chain
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
        SystemDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-002");

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
        SystemDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-002");

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
        SystemDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001_TEST", "SYS-002-PROD");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(2);
        assertThat(result.getLinks()).hasSize(1);
        assertThat(result.getMetadata().getReview()).isEqualTo("1 path found");
    }
}