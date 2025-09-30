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
    
    private final CoreServiceClient coreServiceClient;
    
    @Autowired
    public DiagramService(CoreServiceClient coreServiceClient) {
        this.coreServiceClient = coreServiceClient;
    }
    
    /**
     * Retrieves all system dependencies from the core service.
     *
     * This method fetches a complete list of system dependencies including
     * solution overviews, integration flows, and related metadata for all systems
     * in the organization.
     *
     * @return a list of {@link SystemDependencyDTO} containing all system dependency information
     * @throws RuntimeException if the core service call fails or returns an error
     */
    public List<SystemDependencyDTO> getSystemDependencies() {
        log.info("Calling core service for system dependencies");
        
        try {
            List<SystemDependencyDTO> result = coreServiceClient.getSystemDependencies();
            log.info("Retrieved {} system dependencies", result.size());
            return result;
        } catch (Exception e) {
            log.error("Error calling core service: {}", e.getMessage());
            throw e;
        }
    }
    
    /**
     * Generates a comprehensive system dependencies diagram for the specified system.
     *
     * This method creates a visual representation of system dependencies by analyzing all
     * integration flows across the organization to identify relationships with the target system.
     *
     * The analysis process includes:
     *   Identifying the primary system as the core system being analyzed
     *   Examining all other systems' integration flows to find references to the target system
     *   Determining producer/consumer relationships based on counterpartSystemRole
     *   Creating role-specific nodes with appropriate suffixes for external systems and middleware
     *   Handling middleware routing with proper -P (producer) and -C (consumer) designations
     *
     * Node Types and Naming Conventions:
     *   Core System: The target system (e.g., "sys-001") - no role suffix
     *   External Systems: Other systems with role suffixes (e.g., "sys-002-P", "sys-003-C")
     *   Middleware: Infrastructure components with role suffixes (e.g., "API_GATEWAY-P", "OSB-C")
     *
     * Flow Direction Logic:
     *   If counterpartSystemRole = "CONSUMER" → Target system consumes, source system produces
     *   If counterpartSystemRole = "PRODUCER" → Target system produces, source system consumes
     *   Data flows left-to-right: Producer → [Middleware] → Consumer
     *
     * Middleware Handling:
     *   Creates separate middleware nodes for producer (-P) and consumer (-C) sides
     *   Routes flows through middleware: Producer → Middleware-P/C → Consumer
     *   Ignores middleware when value is null, empty, or "NONE" (creates direct links)
     *
     * @param systemCode the unique identifier of the system to generate the diagram for
     * @return a {@link SystemDiagramDTO} containing nodes, links, and metadata for visualization
     * @throws RuntimeException if the system is not found or if there's an error during diagram generation
     *
     * @see SystemDiagramDTO
     * @see SystemDependencyDTO.IntegrationFlow
     */
    public SystemDiagramDTO generateSystemDependenciesDiagram(String systemCode) {
        log.info("Generating system dependencies diagram for system: {}", systemCode);
        
        try {
            // Get all system dependencies
            List<SystemDependencyDTO> allDependencies = getSystemDependencies();
            
            // Find the primary system
            SystemDependencyDTO primarySystem = allDependencies.stream()
                .filter(system -> systemCode.equals(system.getSystemCode()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("System not found: " + systemCode));
            
            // Build nodes and links
            List<SystemDiagramDTO.NodeDTO> nodes = new ArrayList<>();
            List<SystemDiagramDTO.LinkDTO> links = new ArrayList<>();
            Set<String> middleware = new HashSet<>();
            Set<String> processedLinks = new HashSet<>(); // To avoid duplicates
            
            // Add primary system node (single node without role suffix)
            SystemDiagramDTO.NodeDTO primaryNode = new SystemDiagramDTO.NodeDTO();
            primaryNode.setId(systemCode);
            primaryNode.setName(primarySystem.getSolutionOverview().getSolutionDetails().getSolutionName());
            primaryNode.setType("Core System");
            primaryNode.setCriticality("Major");
            primaryNode.setUrl(systemCode + ".json");
            nodes.add(primaryNode);
            
            // Process all systems to find relationships with the primary system
            for (SystemDependencyDTO system : allDependencies) {
                if (system.getIntegrationFlows() != null) {
                    for (SystemDependencyDTO.IntegrationFlow flow : system.getIntegrationFlows()) {
                        // Check if this flow involves our target system as counterpartSystemCode
                        if (systemCode.equals(flow.getCounterpartSystemCode())) {
                            
                            // Determine producer and consumer based on the counterpartSystemRole
                            String producer, consumer;
                            String producerSystemCode, consumerSystemCode;
                            if ("CONSUMER".equals(flow.getCounterpartSystemRole())) {
                                // core system is CONSUMER, so current system is PRODUCER
                                producer = system.getSystemCode() + "-P";
                                consumer = systemCode; // Main system doesn't have role suffix
                                producerSystemCode = system.getSystemCode();
                                consumerSystemCode = systemCode;
                            } else {
                                // core system is PRODUCER, so current system is CONSUMER
                                producer = systemCode; // Main system doesn't have role suffix
                                consumer = system.getSystemCode() + "-C";
                                producerSystemCode = systemCode;
                                consumerSystemCode = system.getSystemCode();
                            }
                            
                            // Create unique link identifier to avoid duplicates (include source system to allow multiple flows through same middleware)\n                            String linkId = system.getSystemCode() + \":\" + producer + \"->\" + consumer + \":\" + flow.getIntegrationMethod();\n                            if (processedLinks.contains(linkId)) {\n                                continue;\n                            }\n                            processedLinks.add(linkId);
                            
                            // Add external system nodes (only for non-main systems)
                            if (!producerSystemCode.equals(systemCode)) {
                                boolean producerNodeExists = nodes.stream()
                                    .anyMatch(node -> node.getId().equals(producer));
                                if (!producerNodeExists) {
                                    SystemDependencyDTO producerSystemData = allDependencies.stream()
                                        .filter(s -> s.getSystemCode().equals(producerSystemCode))
                                        .findFirst().orElse(null);
                                    
                                    SystemDiagramDTO.NodeDTO producerNode = new SystemDiagramDTO.NodeDTO();
                                    producerNode.setId(producer);
                                    if (producerSystemData != null) {
                                        producerNode.setName(producerSystemData.getSolutionOverview().getSolutionDetails().getSolutionName());
                                    } else {
                                        producerNode.setName(producerSystemCode);
                                    }
                                    producerNode.setType(determineSystemType(producerSystemCode, allDependencies));
                                    producerNode.setCriticality("Major");
                                    producerNode.setUrl(producerSystemCode + ".json");
                                    nodes.add(producerNode);
                                }
                            }
                            
                            if (!consumerSystemCode.equals(systemCode)) {
                                boolean consumerNodeExists = nodes.stream()
                                    .anyMatch(node -> node.getId().equals(consumer));
                                if (!consumerNodeExists) {
                                    SystemDependencyDTO consumerSystemData = allDependencies.stream()
                                        .filter(s -> s.getSystemCode().equals(consumerSystemCode))
                                        .findFirst().orElse(null);
                                    
                                    SystemDiagramDTO.NodeDTO consumerNode = new SystemDiagramDTO.NodeDTO();
                                    consumerNode.setId(consumer);
                                    if (consumerSystemData != null) {
                                        consumerNode.setName(consumerSystemData.getSolutionOverview().getSolutionDetails().getSolutionName());
                                    } else {
                                        consumerNode.setName(consumerSystemCode);
                                    }
                                    consumerNode.setType(determineSystemType(consumerSystemCode, allDependencies));
                                    consumerNode.setCriticality("Major");
                                    consumerNode.setUrl(consumerSystemCode + ".json");
                                    nodes.add(consumerNode);
                                }
                            }
                            
                            // Handle middleware
                            if (flow.getMiddleware() != null && !flow.getMiddleware().trim().isEmpty() 
                                && !"NONE".equalsIgnoreCase(flow.getMiddleware().trim())) {
                                String middlewareName = flow.getMiddleware();
                                middleware.add(middlewareName);
                                
                                // Determine which middleware node we need based on the flow direction
                                String middlewareNodeId;
                                if (producer.startsWith(systemCode)) {
                                    // core system is producer, so we need middleware-C (consumer side)
                                    middlewareNodeId = middlewareName + "-C";
                                } else {
                                    // core system is consumer, so we need middleware-P (producer side)
                                    middlewareNodeId = middlewareName + "-P";
                                }
                                
                                // Add the required middleware node
                                boolean middlewareExists = nodes.stream()
                                    .anyMatch(node -> node.getId().equals(middlewareNodeId));
                                if (!middlewareExists) {
                                    SystemDiagramDTO.NodeDTO middlewareNode = new SystemDiagramDTO.NodeDTO();
                                    middlewareNode.setId(middlewareNodeId);
                                    middlewareNode.setName(middlewareName);
                                    middlewareNode.setType("Middleware");
                                    middlewareNode.setCriticality("Standard-2");
                                    middlewareNode.setUrl(middlewareName + ".json");
                                    nodes.add(middlewareNode);
                                }
                                
                                // Create middleware links for each flow (don't deduplicate middleware routing)
                                SystemDiagramDTO.LinkDTO link1 = new SystemDiagramDTO.LinkDTO();
                                link1.setSource(producer);
                                link1.setTarget(middlewareNodeId);
                                link1.setPattern(flow.getIntegrationMethod());
                                link1.setFrequency(flow.getFrequency());
                                link1.setRole("Producer");
                                links.add(link1);
                                
                                SystemDiagramDTO.LinkDTO link2 = new SystemDiagramDTO.LinkDTO();
                                link2.setSource(middlewareNodeId);
                                link2.setTarget(consumer);
                                link2.setPattern(flow.getIntegrationMethod());
                                link2.setFrequency(flow.getFrequency());
                                link2.setRole("Consumer");
                                links.add(link2);
                                
                            } else {
                                // Direct connection without middleware (when middleware is null, empty, or "NONE")
                                SystemDiagramDTO.LinkDTO link = new SystemDiagramDTO.LinkDTO();
                                link.setSource(producer);
                                link.setTarget(consumer);
                                link.setPattern(flow.getIntegrationMethod());
                                link.setFrequency(flow.getFrequency());
                                link.setRole(flow.getCounterpartSystemRole());
                                links.add(link);
                            }
                        }
                    }
                }
            }
            
            // Also process the primary system's own integration flows
            if (primarySystem.getIntegrationFlows() != null) {
                for (SystemDependencyDTO.IntegrationFlow flow : primarySystem.getIntegrationFlows()) {
                    String counterpartSystemCode = flow.getCounterpartSystemCode();
                    
                    // Determine producer and consumer based on the counterpartSystemRole
                    String producer, consumer;
                    String producerSystemCode, consumerSystemCode;
                    if ("CONSUMER".equals(flow.getCounterpartSystemRole())) {
                        // counterpart system is CONSUMER, so primary system is PRODUCER
                        producer = systemCode; // Main system doesn't have role suffix
                        consumer = counterpartSystemCode + "-C";
                        producerSystemCode = systemCode;
                        consumerSystemCode = counterpartSystemCode;
                    } else {
                        // counterpart system is PRODUCER, so primary system is CONSUMER
                        producer = counterpartSystemCode + "-P";
                        consumer = systemCode; // Main system doesn't have role suffix
                        producerSystemCode = counterpartSystemCode;
                        consumerSystemCode = systemCode;
                    }
                    
                    // Create unique link identifier to avoid duplicates (include counterpart system for primary flows)
                    String linkId = counterpartSystemCode + ":" + producer + "->" + consumer + ":" + flow.getIntegrationMethod();
                    if (processedLinks.contains(linkId)) {
                        continue;
                    }
                    processedLinks.add(linkId);
                    
                    // Add counterpart system nodes (only for non-main systems)
                    if (!producerSystemCode.equals(systemCode)) {
                        boolean producerNodeExists = nodes.stream()
                            .anyMatch(node -> node.getId().equals(producer));
                        if (!producerNodeExists) {
                            SystemDependencyDTO producerSystemData = allDependencies.stream()
                                .filter(s -> s.getSystemCode().equals(producerSystemCode))
                                .findFirst().orElse(null);
                            
                            SystemDiagramDTO.NodeDTO producerNode = new SystemDiagramDTO.NodeDTO();
                            producerNode.setId(producer);
                            if (producerSystemData != null) {
                                producerNode.setName(producerSystemData.getSolutionOverview().getSolutionDetails().getSolutionName());
                            } else {
                                producerNode.setName(producerSystemCode);
                            }
                            producerNode.setType(determineSystemType(producerSystemCode, allDependencies));
                            producerNode.setCriticality("Major");
                            producerNode.setUrl(producerSystemCode + ".json");
                            nodes.add(producerNode);
                        }
                    }
                    
                    if (!consumerSystemCode.equals(systemCode)) {
                        boolean consumerNodeExists = nodes.stream()
                            .anyMatch(node -> node.getId().equals(consumer));
                        if (!consumerNodeExists) {
                            SystemDependencyDTO consumerSystemData = allDependencies.stream()
                                .filter(s -> s.getSystemCode().equals(consumerSystemCode))
                                .findFirst().orElse(null);
                            
                            SystemDiagramDTO.NodeDTO consumerNode = new SystemDiagramDTO.NodeDTO();
                            consumerNode.setId(consumer);
                            if (consumerSystemData != null) {
                                consumerNode.setName(consumerSystemData.getSolutionOverview().getSolutionDetails().getSolutionName());
                            } else {
                                consumerNode.setName(consumerSystemCode);
                            }
                            consumerNode.setType(determineSystemType(consumerSystemCode, allDependencies));
                            consumerNode.setCriticality("Major");
                            consumerNode.setUrl(consumerSystemCode + ".json");
                            nodes.add(consumerNode);
                        }
                    }
                    
                    // Handle middleware
                    if (flow.getMiddleware() != null && !flow.getMiddleware().trim().isEmpty() 
                        && !"NONE".equalsIgnoreCase(flow.getMiddleware().trim())) {
                        String middlewareName = flow.getMiddleware();
                        middleware.add(middlewareName);
                        
                        // Determine which middleware node we need based on the flow direction
                        String middlewareNodeId;
                        if (producer.startsWith(systemCode)) {
                            // primary system is producer, so we need middleware-C (consumer side)
                            middlewareNodeId = middlewareName + "-C";
                        } else {
                            // primary system is consumer, so we need middleware-P (producer side)
                            middlewareNodeId = middlewareName + "-P";
                        }
                        
                        // Add the required middleware node
                        boolean middlewareExists = nodes.stream()
                            .anyMatch(node -> node.getId().equals(middlewareNodeId));
                        if (!middlewareExists) {
                            SystemDiagramDTO.NodeDTO middlewareNode = new SystemDiagramDTO.NodeDTO();
                            middlewareNode.setId(middlewareNodeId);
                            middlewareNode.setName(middlewareName);
                            middlewareNode.setType("Middleware");
                            middlewareNode.setCriticality("Standard-2");
                            middlewareNode.setUrl(middlewareName + ".json");
                            nodes.add(middlewareNode);
                        }
                        
                        // Create middleware links for each flow (don't deduplicate middleware routing)
                        SystemDiagramDTO.LinkDTO link1 = new SystemDiagramDTO.LinkDTO();
                        link1.setSource(producer);
                        link1.setTarget(middlewareNodeId);
                        link1.setPattern(flow.getIntegrationMethod());
                        link1.setFrequency(flow.getFrequency());
                        link1.setRole("Producer");
                        links.add(link1);
                        
                        SystemDiagramDTO.LinkDTO link2 = new SystemDiagramDTO.LinkDTO();
                        link2.setSource(middlewareNodeId);
                        link2.setTarget(consumer);
                        link2.setPattern(flow.getIntegrationMethod());
                        link2.setFrequency(flow.getFrequency());
                        link2.setRole("Consumer");
                        links.add(link2);
                        
                    } else {
                        // Direct connection without middleware (when middleware is null, empty, or "NONE")
                        SystemDiagramDTO.LinkDTO link = new SystemDiagramDTO.LinkDTO();
                        link.setSource(producer);
                        link.setTarget(consumer);
                        link.setPattern(flow.getIntegrationMethod());
                        link.setFrequency(flow.getFrequency());
                        link.setRole(flow.getCounterpartSystemRole());
                        links.add(link);
                    }
                }
            }
            
            // Build metadata
            SystemDiagramDTO.MetadataDTO metadata = new SystemDiagramDTO.MetadataDTO();
            metadata.setCode(systemCode);
            metadata.setReview(primarySystem.getSolutionOverview().getSolutionDetails().getSolutionReviewCode());
            
            // Add only the middleware nodes that were actually created
            List<String> middlewareList = new ArrayList<>();
            for (SystemDiagramDTO.NodeDTO node : nodes) {
                if ("Middleware".equals(node.getType())) {
                    middlewareList.add(node.getId());
                }
            }
            metadata.setIntegrationMiddleware(middlewareList);
            metadata.setGeneratedDate(LocalDate.now());
            
            // Build final diagram
            SystemDiagramDTO diagram = new SystemDiagramDTO();
            diagram.setNodes(nodes);
            diagram.setLinks(links);
            diagram.setMetadata(metadata);
            
            log.info("Generated diagram with {} nodes and {} links for system {}", 
                nodes.size(), links.size(), systemCode);
            
            return diagram;
            
        } catch (Exception e) {
            log.error("Error generating system dependencies diagram for {}: {}", systemCode, e.getMessage());
            throw e;
        }
    }
    
    /**
     * Helper method to determine the system type based on whether the system exists in our data.
     * 
     * @param systemCode the system code to check
     * @param allDependencies the list of all systems in our organization
     * @return "IncomeSystem" if the system exists in our data, "External" otherwise
     */
    private String determineSystemType(String systemCode, List<SystemDependencyDTO> allDependencies) {
        boolean existsInOurData = allDependencies.stream()
            .anyMatch(system -> system.getSystemCode().equals(systemCode));
        return existsInOurData ? "IncomeSystem" : "External";
    }
}