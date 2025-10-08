package com.project.diagram_service.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class OverallSystemDependenciesDiagramDTO {
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
        private int count;
    }
    
    @Data
    public static class MetadataDTO {
        private LocalDate generatedDate;
    }
}