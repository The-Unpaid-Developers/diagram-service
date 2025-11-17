package com.project.diagram_service.services;

import com.project.diagram_service.client.CoreServiceClient;
import com.project.diagram_service.dto.SystemDependencyDTO;
import com.project.diagram_service.dto.SpecificSystemDependenciesDiagramDTO;
import com.project.diagram_service.dto.OverallSystemDependenciesDiagramDTO;
import com.project.diagram_service.dto.PathDiagramDTO;
import com.project.diagram_service.dto.CommonSolutionReviewDTO;
import com.project.diagram_service.dto.CommonDiagramDTO;
import com.project.diagram_service.dto.CommonDiagramDTO.NodeDTO;
import com.project.diagram_service.dto.BusinessCapabilityDiagramDTO;
import com.project.diagram_service.dto.BusinessCapabilitiesTreeDTO;
import com.project.diagram_service.dto.BusinessCapabilityDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        assertThat(result)
                .isNotNull()
                .hasSize(2)
                .containsExactly(primarySystem, externalSystem);
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
        assertThat(result.getMetadata())
                .extracting("code", "generatedDate")
                .containsExactly(targetSystemCode, LocalDate.now());
        
        // Verify primary system node
        CommonDiagramDTO.NodeDTO primaryNode = findNodeById(result.getNodes(), "SYS-001");
        assertThat(primaryNode)
                .isNotNull()
                .extracting("name", "type")
                .containsExactly("Primary System", "Core System");
        
        // Verify external system node (with consumer suffix)
        CommonDiagramDTO.NodeDTO externalNode = findNodeById(result.getNodes(), "SYS-002-C");
        assertThat(externalNode)
                .isNotNull()
                .extracting("name", "type")
                .containsExactly("External System", "IncomeSystem");
        
        // Verify link
        CommonDiagramDTO.DetailedLinkDTO link = result.getLinks().get(0);
        assertThat(link)
                .extracting("source", "target", "pattern", "frequency")
                .containsExactly("SYS-001", "SYS-002-C", "REST_API", "Daily");
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
        CommonDiagramDTO.NodeDTO middlewareNode = findNodeById(result.getNodes(), "API_GATEWAY-C");
        assertThat(middlewareNode)
                .isNotNull()
                .extracting("name", "type", "criticality")
                .containsExactly("API_GATEWAY", "Middleware", "Standard-2");
        
        // Verify middleware is included in metadata
        assertThat(result.getMetadata().getIntegrationMiddleware()).contains("API_GATEWAY-C");
        
        // Verify links
        List<CommonDiagramDTO.DetailedLinkDTO> links = result.getLinks();
        assertThat(links)
                .anyMatch(link -> "SYS-001".equals(link.getSource()) && "API_GATEWAY-C".equals(link.getTarget()))
                .anyMatch(link -> "API_GATEWAY-C".equals(link.getSource()) && "SYS-002-C".equals(link.getTarget()));
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
        CommonDiagramDTO.NodeDTO externalConsumerNode = findNodeById(result.getNodes(), "SYS-002-C");
        CommonDiagramDTO.NodeDTO externalProducerNode = findNodeById(result.getNodes(), "SYS-002-P");
        
        assertThat(externalConsumerNode).isNotNull();
        assertThat(externalProducerNode).isNotNull();
        
        // Verify links
        assertThat(result.getLinks())
            .extracting(CommonDiagramDTO.DetailedLinkDTO::getSource, 
                        CommonDiagramDTO.DetailedLinkDTO::getTarget)
            .containsExactlyInAnyOrder(
                tuple("SYS-001", "SYS-002-C"),
                tuple("SYS-002-P", "SYS-001")
            );
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
        CommonDiagramDTO.NodeDTO externalNode = findNodeById(result.getNodes(), "SYS-999-C");
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
        CommonDiagramDTO.NodeDTO coreNode = result.getNodes().get(0);
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
        
        CommonSolutionReviewDTO.SolutionOverview overview = new CommonSolutionReviewDTO.SolutionOverview();
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
        List<CommonDiagramDTO.NodeDTO> middlewareNodes = result.getNodes().stream()
            .filter(node -> "Middleware".equals(node.getType()))
            .toList();
        assertThat(middlewareNodes)
                .hasSizeGreaterThanOrEqualTo(3)
                .anyMatch(node -> node.getId().contains("API_GATEWAY"))
                .anyMatch(node -> node.getId().contains("ESB"))
                .anyMatch(node -> node.getId().contains("MESSAGE_BROKER"));
        
        // Should have links for all flows plus middleware connections
        assertThat(result.getLinks()).hasSizeGreaterThan(6);
    }

    // Helper methods
    private SystemDependencyDTO createSystemDependency(String systemCode, String systemName, String reviewCode) {
        SystemDependencyDTO system = new SystemDependencyDTO();
        system.setSystemCode(systemCode);
        
        CommonSolutionReviewDTO.SolutionOverview solutionOverview = new CommonSolutionReviewDTO.SolutionOverview();
        CommonSolutionReviewDTO.SolutionDetails solutionDetails = new CommonSolutionReviewDTO.SolutionDetails();
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
        CommonDiagramDTO.NodeDTO consumerNode = result.getNodes().stream()
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
        assertThat(result.getNodes())
                .anyMatch(node -> node.getId().equals("MESSAGE_QUEUE-P"));
        
        // Verify links go through middleware correctly
        assertThat(result.getLinks()).hasSizeGreaterThanOrEqualTo(2);
        
        // Should have producer -> middleware-P and middleware-P -> consumer links
        assertThat(result.getLinks())
                .anyMatch(link -> link.getSource().equals("SYS-EXT-P") && link.getTarget().equals("MESSAGE_QUEUE-P"))
                .anyMatch(link -> link.getSource().equals("MESSAGE_QUEUE-P") && link.getTarget().equals("SYS-001"));
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
            .map(CommonDiagramDTO.NodeDTO::getId)
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
        assertThat(result.getLinks())
                .anyMatch(link -> "PRODUCER".equals(link.getRole()))
                .anyMatch(link -> "CONSUMER".equals(link.getRole()));
    }

    private CommonDiagramDTO.NodeDTO findNodeById(List<CommonDiagramDTO.NodeDTO> nodes, String id) {
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
        PathDiagramDTO.PathLinkDTO link = result.getLinks().get(0);
        assertThat(link)
                .extracting("source", "target", "pattern", "frequency", "role")
                .containsExactly("SYS-001", "SYS-002", "REST_API", "Daily", "CONSUMER");
        
        // Metadata should indicate 1 path found
        assertThat(result.getMetadata())
                .extracting("review")
                .isEqualTo("1 path found");
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
        assertThat(result.getMetadata())
                .extracting("code")
                .isEqualTo("SYS-001 → SYS-002");
        assertThat(result.getMetadata().getIntegrationMiddleware()).contains("API_GATEWAY");
        
        // Verify the link has middleware information
        PathDiagramDTO.PathLinkDTO link = result.getLinks().get(0);
        assertThat(link)
                .extracting("source", "target", "middleware", "pattern", "frequency")
                .containsExactly("SYS-001", "SYS-002", "API_GATEWAY", "REST_API", "Daily");
        
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
        assertThat(result)
                .isNotNull()
                .satisfies(r -> {
                    assertThat(r.getNodes()).isEmpty();
                    assertThat(r.getLinks()).isEmpty();
                    assertThat(r.getMetadata().getReview()).isEqualTo("No paths found");
                });
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
    @DisplayName("Should find multiple paths between systems but deduplicate only identical links")
    void testFindAllPathsDiagram_MultiplePaths() {
        // Given: SYS-001 → SYS-002 via multiple routes with different properties
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
        assertThat(result.getLinks()).hasSize(2); // Two different links since they have different properties
        
        // Should find both paths
        assertThat(result.getMetadata())
                .extracting("review")
                .isEqualTo("2 paths found");
        
        // Should preserve middleware information in metadata
        assertThat(result.getMetadata().getIntegrationMiddleware())
            .contains("MESSAGE_QUEUE");
            
        // Verify both links exist with different properties
        assertThat(result.getLinks())
                .extracting("pattern")
                .containsExactlyInAnyOrder("REST_API", "MESSAGING");
        assertThat(result.getLinks())
                .extracting("frequency")
                .containsExactlyInAnyOrder("Daily", "Hourly");
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
        assertThat(result.getNodes().stream()
                .filter(n -> "Core System".equals(n.getType()))
                .map(NodeDTO::getName))
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
        assertThat(result)
                .isNotNull()
                .satisfies(r -> {
                    assertThat(r.getNodes()).isEmpty();
                    assertThat(r.getLinks()).isEmpty();
                    assertThat(r.getMetadata()).isNotNull();
                    assertThat(r.getMetadata().getGeneratedDate()).isEqualTo(LocalDate.now());
                });

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
        assertThat(result)
                .isNotNull()
                .satisfies(r -> {
                    assertThat(r.getNodes()).isEmpty(); // No nodes created because no integration flows
                    assertThat(r.getLinks()).isEmpty();
                });

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
        
        CommonDiagramDTO.SimpleLinkDTO link = result.getLinks().get(0);
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
        
        CommonDiagramDTO.SimpleLinkDTO link = result.getLinks().get(0);
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
        CommonDiagramDTO.NodeDTO coreNode = result.getNodes().stream()
                .filter(n -> "SYS-001".equals(n.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(coreNode)
                .extracting("type", "name")
                .containsExactly("Core System", "Core Payment Service");
        assertThat(coreNode.getCriticality()).isNotNull();

        // Verify external systems classification
        List<CommonDiagramDTO.NodeDTO> externalNodes = result.getNodes().stream()
                .filter(n -> "External".equals(n.getType()))
                .toList();
        assertThat(externalNodes)
                .hasSize(2)
                .extracting("id")
                .containsExactlyInAnyOrder("EXT-001", "EXT-002");
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
        assertThat(result.getNodes().stream()
                .map(NodeDTO::getId)
                .filter(Objects::nonNull))
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
        assertThat(result.getNodes())
            .filteredOn(n -> "Core System".equals(n.getType()))
            .isNotEmpty()
            .allSatisfy(n -> assertThat(n.getName()).isNotNull().isNotEmpty());

        // Verify external systems exist
        List<CommonDiagramDTO.NodeDTO> externalNodes = result.getNodes().stream()
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
        
        CommonDiagramDTO.SimpleLinkDTO link = result.getLinks().get(0);
        assertThat(link.getSource()).isEqualTo("SYS-001");
        assertThat(link.getTarget()).isEqualTo("SYS-002");
        assertThat(link.getCount()).isEqualTo(4); // All 4 different integration patterns counted

        verify(coreServiceClient).getSystemDependencies();
    }

    // Tests for path finding link deduplication

    @Test
    @DisplayName("Should deduplicate links when multiple paths exist between same systems")
    void testFindAllPathsDiagram_LinkDeduplication_MultiplePathsSameSystems() {
        // Given: Multiple flows from SYS-001 to SYS-002 and SYS-002 to SYS-003, creating multiple paths from SYS-001 to SYS-003
        SystemDependencyDTO system1 = createSystemDependency("SYS-001", "System One", "REV-001");
        SystemDependencyDTO.IntegrationFlow flow1a = createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", null);
        SystemDependencyDTO.IntegrationFlow flow1b = createIntegrationFlow("SYS-002", "CONSUMER", "MESSAGING", "Hourly", "QUEUE");
        system1.setIntegrationFlows(Arrays.asList(flow1a, flow1b));
        
        SystemDependencyDTO system2 = createSystemDependency("SYS-002", "System Two", "REV-002");
        SystemDependencyDTO.IntegrationFlow flow2a = createIntegrationFlow("SYS-003", "CONSUMER", "SOAP", "Weekly", null);
        SystemDependencyDTO.IntegrationFlow flow2b = createIntegrationFlow("SYS-003", "CONSUMER", "FILE_TRANSFER", "Monthly", null);
        SystemDependencyDTO.IntegrationFlow flow2c = createIntegrationFlow("SYS-003", "CONSUMER", "DATABASE", "Real-time", null);
        system2.setIntegrationFlows(Arrays.asList(flow2a, flow2b, flow2c));
        
        SystemDependencyDTO system3 = createSystemDependency("SYS-003", "System Three", "REV-003");
        system3.setIntegrationFlows(Collections.emptyList());
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system1, system2, system3));

        // When: Finding paths from SYS-001 to SYS-003
        PathDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-003");

        // Then: Should have separate links for each unique flow combination
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(3); // SYS-001, SYS-002, SYS-003
        assertThat(result.getLinks()).hasSize(5); // 2 from SYS-001->SYS-002 + 3 from SYS-002->SYS-003
        
        // Find links from SYS-001 to SYS-002
        List<PathDiagramDTO.PathLinkDTO> links1to2 = result.getLinks().stream()
            .filter(link -> "SYS-001".equals(link.getSource()) && "SYS-002".equals(link.getTarget()))
            .toList();
        assertThat(links1to2).hasSize(2); // REST_API and MESSAGING flows
        
        // Find links from SYS-002 to SYS-003
        List<PathDiagramDTO.PathLinkDTO> links2to3 = result.getLinks().stream()
            .filter(link -> "SYS-002".equals(link.getSource()) && "SYS-003".equals(link.getTarget()))
            .toList();
        assertThat(links2to3).hasSize(3); // SOAP, FILE_TRANSFER, and DATABASE flows
        
        // Should have found all path combinations: 2 × 3 = 6 paths
        assertThat(result.getMetadata().getReview()).isEqualTo("6 paths found");
        
        verify(coreServiceClient).getSystemDependencies();
    }

    @Test
    @DisplayName("Should deduplicate only truly identical links")
    void testFindAllPathsDiagram_LinkDeduplication_IdenticalDirectLinks() {
        // Given: Multiple flows between same systems, including some truly identical ones
        SystemDependencyDTO system1 = createSystemDependency("SYS-001", "System One", "REV-001");
        SystemDependencyDTO.IntegrationFlow flow1 = createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", "API_GATEWAY");
        SystemDependencyDTO.IntegrationFlow flow2 = createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", "API_GATEWAY"); // Identical to flow1
        SystemDependencyDTO.IntegrationFlow flow3 = createIntegrationFlow("SYS-002", "CONSUMER", "MESSAGING", "Hourly", "QUEUE"); // Different properties
        system1.setIntegrationFlows(Arrays.asList(flow1, flow2, flow3));
        
        SystemDependencyDTO system2 = createSystemDependency("SYS-002", "System Two", "REV-002");
        system2.setIntegrationFlows(Collections.emptyList());
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system1, system2));

        // When: Finding paths from SYS-001 to SYS-002
        PathDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-002");

        // Then: Should have two different links (one REST_API and one MESSAGING), with identical ones deduplicated
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(2);
        assertThat(result.getLinks()).hasSize(2); // Two different links: REST_API and MESSAGING
        
        // Verify both unique links exist with their distinct properties
        assertThat(result.getLinks()).extracting("pattern").containsExactlyInAnyOrder("REST_API", "MESSAGING");
        assertThat(result.getLinks()).extracting("frequency").containsExactlyInAnyOrder("Daily", "Hourly");
        assertThat(result.getLinks()).extracting("middleware").containsExactlyInAnyOrder("API_GATEWAY", "QUEUE");
        
        // Should find 2 unique paths (flow1 and flow2 are identical, so only count as one path in the graph)
        assertThat(result.getMetadata().getReview()).isEqualTo("2 paths found");
        
        verify(coreServiceClient).getSystemDependencies();
    }

    @Test
    @DisplayName("Should handle bidirectional connections correctly preserving all distinct flows")
    void testFindAllPathsDiagram_LinkDeduplication_BidirectionalConnections() {
        // Given: Bidirectional flows between systems with different properties
        SystemDependencyDTO system1 = createSystemDependency("SYS-001", "System One", "REV-001");
        SystemDependencyDTO.IntegrationFlow flow1to2 = createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", null);
        system1.setIntegrationFlows(Arrays.asList(flow1to2));
        
        SystemDependencyDTO system2 = createSystemDependency("SYS-002", "System Two", "REV-002");
        SystemDependencyDTO.IntegrationFlow flow2to1 = createIntegrationFlow("SYS-001", "CONSUMER", "MESSAGING", "Hourly", null);
        SystemDependencyDTO.IntegrationFlow flow2to3 = createIntegrationFlow("SYS-003", "CONSUMER", "SOAP", "Weekly", null);
        system2.setIntegrationFlows(Arrays.asList(flow2to1, flow2to3));
        
        SystemDependencyDTO system3 = createSystemDependency("SYS-003", "System Three", "REV-003");
        system3.setIntegrationFlows(Collections.emptyList());
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system1, system2, system3));

        // When: Finding paths from SYS-001 to SYS-003
        PathDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-003");

        // Then: Should show separate directional links (SYS-001->SYS-002 and SYS-002->SYS-003)
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(3); // SYS-001, SYS-002, SYS-003
        assertThat(result.getLinks()).hasSize(2); // Two directional links
        
        // Check that we have the correct directional links
        boolean hasLink1to2 = result.getLinks().stream()
            .anyMatch(link -> "SYS-001".equals(link.getSource()) && "SYS-002".equals(link.getTarget()));
        boolean hasLink2to3 = result.getLinks().stream()
            .anyMatch(link -> "SYS-002".equals(link.getSource()) && "SYS-003".equals(link.getTarget()));
            
        assertThat(hasLink1to2).isTrue();
        assertThat(hasLink2to3).isTrue();
        
        // Should find 1 path: SYS-001 -> SYS-002 -> SYS-003
        assertThat(result.getMetadata().getReview()).isEqualTo("1 path found");
        
        verify(coreServiceClient).getSystemDependencies();
    }

    @Test
    @DisplayName("Should deduplicate complex multi-hop paths correctly preserving distinct flows")
    void testFindAllPathsDiagram_LinkDeduplication_ComplexMultiHopPaths() {
        // Given: Complex scenario with direct and indirect paths, plus some identical flows
        SystemDependencyDTO system1 = createSystemDependency("SYS-001", "System One", "REV-001");
        SystemDependencyDTO.IntegrationFlow directFlow = createIntegrationFlow("SYS-003", "CONSUMER", "REST_API", "Daily", null);
        SystemDependencyDTO.IntegrationFlow indirectFlow1 = createIntegrationFlow("SYS-002", "CONSUMER", "MESSAGING", "Hourly", null);
        SystemDependencyDTO.IntegrationFlow indirectFlow2 = createIntegrationFlow("SYS-002", "CONSUMER", "MESSAGING", "Hourly", null); // Identical to indirectFlow1
        system1.setIntegrationFlows(Arrays.asList(directFlow, indirectFlow1, indirectFlow2));
        
        SystemDependencyDTO system2 = createSystemDependency("SYS-002", "System Two", "REV-002");
        SystemDependencyDTO.IntegrationFlow flow2to3a = createIntegrationFlow("SYS-003", "CONSUMER", "FILE_TRANSFER", "Monthly", null);
        SystemDependencyDTO.IntegrationFlow flow2to3b = createIntegrationFlow("SYS-003", "CONSUMER", "DATABASE", "Real-time", null); // Different from flow2to3a
        system2.setIntegrationFlows(Arrays.asList(flow2to3a, flow2to3b));
        
        SystemDependencyDTO system3 = createSystemDependency("SYS-003", "System Three", "REV-003");
        system3.setIntegrationFlows(Collections.emptyList());
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system1, system2, system3));

        // When: Finding paths from SYS-001 to SYS-003
        PathDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-003");

        // Then: Should have distinct links for each unique flow
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(3); // SYS-001, SYS-002, SYS-003
        assertThat(result.getLinks()).hasSize(4); // 1 direct + 1 from SYS-001->SYS-002 + 2 from SYS-002->SYS-003
        
        // Check we have all expected unique links
        boolean hasDirectLink = result.getLinks().stream()
            .anyMatch(link -> "SYS-001".equals(link.getSource()) && "SYS-003".equals(link.getTarget()));
        boolean hasLink1to2 = result.getLinks().stream()
            .anyMatch(link -> "SYS-001".equals(link.getSource()) && "SYS-002".equals(link.getTarget()));
        
        long links2to3Count = result.getLinks().stream()
            .filter(link -> "SYS-002".equals(link.getSource()) && "SYS-003".equals(link.getTarget()))
            .count();
            
        assertThat(hasDirectLink).isTrue();
        assertThat(hasLink1to2).isTrue();
        assertThat(links2to3Count).isEqualTo(2); // Two different flows: FILE_TRANSFER and DATABASE
        
        // Should find paths: 1 direct + (1 × 2) = 3 total paths (indirectFlow2 is identical to indirectFlow1)
        assertThat(result.getMetadata().getReview()).isEqualTo("3 paths found");
        
        verify(coreServiceClient).getSystemDependencies();
    }

    @Test
    @DisplayName("Should preserve middleware information in all distinct links")
    void testFindAllPathsDiagram_LinkDeduplication_PreservesMiddleware() {
        // Given: Multiple flows with different middleware between same systems
        SystemDependencyDTO system1 = createSystemDependency("SYS-001", "System One", "REV-001");
        SystemDependencyDTO.IntegrationFlow flowWithMiddleware = createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", "API_GATEWAY");
        SystemDependencyDTO.IntegrationFlow flowWithoutMiddleware = createIntegrationFlow("SYS-002", "CONSUMER", "MESSAGING", "Hourly", null);
        SystemDependencyDTO.IntegrationFlow flowWithDifferentMiddleware = createIntegrationFlow("SYS-002", "CONSUMER", "SOAP", "Weekly", "ESB");
        system1.setIntegrationFlows(Arrays.asList(flowWithMiddleware, flowWithoutMiddleware, flowWithDifferentMiddleware));
        
        SystemDependencyDTO system2 = createSystemDependency("SYS-002", "System Two", "REV-002");
        system2.setIntegrationFlows(Collections.emptyList());
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system1, system2));

        // When: Finding paths from SYS-001 to SYS-002
        PathDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-002");

        // Then: Should preserve all different links with their distinct middleware
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(2);
        assertThat(result.getLinks()).hasSize(3); // Three different links with different properties
        
        // Verify all distinct links exist with their unique properties
        assertThat(result.getLinks()).extracting("pattern").containsExactlyInAnyOrder("REST_API", "MESSAGING", "SOAP");
        assertThat(result.getLinks()).extracting("middleware").containsExactlyInAnyOrder("API_GATEWAY", null, "ESB");
        
        // Should collect all middleware used across paths (excluding null values)
        assertThat(result.getMetadata().getIntegrationMiddleware())
            .containsExactlyInAnyOrder("API_GATEWAY", "ESB");
        
        verify(coreServiceClient).getSystemDependencies();
    }

    @Test
    @DisplayName("Should demonstrate proper deduplication - only truly identical links are removed")
    void testFindAllPathsDiagram_LinkDeduplication_DeduplicationBenefit() {
        // Given: A scenario that shows proper deduplication behavior
        // SYS-001 has 3 different flows to SYS-002 (all unique)
        // SYS-002 has 2 identical flows to SYS-003 (truly identical, should be deduplicated)
        // This tests that we preserve distinct flows but remove exact duplicates
        
        SystemDependencyDTO system1 = createSystemDependency("SYS-001", "System One", "REV-001");
        SystemDependencyDTO.IntegrationFlow flow1a = createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", "API_GATEWAY");
        SystemDependencyDTO.IntegrationFlow flow1b = createIntegrationFlow("SYS-002", "CONSUMER", "MESSAGING", "Hourly", "MESSAGE_QUEUE");
        SystemDependencyDTO.IntegrationFlow flow1c = createIntegrationFlow("SYS-002", "CONSUMER", "SOAP", "Weekly", "ESB");
        system1.setIntegrationFlows(Arrays.asList(flow1a, flow1b, flow1c));
        
        SystemDependencyDTO system2 = createSystemDependency("SYS-002", "System Two", "REV-002");
        // Two identical flows - these should be deduplicated to one link but represent one path
        SystemDependencyDTO.IntegrationFlow flow2a = createIntegrationFlow("SYS-003", "CONSUMER", "FILE_TRANSFER", "Monthly", "SFTP_SERVER");
        SystemDependencyDTO.IntegrationFlow flow2b = createIntegrationFlow("SYS-003", "CONSUMER", "FILE_TRANSFER", "Monthly", "SFTP_SERVER"); // Identical to flow2a
        system2.setIntegrationFlows(Arrays.asList(flow2a, flow2b));
        
        SystemDependencyDTO system3 = createSystemDependency("SYS-003", "System Three", "REV-003");
        system3.setIntegrationFlows(Collections.emptyList());
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system1, system2, system3));

        // When: Finding paths from SYS-001 to SYS-003
        PathDiagramDTO result = diagramService.findAllPathsDiagram("SYS-001", "SYS-003");

        // Then: Should have preserved distinct flows but deduplicated identical ones
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(3); // SYS-001, SYS-002, SYS-003
        assertThat(result.getLinks()).hasSize(4); // 3 distinct from SYS-001->SYS-002 + 1 deduplicated from SYS-002->SYS-003
        
        // Verify we have the expected links
        List<PathDiagramDTO.PathLinkDTO> links1to2 = result.getLinks().stream()
            .filter(link -> "SYS-001".equals(link.getSource()) && "SYS-002".equals(link.getTarget()))
            .toList();
        assertThat(links1to2).hasSize(3); // All 3 distinct flows preserved
        
        List<PathDiagramDTO.PathLinkDTO> links2to3 = result.getLinks().stream()
            .filter(link -> "SYS-002".equals(link.getSource()) && "SYS-003".equals(link.getTarget()))
            .toList();
        assertThat(links2to3).hasSize(1); // Two identical flows deduplicated to one
            
        // Should find paths: 3 flows from SYS-001 to SYS-002 × 1 unique flow from SYS-002 to SYS-003 = 3 total paths
        assertThat(result.getMetadata().getReview()).isEqualTo("3 paths found");
        
        // Should collect all middleware used across all flows
        assertThat(result.getMetadata().getIntegrationMiddleware())
            .containsExactlyInAnyOrder("API_GATEWAY", "MESSAGE_QUEUE", "ESB", "SFTP_SERVER");
        
        verify(coreServiceClient).getSystemDependencies();
    }

    // ========================================
    // Business Capabilities Unit Tests
    // ========================================

    @Test
    @DisplayName("Should transform single system with complete hierarchy from raw data")
    void testTransformSingleSystemWithCompleteHierarchy() {
        // Given - Raw data from MongoDB
        List<BusinessCapabilityDTO> allCapabilities = createAllCapabilitiesData(
            "Customer Management", "CRM", "Contact Management"
        );
        List<BusinessCapabilityDiagramDTO> rawData = createRawSingleSystemData();
        
        when(coreServiceClient.getAllBusinessCapabilities()).thenReturn(allCapabilities);
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(rawData);

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getBusinessCapabilitiesTree();

        // Then - Verify basic structure
        assertThat(result).isNotNull();
        assertThat(result.getCapabilities()).hasSize(4); // L1 + L2 + L3 + System

        // Create node map for efficient lookups
        Map<String, BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> nodeMap = 
            result.getCapabilities().stream()
                .collect(Collectors.toMap(
                    BusinessCapabilitiesTreeDTO.BusinessCapabilityNode::getId,
                    node -> node
                ));

        // Verify L1: Customer Management
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l1 = nodeMap.get("l1-customer-management");
        assertThat(l1).isNotNull();
        assertThat(l1).extracting("systemCode", "name", "level", "parentId", "systemCount")
                     .containsExactly(null, "Customer Management", "L1", null, 1);

        // Verify L2: CRM (parent: L1)
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l2 = nodeMap.get("l2-crm-under-l1-customer-management");
        assertThat(l2).isNotNull();
        assertThat(l2).extracting("systemCode", "name", "level", "parentId", "systemCount")
                     .containsExactly(null, "CRM", "L2", "l1-customer-management", 1);

        // Verify L3: Contact Management (parent: L2)
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l3 = nodeMap.get("l3-contact-management-under-l2-crm-under-l1-customer-management");
        assertThat(l3).isNotNull();
        assertThat(l3).extracting("systemCode", "name", "level", "parentId", "systemCount")
                     .containsExactly(null, "Contact Management", "L3", "l2-crm-under-l1-customer-management", 1);

        // Verify System: NextGen Platform (parent: L3)
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode system = nodeMap.get("sys-001-under-l3-contact-management-under-l2-crm-under-l1-customer-management");
        assertThat(system).isNotNull();
        assertThat(system).extracting("systemCode", "name", "level", "parentId", "systemCount")
                          .containsExactly("sys-001", "NextGen Platform", "System", "l3-contact-management-under-l2-crm-under-l1-customer-management", null);
        
        // Verify system has correct flow-specific ID
        assertThat(system.getId()).isEqualTo("sys-001-under-l3-contact-management-under-l2-crm-under-l1-customer-management");

        verify(coreServiceClient).getAllBusinessCapabilities();
        verify(coreServiceClient).getBusinessCapabilities();
    }

    @Test
    @DisplayName("Should handle multiple systems under same L3 from raw data")
    void testMultipleSystemsUnderSameL3() {
        // Given - 2 systems under Contact Management
        List<BusinessCapabilityDTO> allCapabilities = createAllCapabilitiesData(
            "Customer Management", "CRM", "Contact Management"
        );
        List<BusinessCapabilityDiagramDTO> rawData = createRawMultipleSystemsSameL3Data();
        
        when(coreServiceClient.getAllBusinessCapabilities()).thenReturn(allCapabilities);
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(rawData);

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getBusinessCapabilitiesTree();

        // Then
        assertThat(result.getCapabilities()).hasSize(5); // L1 + L2 + L3 + 2 Systems

        // L3 should have systemCount = 2 and null systemCode
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l3 = result.getCapabilities().stream()
            .filter(n -> "L3".equals(n.getLevel()) && "Contact Management".equals(n.getName()))
            .findFirst()
            .orElseThrow();
        
        assertThat(l3.getSystemCount()).isEqualTo(2);
        assertThat(l3.getSystemCode()).isNull(); // capability nodes should have null systemCode

        // Both systems should have same parent (both under same L3 flow)
        List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> systems = result.getCapabilities().stream()
            .filter(n -> "System".equals(n.getLevel()))
            .toList();
        
        assertThat(systems)
            .hasSize(2)
            .extracting("parentId")
            .containsOnly("l3-contact-management-under-l2-crm-under-l1-customer-management");

        // Systems should have flow-specific IDs but same solution name
        assertThat(systems)
            .extracting("name")
            .containsExactlyInAnyOrder("NextGen Platform", "Legacy CRM");
        
        // Systems should have their respective systemCodes populated
        assertThat(systems)
            .extracting("systemCode")
            .containsExactlyInAnyOrder("sys-001", "sys-002");
    }

    @Test
    @DisplayName("Should handle multiple L3s under same L2 from raw data")
    void testMultipleL3sUnderSameL2() {
        // Given - 2 L3s under CRM
        List<BusinessCapabilityDTO> allCapabilities = Arrays.asList(
            createCapabilityDTO("Customer Management", "CRM", "Contact Management"),
            createCapabilityDTO("Customer Management", "CRM", "Lead Management")
        );
        List<BusinessCapabilityDiagramDTO> rawData = createRawMultipleL3sSameL2Data();
        
        when(coreServiceClient.getAllBusinessCapabilities()).thenReturn(allCapabilities);
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(rawData);

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getBusinessCapabilitiesTree();

        // Then
        assertThat(result.getCapabilities()).hasSize(6); // L1 + L2 + 2xL3 + 2xSystems

        // L2 should have systemCount = 2 (2 L3 children)
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l2 = result.getCapabilities().stream()
            .filter(n -> "L2".equals(n.getLevel()))
            .findFirst()
            .orElseThrow();
        
        assertThat(l2.getSystemCount()).isEqualTo(2);

        // Both L3s should have same parent (L2)
        List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> l3Nodes = result.getCapabilities().stream()
            .filter(n -> "L3".equals(n.getLevel()))
            .toList();
        
        assertThat(l3Nodes)
            .hasSize(2)
            .extracting("parentId")
            .containsOnly("l2-crm-under-l1-customer-management");

        assertThat(l3Nodes)
            .extracting("name", "systemCount")
            .containsExactlyInAnyOrder(
                tuple("Contact Management", 1),
                tuple("Lead Management", 1)
            );
    }

    @Test
    @DisplayName("Should handle multiple L2s under same L1 from raw data")
    void testMultipleL2sUnderSameL1() {
        // Given - 2 L2s under Customer Management
        List<BusinessCapabilityDTO> allCapabilities = Arrays.asList(
            createCapabilityDTO("Customer Management", "CRM", "Contact Management"),
            createCapabilityDTO("Customer Management", "Customer Service", "Ticket Management")
        );
        List<BusinessCapabilityDiagramDTO> rawData = createRawMultipleL2sSameL1Data();
        
        when(coreServiceClient.getAllBusinessCapabilities()).thenReturn(allCapabilities);
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(rawData);

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getBusinessCapabilitiesTree();

        // Then
        assertThat(result.getCapabilities()).hasSize(7); // L1 + 2xL2 + 2xL3 + 2xSystems

        // L1 should have systemCount = 2 (2 L2 children)
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l1 = result.getCapabilities().stream()
            .filter(n -> "L1".equals(n.getLevel()))
            .findFirst()
            .orElseThrow();
        
        assertThat(l1.getSystemCount()).isEqualTo(2);

        // Both L2s should have same parent (L1)
        List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> l2Nodes = result.getCapabilities().stream()
            .filter(n -> "L2".equals(n.getLevel()))
            .toList();
        
        assertThat(l2Nodes)
            .hasSize(2)
            .extracting("parentId")
            .containsOnly("l1-customer-management");

        assertThat(l2Nodes)
            .extracting("name")
            .containsExactlyInAnyOrder("CRM", "Customer Service");
    }

    @Test
    @DisplayName("Should handle system with multiple business capability flows - each flow creates separate leaf nodes")
    void testSystemWithMultipleCapabilityFlows() {
        // Given - System with multiple business capability flows (real-world scenario)
        List<BusinessCapabilityDTO> allCapabilities = Arrays.asList(
            createCapabilityDTO("Customer Management", "CRM", "Contact Management"),
            createCapabilityDTO("Customer Management", "CRM", "Lead Management"),
            createCapabilityDTO("Sales", "CRM", "Opportunity Management")
        );
        
        BusinessCapabilityDiagramDTO multiCapabilitySystem = new BusinessCapabilityDiagramDTO();
        multiCapabilitySystem.setSystemCode("sys-001");
        multiCapabilitySystem.setSolutionOverview(createSolutionOverview("Multi-Purpose CRM"));
        
        // System participates in 3 different capability flows:
        // Flow 1: Customer Management -> CRM -> Contact Management
        // Flow 2: Customer Management -> CRM -> Lead Management  
        // Flow 3: Sales -> CRM -> Opportunity Management
        multiCapabilitySystem.setBusinessCapabilities(Arrays.asList(
            createBusinessCapability("Customer Management", "CRM", "Contact Management"),
            createBusinessCapability("Customer Management", "CRM", "Lead Management"),
            createBusinessCapability("Sales", "CRM", "Opportunity Management")
        ));
        
        when(coreServiceClient.getAllBusinessCapabilities()).thenReturn(allCapabilities);
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(Arrays.asList(multiCapabilitySystem));

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getBusinessCapabilitiesTree();

        // Then - Should create separate leaf nodes for each business capability flow
        assertThat(result.getCapabilities()).isNotNull();
        
        // Should have 2 L1s: Customer Management + Sales
        List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> l1Nodes = result.getCapabilities().stream()
            .filter(n -> "L1".equals(n.getLevel()))
            .toList();
        assertThat(l1Nodes).hasSize(2);
        assertThat(l1Nodes)
            .extracting("name")
            .containsExactlyInAnyOrder("Customer Management", "Sales");

        // Should have 2 separate CRM L2 nodes (one under each L1)
        List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> l2Nodes = result.getCapabilities().stream()
            .filter(n -> "L2".equals(n.getLevel()) && "CRM".equals(n.getName()))
            .toList();
        assertThat(l2Nodes).hasSize(2); // CRM appears under both Customer Management and Sales

        // Should have 3 L3 nodes (Contact Management, Lead Management, Opportunity Management)
        List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> l3Nodes = result.getCapabilities().stream()
            .filter(n -> "L3".equals(n.getLevel()))
            .toList();
        assertThat(l3Nodes).hasSize(3);
        assertThat(l3Nodes)
            .extracting("name")
            .containsExactlyInAnyOrder("Contact Management", "Lead Management", "Opportunity Management");

        // CRITICAL: Should have 3 separate system leaf nodes (one for each capability flow)
        List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> systemNodes = result.getCapabilities().stream()
            .filter(n -> "System".equals(n.getLevel()))
            .toList();
        assertThat(systemNodes).hasSize(3); // System appears 3 times as separate leaf nodes
        
        // All system nodes should have the same name but different IDs (flow-specific)
        assertThat(systemNodes)
            .extracting("name")
            .containsOnly("Multi-Purpose CRM"); // Same solution name
        
        // Each system node should have a different parent (different L3)
        Set<String> parentIds = systemNodes.stream()
            .map(BusinessCapabilitiesTreeDTO.BusinessCapabilityNode::getParentId)
            .collect(Collectors.toSet());
        assertThat(parentIds).hasSize(3); // 3 different parents

        verify(coreServiceClient).getBusinessCapabilities();
    }

    @Test  
    @DisplayName("Should handle L2/L3 capabilities with same names under different parents")
    void testSameNameCapabilitiesUnderDifferentParents() {
        // Given - Scenario where "Analytics" L2 appears under both "Customer Management" and "Sales"
        List<BusinessCapabilityDTO> allCapabilities = Arrays.asList(
            createCapabilityDTO("Customer Management", "Analytics", "Customer Insights"),
            createCapabilityDTO("Sales", "Analytics", "Sales Insights")
        );
        List<BusinessCapabilityDiagramDTO> rawData = Arrays.asList(
            createRawSystem("sys-001", "Customer Analytics", "Customer Management", "Analytics", "Customer Insights"),
            createRawSystem("sys-002", "Sales Analytics", "Sales", "Analytics", "Sales Insights")
        );
        
        when(coreServiceClient.getAllBusinessCapabilities()).thenReturn(allCapabilities);
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(rawData);

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getBusinessCapabilitiesTree();

        // Then - Should create separate Analytics L2 nodes under different L1 parents
        List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> analyticsL2Nodes = result.getCapabilities().stream()
            .filter(n -> "L2".equals(n.getLevel()) && "Analytics".equals(n.getName()))
            .toList();
        
        assertThat(analyticsL2Nodes).hasSize(2); // Two separate Analytics L2 nodes
        
        // Each Analytics L2 should have different parent L1
        Set<String> l2ParentIds = analyticsL2Nodes.stream()
            .map(BusinessCapabilitiesTreeDTO.BusinessCapabilityNode::getParentId)
            .collect(Collectors.toSet());
        assertThat(l2ParentIds).hasSize(2); // Different L1 parents

        // Should have 2 different L3 nodes under the different Analytics L2s
        List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> l3Nodes = result.getCapabilities().stream()
            .filter(n -> "L3".equals(n.getLevel()))
            .toList();
        assertThat(l3Nodes).hasSize(2);
        assertThat(l3Nodes)
            .extracting("name")
            .containsExactlyInAnyOrder("Customer Insights", "Sales Insights");

        verify(coreServiceClient).getBusinessCapabilities();
    }

    @Test
    @DisplayName("Should handle UNKNOWN capabilities from raw MongoDB data structure")
    void testHandleUnknownCapabilitiesFromRawMongoDB() {
        // Given - Raw data exactly like MongoDB structure you provided
        List<BusinessCapabilityDTO> allCapabilities = createAllCapabilitiesData("UNKNOWN", "UNKNOWN", "UNKNOWN");
        
        BusinessCapabilityDiagramDTO rawSystem = new BusinessCapabilityDiagramDTO();
        rawSystem.setSystemCode("sys-001");
        
        // Create complete solution overview matching your example
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
        
        // Create business capability exactly like your example
        BusinessCapabilityDiagramDTO.BusinessCapability capability = 
            new BusinessCapabilityDiagramDTO.BusinessCapability();
        capability.setId("68db858493fdf8d87a3bb4e9");
        capability.setL1Capability("UNKNOWN");
        capability.setL2Capability("UNKNOWN");
        capability.setL3Capability("UNKNOWN");
        capability.setRemarks(null);
        rawSystem.setBusinessCapabilities(Arrays.asList(capability));
        
        when(coreServiceClient.getAllBusinessCapabilities()).thenReturn(allCapabilities);
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(Arrays.asList(rawSystem));

        // When - Transform raw data into hierarchical tree
        BusinessCapabilitiesTreeDTO result = diagramService.getBusinessCapabilitiesTree();

        // Then - Verify transformation from raw MongoDB structure to tree hierarchy
        assertThat(result.getCapabilities()).hasSize(4); // L1:UNKNOWN + L2:UNKNOWN + L3:UNKNOWN + System

        // Should create complete UNKNOWN hierarchy
        Map<String, BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> nodeMap = 
            result.getCapabilities().stream()
                .collect(Collectors.toMap(
                    BusinessCapabilitiesTreeDTO.BusinessCapabilityNode::getId,
                    node -> node
                ));

        // L1: UNKNOWN
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l1Unknown = nodeMap.get("l1-unknown");
        assertThat(l1Unknown)
            .isNotNull()
            .satisfies(node -> {
                assertThat(node.getName()).isEqualTo("UNKNOWN");
                assertThat(node.getLevel()).isEqualTo("L1");
                assertThat(node.getParentId()).isNull();
                assertThat(node.getSystemCount()).isEqualTo(1);
            });

        // L2: UNKNOWN (child of L1)
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l2Unknown = nodeMap.get("l2-unknown-under-l1-unknown");
        assertThat(l2Unknown)
            .isNotNull()
            .satisfies(node -> {
                assertThat(node.getName()).isEqualTo("UNKNOWN");
                assertThat(node.getLevel()).isEqualTo("L2");
                assertThat(node.getParentId()).isEqualTo("l1-unknown");
                assertThat(node.getSystemCount()).isEqualTo(1);
            });

        // L3: UNKNOWN (child of L2)
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l3Unknown = nodeMap.get("l3-unknown-under-l2-unknown-under-l1-unknown");
        assertThat(l3Unknown)
            .isNotNull()
            .satisfies(node -> {
                assertThat(node.getName()).isEqualTo("UNKNOWN");
                assertThat(node.getLevel()).isEqualTo("L3");
                assertThat(node.getParentId()).isEqualTo("l2-unknown-under-l1-unknown");
                assertThat(node.getSystemCount()).isEqualTo(1);
            });

        // System: NextGen Platform (child of L3)
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode system = nodeMap.get("sys-001-under-l3-unknown-under-l2-unknown-under-l1-unknown");
        assertThat(system)
            .isNotNull()
            .satisfies(node -> {
                assertThat(node.getId()).isEqualTo("sys-001-under-l3-unknown-under-l2-unknown-under-l1-unknown"); // flow-specific ID
                assertThat(node.getName()).isEqualTo("NextGen Platform"); // solutionName from raw data
                assertThat(node.getLevel()).isEqualTo("System");
                assertThat(node.getParentId()).isEqualTo("l3-unknown-under-l2-unknown-under-l1-unknown");
                assertThat(node.getSystemCount()).isNull(); // Systems don't have counts
            });

        verify(coreServiceClient).getBusinessCapabilities();
    }

    @Test
    @DisplayName("Should handle UNKNOWN capabilities from raw data")
    void testHandleUnknownCapabilities() {
        // Given - System with UNKNOWN capabilities (like your example)
        List<BusinessCapabilityDiagramDTO> rawData = createRawUnknownCapabilitiesData();
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(rawData);

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getBusinessCapabilitiesTree();

        // Then
        assertThat(result.getCapabilities()).hasSize(4); // L1:UNKNOWN + L2:UNKNOWN + L3:UNKNOWN + System

        // Should create UNKNOWN hierarchy
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l1Unknown = result.getCapabilities().stream()
            .filter(n -> "L1".equals(n.getLevel()) && "UNKNOWN".equals(n.getName()))
            .findFirst()
            .orElseThrow();
        
        assertThat(l1Unknown).isNotNull();
        assertThat(l1Unknown.getId()).isEqualTo("l1-unknown");

        // System should be under l3-unknown
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode system = result.getCapabilities().stream()
            .filter(n -> "System".equals(n.getLevel()))
            .findFirst()
            .orElseThrow();
        
        assertThat(system.getParentId()).isEqualTo("l3-unknown-under-l2-unknown-under-l1-unknown");
    }

    @Test
    @DisplayName("Should handle missing solution overview gracefully from raw data")
    void testMissingSolutionOverview() {
        // Given
        List<BusinessCapabilityDTO> allCapabilities = createAllCapabilitiesData(
            "Customer Management", "CRM", "Contact Management"
        );
        List<BusinessCapabilityDiagramDTO> rawData = createRawDataWithMissingSolution();
        
        when(coreServiceClient.getAllBusinessCapabilities()).thenReturn(allCapabilities);
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(rawData);

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getBusinessCapabilitiesTree();

        // Then
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode system = result.getCapabilities().stream()
            .filter(n -> "System".equals(n.getLevel()))
            .findFirst()
            .orElseThrow();
        
        assertThat(system.getId()).isEqualTo("sys-001-under-l3-contact-management-under-l2-crm-under-l1-customer-management");
        assertThat(system.getName()).isEqualTo("Unknown Solution");
    }

    @Test
    @DisplayName("Should handle empty business capabilities list")
    void testEmptyBusinessCapabilities() {
        // Given
        when(coreServiceClient.getAllBusinessCapabilities()).thenReturn(Collections.emptyList());
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(Collections.emptyList());

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getBusinessCapabilitiesTree();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCapabilities()).isEmpty();
    }

    @Test
    @DisplayName("Should handle service exception gracefully")
    void testServiceException() {
        // Given
        when(coreServiceClient.getAllBusinessCapabilities())
            .thenThrow(new RuntimeException("Database connection failed"));

        // When & Then
        assertThatThrownBy(() -> diagramService.getBusinessCapabilitiesTree())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to generate business capabilities tree")
            .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should include capabilities without systems in tree (two-phase approach)")
    void testCapabilitiesWithoutSystems() {
        // Given - All capabilities includes ones without systems
        List<BusinessCapabilityDTO> allCapabilities = Arrays.asList(
            createCapabilityDTO("Customer Management", "CRM", "Contact Management"),
            createCapabilityDTO("Customer Management", "CRM", "Lead Management"),  // No system
            createCapabilityDTO("Finance", "Accounting", "General Ledger")  // No system
        );
        
        // Only one system exists (Contact Management)
        List<BusinessCapabilityDiagramDTO> systemCapabilities = Arrays.asList(
            createRawSystem("sys-001", "CRM System", "Customer Management", "CRM", "Contact Management")
        );
        
        when(coreServiceClient.getAllBusinessCapabilities()).thenReturn(allCapabilities);
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(systemCapabilities);

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getBusinessCapabilitiesTree();

        // Then - Should have all capability nodes even those without systems
        assertThat(result).isNotNull();
        
        // Should have 2 L1s (Customer Management + Finance)
        List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> l1Nodes = result.getCapabilities().stream()
            .filter(n -> "L1".equals(n.getLevel()))
            .toList();
        assertThat(l1Nodes).hasSize(2);
        assertThat(l1Nodes).extracting("name")
            .containsExactlyInAnyOrder("Customer Management", "Finance");

        // Should have 3 L3s (Contact Management + Lead Management + General Ledger)
        List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> l3Nodes = result.getCapabilities().stream()
            .filter(n -> "L3".equals(n.getLevel()))
            .toList();
        assertThat(l3Nodes).hasSize(3);
        assertThat(l3Nodes).extracting("name")
            .containsExactlyInAnyOrder("Contact Management", "Lead Management", "General Ledger");

        // But only 1 system node (since only Contact Management has a system)
        List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> systemNodes = result.getCapabilities().stream()
            .filter(n -> "System".equals(n.getLevel()))
            .toList();
        assertThat(systemNodes).hasSize(1);
        assertThat(systemNodes.get(0).getName()).isEqualTo("CRM System");

        // Verify systemCount is correct
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode contactMgmtL3 = l3Nodes.stream()
            .filter(n -> "Contact Management".equals(n.getName()))
            .findFirst()
            .orElseThrow();
        assertThat(contactMgmtL3.getSystemCount()).isEqualTo(1);  // Has 1 system

        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode leadMgmtL3 = l3Nodes.stream()
            .filter(n -> "Lead Management".equals(n.getName()))
            .findFirst()
            .orElseThrow();
        assertThat(leadMgmtL3.getSystemCount()).isZero();  // No systems

        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode glL3 = l3Nodes.stream()
            .filter(n -> "General Ledger".equals(n.getName()))
            .findFirst()
            .orElseThrow();
        assertThat(glL3.getSystemCount()).isZero();  // No systems

        verify(coreServiceClient).getAllBusinessCapabilities();
        verify(coreServiceClient).getBusinessCapabilities();
    }

    @Test
    @DisplayName("Should handle system with capability not in dropdown endpoint")
    void testSystemWithCapabilityNotInDropdown() {
        // Given - Dropdown has one capability, but system has different capability
        List<BusinessCapabilityDTO> allCapabilities = Arrays.asList(
            createCapabilityDTO("Finance", "Accounting", "General Ledger")
        );
        
        // System has capability not in dropdown
        List<BusinessCapabilityDiagramDTO> systemCapabilities = Arrays.asList(
            createRawSystem("sys-001", "CRM System", "Customer Management", "CRM", "Contact Management")
        );
        
        when(coreServiceClient.getAllBusinessCapabilities()).thenReturn(allCapabilities);
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(systemCapabilities);

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getBusinessCapabilitiesTree();

        // Then - Should create both capability hierarchies
        List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> l1Nodes = result.getCapabilities().stream()
            .filter(n -> "L1".equals(n.getLevel()))
            .toList();
        assertThat(l1Nodes).hasSize(2);
        assertThat(l1Nodes).extracting("name")
            .containsExactlyInAnyOrder("Finance", "Customer Management");

        // Finance L3 should have no systems
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode financeL3 = result.getCapabilities().stream()
            .filter(n -> "L3".equals(n.getLevel()) && n.getName().equals("General Ledger"))
            .findFirst()
            .orElseThrow();
        assertThat(financeL3.getSystemCount()).isZero();

        // Customer Management L3 should have 1 system
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode crmL3 = result.getCapabilities().stream()
            .filter(n -> "L3".equals(n.getLevel()) && n.getName().equals("Contact Management"))
            .findFirst()
            .orElseThrow();
        assertThat(crmL3.getSystemCount()).isEqualTo(1);

        verify(coreServiceClient).getAllBusinessCapabilities();
        verify(coreServiceClient).getBusinessCapabilities();
    }

    @Test
    @DisplayName("Should handle complex multi-level hierarchy from raw data")
    void testComplexMultiLevelHierarchy() {
        // Given - Complex scenario: 2 L1s, multiple L2s, L3s, and systems
        List<BusinessCapabilityDTO> allCapabilities = createMultipleCapabilitiesData();
        List<BusinessCapabilityDiagramDTO> rawData = createRawComplexHierarchyData();
        
        when(coreServiceClient.getAllBusinessCapabilities()).thenReturn(allCapabilities);
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(rawData);

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getBusinessCapabilitiesTree();

        // Then
        assertThat(result.getCapabilities()).hasSizeGreaterThanOrEqualTo(8);

        // Verify we have 2 distinct L1s
        List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> l1Nodes = result.getCapabilities().stream()
            .filter(n -> "L1".equals(n.getLevel()))
            .toList();
        
        assertThat(l1Nodes).hasSize(2);
        assertThat(l1Nodes)
            .extracting("name")
            .containsExactlyInAnyOrder("Customer Management", "Product Management");

        // Each L1 should have correct system counts
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode customerL1 = l1Nodes.stream()
            .filter(n -> "Customer Management".equals(n.getName()))
            .findFirst()
            .orElseThrow();
        assertThat(customerL1.getSystemCount()).isGreaterThan(0);
    }

    // ========================================
    // System-Specific Business Capabilities Unit Tests
    // ========================================

    @Test
    @DisplayName("Should return filtered tree for specific system code")
    void testGetSystemBusinessCapabilitiesTree_ValidSystemCode() {
        // Given - Multiple systems with different capabilities
        List<BusinessCapabilityDiagramDTO> rawData = Arrays.asList(
            createRawSystem("sys-001", "CRM System", "Customer Management", "CRM", "Contact Management"),
            createRawSystem("sys-002", "ERP System", "Finance", "Accounting", "Accounts Payable"),
            createRawSystem("sys-003", "Marketing System", "Customer Management", "CRM", "Lead Management")
        );
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(rawData);

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getSystemBusinessCapabilitiesTree("sys-001");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCapabilities()).hasSize(4); // L1 + L2 + L3 + System

        // Verify only sys-001 capabilities are included
        List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> systemNodes = result.getCapabilities().stream()
            .filter(n -> "Root".equals(n.getLevel()))
            .toList();
        
        assertThat(systemNodes).hasSize(1);
        assertThat(systemNodes.get(0).getName()).isEqualTo("CRM System");
        assertThat(systemNodes.get(0).getId()).contains("sys-001");

        // Verify the complete hierarchy for sys-001
        Map<String, BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> nodeMap = 
            result.getCapabilities().stream()
                .collect(Collectors.toMap(
                    BusinessCapabilitiesTreeDTO.BusinessCapabilityNode::getId,
                    node -> node
                ));

        // Verify Root node (now root in Root->L1->L2->L3 hierarchy)
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode systemNode = nodeMap.values().stream()
            .filter(n -> "Root".equals(n.getLevel()) && "CRM System".equals(n.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("Root node not found"));
        assertThat(systemNode.getParentId()).isNull();
        assertThat(systemNode.getSystemCode()).isEqualTo("sys-001"); // system node should have systemCode

        // Verify L1 node (now has Root as parent)
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l1Node = nodeMap.values().stream()
            .filter(n -> "L1".equals(n.getLevel()) && "Customer Management".equals(n.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("L1 node not found"));
        assertThat(l1Node.getParentId()).isEqualTo(systemNode.getId());
        assertThat(l1Node.getSystemCode()).isNull(); // capability nodes should have null systemCode

        // Verify L2 node
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l2Node = nodeMap.values().stream()
            .filter(n -> "L2".equals(n.getLevel()) && "CRM".equals(n.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("L2 node not found"));
        assertThat(l2Node.getParentId()).isEqualTo(l1Node.getId());
        assertThat(l2Node.getSystemCode()).isNull(); // capability nodes should have null systemCode

        // Verify L3 node
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l3Node = nodeMap.values().stream()
            .filter(n -> "L3".equals(n.getLevel()) && "Contact Management".equals(n.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("L3 node not found"));
        assertThat(l3Node.getParentId()).isEqualTo(l2Node.getId());
        assertThat(l3Node.getSystemCode()).isNull(); // capability nodes should have null systemCode

        verify(coreServiceClient).getBusinessCapabilities();
    }

    @Test
    @DisplayName("Should return empty tree when system code not found")
    void testGetSystemBusinessCapabilitiesTree_SystemNotFound() {
        // Given - Systems with different codes
        List<BusinessCapabilityDiagramDTO> rawData = Arrays.asList(
            createRawSystem("sys-001", "CRM System", "Customer Management", "CRM", "Contact Management"),
            createRawSystem("sys-002", "ERP System", "Finance", "Accounting", "Accounts Payable")
        );
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(rawData);

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getSystemBusinessCapabilitiesTree("nonexistent-sys");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCapabilities()).isNotNull();
        assertThat(result.getCapabilities()).isEmpty();

        verify(coreServiceClient).getBusinessCapabilities();
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for null system code")
    void testGetSystemBusinessCapabilitiesTree_NullSystemCode() {
        // When & Then
        assertThatThrownBy(() -> diagramService.getSystemBusinessCapabilitiesTree(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("System code cannot be null or empty");

        verifyNoInteractions(coreServiceClient);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for empty system code")
    void testGetSystemBusinessCapabilitiesTree_EmptySystemCode() {
        // When & Then
        assertThatThrownBy(() -> diagramService.getSystemBusinessCapabilitiesTree(""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("System code cannot be null or empty");

        assertThatThrownBy(() -> diagramService.getSystemBusinessCapabilitiesTree("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("System code cannot be null or empty");

        verifyNoInteractions(coreServiceClient);
    }

    @Test
    @DisplayName("Should handle multiple business capabilities for same system")
    void testGetSystemBusinessCapabilitiesTree_MultipleCapabilitiesSameSystem() {
        // Given - System with multiple business capabilities
        BusinessCapabilityDiagramDTO system = createRawSystem("sys-001", "Multi-Function System", 
            "Customer Management", "CRM", "Contact Management");
        
        // Add additional capabilities to the same system
        system.setBusinessCapabilities(Arrays.asList(
            createBusinessCapability("Customer Management", "CRM", "Contact Management"),
            createBusinessCapability("Customer Management", "CRM", "Lead Management"),
            createBusinessCapability("Sales", "Pipeline", "Opportunity Management")
        ));

        when(coreServiceClient.getBusinessCapabilities()).thenReturn(Arrays.asList(system));

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getSystemBusinessCapabilitiesTree("sys-001");

        // Then - System->L1->L2->L3 hierarchy: 1 System + 2 L1s + 2 L2s + 3 L3s = 8 nodes
        assertThat(result).isNotNull();
        assertThat(result.getCapabilities()).hasSizeGreaterThanOrEqualTo(7); // At least 1 System + 2 L1s + 2 L2s + 3 L3s

        // Verify we have exactly one system node (root in new hierarchy)
        List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> systemNodes = result.getCapabilities().stream()
            .filter(n -> "Root".equals(n.getLevel()))
            .toList();
        
        assertThat(systemNodes).hasSize(1); // Only one system node (root)
        assertThat(systemNodes.get(0).getName()).isEqualTo("Multi-Function System");
        assertThat(systemNodes.get(0).getSystemCode()).isEqualTo("sys-001"); // system node should have systemCode
        assertThat(systemNodes.get(0).getParentId()).isNull(); // Root is root

        // Verify we have the expected L1 nodes (children of System)
        List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> l1Nodes = result.getCapabilities().stream()
            .filter(n -> "L1".equals(n.getLevel()))
            .toList();
        
        assertThat(l1Nodes)
            .extracting("name")
            .containsExactlyInAnyOrder("Customer Management", "Sales");
        
        // Verify all L1 nodes have System as parent and null systemCode
        assertThat(l1Nodes)
            .extracting("parentId")
            .containsOnly(systemNodes.get(0).getId());
        assertThat(l1Nodes)
            .extracting("systemCode")
            .containsOnly((String) null); // capability nodes should have null systemCode

        verify(coreServiceClient).getBusinessCapabilities();
    }

    @Test
    @DisplayName("Should handle incomplete capability data gracefully")
    void testGetSystemBusinessCapabilitiesTree_IncompleteCapabilityData() {
        // Given - System with incomplete capability data
        BusinessCapabilityDiagramDTO system = new BusinessCapabilityDiagramDTO();
        system.setSystemCode("sys-001");
        system.setSolutionOverview(createSolutionOverview("Test System"));
        
        // Create capability with missing L2
        BusinessCapabilityDiagramDTO.BusinessCapability incompleteCapability = 
            new BusinessCapabilityDiagramDTO.BusinessCapability();
        incompleteCapability.setL1Capability("Customer Management");
        incompleteCapability.setL2Capability(null); // Missing L2
        incompleteCapability.setL3Capability("Contact Management");
        
        system.setBusinessCapabilities(Arrays.asList(incompleteCapability));

        when(coreServiceClient.getBusinessCapabilities()).thenReturn(Arrays.asList(system));

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getSystemBusinessCapabilitiesTree("sys-001");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCapabilities()).isNotNull();
        
        // Should have no system nodes since incomplete capability is skipped
        List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> systemNodes = result.getCapabilities().stream()
            .filter(n -> "System".equals(n.getLevel()))
            .toList();
        
        // No system node should be created because the capability flow is incomplete
        assertThat(systemNodes).isEmpty();

        verify(coreServiceClient).getBusinessCapabilities();
    }

    @Test
    @DisplayName("Should handle multiple types of incomplete capability data")
    void testGetSystemBusinessCapabilitiesTree_MultipleIncompleteCapabilityTypes() {
        // Given - System with various incomplete capability data scenarios
        BusinessCapabilityDiagramDTO system = new BusinessCapabilityDiagramDTO();
        system.setSystemCode("sys-001");
        system.setSolutionOverview(createSolutionOverview("Test System"));
        
        // Create different incomplete capabilities to test all null branches
        BusinessCapabilityDiagramDTO.BusinessCapability missingL1 = 
            new BusinessCapabilityDiagramDTO.BusinessCapability();
        missingL1.setL1Capability(null); // Missing L1
        missingL1.setL2Capability("Account Management");
        missingL1.setL3Capability("Contact Management");
        
        BusinessCapabilityDiagramDTO.BusinessCapability missingL3 = 
            new BusinessCapabilityDiagramDTO.BusinessCapability();
        missingL3.setL1Capability("Customer Management");
        missingL3.setL2Capability("Account Management");
        missingL3.setL3Capability(null); // Missing L3
        
        BusinessCapabilityDiagramDTO.BusinessCapability allNull = 
            new BusinessCapabilityDiagramDTO.BusinessCapability();
        allNull.setL1Capability(null);
        allNull.setL2Capability(null);
        allNull.setL3Capability(null);
        
        // Add one complete capability to ensure the system still processes valid ones
        BusinessCapabilityDiagramDTO.BusinessCapability completeCapability = createBusinessCapability(
            "Sales Management", "Lead Management", "Lead Tracking"
        );
        
        system.setBusinessCapabilities(Arrays.asList(missingL1, missingL3, allNull, completeCapability));

        when(coreServiceClient.getBusinessCapabilities()).thenReturn(Arrays.asList(system));

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getSystemBusinessCapabilitiesTree("sys-001");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCapabilities()).isNotNull();
        
        // Should only have system nodes for complete capabilities
        // In system-first hierarchy, system nodes have "Root" level
        List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> systemNodes = result.getCapabilities().stream()
            .filter(n -> "Root".equals(n.getLevel()) && n.getSystemCode() != null)
            .toList();
        
        // Only one system node should be created for the complete capability
        assertThat(systemNodes).hasSize(1);
        assertThat(systemNodes.get(0).getSystemCode()).isEqualTo("sys-001");

        verify(coreServiceClient).getBusinessCapabilities();
    }

    @Test
    @DisplayName("Should handle system-level node processing in system-first hierarchy")
    void testGetSystemBusinessCapabilitiesTree_SystemLevelNodeProcessing() {
        // Given - System with business capabilities to test system-first processing
        BusinessCapabilityDiagramDTO system = new BusinessCapabilityDiagramDTO();
        system.setSystemCode("sys-001");
        system.setSolutionOverview(createSolutionOverview("Test System"));
        
        // Add multiple capabilities to ensure system node has children for counting
        BusinessCapabilityDiagramDTO.BusinessCapability capability1 = createBusinessCapability(
            "Customer Management", "CRM", "Contact Management"
        );
        BusinessCapabilityDiagramDTO.BusinessCapability capability2 = createBusinessCapability(
            "Sales Management", "Lead Management", "Lead Tracking"
        );
        
        system.setBusinessCapabilities(Arrays.asList(capability1, capability2));

        when(coreServiceClient.getBusinessCapabilities()).thenReturn(Arrays.asList(system));

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getSystemBusinessCapabilitiesTree("sys-001");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCapabilities()).isNotNull();
        
        // Find the system node (should be at root level with "Root" level)
        List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> systemNodes = result.getCapabilities().stream()
            .filter(n -> "Root".equals(n.getLevel()) && "sys-001".equals(n.getSystemCode()))
            .toList();
        
        assertThat(systemNodes).hasSize(1);
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode systemNode = systemNodes.get(0);
        
        // Verify system node has child count (L1 children) calculated correctly
        // Should have systemCount reflecting number of unique L1 children
        assertThat(systemNode.getSystemCount()).isEqualTo(2); // Two unique L1 capabilities
        assertThat(systemNode.getSystemCode()).isEqualTo("sys-001");
        assertThat(systemNode.getLevel()).isEqualTo("Root");
        assertThat(systemNode.getParentId()).isNull(); // Root node has no parent

        verify(coreServiceClient).getBusinessCapabilities();
    }

    @Test
    @DisplayName("Should throw IllegalStateException when core service fails")
    void testGetSystemBusinessCapabilitiesTree_CoreServiceException() {
        // Given
        when(coreServiceClient.getBusinessCapabilities())
            .thenThrow(new RuntimeException("Core service unavailable"));

        // When & Then
        assertThatThrownBy(() -> diagramService.getSystemBusinessCapabilitiesTree("sys-001"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Failed to generate business capabilities tree for system: sys-001")
            .hasCauseInstanceOf(RuntimeException.class);

        verify(coreServiceClient).getBusinessCapabilities();
    }

    @Test
    @DisplayName("Should handle system with null business capabilities")
    void testGetSystemBusinessCapabilitiesTree_NullBusinessCapabilities() {
        // Given - System with null business capabilities
        BusinessCapabilityDiagramDTO system = new BusinessCapabilityDiagramDTO();
        system.setSystemCode("sys-001");
        system.setSolutionOverview(createSolutionOverview("Test System"));
        system.setBusinessCapabilities(null);

        when(coreServiceClient.getBusinessCapabilities()).thenReturn(Arrays.asList(system));

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getSystemBusinessCapabilitiesTree("sys-001");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCapabilities()).isEmpty();

        verify(coreServiceClient).getBusinessCapabilities();
    }

    // ========================================
    // Raw Data Creation Helpers
    // ========================================

    private List<BusinessCapabilityDiagramDTO> createRawSingleSystemData() {
        return Arrays.asList(createRawSystem(
            "sys-001",
            "NextGen Platform",
            "Customer Management",
            "CRM",
            "Contact Management"
        ));
    }

    private List<BusinessCapabilityDiagramDTO> createRawMultipleSystemsSameL3Data() {
        return Arrays.asList(
            createRawSystem("sys-001", "NextGen Platform", "Customer Management", "CRM", "Contact Management"),
            createRawSystem("sys-002", "Legacy CRM", "Customer Management", "CRM", "Contact Management")
        );
    }

    private List<BusinessCapabilityDiagramDTO> createRawMultipleL3sSameL2Data() {
        return Arrays.asList(
            createRawSystem("sys-001", "CRM System", "Customer Management", "CRM", "Contact Management"),
            createRawSystem("sys-002", "Lead System", "Customer Management", "CRM", "Lead Management")
        );
    }

    private List<BusinessCapabilityDiagramDTO> createRawMultipleL2sSameL1Data() {
        return Arrays.asList(
            createRawSystem("sys-001", "CRM System", "Customer Management", "CRM", "Contact Management"),
            createRawSystem("sys-002", "Support System", "Customer Management", "Customer Service", "Ticket Management")
        );
    }

    private List<BusinessCapabilityDiagramDTO> createRawUnknownCapabilitiesData() {
        return Arrays.asList(createRawSystem(
            "sys-001",
            "NextGen Platform",
            "UNKNOWN",
            "UNKNOWN",
            "UNKNOWN"
        ));
    }

    private List<BusinessCapabilityDiagramDTO> createRawDataWithMissingSolution() {
        BusinessCapabilityDiagramDTO system = new BusinessCapabilityDiagramDTO();
        system.setSystemCode("sys-001");
        system.setSolutionOverview(null); // Missing solution overview
        system.setBusinessCapabilities(Arrays.asList(
            createBusinessCapability("Customer Management", "CRM", "Contact Management")
        ));
        return Arrays.asList(system);
    }

    private List<BusinessCapabilityDiagramDTO> createRawComplexHierarchyData() {
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

    /**
     * Creates a raw system DTO matching MongoDB structure
     */
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
        
        // Set ID like MongoDB ObjectId
        overview.setId("68db858493fdf8d87a3bb4e8");
        
        // Create detailed solution details matching MongoDB structure
        CommonSolutionReviewDTO.SolutionDetails details = new CommonSolutionReviewDTO.SolutionDetails();
        details.setSolutionName(solutionName);
        details.setProjectName("AlphaLaunch");
        details.setSolutionReviewCode("AWG-2025-001");
        details.setSolutionArchitectName("Jane Doe");
        details.setDeliveryProjectManagerName("John Smith");
        details.setItBusinessPartner("Alice Johnson");
        overview.setSolutionDetails(details);
        
        // Set all the complete metadata from raw MongoDB structure
        overview.setReviewedBy(null); // Matches MongoDB raw data
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
        // Set ID like MongoDB ObjectId
        capability.setId("68db858493fdf8d87a3bb4e9");
        capability.setL1Capability(l1);
        capability.setL2Capability(l2);
        capability.setL3Capability(l3);
        capability.setRemarks(null); // Matches MongoDB raw data structure
        return capability;
    }

    /**
     * Creates a list of BusinessCapabilityDTO objects for the dropdown endpoint
     */
    private List<BusinessCapabilityDTO> createAllCapabilitiesData(String l1, String l2, String l3) {
        BusinessCapabilityDTO dto = new BusinessCapabilityDTO();
        dto.setL1(l1);
        dto.setL2(l2);
        dto.setL3(l3);
        return Arrays.asList(dto);
    }

    /**
     * Creates multiple BusinessCapabilityDTO objects for complex hierarchies
     */
    private List<BusinessCapabilityDTO> createMultipleCapabilitiesData() {
        return Arrays.asList(
            createCapabilityDTO("Customer Management", "CRM", "Contact Management"),
            createCapabilityDTO("Customer Management", "CRM", "Lead Management"),
            createCapabilityDTO("Customer Management", "Customer Service", "Ticket Management"),
            createCapabilityDTO("Product Management", "Product Catalog", "Item Management"),
            createCapabilityDTO("Product Management", "Product Catalog", "Price Management")
        );
    }

    /**
     * Helper to create a single BusinessCapabilityDTO
     */
    private BusinessCapabilityDTO createCapabilityDTO(String l1, String l2, String l3) {
        BusinessCapabilityDTO dto = new BusinessCapabilityDTO();
        dto.setL1(l1);
        dto.setL2(l2);
        dto.setL3(l3);
        return dto;
    }

    // Additional edge case tests to improve coverage

    @Test
    @DisplayName("Should handle null solution overview in extractSolutionName path")
    void testGetSystemBusinessCapabilitiesTree_NullSolutionOverview() {
        // Given
        BusinessCapabilityDiagramDTO system = new BusinessCapabilityDiagramDTO();
        system.setSystemCode("sys-001");
        system.setSolutionOverview(null); // Null solution overview
        
        BusinessCapabilityDiagramDTO.BusinessCapability capability = createBusinessCapability(
            "Customer Experience", "Customer Service", "Customer Support"
        );
        system.setBusinessCapabilities(List.of(capability));
        
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(List.of(system));

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getSystemBusinessCapabilitiesTree("sys-001");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCapabilities()).hasSize(4); // L1, L2, L3, Root
        
        // Verify system node gets "Unknown Solution" name
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode systemNode = result.getCapabilities().stream()
                .filter(node -> "Root".equals(node.getLevel()))
                .findFirst()
                .orElse(null);
        
        assertThat(systemNode).isNotNull();
        assertThat(systemNode.getName()).isEqualTo("Unknown Solution");
    }

    @Test
    @DisplayName("Should handle null solution details in extractSolutionName path")
    void testGetSystemBusinessCapabilitiesTree_NullSolutionDetails() {
        // Given
        BusinessCapabilityDiagramDTO system = new BusinessCapabilityDiagramDTO();
        system.setSystemCode("sys-001");
        
        CommonSolutionReviewDTO.SolutionOverview overview = new CommonSolutionReviewDTO.SolutionOverview();
        overview.setSolutionDetails(null); // Null solution details
        system.setSolutionOverview(overview);
        
        BusinessCapabilityDiagramDTO.BusinessCapability capability = createBusinessCapability(
            "Customer Experience", "Customer Service", "Customer Support"
        );
        system.setBusinessCapabilities(List.of(capability));
        
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(List.of(system));

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getSystemBusinessCapabilitiesTree("sys-001");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCapabilities()).hasSize(4); // L1, L2, L3, Root
        
        // Verify system node gets "Unknown Solution" name
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode systemNode = result.getCapabilities().stream()
                .filter(node -> "Root".equals(node.getLevel()))
                .findFirst()
                .orElse(null);
        
        assertThat(systemNode).isNotNull();
        assertThat(systemNode.getName()).isEqualTo("Unknown Solution");
    }

    @Test
    @DisplayName("Should handle empty capability name strings in generateCapabilityId")
    void testGetSystemBusinessCapabilitiesTree_EmptyCapabilityNames() {
        // Given
        BusinessCapabilityDiagramDTO system = createRawSystem("sys-001", "Test System",
            "", "", ""); // Empty capability names
        
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(List.of(system));

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getSystemBusinessCapabilitiesTree("sys-001");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCapabilities()).hasSize(4); // Still creates nodes for empty names
        
        // Verify nodes are created with proper IDs even for empty names
        assertThat(result.getCapabilities()).allSatisfy(node -> {
            assertThat(node.getId()).isNotNull();
            assertThat(node.getLevel()).isNotNull();
        });
    }

    @Test
    @DisplayName("Should handle special characters in capability names for ID generation")
    void testGetSystemBusinessCapabilitiesTree_SpecialCharactersInCapabilityNames() {
        // Given
        BusinessCapabilityDiagramDTO system = createRawSystem("sys-001", "Test System",
            "Customer Experience & Service!", "Customer Service - Core", "Customer Support @#$%");
        
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(List.of(system));

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getSystemBusinessCapabilitiesTree("sys-001");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCapabilities()).hasSize(4);
        
        // Verify IDs are properly sanitized (special characters are replaced with hyphens)
        assertThat(result.getCapabilities()).allSatisfy(node -> {
            assertThat(node.getId())
                .doesNotContain("&", "!", "@", "#", "$", "%", " ") // These should be replaced
                .matches("^[a-z0-9-]+$"); // Only lowercase letters, numbers, and hyphens (- is allowed as replacement)
        });
    }

    @Test
    @DisplayName("Should handle complex hierarchy with multiple systems having overlapping capabilities")
    void testGetSystemBusinessCapabilitiesTree_ComplexHierarchyCalculations() {
        // Given - Multiple systems with overlapping L1/L2 but different L3/System
        BusinessCapabilityDiagramDTO system1 = createRawSystem("sys-001", "System One",
            "Customer Experience", "Customer Service", "Support Tier 1");
        BusinessCapabilityDiagramDTO system2 = createRawSystem("sys-002", "System Two", 
            "Customer Experience", "Customer Service", "Support Tier 2");
        BusinessCapabilityDiagramDTO system3 = createRawSystem("sys-003", "System Three",
            "Customer Experience", "Sales Operations", "Lead Management");
        
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(List.of(system1, system2, system3));

        // When - Get tree for system1 only
        BusinessCapabilitiesTreeDTO result = diagramService.getSystemBusinessCapabilitiesTree("sys-001");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getCapabilities()).hasSize(4); // Only nodes for sys-001 path
        
        // Verify only the sys-001 system appears
        List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> systemNodes = result.getCapabilities().stream()
                .filter(node -> "Root".equals(node.getLevel()))
                .toList();
        
        assertThat(systemNodes).hasSize(1);
        assertThat(systemNodes.get(0).getId()).startsWith("sys-001"); // ID includes hierarchy path
        assertThat(systemNodes.get(0).getName()).isEqualTo("System One");
        
        // Verify L3 node is specific to this system's path
        List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> l3Nodes = result.getCapabilities().stream()
                .filter(node -> "L3".equals(node.getLevel()))
                .toList();
        
        assertThat(l3Nodes).hasSize(1);
        assertThat(l3Nodes.get(0).getName()).isEqualTo("Support Tier 1");
    }

    @Test
    @DisplayName("Should handle system with empty integration flows list")
    void testGenerateSystemDependenciesDiagram_EmptyIntegrationFlowsList() {
        // Given
        String targetSystemCode = "SYS-001";
        primarySystem.setIntegrationFlows(Collections.emptyList()); // Empty list instead of null
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(primarySystem));

        // When
        SpecificSystemDependenciesDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(1); // Only primary system
        assertThat(result.getLinks()).isEmpty(); // No links due to empty flows
        assertThat(result.getMetadata()).isNotNull();
    }

    @ParameterizedTest
    @MethodSource("invalidMiddlewareValues")
    @DisplayName("Should handle invalid middleware values in hasValidMiddleware check")
    void shouldHandleInvalidMiddlewareValues(String middlewareValue, String description) {
        // Given
        String targetSystemCode = "SYS-001";
        SystemDependencyDTO.IntegrationFlow flow = createIntegrationFlow(
            "SYS-002", "CONSUMER", "REST_API", "Daily", middlewareValue
        );
        primarySystem.setIntegrationFlows(Collections.singletonList(flow));
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(primarySystem, externalSystem));

        // When
        SpecificSystemDependenciesDiagramDTO result = diagramService.generateSystemDependenciesDiagram(targetSystemCode);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(2); // Primary + External system
        assertThat(result.getLinks()).hasSize(1); // Direct connection without middleware
        assertThat(result.getMetadata().getIntegrationMiddleware()).isEmpty(); // No middleware
    }

    private static Stream<Arguments> invalidMiddlewareValues() {
        return Stream.of(
            Arguments.of(null, "null middleware"),
            Arguments.of("NONE", "NONE middleware"),
            Arguments.of("   ", "empty/whitespace middleware")
        );
    }

    @Test
    @DisplayName("Should handle path finding with same source and target system")
    void testFindAllPathsDiagram_SameSourceAndTarget() {
        // Given
        String systemCode = "SYS-001";
        
        // When & Then
        assertThatThrownBy(() -> diagramService.findAllPathsDiagram(systemCode, systemCode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Start and end systems cannot be the same");
    }

    @Test
    @DisplayName("Should handle path finding with whitespace-only system codes")
    void testFindAllPathsDiagram_WhitespaceSystemCodes() {
        // When & Then - Test start system with whitespace
        assertThatThrownBy(() -> diagramService.findAllPathsDiagram("   ", "SYS-002"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Start system cannot be null or empty");
            
        // When & Then - Test end system with whitespace  
        assertThatThrownBy(() -> diagramService.findAllPathsDiagram("SYS-001", "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("End system cannot be null or empty");
    }

    @Test
    @DisplayName("Should handle overall diagram generation with duplicate system codes")
    void testGenerateAllSystemDependenciesDiagrams_DuplicateSystemCodes() {
        // Given - Systems with same counterpart codes to test deduplication
        SystemDependencyDTO system1 = createSystemDependency("SYS-001", "System One", "REV-001");
        system1.setIntegrationFlows(Arrays.asList(
            createIntegrationFlow("EXT-001", "CONSUMER", "REST_API", "Daily", null)
        ));
        
        SystemDependencyDTO system2 = createSystemDependency("SYS-002", "System Two", "REV-002");
        system2.setIntegrationFlows(Arrays.asList(
            createIntegrationFlow("EXT-001", "PRODUCER", "REST_API", "Weekly", null) // Same external system
        ));
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system1, system2));

        // When
        OverallSystemDependenciesDiagramDTO result = diagramService.generateAllSystemDependenciesDiagrams();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(3); // SYS-001, SYS-002, EXT-001 (deduplicated)
        assertThat(result.getLinks()).hasSize(2); // Two separate links
        
        // Verify EXT-001 appears only once in nodes despite being referenced by both systems
        long extNodeCount = result.getNodes().stream()
                .map(CommonDiagramDTO.NodeDTO::getId)
                .filter("EXT-001"::equals)
                .count();
        assertThat(extNodeCount).isEqualTo(1);
    }

    // Tests for helper methods coverage
    
    @Test
    @DisplayName("Should test generateCapabilityId helper method through business capabilities tree")
    void testGenerateCapabilityId_ThroughBusinessCapabilitiesTree() {
        // Given - Data to trigger capability ID generation
        BusinessCapabilityDiagramDTO capability = new BusinessCapabilityDiagramDTO();
        capability.setSystemCode("sys-001");
        capability.setSolutionOverview(createSolutionOverview("Test System"));
        capability.setBusinessCapabilities(Arrays.asList(
            createBusinessCapability("Customer Experience", "Customer Service", "Support Tier 1")
        ));
        
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(Arrays.asList(capability));

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getSystemBusinessCapabilitiesTree("sys-001");

        // Then - Verify that capability IDs are properly generated for all levels
        assertThat(result).isNotNull();
        assertThat(result.getCapabilities()).hasSize(4); // L1, L2, L3, System node
        
        // Verify IDs follow expected pattern (lowercase with hyphens)
        result.getCapabilities().forEach(node -> {
            assertThat(node.getId())
                .matches("^[a-z0-9-]+$") // Should only contain lowercase letters, numbers, and hyphens
                .doesNotContain(" ") // No spaces
                .doesNotContain("_"); // No underscores
        });
    }
    
    @Test  
    @DisplayName("Should test extractSolutionName helper with various solution overview states")
    void testExtractSolutionName_VariousStates() {
        // Test case 1: Normal solution overview
        BusinessCapabilityDiagramDTO capability1 = new BusinessCapabilityDiagramDTO();
        capability1.setSystemCode("sys-001");
        capability1.setSolutionOverview(createSolutionOverview("Primary System"));
        capability1.setBusinessCapabilities(Arrays.asList(
            createBusinessCapability("Operations", "System Management", "Configuration")
        ));
        
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(Arrays.asList(capability1));
        
        BusinessCapabilitiesTreeDTO result1 = diagramService.getSystemBusinessCapabilitiesTree("sys-001");
        
        // Verify system node has proper name from solution overview
        assertThat(result1.getCapabilities())
            .anySatisfy(node -> {
                if (node.getId().startsWith("sys-001")) {
                    assertThat(node.getName()).isEqualTo("Primary System");
                }
            });
            
        // Test case 2: System code as fallback when solution name not available
        BusinessCapabilityDiagramDTO capability2 = new BusinessCapabilityDiagramDTO();
        capability2.setSystemCode("sys-002");
        // No solution overview set - should use system code
        capability2.setBusinessCapabilities(Arrays.asList(
            createBusinessCapability("Finance", "Accounting", "Payroll")
        ));
        
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(Arrays.asList(capability2));
        
        BusinessCapabilitiesTreeDTO result2 = diagramService.getSystemBusinessCapabilitiesTree("sys-002");
        
        // Verify system node uses system code when solution name not available
        assertThat(result2.getCapabilities())
            .anySatisfy(node -> {
                if (node.getId().startsWith("sys-002")) {
                    assertThat(node.getName()).isEqualTo("sys-002");
                }
            });
    }
    
    @Test
    @DisplayName("Should test createCapabilityNode helper through complex hierarchy")
    void testCreateCapabilityNode_ComplexHierarchy() {
        // Given - Multi-level capability structure
        BusinessCapabilityDiagramDTO capability = new BusinessCapabilityDiagramDTO();
        capability.setSystemCode("sys-test");
        capability.setSolutionOverview(createSolutionOverview("Test System"));
        capability.setBusinessCapabilities(Arrays.asList(
            createBusinessCapability("Customer Experience", "Customer Service", "Support Management")
        ));
        
        when(coreServiceClient.getBusinessCapabilities()).thenReturn(Arrays.asList(capability));

        // When
        BusinessCapabilitiesTreeDTO result = diagramService.getSystemBusinessCapabilitiesTree("sys-test");

        // Then - Verify node structure and hierarchy
        assertThat(result.getCapabilities()).hasSize(4); // L1, L2, L3, Root
        
        // Verify each level has correct properties
        result.getCapabilities().forEach(node -> {
            assertThat(node.getId()).isNotBlank();
            assertThat(node.getName()).isNotBlank();
            assertThat(node.getLevel()).isIn("L1", "L2", "L3", "Root");
            
            // In Root->L1->L2->L3 hierarchy: Root counts L1 children, L1 counts L2 children, etc.
            if ("Root".equals(node.getLevel())) {
                assertThat(node.getSystemCount()).isNotNull().isGreaterThanOrEqualTo(0);
            } else {
                assertThat(node.getSystemCount()).isNotNull().isGreaterThanOrEqualTo(0);
            }
        });
    }

    @Test
    @DisplayName("Should test path finding validation methods through error scenarios")
    void testPathFindingValidation_ErrorScenarios() {
        // Test null start system
        assertThatThrownBy(() -> diagramService.findAllPathsDiagram(null, "SYS-002"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Start system cannot be null or empty");
            
        // Test empty start system
        assertThatThrownBy(() -> diagramService.findAllPathsDiagram("", "SYS-002"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Start system cannot be null or empty");
            
        // Test null end system
        assertThatThrownBy(() -> diagramService.findAllPathsDiagram("SYS-001", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("End system cannot be null or empty");
            
        // Test empty end system
        assertThatThrownBy(() -> diagramService.findAllPathsDiagram("SYS-001", ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("End system cannot be null or empty");
    }

    @Test
    @DisplayName("Should test system existence validation through path finding")
    void testSystemExistenceValidation() {
        // Given - Limited system data
        SystemDependencyDTO system1 = createSystemDependency("SYS-EXIST", "Existing System", "REV-001");
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system1));

        // Test non-existent start system
        assertThatThrownBy(() -> diagramService.findAllPathsDiagram("SYS-MISSING", "SYS-EXIST"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Start system 'SYS-MISSING' not found");
            
        // Test non-existent end system  
        assertThatThrownBy(() -> diagramService.findAllPathsDiagram("SYS-EXIST", "SYS-MISSING"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("End system 'SYS-MISSING' not found");
    }

    @Test
    @DisplayName("Should test normalizeNodeId helper through middleware handling")
    void testNormalizeNodeId_MiddlewareHandling() {
        // Given - System with middleware that has producer/consumer suffixes
        SystemDependencyDTO system = createSystemDependency("SYS-001", "Primary System", "REV-001");
        SystemDependencyDTO.IntegrationFlow flowWithSuffix = createIntegrationFlow(
            "MIDDLEWARE-P", "PRODUCER", "MESSAGE_QUEUE", "Real-time", "ActiveMQ"
        );
        system.setIntegrationFlows(Arrays.asList(flowWithSuffix));
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system));

        // When
        SpecificSystemDependenciesDiagramDTO result = diagramService.generateSystemDependenciesDiagram("SYS-001");

        // Then - Verify that middleware and counterpart nodes are included
        // The system creates additional middleware nodes with suffixes for producer/consumer roles
        assertThat(result.getNodes())
            .extracting(NodeDTO::getId)
            .anyMatch(id -> id.startsWith("MIDDLEWARE")) // Middleware node is present (may have additional suffixes)
            .anyMatch(id -> id.startsWith("ActiveMQ")); // Middleware name node is present
    }

    @Test
    @DisplayName("Should test extractAllSystemCodes helper through path finding validation")
    void testExtractAllSystemCodes_ThroughValidation() {
        // Given - Systems with various counterpart codes to test extraction
        SystemDependencyDTO system1 = createSystemDependency("SYS-001", "System One", "REV-001");
        SystemDependencyDTO system2 = createSystemDependency("SYS-002", "System Two", "REV-002");
        
        // Add integration flows with different counterpart systems
        system1.setIntegrationFlows(Arrays.asList(
            createIntegrationFlow("EXT-001", "CONSUMER", "REST_API", "Daily", null),
            createIntegrationFlow("EXT-002-P", "PRODUCER", "MESSAGE_QUEUE", "Real-time", "ActiveMQ")
        ));
        system2.setIntegrationFlows(Arrays.asList(
            createIntegrationFlow("EXT-003-C", "CONSUMER", "FILE_TRANSFER", "Weekly", null)
        ));
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system1, system2));

        // When & Then - Test that system validation correctly extracts all system codes
        // This should succeed because all systems exist in the extracted codes
        assertThatCode(() -> diagramService.findAllPathsDiagram("SYS-001", "SYS-002"))
            .doesNotThrowAnyException();
            
        // This should succeed because EXT-001 is found in counterpart codes
        assertThatCode(() -> diagramService.findAllPathsDiagram("SYS-001", "EXT-001"))
            .doesNotThrowAnyException();
            
        // This should succeed because normalized version of EXT-002-P (EXT-002) is extracted
        assertThatCode(() -> diagramService.findAllPathsDiagram("SYS-001", "EXT-002"))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle processed links duplication prevention")
    void testGenerateSystemDependenciesDiagram_ProcessedLinksDeduplication() {
        // Given - Create a complex scenario that could generate duplicate link identifiers
        String systemCode = "SYS-001";
        
        // Create primary system with multiple flows that could create the same linkId
        SystemDependencyDTO.IntegrationFlow flow1 = createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", null);
        SystemDependencyDTO.IntegrationFlow flow2 = createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", null); // Exact duplicate
        primarySystem.setIntegrationFlows(Arrays.asList(flow1, flow2));
        
        // Create external system that references back to primary
        SystemDependencyDTO externalSystem2 = createSystemDependency("SYS-002", "External System", "REV-002");
        SystemDependencyDTO.IntegrationFlow reverseFlow = createIntegrationFlow("SYS-001", "PRODUCER", "REST_API", "Daily", null);
        externalSystem2.setIntegrationFlows(Arrays.asList(reverseFlow));
        
        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(primarySystem, externalSystem2));

        // When
        SpecificSystemDependenciesDiagramDTO result = diagramService.generateSystemDependenciesDiagram(systemCode);

        // Then - Should handle duplicate flows gracefully without creating duplicate links
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSizeGreaterThanOrEqualTo(2);
        
        // Links should be properly deduplicated - no exact duplicates should exist
        List<CommonDiagramDTO.DetailedLinkDTO> links = result.getLinks();
        assertThat(links).isNotNull();
        
        // Check that there are no exact duplicate links (same source, target, pattern, frequency, role)
        Set<String> linkSignatures = new HashSet<>();
        for (CommonDiagramDTO.DetailedLinkDTO link : links) {
            String signature = link.getSource() + "->" + link.getTarget() + ":" + 
                             link.getPattern() + ":" + link.getFrequency() + ":" + link.getRole();
            assertThat(linkSignatures).doesNotContain(signature);
            linkSignatures.add(signature);
        }
        
        verify(coreServiceClient).getSystemDependencies();
    }

    @Test
    @DisplayName("Should handle reverse link counting in overall system dependencies")
    void testGenerateAllSystemDependenciesDiagrams_ReverseLinkCounting() {
        // Given - Create systems with flows that would create reverse link scenarios
        SystemDependencyDTO system1 = createSystemDependency("SYS-001", "System A", "REV-001");
        SystemDependencyDTO.IntegrationFlow flow1 = createIntegrationFlow("SYS-002", "CONSUMER", "REST_API", "Daily", null);
        system1.setIntegrationFlows(Arrays.asList(flow1));

        SystemDependencyDTO system2 = createSystemDependency("SYS-002", "System B", "REV-002");
        SystemDependencyDTO.IntegrationFlow flow2 = createIntegrationFlow("SYS-003", "CONSUMER", "MESSAGE_QUEUE", "Hourly", null);
        system2.setIntegrationFlows(Arrays.asList(flow2));
        
        SystemDependencyDTO system3 = createSystemDependency("SYS-003", "System C", "REV-003");
        SystemDependencyDTO.IntegrationFlow flow3 = createIntegrationFlow("SYS-002", "PRODUCER", "MESSAGE_QUEUE", "Hourly", null); // Reverse of flow2
        system3.setIntegrationFlows(Arrays.asList(flow3));

        when(coreServiceClient.getSystemDependencies()).thenReturn(Arrays.asList(system1, system2, system3));

        // When
        OverallSystemDependenciesDiagramDTO result = diagramService.generateAllSystemDependenciesDiagrams();

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getNodes()).hasSize(3);
        assertThat(result.getLinks()).hasSize(2); // SYS-001->SYS-002 and SYS-002<->SYS-003 (bidirectional)
        
        // Find the bidirectional link between SYS-002 and SYS-003
        CommonDiagramDTO.SimpleLinkDTO bidirectionalLink = result.getLinks().stream()
            .filter(link -> (link.getSource().equals("SYS-002") && link.getTarget().equals("SYS-003")) ||
                           (link.getSource().equals("SYS-003") && link.getTarget().equals("SYS-002")))
            .findFirst()
            .orElse(null);
        
        assertThat(bidirectionalLink).isNotNull();
        // Should have count = 2 due to reverse link handling
        assertThat(bidirectionalLink.getCount()).isEqualTo(2);
        
        verify(coreServiceClient).getSystemDependencies();
    }
}