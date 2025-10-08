package com.project.diagram_service.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO for path finding diagrams that stores middleware as link metadata
 * instead of creating separate middleware nodes. This prevents circular
 * dependencies while maintaining all middleware information.
 */
@Data
public class PathDiagramDTO {
    private List<NodeDTO> nodes;
    private List<LinkDTO> links;
    private MetadataDTO metadata;
    
    @Data
    public static class NodeDTO {
        private String id;
        private String name;
        private String type;
        private String criticality;
        private String url;
    }
    
    @Data
    public static class LinkDTO {
        private String source;
        private String target;
        private String pattern;
        private String frequency;
        private String role;
        private String middleware;
    }
    
    @Data
    public static class MetadataDTO {
        private String code;
        private String review;
        private List<String> integrationMiddleware;
        private LocalDate generatedDate;
    }
}