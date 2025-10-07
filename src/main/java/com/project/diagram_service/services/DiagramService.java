package com.project.diagram_service.services;

import com.project.diagram_service.client.CoreServiceClient;
import com.project.diagram_service.dto.SystemDependencyDTO;
import com.project.diagram_service.dto.SystemDiagramDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Objects;

/**
 * Service responsible for generating system dependency diagrams and finding paths between systems.
 * 
 * This service provides two main functionalities:
 * 1. Generating comprehensive system dependency diagrams showing integration flows
 * 2. Finding all possible paths between two systems for integration analysis
 * 
 * The service uses D3.js Sankey visualization format for diagram output and implements
 * depth-first search algorithms for path finding with loop prevention.
 * 
 * @author DiagramService Team
 * @version 1.0
 * @since 2025-10-07
 */
@Service
@Slf4j
public class DiagramService {
    
    // System Types
    private static final String CORE_SYSTEM_TYPE = "Core System";
    private static final String MIDDLEWARE_TYPE = "Middleware";
    private static final String INCOME_SYSTEM_TYPE = "IncomeSystem";
    private static final String EXTERNAL_SYSTEM_TYPE = "External";
    
    // Criticality Levels
    private static final String MAJOR_CRITICALITY = "Major";
    private static final String STANDARD_CRITICALITY = "Standard-2";
    
    // System Roles
    private static final String PRODUCER_ROLE = "PRODUCER";
    private static final String CONSUMER_ROLE = "CONSUMER";
    
    // Middleware Constants
    private static final String NONE_MIDDLEWARE = "NONE";
    
    // Node Suffixes
    private static final String PRODUCER_SUFFIX = "-P";
    private static final String CONSUMER_SUFFIX = "-C";
    
    // File Extensions
    private static final String JSON_EXTENSION = ".json";
    
    // Path Finding Constants
    private static final String PATH_SEPARATOR = " → ";
    private static final String NO_PATHS_MESSAGE = "No paths found";
    private static final String SINGLE_PATH_FORMAT = "%d path found";
    private static final String MULTIPLE_PATHS_FORMAT = "%d paths found";
    
    private final CoreServiceClient coreServiceClient;
    
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
     * Finds all paths from startSystem to endSystem and returns them in diagram format.
     *
     * This method performs comprehensive path finding analysis to discover all possible
     * integration routes between two systems. It uses depth-first search with loop
     * prevention to identify both direct connections and multi-hop paths through
     * intermediate systems and middleware components.
     *
     * The algorithm:
     * 1. Builds a directed graph of all integration flows
     * 2. Uses DFS with visited set to prevent infinite loops
     * 3. Explores all possible paths from source to target
     * 4. Handles middleware as intermediate nodes in the path
     * 5. Converts discovered paths to Sankey diagram format
     *
     * @param startSystem the source system code to start path finding from
     * @param endSystem the target system code to find paths to
     * @return diagram with all discovered paths visualized as nodes and links
     * @throws IllegalArgumentException if either system code is invalid, systems are the same, or systems not found
     */
    public SystemDiagramDTO findAllPathsDiagram(String startSystem, String endSystem) {
        long startTime = System.currentTimeMillis();
        
        validatePathFindingInput(startSystem, endSystem);
        
        log.info("Finding all paths from {} to {}", startSystem, endSystem);
        
        // Get all system dependencies
        List<SystemDependencyDTO> allDependencies = getSystemDependencies();
        
        // Validate systems exist
        validateSystemsExist(startSystem, endSystem, allDependencies);
        
        // Build the integration graph
        IntegrationGraph graph = buildIntegrationGraph(allDependencies);
        
        // Find all paths using DFS with loop prevention
        List<Path> paths = findPaths(graph, startSystem, endSystem);
        
        log.info("Found {} paths from {} to {} in {}ms", 
            paths.size(), startSystem, endSystem, System.currentTimeMillis() - startTime);
        
        // Convert paths to diagram format
        SystemDiagramDTO diagram = convertPathsToDiagram(paths, startSystem, endSystem, allDependencies);
        
        return diagram;
    }
    
