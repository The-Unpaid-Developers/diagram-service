package com.project.diagram_service.controllers;

import com.project.diagram_service.dto.SystemDependencyDTO;
import com.project.diagram_service.dto.BusinessCapabilityDiagramDTO;
import com.project.diagram_service.dto.BusinessCapabilitiesTreeDTO;
import com.project.diagram_service.dto.OverallSystemDependenciesDiagramDTO;
import com.project.diagram_service.dto.SpecificSystemDependenciesDiagramDTO;
import com.project.diagram_service.dto.PathDiagramDTO;
import com.project.diagram_service.services.DiagramService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/diagram")
@Slf4j
public class DiagramController {
    
    private final DiagramService diagramService;
    
    public DiagramController(DiagramService diagramService) {
        this.diagramService = diagramService;
    }
    
    /**
     * Retrieves all system dependencies from the core service.
     *
     * This endpoint returns a comprehensive list of all systems in the organization
     * along with their solution overviews, integration flows, and related metadata.
     *
     * The response includes detailed information about each system including:
     *   System identification and business details
     *   Solution review status and approval information
     *   Integration flows and counterpart system relationships
     *   Concerns and follow-up items
     *
     * @return a {@link ResponseEntity} containing a list of {@link SystemDependencyDTO}
     *         with HTTP 200 on success, or HTTP 500 on internal server error
     */
    @GetMapping("/system-dependencies")
    public ResponseEntity<List<SystemDependencyDTO>> getSystemDependencies() {
        log.info("Received request for system dependencies");
        
        try {
            List<SystemDependencyDTO> dependencies = diagramService.getSystemDependencies();
            return ResponseEntity.ok(dependencies);
        } catch (Exception e) {
            log.error("Error getting system dependencies: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Retrieves all business capability solution reviews from the core service.
     *
     * This endpoint returns a comprehensive list of all active solution reviews in the organization
     * along with their business capabilities, solution overviews, and related metadata.
     *
     * The response includes detailed information about each system including:
     *   System identification and business details
     *   Solution review status and approval information
     *   Business capability mappings (L1, L2, L3 capabilities)
     *   Solution architecture and project management details
     *   Concerns and follow-up items
     *
     * Only solution reviews with ACTIVE document state are returned, providing
     * current business capability mapping for organizational analysis and reporting.
     *
     * @return a {@link ResponseEntity} containing a list of {@link BusinessCapabilityDiagramDTO}
     *         with HTTP 200 on success, or HTTP 500 on internal server error
     */
    @GetMapping("/business-capabilities")
    public ResponseEntity<List<BusinessCapabilityDiagramDTO>> getBusinessCapabilities() {
        log.info("Received request for business capabilities");
        
        try {
            List<BusinessCapabilityDiagramDTO> capabilities = diagramService.getBusinessCapabilities();
            return ResponseEntity.ok(capabilities);
        } catch (Exception e) {
            log.error("Error getting business capabilities: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Retrieves all business capabilities in a hierarchical tree structure.
     *
     * This endpoint returns business capabilities organized in a tree format suitable
     * for collapsible tree visualizations using D3.js or similar frameworks.
     *
     * The response includes:
     *   L1, L2, L3 capability levels with system counts
     *   System-level nodes with detailed metadata
     *   Parent-child relationships for tree structure
     *
     * Currently returns mock data structure until proper business capability
     * hierarchy is implemented in the core service.
     *
     * @return a {@link ResponseEntity} containing a {@link BusinessCapabilitiesTreeDTO}
     *         with HTTP 200 on success, or HTTP 500 on internal server error
     */
    @GetMapping("/business-capabilities/all")
    public ResponseEntity<BusinessCapabilitiesTreeDTO> getAllBusinessCapabilitiesTree() {
        log.info("Received request for business capabilities tree");
        
        try {
            BusinessCapabilitiesTreeDTO tree = diagramService.getBusinessCapabilitiesTree();
            return ResponseEntity.ok(tree);
        } catch (Exception e) {
            log.error("Error getting business capabilities tree: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Generates and retrieves a system dependencies diagram for a specific system.
     *
     * This endpoint creates a visual representation of system dependencies by analyzing
     * the relationships between the specified system and all other systems in the organization.
     *
     * The generated diagram includes:
     *   Nodes: Representing systems with different types (PRIMARY, DEPENDENT, TARGET)
     *   Links: Showing integration flows with patterns, frequencies, and roles
     *   Metadata: Including system codes, review information, and middleware details
     *
     * The analysis considers both incoming dependencies (systems that depend on the target system)
     * and outgoing dependencies (systems that the target system depends on).
     *
     * @param systemCode the unique identifier of the system to generate the diagram for
     * @return a {@link ResponseEntity} containing a {@link SpecificSystemDependenciesDiagramDTO} with the complete diagram
     *         structure, HTTP 200 on success, or HTTP 500 on internal server error
     * @throws RuntimeException if the specified system code is not found
     */
    @GetMapping("/system-dependencies/{systemCode}")
    public ResponseEntity<SpecificSystemDependenciesDiagramDTO> getSystemDependenciesDiagram(@PathVariable String systemCode) {
        log.info("Received request for system dependencies diagram for system: {}", systemCode);
        
        try {
            SpecificSystemDependenciesDiagramDTO diagram = diagramService.generateSystemDependenciesDiagram(systemCode);
            return ResponseEntity.ok(diagram);
        } catch (Exception e) {
            log.error("Error generating system dependencies diagram for {}: {}", systemCode, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/system-dependencies/all")
    public ResponseEntity<OverallSystemDependenciesDiagramDTO> getAllSystemDependenciesDiagrams() {
        log.info("Received request for all system dependencies diagrams");
        
        try {
            OverallSystemDependenciesDiagramDTO diagram = diagramService.generateAllSystemDependenciesDiagrams();
            return ResponseEntity.ok(diagram);
        } catch (Exception e) {
            log.error("Error generating all system dependencies diagrams: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    
    /**
     * Finds all integration paths between two systems and returns them as a diagram.
     *
     * This endpoint performs path finding analysis to discover all possible integration
     * routes between a source and target system. It uses depth-first search with loop
     * prevention to identify both direct connections and paths through intermediate systems.
     *
     * The generated diagram includes:
     *   All systems and middleware components in the discovered paths
     *   Integration links showing data flow patterns and frequencies
     *   Path metadata including route information and middleware usage
     *
     * Path finding considers:
     *   Direct system-to-system integration flows
     *   Paths through middleware components (API gateways, message brokers, etc.)
     *   Multi-hop paths through intermediate systems
     *   Producer-consumer relationships and data flow direction
     *
     * @param start the source system code to start path finding from
     * @param end the target system code to find paths to
     * @return a {@link ResponseEntity} containing a {@link PathDiagramDTO} with all discovered paths
     *         visualized as a diagram, HTTP 200 on success, or HTTP 500 on internal server error
     * @throws IllegalArgumentException if either system code is invalid or systems are the same
     */
    @GetMapping("/system-dependencies/path")
    public ResponseEntity<PathDiagramDTO> findPathsBetweenSystems(
            @RequestParam String start, 
            @RequestParam String end) {
        log.info("Received request to find paths from {} to {}", start, end);
        
        try {
            PathDiagramDTO pathDiagram = diagramService.findAllPathsDiagram(start, end);
            return ResponseEntity.ok(pathDiagram);
        } catch (IllegalArgumentException e) {
            log.error("Invalid request for path finding from {} to {}: {}", start, end, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error finding paths from {} to {}: {}", start, end, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}