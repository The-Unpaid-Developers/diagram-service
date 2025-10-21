package com.project.diagram_service.services;

import com.project.diagram_service.client.CoreServiceClient;
import com.project.diagram_service.dto.SystemDependencyDTO;
import com.project.diagram_service.dto.BusinessCapabilityDiagramDTO;
import com.project.diagram_service.dto.BusinessCapabilitiesTreeDTO;
import com.project.diagram_service.dto.SpecificSystemDependenciesDiagramDTO;
import com.project.diagram_service.dto.OverallSystemDependenciesDiagramDTO;
import com.project.diagram_service.dto.PathDiagramDTO;
import com.project.diagram_service.dto.CommonDiagramDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.Objects;

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

    // ID Separator Constants
    private static final String UNDER_SEPARATOR = "-under-";

    private final CoreServiceClient coreServiceClient;

    public DiagramService(CoreServiceClient coreServiceClient) {
        this.coreServiceClient = coreServiceClient;
    }

    /**
     * Retrieves all system dependencies from the core service.
     *
     * @return list of system dependencies with solution overviews and integration
     *         flows
     * @throws IllegalStateException if the core service call fails
     */
    public List<SystemDependencyDTO> getSystemDependencies() {
        log.info("Calling core service for system dependencies");

        List<SystemDependencyDTO> result = coreServiceClient.getSystemDependencies();
        log.info("Retrieved {} system dependencies", result.size());
        return result;
    }

    /**
     * Retrieves all business capability solution reviews from the core service.
     *
     * @return list of business capability solution reviews with system codes, 
     *         solution overviews, and business capabilities
     * @throws IllegalStateException if the core service call fails
     */
    public List<BusinessCapabilityDiagramDTO> getBusinessCapabilities() {
        log.info("Calling core service for business capabilities");

        List<BusinessCapabilityDiagramDTO> result = coreServiceClient.getBusinessCapabilities();
        log.info("Retrieved {} business capability solution reviews", result.size());
        return result;
    }

    /**
     * Generates a hierarchical tree structure of business capabilities for D3.js visualization.
     * 
     * This method creates a tree structure with L1, L2, L3 capability levels and system nodes
     * based on actual data from the core service. Each capability level includes a count of
     * associated systems, and system nodes include detailed metadata. The structure uses
     * parent-child relationships via parentId for D3.js tree rendering.
     *
     * @return BusinessCapabilitiesTreeDTO containing the hierarchical tree structure
     * @throws IllegalStateException if the core service call fails
     */
    public BusinessCapabilitiesTreeDTO getBusinessCapabilitiesTree() {
        log.info("Generating business capabilities tree structure");

        try {
            // Get existing business capabilities from core service
            List<BusinessCapabilityDiagramDTO> capabilities = coreServiceClient.getBusinessCapabilities();
            
            BusinessCapabilitiesTreeDTO tree = new BusinessCapabilitiesTreeDTO();
            List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> nodes = new ArrayList<>();

            // Track unique nodes to avoid duplicates (using full path as key)
            Map<String, BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> uniqueNodes = new HashMap<>();
            
            // Process each system and each of its business capability flows
            for (BusinessCapabilityDiagramDTO system : capabilities) {
                if (system.getBusinessCapabilities() != null) {
                    for (BusinessCapabilityDiagramDTO.BusinessCapability bizCap : system.getBusinessCapabilities()) {
                        // Each business capability represents one complete flow: L1 -> L2 -> L3 -> System
                        processCapabilityFlow(system, bizCap, uniqueNodes);
                    }
                }
            }
            
            // Convert unique nodes map to list and calculate system counts
            nodes.addAll(uniqueNodes.values());
            calculateSystemCounts(nodes);

            tree.setCapabilities(nodes);
            log.info("Generated business capabilities tree with {} nodes from {} capability flows", 
                     nodes.size(), countTotalFlows(capabilities));
            return tree;

        } catch (Exception e) {
            log.error("Error generating business capabilities tree: {}", e.getMessage());
            throw new IllegalStateException("Failed to generate business capabilities tree", e);
        }
    }

    /**
     * Generates a business capabilities tree filtered by a specific system code.
     * Returns only the hierarchy path from L1 → L2 → L3 → System for the given system.
     * 
     * @param systemCode The specific system code to filter capabilities for
     * @return BusinessCapabilitiesTreeDTO containing the filtered capabilities tree
     * @throws IllegalArgumentException if systemCode is null or empty
     * @throws IllegalStateException if there's an error generating the filtered tree
     */
    public BusinessCapabilitiesTreeDTO getSystemBusinessCapabilitiesTree(String systemCode) {
        log.info("Generating business capabilities tree for system: {}", systemCode);
        
        if (systemCode == null || systemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("System code cannot be null or empty");
        }
        
        try {
            // Get existing business capabilities from core service
            List<BusinessCapabilityDiagramDTO> capabilities = coreServiceClient.getBusinessCapabilities();
            
            // Filter capabilities for the specific system
            List<BusinessCapabilityDiagramDTO> systemCapabilities = capabilities.stream()
                    .filter(capability -> systemCode.equals(capability.getSystemCode()))
                    .toList();
            
            if (systemCapabilities.isEmpty()) {
                log.warn("No business capabilities found for system: {}", systemCode);
                BusinessCapabilitiesTreeDTO emptyTree = new BusinessCapabilitiesTreeDTO();
                emptyTree.setCapabilities(new ArrayList<>());
                return emptyTree;
            }
            
            log.debug("Found {} capabilities for system: {}", systemCapabilities.size(), systemCode);
            
            BusinessCapabilitiesTreeDTO tree = new BusinessCapabilitiesTreeDTO();
            List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> nodes = new ArrayList<>();

            // Track unique nodes to avoid duplicates (using full path as key)
            Map<String, BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> uniqueNodes = new HashMap<>();
            
            // Process each system and each of its business capability flows
            for (BusinessCapabilityDiagramDTO system : systemCapabilities) {
                if (system.getBusinessCapabilities() != null) {
                    for (BusinessCapabilityDiagramDTO.BusinessCapability bizCap : system.getBusinessCapabilities()) {
                        // Each business capability represents one complete flow: L1 -> L2 -> L3 -> System
                        processCapabilityFlow(system, bizCap, uniqueNodes);
                    }
                }
            }
            
            // Convert unique nodes map to list and calculate system counts
            nodes.addAll(uniqueNodes.values());
            calculateSystemCounts(nodes);

            tree.setCapabilities(nodes);
            log.info("Generated business capabilities tree for system: {} with {} nodes from {} capability flows", 
                     systemCode, nodes.size(), countTotalFlows(systemCapabilities));
            
            return tree;
            
        } catch (Exception e) {
            log.error("Error generating business capabilities tree for system {}: {}", systemCode, e.getMessage());
            throw new IllegalStateException("Failed to generate business capabilities tree for system: " + systemCode, e);
        }
    }

    /**
     * Processes a single business capability flow from L1 -> L2 -> L3 -> System.
     * Creates nodes for each level if they don't already exist, ensuring proper parent-child relationships.
     */
    private void processCapabilityFlow(BusinessCapabilityDiagramDTO system, 
                                       BusinessCapabilityDiagramDTO.BusinessCapability bizCap,
                                       Map<String, BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> uniqueNodes) {
        
        String l1 = bizCap.getL1Capability();
        String l2 = bizCap.getL2Capability();
        String l3 = bizCap.getL3Capability();
        
        if (l1 == null || l2 == null || l3 == null) {
            log.warn("Incomplete capability flow for system {}: L1={}, L2={}, L3={}", 
                     system.getSystemCode(), l1, l2, l3);
            return;
        }

        // Create L1 node
        String l1Id = generateCapabilityId(l1, "L1");
        if (!uniqueNodes.containsKey(l1Id)) {
            BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l1Node = createCapabilityNode(l1, "L1", null, 0);
            l1Node.setId(l1Id);
            uniqueNodes.put(l1Id, l1Node);
        }

        // Create L2 node with L1 as parent (include parent in ID for uniqueness)
        String l2Id = generateCapabilityId(l2, "L2") + UNDER_SEPARATOR + l1Id;
        if (!uniqueNodes.containsKey(l2Id)) {
            BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l2Node = createCapabilityNode(l2, "L2", l1Id, 0);
            l2Node.setId(l2Id);
            uniqueNodes.put(l2Id, l2Node);
        }

        // Create L3 node with L2 as parent (include parent in ID for uniqueness)
        String l3Id = generateCapabilityId(l3, "L3") + UNDER_SEPARATOR + l2Id;
        if (!uniqueNodes.containsKey(l3Id)) {
            BusinessCapabilitiesTreeDTO.BusinessCapabilityNode l3Node = createCapabilityNode(l3, "L3", l2Id, 0);
            l3Node.setId(l3Id);
            uniqueNodes.put(l3Id, l3Node);
        }

        // Create system node for this specific capability flow
        // System appears as separate leaf node for each business capability flow
        String systemId = system.getSystemCode() + UNDER_SEPARATOR + l3Id;
        if (!uniqueNodes.containsKey(systemId)) {
            BusinessCapabilitiesTreeDTO.BusinessCapabilityNode systemNode = createSystemNode(system, l3Id);
            systemNode.setId(systemId); // Override ID to make it unique per flow
            uniqueNodes.put(systemId, systemNode);
        }
    }

    /**
     * Calculates system counts for each capability level after all nodes are created.
     * L1 systemCount = number of direct L2 children
     * L2 systemCount = number of direct L3 children  
     * L3 systemCount = number of direct system children
     */
    private void calculateSystemCounts(List<BusinessCapabilitiesTreeDTO.BusinessCapabilityNode> nodes) {
        Map<String, Long> childCounts = new HashMap<>();
        
        // Count children for each parent
        for (BusinessCapabilitiesTreeDTO.BusinessCapabilityNode node : nodes) {
            if (node.getParentId() != null) {
                childCounts.merge(node.getParentId(), 1L, Long::sum);
            }
        }
        
        // Set system counts based on child counts
        for (BusinessCapabilitiesTreeDTO.BusinessCapabilityNode node : nodes) {
            if (!"System".equals(node.getLevel())) {
                node.setSystemCount(childCounts.getOrDefault(node.getId(), 0L).intValue());
            }
        }
    }

    /**
     * Counts total number of business capability flows across all systems.
     */
    private int countTotalFlows(List<BusinessCapabilityDiagramDTO> capabilities) {
        return capabilities.stream()
                .mapToInt(system -> system.getBusinessCapabilities() != null ? 
                         system.getBusinessCapabilities().size() : 0)
                .sum();
    }

        /**
     * Creates a capability node for the specified level.
     * 
     * @param name the capability name
     * @param level the capability level (L1, L2, L3)
     * @param parentId the parent node ID (null for L1)
     * @param systemCount the number of systems under this capability
     * @return the created capability node
     */
    private BusinessCapabilitiesTreeDTO.BusinessCapabilityNode createCapabilityNode(String name, String level, String parentId, int systemCount) {
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode node = new BusinessCapabilitiesTreeDTO.BusinessCapabilityNode();
        // Don't set ID here - it will be set by the caller with proper flow-specific ID
        node.setName(name);
        node.setLevel(level);
        node.setParentId(parentId);
        node.setSystemCount(systemCount);
        return node;
    }

    /**
     * Creates a system node from business capability data.
     * Uses systemCode as id and solutionName as name.
     */
    private BusinessCapabilitiesTreeDTO.BusinessCapabilityNode createSystemNode(BusinessCapabilityDiagramDTO capability, String parentId) {
        BusinessCapabilitiesTreeDTO.BusinessCapabilityNode systemNode = new BusinessCapabilitiesTreeDTO.BusinessCapabilityNode();
        systemNode.setId(capability.getSystemCode());
        systemNode.setName(extractSolutionName(capability));
        systemNode.setLevel("System");
        systemNode.setParentId(parentId);
        systemNode.setSystemCount(null); // Not applicable for systems
        return systemNode;
    }

    /**
     * Extracts solution name from business capability data.
     */
    private String extractSolutionName(BusinessCapabilityDiagramDTO capability) {
        if (capability.getSolutionOverview() != null && 
            capability.getSolutionOverview().getSolutionDetails() != null) {
            return capability.getSolutionOverview().getSolutionDetails().getSolutionName();
        }
        return "Unknown Solution";
    }

    /**
     * Generates a unique ID for capability nodes based on name and level.
     * 
     * @param name the capability name
     * @param level the capability level (L1, L2, L3)
     * @return formatted ID string
     */
    private String generateCapabilityId(String name, String level) {
        return level.toLowerCase() + "-" + name.toLowerCase().replaceAll("[^a-z0-9]", "-");
    }

    /**
     * Generates a system dependencies diagram showing integration flows and
     * relationships.
     *
     * Creates nodes for systems and middleware with role-based naming:
     * - Core system: no suffix (e.g., "sys-001")
     * - External systems: role suffix (e.g., "sys-002-P" for producer, "sys-003-C"
     * for consumer)
     * - Middleware: role suffix (e.g., "API_GATEWAY-P", "OSB-C")
     *
     * Flow logic based on counterpartSystemRole:
     * - "CONSUMER": target system consumes, source produces
     * - "PRODUCER": target system produces, source consumes
     *
     * @param systemCode the system to generate the diagram for (must not be null or
     *                   blank)
     * @return diagram with nodes, links, and metadata for D3.js Sankey
     *         visualization
     * @throws IllegalArgumentException if system not found or systemCode is invalid
     */
    public SpecificSystemDependenciesDiagramDTO generateSystemDependenciesDiagram(String systemCode) {
        if (systemCode == null || systemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("System code must not be null or blank");
        }
        log.info("Generating system dependencies diagram for system: {}", systemCode);

        List<SystemDependencyDTO> allDependencies = getSystemDependencies();
        SystemDependencyDTO primarySystem = findPrimarySystem(systemCode, allDependencies);

        DiagramComponents components = initializeDiagramComponents(systemCode, primarySystem);
        processAllIntegrationFlows(systemCode, allDependencies, components);

        CommonDiagramDTO.ExtendedMetadataDTO metadata = buildMetadata(systemCode, primarySystem, components.nodes());
        SpecificSystemDependenciesDiagramDTO diagram = assembleDiagram(components, metadata);

        log.info("Generated diagram with {} nodes and {} links for system {}",
            components.nodes().size(), components.links().size(), systemCode);
        
        return diagram;
    }

    /**
     * Finds all paths from startSystem to endSystem and returns them in diagram
     * format.
     *
     * This method performs comprehensive path finding analysis to discover all
     * possible
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
     * @param endSystem   the target system code to find paths to
     * @return diagram with all discovered paths visualized as nodes and links with
     *         middleware as metadata
     * @throws IllegalArgumentException if either system code is invalid, systems
     *                                  are the same, or systems not found
     */
    public PathDiagramDTO findAllPathsDiagram(String startSystem, String endSystem) {
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

        // Convert paths to diagram format with direct system-to-system links
        return convertPathsToPathDiagram(paths, startSystem, endSystem, allDependencies);
    }

    /**
     * Record to hold flow direction information.
     */
    private record FlowDirection(String producer, String consumer, String producerSystemCode,
            String consumerSystemCode) {
    }

    // Record types for path finding algorithm

    /**
     * Represents a node in the integration graph with its connections.
     */
    record IntegrationGraph(Map<String, Set<EdgeDetails>> adjacencyMap) {
    }

    /**
     * Represents an edge in the integration graph with metadata.
     * Includes original flow data for accurate visualization.
     */
    record EdgeDetails(String target, String middleware, SystemDependencyDTO.IntegrationFlow originalFlow) {
    }

    /**
     * Represents a segment of a path with source, target and connection details.
     * Includes original flow data for accurate visualization.
     */
    record PathSegment(String source, String target, String middleware,
            SystemDependencyDTO.IntegrationFlow originalFlow) {
    }

    /**
     * Represents a complete path from source to destination.
     */
    record Path(List<PathSegment> segments) {
    }

    // Validation methods for path finding

    /**
     * Validates input parameters for path finding operations.
     * 
     * @param startSystem the source system code
     * @param endSystem   the target system code
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
     * @param fieldName  the name of the field for error messages
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
     * @param endSystem   the target system
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
     * @param startSystem     the source system to validate
     * @param endSystem       the target system to validate
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
     * @param dependency   the system dependency to process
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
     * @param flow          the integration flow
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
     * @param producer     the producer system
     * @param consumer     the consumer system
     * @param flow         the integration flow
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
    private record ProducerConsumerPair(String producer, String consumer) {
    }

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
     * Converts discovered paths to PathDiagramDTO format with direct
     * system-to-system links.
     * 
     * @param paths           the list of discovered paths
     * @param startSystem     the source system
     * @param endSystem       the target system
     * @param allDependencies all system dependencies
     * @return the complete PathDiagramDTO
     */
    private PathDiagramDTO convertPathsToPathDiagram(List<Path> paths, String startSystem,
            String endSystem, List<SystemDependencyDTO> allDependencies) {
        if (paths.isEmpty()) {
            return createEmptyPathDiagramDTO(startSystem, endSystem);
        }

        PathDiagramComponents components = buildPathDiagramComponentsWithDirectLinks(paths, allDependencies);
        CommonDiagramDTO.ExtendedMetadataDTO metadata = createPathDiagramMetadata(startSystem, endSystem, paths.size(),
                components.middleware());

        return assemblePathDiagram(components, metadata);
    }

    /**
     * Creates an empty PathDiagramDTO when no paths are found.
     * 
     * @param startSystem the source system
     * @param endSystem   the target system
     * @return empty diagram with appropriate metadata
     */
    private PathDiagramDTO createEmptyPathDiagramDTO(String startSystem, String endSystem) {
        log.warn("No paths found from {} to {}", startSystem, endSystem);

        PathDiagramDTO diagram = new PathDiagramDTO();
        diagram.setNodes(List.of());
        diagram.setLinks(List.of());
        diagram.setMetadata(createPathDiagramMetadata(startSystem, endSystem, 0, Set.of()));

        return diagram;
    }

    /**
     * Creates metadata for PathDiagramDTO.
     * 
     * @param startSystem the source system
     * @param endSystem   the target system
     * @param pathCount   the number of paths found
     * @param middleware  the set of middleware components used
     * @return the metadata DTO
     */
    private CommonDiagramDTO.ExtendedMetadataDTO createPathDiagramMetadata(String startSystem, String endSystem,
            int pathCount, Set<String> middleware) {
        CommonDiagramDTO.ExtendedMetadataDTO metadata = new CommonDiagramDTO.ExtendedMetadataDTO();
        metadata.setCode(startSystem + PATH_SEPARATOR + endSystem);
        metadata.setReview(formatPathCount(pathCount));
        metadata.setIntegrationMiddleware(new ArrayList<>(middleware));
        metadata.setGeneratedDate(LocalDate.now());

        return metadata;
    }

    /**
     * Record to hold diagram components during construction.
     */
    private record DiagramComponents(List<CommonDiagramDTO.NodeDTO> nodes,
            List<CommonDiagramDTO.DetailedLinkDTO> links,
            Set<String> middleware,
            Set<String> processedLinks) {
    }

    /**
     * Record to hold path diagram components with direct links during construction.
     */
    private record PathDiagramComponents(List<CommonDiagramDTO.NodeDTO> nodes,
            List<PathDiagramDTO.PathLinkDTO> links,
            Set<String> middleware) {
    }

    /**
     * Builds path diagram components with direct system-to-system links.
     * 
     * @param paths           the discovered paths
     * @param allDependencies all system dependencies
     * @return path diagram components
     */
    private PathDiagramComponents buildPathDiagramComponentsWithDirectLinks(List<Path> paths,
            List<SystemDependencyDTO> allDependencies) {
        Set<String> allSystemsInPaths = new HashSet<>();
        Set<String> middlewareNames = new HashSet<>();
        Map<String, PathDiagramDTO.PathLinkDTO> uniqueLinks = new HashMap<>();

        // Process all path segments to create direct system-to-system links with deduplication
        for (Path path : paths) {
            processPathForDirectLinksWithDeduplication(path, allSystemsInPaths, middlewareNames, uniqueLinks);
        }

        // Convert map to list
        List<PathDiagramDTO.PathLinkDTO> links = new ArrayList<>(uniqueLinks.values());

        // Create nodes only for systems (not middleware)
        List<CommonDiagramDTO.NodeDTO> nodes = createPathNodes(allSystemsInPaths, allDependencies);

        return new PathDiagramComponents(nodes, links, middlewareNames);
    }

    /**
     * Assembles the final PathDiagramDTO from components and metadata.
     * 
     * @param components the path diagram components
     * @param metadata   the diagram metadata
     * @return the complete PathDiagramDTO
     */
    private PathDiagramDTO assemblePathDiagram(PathDiagramComponents components, CommonDiagramDTO.ExtendedMetadataDTO metadata) {
        PathDiagramDTO diagram = new PathDiagramDTO();
        diagram.setNodes(components.nodes());
        diagram.setLinks(components.links());
        diagram.setMetadata(metadata);
        return diagram;
    }

    /**
     * Processes a path to create direct system-to-system links with deduplication and middleware as metadata.
     * 
     * @param path              the path to process
     * @param allSystemsInPaths set to collect all system IDs
     * @param middlewareNames   set to collect middleware names
     * @param uniqueLinks       map to store unique links by identifier
     */
    private void processPathForDirectLinksWithDeduplication(Path path, Set<String> allSystemsInPaths,
            Set<String> middlewareNames, Map<String, PathDiagramDTO.PathLinkDTO> uniqueLinks) {
        for (PathSegment segment : path.segments()) {
            String source = segment.source();
            String target = segment.target();
            String middleware = segment.middleware();
            SystemDependencyDTO.IntegrationFlow originalFlow = segment.originalFlow();

            allSystemsInPaths.add(source);
            allSystemsInPaths.add(target);

            if (hasValidMiddleware(middleware)) {
                middlewareNames.add(normalizeNodeId(middleware));
            }

            // Create link identifier for deduplication - include all distinguishing properties
            // Only truly identical links (same source, target, pattern, frequency, middleware, role) are deduplicated
            String linkId = createPathLinkIdentifier(source, target, originalFlow);
            
            // Only add the link if we haven't seen this exact link before
            uniqueLinks.computeIfAbsent(linkId, key -> createPathDiagramLink(source, target, originalFlow));
        }
    }

    /**
     * Creates nodes for systems in paths.
     * 
     * @param allSystemsInPaths all system IDs found in paths
     * @param allDependencies   all system dependencies
     * @return list of created path nodes
     */
    private List<CommonDiagramDTO.NodeDTO> createPathNodes(Set<String> allSystemsInPaths,
            List<SystemDependencyDTO> allDependencies) {
        List<CommonDiagramDTO.NodeDTO> nodes = new ArrayList<>();

        for (String systemId : allSystemsInPaths) {
            CommonDiagramDTO.NodeDTO node = createPathDiagramNode(systemId, allDependencies);
            nodes.add(node);
        }

        return nodes;
    }

    /**
     * Creates a PathDiagramDTO link with middleware as metadata.
     * 
     * @param source the source system
     * @param target the target system
     * @param flow   the integration flow
     * @return the created link DTO
     */
    private PathDiagramDTO.PathLinkDTO createPathDiagramLink(String source, String target,
            SystemDependencyDTO.IntegrationFlow flow) {
        PathDiagramDTO.PathLinkDTO link = new PathDiagramDTO.PathLinkDTO();
        link.setSource(source);
        link.setTarget(target);
        link.setPattern(flow.getIntegrationMethod());
        link.setFrequency(flow.getFrequency());
        link.setRole(flow.getCounterpartSystemRole());
        link.setMiddleware(hasValidMiddleware(flow.getMiddleware()) ? flow.getMiddleware() : null);
        return link;
    }

    /**
     * Creates a PathDiagramDTO node for a system.
     * 
     * @param systemId        the system ID
     * @param allDependencies all system dependencies
     * @return the created node DTO
     */
    private CommonDiagramDTO.NodeDTO createPathDiagramNode(String systemId, List<SystemDependencyDTO> allDependencies) {
        CommonDiagramDTO.NodeDTO node = new CommonDiagramDTO.NodeDTO();
        node.setId(systemId);

        String systemName = findSystemNameFromDependencies(systemId, allDependencies);
        node.setName(systemName);
        node.setType(determineSystemType(systemId, allDependencies));
        node.setCriticality(MAJOR_CRITICALITY);
        node.setUrl(systemId + JSON_EXTENSION);

        return node;
    }

    /**
     * Finds the primary system in the dependencies list.
     * 
     * @param systemCode      the system code to find
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
     * @param systemCode    the primary system code
     * @param primarySystem the primary system data
     * @return initialized diagram components
     */
    private DiagramComponents initializeDiagramComponents(String systemCode, SystemDependencyDTO primarySystem) {
        List<CommonDiagramDTO.NodeDTO> nodes = new ArrayList<>();
        nodes.add(createPrimarySystemNode(systemCode, primarySystem));

        return new DiagramComponents(
                nodes,
                new ArrayList<>(),
                new HashSet<>(),
                new HashSet<>());
    }

    /**
     * Processes all integration flows from all systems.
     * 
     * @param systemCode      the target system code
     * @param allDependencies all system dependencies
     * @param components      diagram components to populate
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
     * @param system           the system whose flows to process
     * @param allDependencies  all system dependencies
     * @param components       diagram components to populate
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
     * @param targetSystemCode      the target system code
     * @param currentSystemCode     the current system code
     * @param counterpartSystemCode the counterpart system code
     * @return true if flow involves target system
     */
    private static boolean isFlowRelevant(String targetSystemCode, String currentSystemCode,
            String counterpartSystemCode) {
        return targetSystemCode.equals(currentSystemCode) || targetSystemCode.equals(counterpartSystemCode);
    }

    /**
     * Processes a single integration flow.
     * 
     * @param targetSystemCode  the target system code
     * @param currentSystemCode the current system code
     * @param flow              the integration flow to process
     * @param allDependencies   all system dependencies
     * @param components        diagram components to populate
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
     * @param systemCode    the system code
     * @param primarySystem the primary system data
     * @param nodes         the diagram nodes
     * @return the metadata
     */
    private CommonDiagramDTO.ExtendedMetadataDTO buildMetadata(String systemCode,
            SystemDependencyDTO primarySystem,
            List<CommonDiagramDTO.NodeDTO> nodes) {
        CommonDiagramDTO.ExtendedMetadataDTO metadata = new CommonDiagramDTO.ExtendedMetadataDTO();
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
    private List<String> extractMiddlewareList(List<CommonDiagramDTO.NodeDTO> nodes) {
        return nodes.stream()
                .filter(node -> MIDDLEWARE_TYPE.equals(node.getType()))
                .map(CommonDiagramDTO.NodeDTO::getId)
                .toList();
    }

    /**
     * Assembles the final diagram from components and metadata.
     * 
     * @param components the diagram components
     * @param metadata   the diagram metadata
     * @return the complete diagram
     */
    private static SpecificSystemDependenciesDiagramDTO assembleDiagram(DiagramComponents components,
            CommonDiagramDTO.ExtendedMetadataDTO metadata) {
        SpecificSystemDependenciesDiagramDTO diagram = new SpecificSystemDependenciesDiagramDTO();
        diagram.setNodes(components.nodes());
        diagram.setLinks(components.links());
        diagram.setMetadata(metadata);
        return diagram;
    }

    /**
     * Creates the primary system node.
     */
    private CommonDiagramDTO.NodeDTO createPrimarySystemNode(String systemCode, SystemDependencyDTO primarySystem) {
        CommonDiagramDTO.NodeDTO primaryNode = new CommonDiagramDTO.NodeDTO();
        primaryNode.setId(systemCode);
        primaryNode.setName(primarySystem.getSolutionOverview().getSolutionDetails().getSolutionName());
        primaryNode.setType(CORE_SYSTEM_TYPE);
        primaryNode.setCriticality(MAJOR_CRITICALITY);
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
                        counterpartSystemCode);
            } else {
                // counterpart is producer, primary is consumer
                return new FlowDirection(
                        counterpartSystemCode + PRODUCER_SUFFIX,
                        primarySystemCode,
                        counterpartSystemCode,
                        primarySystemCode);
            }
        } else {
            // Primary system is the counterpart system
            if (CONSUMER_ROLE.equals(counterpartRole)) {
                // primary is consumer, current is producer
                return new FlowDirection(
                        currentSystemCode + PRODUCER_SUFFIX,
                        primarySystemCode,
                        currentSystemCode,
                        primarySystemCode);
            } else {
                // primary is producer, current is consumer
                return new FlowDirection(
                        primarySystemCode,
                        currentSystemCode + CONSUMER_SUFFIX,
                        primarySystemCode,
                        currentSystemCode);
            }
        }
    }

    /**
     * Creates a unique link identifier.
     */
    private String createLinkIdentifier(String currentSystemCode, FlowDirection flowDirection,
            String integrationMethod) {
        return currentSystemCode + ":" + flowDirection.producer() + "->" + flowDirection.consumer() + ":"
                + integrationMethod;
    }

    /**
     * Creates a unique identifier for path diagram links to enable proper deduplication.
     * Only links with identical source, target, pattern, frequency, middleware, and role are considered duplicates.
     * 
     * @param source       the source system
     * @param target       the target system
     * @param originalFlow the original integration flow containing link properties
     * @return unique identifier for the link
     */
    private String createPathLinkIdentifier(String source, String target, SystemDependencyDTO.IntegrationFlow originalFlow) {
        String pattern = originalFlow.getIntegrationMethod();
        String frequency = originalFlow.getFrequency();
        String middleware = originalFlow.getMiddleware();
        String role = originalFlow.getCounterpartSystemRole();
        
        // Create identifier that includes all distinguishing properties
        // Use null-safe strings to handle null values consistently
        return String.format("%s->%s:%s:%s:%s:%s", 
                source, 
                target, 
                pattern != null ? pattern : "null",
                frequency != null ? frequency : "null", 
                middleware != null ? middleware : "null",
                role != null ? role : "null");
    }

    /**
     * Checks if middleware is valid (not null, not empty, not "NONE").
     */
    private boolean hasValidMiddleware(String middleware) {
        return middleware != null && !middleware.trim().isEmpty()
                && !NONE_MIDDLEWARE.equalsIgnoreCase(middleware.trim());
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
     * @param nodes            the nodes list to add to
     * @param middlewareNodeId the middleware node ID
     * @param middlewareName   the middleware name
     */
    private void addMiddlewareNodeIfNeeded(List<CommonDiagramDTO.NodeDTO> nodes,
            String middlewareNodeId, String middlewareName) {
        if (nodeExists(nodes, middlewareNodeId)) {
            return;
        }

        CommonDiagramDTO.NodeDTO middlewareNode = createMiddlewareNode(middlewareNodeId, middlewareName);
        nodes.add(middlewareNode);
    }

    /**
     * Creates a middleware node DTO.
     * 
     * @param middlewareNodeId the middleware node ID
     * @param middlewareName   the middleware name
     * @return the created middleware node
     */
    private CommonDiagramDTO.NodeDTO createMiddlewareNode(String middlewareNodeId, String middlewareName) {
        CommonDiagramDTO.NodeDTO middlewareNode = new CommonDiagramDTO.NodeDTO();
        middlewareNode.setId(middlewareNodeId);
        middlewareNode.setName(middlewareName);
        middlewareNode.setType(MIDDLEWARE_TYPE);
        middlewareNode.setCriticality(STANDARD_CRITICALITY);
        return middlewareNode;
    }

    /**
     * Creates a link DTO.
     */
    private CommonDiagramDTO.DetailedLinkDTO createLink(String source, String target,
            SystemDependencyDTO.IntegrationFlow flow, String role) {
        CommonDiagramDTO.DetailedLinkDTO link = new CommonDiagramDTO.DetailedLinkDTO();
        link.setSource(source);
        link.setTarget(target);
        link.setPattern(flow.getIntegrationMethod());
        link.setFrequency(flow.getFrequency());
        link.setRole(role);
        return link;
    }

    /**
     * Adds a system node if it doesn't already exist and it's not the primary
     * system.
     * 
     * @param nodes             the nodes list to add to
     * @param nodeId            the ID of the node to add
     * @param systemCode        the system code
     * @param primarySystemCode the primary system code
     * @param allDependencies   all system dependencies
     */
    private void addSystemNodeIfNeeded(List<CommonDiagramDTO.NodeDTO> nodes, String nodeId,
            String systemCode, String primarySystemCode,
            List<SystemDependencyDTO> allDependencies) {
        if (systemCode.equals(primarySystemCode) || nodeExists(nodes, nodeId)) {
            return;
        }

        SystemDependencyDTO systemData = findSystemData(systemCode, allDependencies);
        CommonDiagramDTO.NodeDTO node = createSystemNode(nodeId, systemCode, systemData, allDependencies);
        nodes.add(node);
    }

    /**
     * Checks if a node with the given ID already exists.
     * 
     * @param nodes  the nodes list
     * @param nodeId the node ID to check
     * @return true if node exists
     */
    private static boolean nodeExists(List<CommonDiagramDTO.NodeDTO> nodes, String nodeId) {
        return nodes.stream().anyMatch(node -> nodeId.equals(node.getId()));
    }

    /**
     * Finds system data by system code.
     * 
     * @param systemCode      the system code
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
     * @param nodeId          the node ID
     * @param systemCode      the system code
     * @param systemData      the system data (may be null)
     * @param allDependencies all system dependencies
     * @return the created node
     */
    private CommonDiagramDTO.NodeDTO createSystemNode(String nodeId, String systemCode,
            SystemDependencyDTO systemData,
            List<SystemDependencyDTO> allDependencies) {
        CommonDiagramDTO.NodeDTO node = new CommonDiagramDTO.NodeDTO();
        node.setId(nodeId);
        node.setName(systemData != null ? systemData.getSolutionOverview().getSolutionDetails().getSolutionName()
                : systemCode);
        node.setType(determineSystemType(systemCode, allDependencies));
        node.setCriticality(MAJOR_CRITICALITY);
        return node;
    }

    /**
     * Processes a flow, handling both middleware and direct connections.
     */
    private void processFlow(SystemDependencyDTO.IntegrationFlow flow, FlowDirection flowDirection,
            String primarySystemCode, Set<String> middleware,
            List<CommonDiagramDTO.NodeDTO> nodes, List<CommonDiagramDTO.DetailedLinkDTO> links) {

        // Handle middleware
        if (hasValidMiddleware(flow.getMiddleware())) {
            String middlewareName = flow.getMiddleware();
            middleware.add(middlewareName);

            // Determine which middleware node we need based on the flow direction
            String middlewareNodeId = determineMiddlewareNodeId(middlewareName, flowDirection.producer(),
                    primarySystemCode);

            // Add the required middleware node
            addMiddlewareNodeIfNeeded(nodes, middlewareNodeId, middlewareName);

            // Create middleware links for each flow
            links.add(createLink(flowDirection.producer(), middlewareNodeId, flow, PRODUCER_ROLE));
            links.add(createLink(middlewareNodeId, flowDirection.consumer(), flow, CONSUMER_ROLE));

        } else {
            // Direct connection without middleware
            links.add(createLink(flowDirection.producer(), flowDirection.consumer(), flow,
                    flow.getCounterpartSystemRole()));
        }
    }

    /**
     * Determines system type based on data availability.
     * 
     * @param systemCode      system to check
     * @param allDependencies all known systems
     * @return "IncomeSystem" if system exists in our data, "External" otherwise
     */
    private String determineSystemType(String systemCode, List<SystemDependencyDTO> allDependencies) {
        boolean existsInOurData = allDependencies.stream()
                .anyMatch(system -> system.getSystemCode().equals(systemCode));
        return existsInOurData ? INCOME_SYSTEM_TYPE : EXTERNAL_SYSTEM_TYPE;
    }


    /**
     * 
     */
    public OverallSystemDependenciesDiagramDTO generateAllSystemDependenciesDiagrams() {
        log.info("Generating diagrams for all systems");
        
        List<SystemDependencyDTO> allDependencies = getSystemDependencies();
        OverallSystemDependenciesDiagramDTO results = extractUniqueLinksAndNodes(allDependencies);
        CommonDiagramDTO.BasicMetadataDTO metadata = new CommonDiagramDTO.BasicMetadataDTO();
        metadata.setGeneratedDate(LocalDate.now());
        results.setMetadata(metadata);
        return results;
    }

    private OverallSystemDependenciesDiagramDTO extractUniqueLinksAndNodes(List<SystemDependencyDTO> allDependencies) {
        List<CommonDiagramDTO.SimpleLinkDTO> uniqueLinks = new ArrayList<>();
        List<CommonDiagramDTO.NodeDTO> uniqueNodes = new ArrayList<>();
        Map<String, Integer> linkIdentifiers = new HashMap<>();
        Set<String> nodeIdentifiers = new HashSet<>();

        processSystemDependenciesForOverallDiagram(allDependencies, uniqueLinks, uniqueNodes, linkIdentifiers, nodeIdentifiers);
        updateLinkCounts(uniqueLinks, linkIdentifiers);

        return createOverallDiagram(uniqueLinks, uniqueNodes);
    }

    /**
     * Processes all system dependencies to extract unique links and nodes.
     */
    private void processSystemDependenciesForOverallDiagram(List<SystemDependencyDTO> allDependencies,
                                                           List<CommonDiagramDTO.SimpleLinkDTO> uniqueLinks,
                                                           List<CommonDiagramDTO.NodeDTO> uniqueNodes,
                                                           Map<String, Integer> linkIdentifiers,
                                                           Set<String> nodeIdentifiers) {
        for (SystemDependencyDTO system : allDependencies) {
            if (system.getIntegrationFlows() != null) {
                processSystemFlowsForOverallDiagram(system, uniqueLinks, uniqueNodes, linkIdentifiers, nodeIdentifiers);
            }
        }
    }

    /**
     * Processes integration flows for a single system in overall diagram context.
     */
    private void processSystemFlowsForOverallDiagram(SystemDependencyDTO system,
                                                    List<CommonDiagramDTO.SimpleLinkDTO> uniqueLinks,
                                                    List<CommonDiagramDTO.NodeDTO> uniqueNodes,
                                                    Map<String, Integer> linkIdentifiers,
                                                    Set<String> nodeIdentifiers) {
        for (SystemDependencyDTO.IntegrationFlow flow : system.getIntegrationFlows()) {
            processIntegrationFlow(system, flow, uniqueLinks, linkIdentifiers);
            addSystemNodeIfNotExists(system, uniqueNodes, nodeIdentifiers);
            addCounterpartNodeIfNotExists(flow, uniqueNodes, nodeIdentifiers);
        }
    }

    /**
     * Processes a single integration flow to handle link creation and counting.
     */
    private void processIntegrationFlow(SystemDependencyDTO system,
                                      SystemDependencyDTO.IntegrationFlow flow,
                                      List<CommonDiagramDTO.SimpleLinkDTO> uniqueLinks,
                                      Map<String, Integer> linkIdentifiers) {
        String linkId = system.getSystemCode() + "-" + flow.getCounterpartSystemCode();
        String reverseLinkId = flow.getCounterpartSystemCode() + "-" + system.getSystemCode();

        if (!linkExists(linkId, reverseLinkId, linkIdentifiers)) {
            createNewLink(system, flow, uniqueLinks, linkIdentifiers, linkId);
        } else {
            incrementExistingLinkCount(linkId, reverseLinkId, linkIdentifiers);
        }
    }

    /**
     * Checks if a link already exists in either direction.
     */
    private boolean linkExists(String linkId, String reverseLinkId, Map<String, Integer> linkIdentifiers) {
        return linkIdentifiers.containsKey(linkId) || linkIdentifiers.containsKey(reverseLinkId);
    }

    /**
     * Creates a new link and adds it to the collection.
     */
    private void createNewLink(SystemDependencyDTO system,
                             SystemDependencyDTO.IntegrationFlow flow,
                             List<CommonDiagramDTO.SimpleLinkDTO> uniqueLinks,
                             Map<String, Integer> linkIdentifiers,
                             String linkId) {
        linkIdentifiers.put(linkId, 1);
        CommonDiagramDTO.SimpleLinkDTO link = new CommonDiagramDTO.SimpleLinkDTO();
        link.setSource(system.getSystemCode());
        link.setTarget(flow.getCounterpartSystemCode());
        uniqueLinks.add(link);
    }

    /**
     * Increments the count for an existing link.
     */
    private void incrementExistingLinkCount(String linkId, String reverseLinkId, Map<String, Integer> linkIdentifiers) {
        if (linkIdentifiers.containsKey(linkId)) {
            linkIdentifiers.put(linkId, linkIdentifiers.get(linkId) + 1);
        } else {
            linkIdentifiers.put(reverseLinkId, linkIdentifiers.get(reverseLinkId) + 1);
        }
    }

    /**
     * Adds a system node if it doesn't already exist.
     */
    private void addSystemNodeIfNotExists(SystemDependencyDTO system,
                                        List<CommonDiagramDTO.NodeDTO> uniqueNodes,
                                        Set<String> nodeIdentifiers) {
        if (!nodeIdentifiers.contains(system.getSystemCode())) {
            nodeIdentifiers.add(system.getSystemCode());
            CommonDiagramDTO.NodeDTO node = createSystemNodeFromDependency(system);
            uniqueNodes.add(node);
        }
    }

    /**
     * Adds a counterpart system node if it doesn't already exist.
     */
    private void addCounterpartNodeIfNotExists(SystemDependencyDTO.IntegrationFlow flow,
                                             List<CommonDiagramDTO.NodeDTO> uniqueNodes,
                                             Set<String> nodeIdentifiers) {
        String counterpartCode = flow.getCounterpartSystemCode();
        if (!nodeIdentifiers.contains(counterpartCode)) {
            nodeIdentifiers.add(counterpartCode);
            CommonDiagramDTO.NodeDTO node = createCounterpartNode(counterpartCode);
            uniqueNodes.add(node);
        }
    }

    /**
     * Creates a system node from dependency data.
     */
    private CommonDiagramDTO.NodeDTO createSystemNodeFromDependency(SystemDependencyDTO system) {
        CommonDiagramDTO.NodeDTO node = new CommonDiagramDTO.NodeDTO();
        node.setId(system.getSystemCode());
        node.setName(system.getSolutionOverview().getSolutionDetails().getSolutionName());
        node.setType(CORE_SYSTEM_TYPE);
        node.setCriticality(MAJOR_CRITICALITY);
        return node;
    }

    /**
     * Creates a counterpart system node.
     */
    private CommonDiagramDTO.NodeDTO createCounterpartNode(String counterpartCode) {
        CommonDiagramDTO.NodeDTO node = new CommonDiagramDTO.NodeDTO();
        node.setId(counterpartCode);
        node.setName(counterpartCode);
        node.setType(EXTERNAL_SYSTEM_TYPE);
        node.setCriticality(STANDARD_CRITICALITY);
        return node;
    }

    /**
     * Updates link counts based on the link identifiers map.
     */
    private void updateLinkCounts(List<CommonDiagramDTO.SimpleLinkDTO> uniqueLinks,
                                Map<String, Integer> linkIdentifiers) {
        for (CommonDiagramDTO.SimpleLinkDTO link : uniqueLinks) {
            String linkId = link.getSource() + "-" + link.getTarget();
            String reverseLinkId = link.getTarget() + "-" + link.getSource();
            
            if (linkIdentifiers.containsKey(linkId)) {
                link.setCount(linkIdentifiers.get(linkId));
            } else if (linkIdentifiers.containsKey(reverseLinkId)) {
                link.setCount(linkIdentifiers.get(reverseLinkId));
            } else {
                link.setCount(1);
            }
        }
    }

    /**
     * Creates the final overall diagram with links and nodes.
     */
    private OverallSystemDependenciesDiagramDTO createOverallDiagram(List<CommonDiagramDTO.SimpleLinkDTO> uniqueLinks,
                                                                   List<CommonDiagramDTO.NodeDTO> uniqueNodes) {
        OverallSystemDependenciesDiagramDTO diagram = new OverallSystemDependenciesDiagramDTO();
        diagram.setLinks(uniqueLinks);
        diagram.setNodes(uniqueNodes);
        return diagram;
    }

    /**
     * Finds system name from dependencies or returns nodeId if not found.
     * 
     * @param nodeId          the node ID to find name for
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
}