    /**
     * Record to hold flow direction information.
     */
    private record FlowDirection(String producer, String consumer, String producerSystemCode, String consumerSystemCode) {}
    
    // Record types for path finding algorithm
    
    /**
     * Represents a node in the integration graph with its connections.
     */
    record IntegrationGraph(Map<String, Set<EdgeDetails>> adjacencyMap) {}
    
    /**
     * Represents an edge in the integration graph with metadata.
     * Includes original flow data for accurate visualization.
     */
    record EdgeDetails(String target, String middleware, SystemDependencyDTO.IntegrationFlow originalFlow) {}
    
    /**
     * Represents a segment of a path with source, target and connection details.
     * Includes original flow data for accurate visualization.
     */
    record PathSegment(String source, String target, String middleware, SystemDependencyDTO.IntegrationFlow originalFlow) {}
    
    /**
     * Represents a complete path from source to destination.
     */
    record Path(List<PathSegment> segments) {}
    
    // Validation methods for path finding
    
    /**
     * Validates input parameters for path finding operations.
     * 
     * @param startSystem the source system code
     * @param endSystem the target system code
     * @throws IllegalArgumentException if validation fails
     */
    private void validatePathFindingInput(String startSystem, String endSystem) {
        validateSystemCode(startSystem, "Start system");
        validateSystemCode(endSystem, "End system");
        validateSystemsAreDifferent(startSystem, endSystem);
    }
    
    /**
     * Validates that a system code is not null or empty.
     * 
     * @param systemCode the system code to validate
     * @param fieldName the name of the field for error messages
     * @throws IllegalArgumentException if system code is invalid
     */
    private void validateSystemCode(String systemCode, String fieldName) {
        if (systemCode == null || systemCode.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or empty");
        }
    }
    
    /**
     * Validates that start and end systems are different.
     * 
     * @param startSystem the source system
     * @param endSystem the target system
     * @throws IllegalArgumentException if systems are the same
     */
    private void validateSystemsAreDifferent(String startSystem, String endSystem) {
        if (startSystem.equals(endSystem)) {
            throw new IllegalArgumentException("Start and end systems cannot be the same");
        }
    }
    
    /**
     * Validates that both systems exist in the dependency data.
     * 
     * @param startSystem the source system to validate
     * @param endSystem the target system to validate 
     * @param allDependencies the list of all system dependencies
     * @throws IllegalArgumentException if either system is not found
     */
    private void validateSystemsExist(String startSystem, String endSystem, List<SystemDependencyDTO> allDependencies) {
        Set<String> allSystems = extractAllSystemCodes(allDependencies);
        
        validateSystemExists(startSystem, allSystems, "Start system");
        validateSystemExists(endSystem, allSystems, "End system");
    }
    
    /**
     * Extracts all system codes from dependencies including counterpart systems.
     * 
     * @param dependencies the list of system dependencies
     * @return set of all system codes found
     */
    private Set<String> extractAllSystemCodes(List<SystemDependencyDTO> dependencies) {
        return dependencies.stream()
            .flatMap(this::extractSystemCodesFromDependency)
            .collect(Collectors.toSet());
    }
    
    /**
     * Extracts system codes from a single dependency including integration flows.
     * 
     * @param dependency the system dependency to process
     * @return stream of system codes
     */
    private Stream<String> extractSystemCodesFromDependency(SystemDependencyDTO dependency) {
        Set<String> systems = new HashSet<>();
        systems.add(dependency.getSystemCode());
        
        if (dependency.getIntegrationFlows() != null) {
            dependency.getIntegrationFlows().forEach(flow -> {
                String normalizedCounterpart = normalizeNodeId(flow.getCounterpartSystemCode());
                systems.add(normalizedCounterpart);
                systems.add(flow.getCounterpartSystemCode());
            });
        }
        
        return systems.stream();
    }
    
