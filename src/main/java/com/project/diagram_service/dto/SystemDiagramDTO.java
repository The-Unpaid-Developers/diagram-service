package com.project.diagram_service.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class SystemDiagramDTO {
    private List<NodeDTO> nodes;
    private List<LinkDTO> links;
    private MetadataDTO metadata;
    
    @Data
    public static class NodeDTO {
        private String id;
        private String name;
        private String type;
        private String criticality;
    }
    
    @Data
    public static class LinkDTO {
        private String source;
        private String target;
        private String pattern;
        private String frequency;
        private String role;
    }
    
    @Data
    public static class MetadataDTO {
        private String code;
        private String review;
        private List<String> integrationMiddleware;
        private LocalDate generatedDate;
    }
}