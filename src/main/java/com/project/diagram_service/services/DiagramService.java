package com.project.diagram_service.services;

import com.project.diagram_service.client.CoreServiceClient;
import com.project.diagram_service.dto.SystemDependencyDTO;
import com.project.diagram_service.dto.SystemDiagramDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

@Service
@Slf4j
public class DiagramService {
    
    /** System type for core systems. */
    private static final String CORE_SYSTEM_TYPE = "Core System";
    /** System type for middleware components. */
    private static final String MIDDLEWARE_TYPE = "Middleware";
    /** System type for internal income systems. */
    private static final String INCOME_SYSTEM_TYPE = "IncomeSystem";
    /** System type for external systems. */
    private static final String EXTERNAL_SYSTEM_TYPE = "External";
    /** Criticality level for major systems. */
    private static final String MAJOR_CRITICALITY = "Major";
    /** Criticality level for standard systems. */
    private static final String STANDARD_CRITICALITY = "Standard-2";
    /** Role identifier for producer systems. */
    private static final String PRODUCER_ROLE = "PRODUCER";
    /** Role identifier for consumer systems. */
    private static final String CONSUMER_ROLE = "CONSUMER";
    /** Constant indicating no middleware is used. */
    private static final String NONE_MIDDLEWARE = "NONE";
    /** Suffix for producer node identifiers. */
    private static final String PRODUCER_SUFFIX = "-P";
    /** Suffix for consumer node identifiers. */
    private static final String CONSUMER_SUFFIX = "-C";
    /** File extension for JSON resources. */
    private static final String JSON_EXTENSION = ".json";
    
    private final CoreServiceClient coreServiceClient;
    
    @Autowired
    public DiagramService(CoreServiceClient coreServiceClient) {
        this.coreServiceClient = coreServiceClient;
    }
    
    /**
     * Retrieves all system dependencies from the core service.
     *
     * @return list of system dependencies with solution overviews and integration flows
     * @throws IllegalStateException if the core service call fails
     */
    public List<SystemDependencyDTO> getSystemDependencies() {
        log.info("Calling core service for system dependencies");
        
        List<SystemDependencyDTO> result = coreServiceClient.getSystemDependencies();
        log.info("Retrieved {} system dependencies", result.size());
        return result;
    }
    
    /**
     * Generates a system dependencies diagram showing integration flows and relationships.
     *
     * Creates nodes for systems and middleware with role-based naming:
     * - Core system: no suffix (e.g., "sys-001")
     * - External systems: role suffix (e.g., "sys-002-P" for producer, "sys-003-C" for consumer)
     * - Middleware: role suffix (e.g., "API_GATEWAY-P", "OSB-C")
     *
     * Flow logic based on counterpartSystemRole:
     * - "CONSUMER": target system consumes, source produces
     * - "PRODUCER": target system produces, source consumes
     *
     * @param systemCode the system to generate the diagram for (must not be null or blank)
     * @return diagram with nodes, links, and metadata for D3.js Sankey visualization
     * @throws IllegalArgumentException if system not found or systemCode is invalid
     */
    public SystemDiagramDTO generateSystemDependenciesDiagram(String systemCode) {
        if (systemCode == null || systemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("System code must not be null or blank");
        }
        log.info("Generating system dependencies diagram for system: {}", systemCode);
        
        List<SystemDependencyDTO> allDependencies = getSystemDependencies();
        SystemDependencyDTO primarySystem = findPrimarySystem(systemCode, allDependencies);
        
        DiagramComponents components = initializeDiagramComponents(systemCode, primarySystem);
        processAllIntegrationFlows(systemCode, allDependencies, components);
        
        SystemDiagramDTO.MetadataDTO metadata = buildMetadata(systemCode, primarySystem, components.nodes());
        SystemDiagramDTO diagram = assembleDiagram(components, metadata);
        
        log.info("Generated diagram with {} nodes and {} links for system {}", 
            components.nodes().size(), components.links().size(), systemCode);
        
        return diagram;
    }
    