    /**
     * Validates that a specific system exists in the system set.
     * 
     * @param systemCode the system code to check
     * @param allSystems the set of all available systems
     * @param systemType the type of system for error messaging
     * @throws IllegalArgumentException if system is not found
     */
    private void validateSystemExists(String systemCode, Set<String> allSystems, String systemType) {
        if (!allSystems.contains(systemCode)) {
            throw new IllegalArgumentException(systemType + " '" + systemCode + "' not found");
        }
    }
    
    // Graph building methods
    
    /**
     * Normalizes node IDs by removing -P/-C suffixes for middleware nodes.
     * This allows middleware to act as both producers and consumers in paths.
     */
    private String normalizeNodeId(String nodeId) {
        if (nodeId != null && (nodeId.endsWith("-P") || nodeId.endsWith("-C"))) {
            return nodeId.substring(0, nodeId.length() - 2);
        }
        return nodeId;
    }
    
    /**
     * Builds a directed graph from system dependencies.
     * Each integration flow represents a specific point-to-point connection.
     * Middleware is just the transport mechanism, not a routing hub.
     */
    /**
     * Builds a directed graph from system dependencies for path finding.
     * 
     * @param dependencies the list of system dependencies
     * @return the integration graph
     */
    private IntegrationGraph buildIntegrationGraph(List<SystemDependencyDTO> dependencies) {
        Map<String, Set<EdgeDetails>> adjacencyMap = new HashMap<>();
        
        for (SystemDependencyDTO dependency : dependencies) {
            if (dependency.getIntegrationFlows() != null) {
                processIntegrationFlowsForGraph(dependency, adjacencyMap);
            }
        }
        
        return new IntegrationGraph(adjacencyMap);
    }
    
    /**
     * Processes integration flows for a single system dependency.
     * 
     * @param dependency the system dependency to process
     * @param adjacencyMap the graph adjacency map to populate
     */
    private void processIntegrationFlowsForGraph(SystemDependencyDTO dependency, 
                                                Map<String, Set<EdgeDetails>> adjacencyMap) {
        String currentSystem = dependency.getSystemCode();
        
        for (SystemDependencyDTO.IntegrationFlow flow : dependency.getIntegrationFlows()) {
            ProducerConsumerPair pair = determineProducerConsumer(currentSystem, flow);
            if (pair != null) {
                addEdgeToGraph(adjacencyMap, pair.producer(), pair.consumer(), flow);
            }
        }
    }
    
    /**
     * Determines producer and consumer from flow information.
     * 
     * @param currentSystem the current system code
     * @param flow the integration flow
     * @return producer-consumer pair or null if role is unclear
     */
    private ProducerConsumerPair determineProducerConsumer(String currentSystem, 
                                                          SystemDependencyDTO.IntegrationFlow flow) {
        String counterpartSystem = normalizeNodeId(flow.getCounterpartSystemCode());
        String counterpartRole = flow.getCounterpartSystemRole();
        
        if (PRODUCER_ROLE.equals(counterpartRole)) {
            return new ProducerConsumerPair(counterpartSystem, currentSystem);
        } else if (CONSUMER_ROLE.equals(counterpartRole)) {
            return new ProducerConsumerPair(currentSystem, counterpartSystem);
        }
        
        return null; // Skip if role is unclear
    }
    
    /**
     * Adds an edge to the integration graph.
     * 
     * @param adjacencyMap the graph adjacency map
     * @param producer the producer system
     * @param consumer the consumer system
     * @param flow the integration flow
     */
    private void addEdgeToGraph(Map<String, Set<EdgeDetails>> adjacencyMap, 
                               String producer, String consumer, 
                               SystemDependencyDTO.IntegrationFlow flow) {
        String middleware = hasValidMiddleware(flow.getMiddleware()) 
            ? normalizeNodeId(flow.getMiddleware()) 
            : null;
            
        adjacencyMap.computeIfAbsent(producer, k -> new HashSet<>())
            .add(new EdgeDetails(consumer, middleware, flow));
    }
    
