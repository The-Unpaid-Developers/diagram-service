package com.project.diagram_service.dto;

import lombok.Data;
import java.util.List;

/**
 * DTO for business capabilities tree structure used in hierarchical diagrams.
 * This structure supports collapsible tree visualizations with D3.js.
 */
@Data
public class BusinessCapabilitiesTreeDTO {
    
    private List<BusinessCapabilityNode> capabilities;
    
    /**
     * Represents a node in the business capabilities tree.
     * Can be either a capability level (L1, L2, L3) or a system.
     * id = unique identifier for the node showing parents
     * systemCode = only for system nodes, null for capability levels
     * For systems: name = solutionName
     * For capabilities: name = capability name
     */
    @Data
    public static class BusinessCapabilityNode {
        private String id;
        private String systemCode; // Only for system nodes, null for capability levels
        private String name;
        private String level; // "L1", "L2", "L3", or "System"
        private String parentId;
        private Integer systemCount; // Only for capability levels, null for systems
    }
}