    /**
     * Record to hold flow direction information.
     */
    private record FlowDirection(String producer, String consumer, String producerSystemCode, String consumerSystemCode) {}
    
    /**
     * Record to hold diagram components during construction.
     */
    private record DiagramComponents(List<SystemDiagramDTO.NodeDTO> nodes, 
                                   List<SystemDiagramDTO.LinkDTO> links, 
                                   Set<String> middleware, 
                                   Set<String> processedLinks) {}
    
    /**
     * Finds the primary system in the dependencies list.
     * 
     * @param systemCode the system code to find
     * @param allDependencies list of all system dependencies
     * @return the primary system
     * @throws IllegalArgumentException if system not found
     */
    private SystemDependencyDTO findPrimarySystem(String systemCode, List<SystemDependencyDTO> allDependencies) {
        return allDependencies.stream()
            .filter(system -> systemCode.equals(system.getSystemCode()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("System not found: " + systemCode));
    }
    
    /**
     * Initializes diagram components with primary system node.
     * 
     * @param systemCode the primary system code
     * @param primarySystem the primary system data
     * @return initialized diagram components
     */
    private DiagramComponents initializeDiagramComponents(String systemCode, SystemDependencyDTO primarySystem) {
        List<SystemDiagramDTO.NodeDTO> nodes = new ArrayList<>();
        nodes.add(createPrimarySystemNode(systemCode, primarySystem));
        
        return new DiagramComponents(
            nodes,
            new ArrayList<>(),
            new HashSet<>(),
            new HashSet<>()
        );
    }
    
    /**
     * Processes all integration flows from all systems.
     * 
     * @param systemCode the target system code
     * @param allDependencies all system dependencies
     * @param components diagram components to populate
     */
    private void processAllIntegrationFlows(String systemCode, 
                                           List<SystemDependencyDTO> allDependencies, 
                                           DiagramComponents components) {
        for (SystemDependencyDTO system : allDependencies) {
            if (system.getIntegrationFlows() != null) {
                processSystemIntegrationFlows(systemCode, system, allDependencies, components);
            }
        }
    }
    
    /**
     * Processes integration flows for a single system.
     * 
     * @param targetSystemCode the target system code
     * @param system the system whose flows to process
     * @param allDependencies all system dependencies
     * @param components diagram components to populate
     */
    private void processSystemIntegrationFlows(String targetSystemCode, 
                                              SystemDependencyDTO system,
                                              List<SystemDependencyDTO> allDependencies,
                                              DiagramComponents components) {
        for (SystemDependencyDTO.IntegrationFlow flow : system.getIntegrationFlows()) {
            if (isFlowRelevant(targetSystemCode, system.getSystemCode(), flow.getCounterpartSystemCode())) {
                processSingleFlow(targetSystemCode, system.getSystemCode(), flow, allDependencies, components);
            }
        }
    }
    
    /**
     * Checks if a flow is relevant to the target system.
     * 
     * @param targetSystemCode the target system code
     * @param currentSystemCode the current system code
     * @param counterpartSystemCode the counterpart system code
     * @return true if flow involves target system
     */
    private static boolean isFlowRelevant(String targetSystemCode, String currentSystemCode, String counterpartSystemCode) {
        return targetSystemCode.equals(currentSystemCode) || targetSystemCode.equals(counterpartSystemCode);
    }
    
    /**
     * Processes a single integration flow.
     * 
     * @param targetSystemCode the target system code
     * @param currentSystemCode the current system code
     * @param flow the integration flow to process
     * @param allDependencies all system dependencies
     * @param components diagram components to populate
     */
    private void processSingleFlow(String targetSystemCode, 
                                 String currentSystemCode,
                                 SystemDependencyDTO.IntegrationFlow flow,
                                 List<SystemDependencyDTO> allDependencies,
                                 DiagramComponents components) {
        FlowDirection flowDirection = determineFlowDirection(targetSystemCode, currentSystemCode, 
            flow.getCounterpartSystemCode(), flow.getCounterpartSystemRole());
        
        String linkId = createLinkIdentifier(currentSystemCode, flowDirection, flow.getIntegrationMethod());
        if (components.processedLinks().contains(linkId)) {
            return;
        }
        components.processedLinks().add(linkId);
        
        addSystemNodeIfNeeded(components.nodes(), flowDirection.producer(), 
            flowDirection.producerSystemCode(), targetSystemCode, allDependencies);
        addSystemNodeIfNeeded(components.nodes(), flowDirection.consumer(), 
            flowDirection.consumerSystemCode(), targetSystemCode, allDependencies);
        
        processFlow(flow, flowDirection, targetSystemCode, components.middleware(), 
            components.nodes(), components.links());
    }
    
    /**
     * Builds metadata for the diagram.
     * 
     * @param systemCode the system code
     * @param primarySystem the primary system data
     * @param nodes the diagram nodes
     * @return the metadata
     */
    private SystemDiagramDTO.MetadataDTO buildMetadata(String systemCode, 
                                                      SystemDependencyDTO primarySystem, 
                                                      List<SystemDiagramDTO.NodeDTO> nodes) {
        SystemDiagramDTO.MetadataDTO metadata = new SystemDiagramDTO.MetadataDTO();
        metadata.setCode(systemCode);
        metadata.setReview(primarySystem.getSolutionOverview().getSolutionDetails().getSolutionReviewCode());
        metadata.setIntegrationMiddleware(extractMiddlewareList(nodes));
        metadata.setGeneratedDate(LocalDate.now());
        return metadata;
    }
    
    /**
     * Extracts middleware node IDs from the nodes list.
     * 
     * @param nodes the diagram nodes
     * @return list of middleware node IDs
     */
    private List<String> extractMiddlewareList(List<SystemDiagramDTO.NodeDTO> nodes) {
        return nodes.stream()
            .filter(node -> MIDDLEWARE_TYPE.equals(node.getType()))
            .map(SystemDiagramDTO.NodeDTO::getId)
            .toList();
    }
    
    /**
     * Assembles the final diagram from components and metadata.
     * 
     * @param components the diagram components
     * @param metadata the diagram metadata
     * @return the complete diagram
     */
    private static SystemDiagramDTO assembleDiagram(DiagramComponents components, SystemDiagramDTO.MetadataDTO metadata) {
        SystemDiagramDTO diagram = new SystemDiagramDTO();
        diagram.setNodes(components.nodes());
        diagram.setLinks(components.links());
        diagram.setMetadata(metadata);
        return diagram;
    }
    
    /**
     * Creates the primary system node.
     */
    private SystemDiagramDTO.NodeDTO createPrimarySystemNode(String systemCode, SystemDependencyDTO primarySystem) {
        SystemDiagramDTO.NodeDTO primaryNode = new SystemDiagramDTO.NodeDTO();
        primaryNode.setId(systemCode);
        primaryNode.setName(primarySystem.getSolutionOverview().getSolutionDetails().getSolutionName());
        primaryNode.setType(CORE_SYSTEM_TYPE);
        primaryNode.setCriticality(MAJOR_CRITICALITY);
        primaryNode.setUrl(systemCode + JSON_EXTENSION);
        return primaryNode;
    }
    
    /**
     * Determines the flow direction between systems.
     */
    private FlowDirection determineFlowDirection(String primarySystemCode, String currentSystemCode, 
                                               String counterpartSystemCode, String counterpartRole) {
        if (primarySystemCode.equals(currentSystemCode)) {
            // Primary system is the current system
            if (CONSUMER_ROLE.equals(counterpartRole)) {
                // counterpart is consumer, primary is producer
                return new FlowDirection(
                    primarySystemCode,
                    counterpartSystemCode + CONSUMER_SUFFIX,
                    primarySystemCode,
                    counterpartSystemCode
                );
            } else {
                // counterpart is producer, primary is consumer
                return new FlowDirection(
                    counterpartSystemCode + PRODUCER_SUFFIX,
                    primarySystemCode,
                    counterpartSystemCode,
                    primarySystemCode
                );
            }
        } else {
            // Primary system is the counterpart system
            if (CONSUMER_ROLE.equals(counterpartRole)) {
                // primary is consumer, current is producer
                return new FlowDirection(
                    currentSystemCode + PRODUCER_SUFFIX,
                    primarySystemCode,
                    currentSystemCode,
                    primarySystemCode
                );
            } else {
                // primary is producer, current is consumer
                return new FlowDirection(
                    primarySystemCode,
                    currentSystemCode + CONSUMER_SUFFIX,
                    primarySystemCode,
                    currentSystemCode
                );
            }
        }
    }
    
    /**
     * Creates a unique link identifier.
     */
    private String createLinkIdentifier(String currentSystemCode, FlowDirection flowDirection, String integrationMethod) {
        return currentSystemCode + ":" + flowDirection.producer() + "->" + flowDirection.consumer() + ":" + integrationMethod;
    }
    
    /**
     * Checks if middleware is valid (not null, not empty, not "NONE").
     */
    private boolean hasValidMiddleware(String middleware) {
        return middleware != null && !middleware.trim().isEmpty() && !NONE_MIDDLEWARE.equalsIgnoreCase(middleware.trim());
    }
    
    /**
     * Determines the middleware node ID based on flow direction.
     */
    private String determineMiddlewareNodeId(String middlewareName, String producer, String primarySystemCode) {
        if (producer.startsWith(primarySystemCode)) {
            // primary system is producer, so we need middleware-C (consumer side)
            return middlewareName + CONSUMER_SUFFIX;
        } else {
            // primary system is consumer, so we need middleware-P (producer side)
            return middlewareName + PRODUCER_SUFFIX;
        }
    }
    
    /**
     * Adds a middleware node if it doesn't already exist.
     * 
     * @param nodes the nodes list to add to
     * @param middlewareNodeId the middleware node ID
     * @param middlewareName the middleware name
     */
    private void addMiddlewareNodeIfNeeded(List<SystemDiagramDTO.NodeDTO> nodes, 
                                         String middlewareNodeId, String middlewareName) {
        if (nodeExists(nodes, middlewareNodeId)) {
            return;
        }
        
        SystemDiagramDTO.NodeDTO middlewareNode = createMiddlewareNode(middlewareNodeId, middlewareName);
        nodes.add(middlewareNode);
    }
    
    /**
     * Creates a middleware node DTO.
     * 
     * @param middlewareNodeId the middleware node ID
     * @param middlewareName the middleware name
     * @return the created middleware node
     */
    private SystemDiagramDTO.NodeDTO createMiddlewareNode(String middlewareNodeId, String middlewareName) {
        SystemDiagramDTO.NodeDTO middlewareNode = new SystemDiagramDTO.NodeDTO();
        middlewareNode.setId(middlewareNodeId);
        middlewareNode.setName(middlewareName);
        middlewareNode.setType(MIDDLEWARE_TYPE);
        middlewareNode.setCriticality(STANDARD_CRITICALITY);
        middlewareNode.setUrl(middlewareName + JSON_EXTENSION);
        return middlewareNode;
    }
    
    /**
     * Creates a link DTO.
     */
    private SystemDiagramDTO.LinkDTO createLink(String source, String target, 
                                               SystemDependencyDTO.IntegrationFlow flow, String role) {
        SystemDiagramDTO.LinkDTO link = new SystemDiagramDTO.LinkDTO();
        link.setSource(source);
        link.setTarget(target);
        link.setPattern(flow.getIntegrationMethod());
        link.setFrequency(flow.getFrequency());
        link.setRole(role);
        return link;
    }
    
    /**
     * Adds a system node if it doesn't already exist and it's not the primary system.
     * 
     * @param nodes the nodes list to add to
     * @param nodeId the ID of the node to add
     * @param systemCode the system code
     * @param primarySystemCode the primary system code
     * @param allDependencies all system dependencies
     */
    private void addSystemNodeIfNeeded(List<SystemDiagramDTO.NodeDTO> nodes, String nodeId, 
                                     String systemCode, String primarySystemCode, 
                                     List<SystemDependencyDTO> allDependencies) {
        if (systemCode.equals(primarySystemCode) || nodeExists(nodes, nodeId)) {
            return;
        }
        
        SystemDependencyDTO systemData = findSystemData(systemCode, allDependencies);
        SystemDiagramDTO.NodeDTO node = createSystemNode(nodeId, systemCode, systemData, allDependencies);
        nodes.add(node);
    }
    
    /**
     * Checks if a node with the given ID already exists.
     * 
     * @param nodes the nodes list
     * @param nodeId the node ID to check
     * @return true if node exists
     */
    private static boolean nodeExists(List<SystemDiagramDTO.NodeDTO> nodes, String nodeId) {
        return nodes.stream().anyMatch(node -> nodeId.equals(node.getId()));
    }
    
    /**
     * Finds system data by system code.
     * 
     * @param systemCode the system code
     * @param allDependencies all system dependencies
     * @return the system data or null if not found
     */
    private static SystemDependencyDTO findSystemData(String systemCode, List<SystemDependencyDTO> allDependencies) {
        return allDependencies.stream()
            .filter(s -> systemCode.equals(s.getSystemCode()))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Creates a system node DTO.
     * 
     * @param nodeId the node ID
     * @param systemCode the system code
     * @param systemData the system data (may be null)
     * @param allDependencies all system dependencies
     * @return the created node
     */
    private SystemDiagramDTO.NodeDTO createSystemNode(String nodeId, String systemCode, 
                                                     SystemDependencyDTO systemData, 
                                                     List<SystemDependencyDTO> allDependencies) {
        SystemDiagramDTO.NodeDTO node = new SystemDiagramDTO.NodeDTO();
        node.setId(nodeId);
        node.setName(systemData != null ? 
            systemData.getSolutionOverview().getSolutionDetails().getSolutionName() : systemCode);
        node.setType(determineSystemType(systemCode, allDependencies));
        node.setCriticality(MAJOR_CRITICALITY);
        node.setUrl(systemCode + JSON_EXTENSION);
        return node;
    }
    
    /**
     * Processes a flow, handling both middleware and direct connections.
     */
    private void processFlow(SystemDependencyDTO.IntegrationFlow flow, FlowDirection flowDirection,
                           String primarySystemCode, Set<String> middleware,
                           List<SystemDiagramDTO.NodeDTO> nodes, List<SystemDiagramDTO.LinkDTO> links) {
        
        // Handle middleware
        if (hasValidMiddleware(flow.getMiddleware())) {
            String middlewareName = flow.getMiddleware();
            middleware.add(middlewareName);
            
            // Determine which middleware node we need based on the flow direction
            String middlewareNodeId = determineMiddlewareNodeId(middlewareName, flowDirection.producer(), primarySystemCode);
            
            // Add the required middleware node
            addMiddlewareNodeIfNeeded(nodes, middlewareNodeId, middlewareName);
            
            // Create middleware links for each flow
            links.add(createLink(flowDirection.producer(), middlewareNodeId, flow, PRODUCER_ROLE));
            links.add(createLink(middlewareNodeId, flowDirection.consumer(), flow, CONSUMER_ROLE));
            
        } else {
            // Direct connection without middleware
            links.add(createLink(flowDirection.producer(), flowDirection.consumer(), flow, flow.getCounterpartSystemRole()));
        }
    }
    
    /**
     * Determines system type based on data availability.
     * 
     * @param systemCode system to check
     * @param allDependencies all known systems
     * @return "IncomeSystem" if system exists in our data, "External" otherwise
     */
    private String determineSystemType(String systemCode, List<SystemDependencyDTO> allDependencies) {
        boolean existsInOurData = allDependencies.stream()
            .anyMatch(system -> system.getSystemCode().equals(systemCode));
        return existsInOurData ? INCOME_SYSTEM_TYPE : EXTERNAL_SYSTEM_TYPE;
    }
}