    /**
     * Record to hold producer-consumer pair information.
     */
    private record ProducerConsumerPair(String producer, String consumer) {}
    
    
    // Path finding methods
    
    /**
     * Finds all paths between two systems using depth-first search.
     */
    private List<Path> findPaths(IntegrationGraph graph, String startSystem, String endSystem) {
        List<Path> allPaths = new ArrayList<>();
        List<PathSegment> currentPath = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        
        findPathsDFS(graph, startSystem, endSystem, currentPath, visited, allPaths);
        
        return allPaths;
    }
    
    /**
     * Recursive DFS implementation for path finding with loop prevention.
     */
    private void findPathsDFS(IntegrationGraph graph, String current, String target, 
                             List<PathSegment> currentPath, Set<String> visited, 
                             List<Path> allPaths) {
        if (current.equals(target)) {
            allPaths.add(new Path(new ArrayList<>(currentPath)));
            return;
        }
        
        visited.add(current);
        
        Set<EdgeDetails> neighbors = graph.adjacencyMap().getOrDefault(current, Set.of());
        for (EdgeDetails edge : neighbors) {
            if (!visited.contains(edge.target())) {
                currentPath.add(new PathSegment(current, edge.target(), edge.middleware(), edge.originalFlow()));
                findPathsDFS(graph, edge.target(), target, currentPath, visited, allPaths);
                currentPath.remove(currentPath.size() - 1);
            }
        }
        
        visited.remove(current);
    }
    
    // Diagram conversion methods
    
    /**
     * Converts discovered paths to SystemDiagramDTO format for visualization.
     * 
     * @param paths the list of discovered paths
     * @param startSystem the source system
     * @param endSystem the target system
     * @param allDependencies all system dependencies
     * @return the complete diagram DTO
     */
    private SystemDiagramDTO convertPathsToDiagram(List<Path> paths, String startSystem, 
                                                  String endSystem, List<SystemDependencyDTO> allDependencies) {
        if (paths.isEmpty()) {
            return createEmptyPathDiagram(startSystem, endSystem);
        }
        
        DiagramComponents components = buildPathDiagramComponents(paths, allDependencies);
        SystemDiagramDTO.MetadataDTO metadata = createPathMetadata(startSystem, endSystem, paths.size(), components.middleware());
        
        return assembleDiagram(components, metadata);
    }
    
    /**
     * Creates an empty diagram when no paths are found.
     * 
     * @param startSystem the source system
     * @param endSystem the target system
     * @return empty diagram with appropriate metadata
     */
    private SystemDiagramDTO createEmptyPathDiagram(String startSystem, String endSystem) {
        log.warn("No paths found from {} to {}", startSystem, endSystem);
        
        SystemDiagramDTO diagram = new SystemDiagramDTO();
        diagram.setNodes(List.of());
        diagram.setLinks(List.of());
        diagram.setMetadata(createPathMetadata(startSystem, endSystem, 0, Set.of()));
        
        return diagram;
    }
    
    /**
     * Creates metadata for path finding results.
     * 
     * @param startSystem the source system
     * @param endSystem the target system
     * @param pathCount the number of paths found
     * @param middleware the set of middleware components used
     * @return the metadata DTO
     */
    private SystemDiagramDTO.MetadataDTO createPathMetadata(String startSystem, String endSystem, 
                                                           int pathCount, Set<String> middleware) {
        SystemDiagramDTO.MetadataDTO metadata = new SystemDiagramDTO.MetadataDTO();
        metadata.setCode(startSystem + PATH_SEPARATOR + endSystem);
        metadata.setReview(formatPathCount(pathCount));
        metadata.setIntegrationMiddleware(new ArrayList<>(middleware));
        metadata.setGeneratedDate(LocalDate.now());
        
        return metadata;
    }
    
    /**
     * Formats the path count message for display.
     * 
     * @param pathCount the number of paths found
     * @return formatted message
     */
    private String formatPathCount(int pathCount) {
        if (pathCount == 0) {
            return NO_PATHS_MESSAGE;
        }
        return pathCount == 1 
            ? String.format(SINGLE_PATH_FORMAT, pathCount)
            : String.format(MULTIPLE_PATHS_FORMAT, pathCount);
    }
    
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
    
