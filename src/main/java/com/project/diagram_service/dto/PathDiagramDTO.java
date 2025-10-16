package com.project.diagram_service.dto;

import com.project.diagram_service.dto.common.CommonDiagramDTO;
import lombok.Data;
import java.util.List;

/**
 * DTO for path finding diagrams that stores middleware as link metadata
 * instead of creating separate middleware nodes. This prevents circular
 * dependencies while maintaining all middleware information.
 */
@Data
public class PathDiagramDTO {
    private List<CommonDiagramDTO.NodeDTO> nodes;
    private List<PathLinkDTO> links;
    private CommonDiagramDTO.ExtendedMetadataDTO metadata;
    
    /**
     * Extended link DTO for path diagrams that includes middleware information
     */
    @Data
    public static class PathLinkDTO {
        private String source;
        private String target;
        private String pattern;
        private String frequency;
        private String role;
        private String middleware;
    }
}