    // Path finding diagram helper methods
    
    /**
     * Builds diagram components for path visualization.
     * 
     * @param paths the discovered paths
     * @param allDependencies all system dependencies
     * @return diagram components
     */
    private DiagramComponents buildPathDiagramComponents(List<Path> paths, 
                                                        List<SystemDependencyDTO> allDependencies) {
        Set<String> allNodesInPaths = new HashSet<>();
        Set<String> middlewareNames = new HashSet<>();
        List<SystemDiagramDTO.LinkDTO> links = new ArrayList<>();
        
        // Process all path segments to extract nodes and create links
        for (Path path : paths) {
            processPathSegments(path, allNodesInPaths, middlewareNames, links);
        }
        
        // Create nodes for all systems and middleware found in paths
        List<SystemDiagramDTO.NodeDTO> nodes = createNodesForPaths(allNodesInPaths, middlewareNames, allDependencies);
        
        return new DiagramComponents(nodes, links, middlewareNames, new HashSet<>());
    }
    
    /**
     * Processes all segments in a path to extract nodes and create links.
     * 
     * @param path the path to process
     * @param allNodesInPaths set to collect all node IDs
     * @param middlewareNames set to collect middleware names
     * @param links list to collect created links
     */
    private void processPathSegments(Path path, Set<String> allNodesInPaths, 
                                   Set<String> middlewareNames, List<SystemDiagramDTO.LinkDTO> links) {
        for (PathSegment segment : path.segments()) {
            processSinglePathSegment(segment, allNodesInPaths, middlewareNames, links);
        }
    }
    
    /**
     * Processes a single path segment to extract nodes and create links.
     * 
     * @param segment the path segment to process
     * @param allNodesInPaths set to collect all node IDs
     * @param middlewareNames set to collect middleware names
     * @param links list to collect created links
     */
    private void processSinglePathSegment(PathSegment segment, Set<String> allNodesInPaths,
                                        Set<String> middlewareNames, List<SystemDiagramDTO.LinkDTO> links) {
        String source = segment.source();
        String target = segment.target();
        String middleware = segment.middleware();
        SystemDependencyDTO.IntegrationFlow originalFlow = segment.originalFlow();
        
        allNodesInPaths.add(source);
        allNodesInPaths.add(target);
        
        if (hasValidMiddleware(middleware)) {
            processMiddlewareSegment(source, target, middleware, originalFlow, 
                                   allNodesInPaths, middlewareNames, links);
        } else {
            processDirectSegment(source, target, originalFlow, links);
        }
    }
    
    /**
     * Processes a path segment that uses middleware.
     * 
     * @param source the source system
     * @param target the target system
     * @param middleware the middleware component
     * @param originalFlow the original integration flow
     * @param allNodesInPaths set to collect all node IDs
     * @param middlewareNames set to collect middleware names
     * @param links list to collect created links
     */
    private void processMiddlewareSegment(String source, String target, String middleware,
                                        SystemDependencyDTO.IntegrationFlow originalFlow,
                                        Set<String> allNodesInPaths, Set<String> middlewareNames,
                                        List<SystemDiagramDTO.LinkDTO> links) {
        String normalizedMiddleware = normalizeNodeId(middleware);
        allNodesInPaths.add(normalizedMiddleware);
        middlewareNames.add(normalizedMiddleware);
        
        RolePair roles = determineSegmentRoles(source, originalFlow);
        
        // Create two links: source -> middleware -> target
        links.add(createPathLink(source, normalizedMiddleware, originalFlow, roles.sourceRole()));
        links.add(createPathLink(normalizedMiddleware, target, originalFlow, roles.targetRole()));
    }
    
    /**
     * Processes a direct path segment without middleware.
     * 
     * @param source the source system
     * @param target the target system
     * @param originalFlow the original integration flow
     * @param links list to collect created links
     */
    private void processDirectSegment(String source, String target,
                                    SystemDependencyDTO.IntegrationFlow originalFlow,
                                    List<SystemDiagramDTO.LinkDTO> links) {
        links.add(createPathLink(source, target, originalFlow, originalFlow.getCounterpartSystemRole()));
    }
    
    /**
     * Determines roles for source and target in a segment.
     * 
     * @param source the source system
     * @param originalFlow the original integration flow
     * @return role pair for source and target
     */
    private RolePair determineSegmentRoles(String source, SystemDependencyDTO.IntegrationFlow originalFlow) {
        String counterpartRole = originalFlow.getCounterpartSystemRole();
        String counterpartSystem = normalizeNodeId(originalFlow.getCounterpartSystemCode());
        
        if (PRODUCER_ROLE.equals(counterpartRole)) {
            if (source.equals(counterpartSystem)) {
                return new RolePair(PRODUCER_ROLE, CONSUMER_ROLE);
            } else {
                return new RolePair(CONSUMER_ROLE, PRODUCER_ROLE);
            }
        } else {
            if (source.equals(counterpartSystem)) {
                return new RolePair(CONSUMER_ROLE, PRODUCER_ROLE);
            } else {
                return new RolePair(PRODUCER_ROLE, CONSUMER_ROLE);
            }
        }
    }
    
    /**
     * Creates a link DTO for path visualization.
     * 
     * @param source the source node
     * @param target the target node
     * @param flow the integration flow
     * @param role the role for this link
     * @return the created link DTO
     */
    private SystemDiagramDTO.LinkDTO createPathLink(String source, String target,
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
     * Creates nodes for all systems and middleware found in paths.
     * 
     * @param allNodesInPaths all node IDs found in paths
     * @param middlewareNames middleware component names
     * @param allDependencies all system dependencies
     * @return list of created nodes
     */
    private List<SystemDiagramDTO.NodeDTO> createNodesForPaths(Set<String> allNodesInPaths,
                                                              Set<String> middlewareNames,
                                                              List<SystemDependencyDTO> allDependencies) {
        List<SystemDiagramDTO.NodeDTO> nodes = new ArrayList<>();
        
        for (String nodeId : allNodesInPaths) {
            if (middlewareNames.contains(nodeId)) {
                nodes.add(createMiddlewareNode(nodeId, nodeId));
            } else {
                nodes.add(createSystemNodeForPath(nodeId, allDependencies));
            }
        }
        
        return nodes;
    }
    
    /**
     * Creates a system node for path visualization.
     * 
     * @param nodeId the node ID
     * @param allDependencies all system dependencies
     * @return the created system node
     */
    private SystemDiagramDTO.NodeDTO createSystemNodeForPath(String nodeId, List<SystemDependencyDTO> allDependencies) {
        SystemDiagramDTO.NodeDTO node = new SystemDiagramDTO.NodeDTO();
        node.setId(nodeId);
        
        String systemName = findSystemNameFromDependencies(nodeId, allDependencies);
        node.setName(systemName);
        node.setType(determineSystemType(nodeId, allDependencies));
        node.setCriticality(MAJOR_CRITICALITY);
        node.setUrl(nodeId + JSON_EXTENSION);
        
        return node;
    }
    
    /**
     * Finds system name from dependencies or returns nodeId if not found.
     * 
     * @param nodeId the node ID to find name for
     * @param allDependencies all system dependencies
     * @return system name or nodeId as fallback
     */
    private String findSystemNameFromDependencies(String nodeId, List<SystemDependencyDTO> allDependencies) {
        return allDependencies.stream()
            .filter(dep -> dep.getSystemCode().equals(nodeId))
            .map(dep -> dep.getSolutionOverview())
            .filter(Objects::nonNull)
            .map(overview -> overview.getSolutionDetails())
            .filter(Objects::nonNull)
            .map(details -> details.getSolutionName())
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(nodeId);
    }
    
    /**
     * Record to hold role pair information.
     */
    private record RolePair(String sourceRole, String targetRole) {}
    
}