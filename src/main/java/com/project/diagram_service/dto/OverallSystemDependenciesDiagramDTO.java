package com.project.diagram_service.dto;

import com.project.diagram_service.dto.CommonDiagramDTO;
import lombok.Data;
import java.util.List;

@Data
public class OverallSystemDependenciesDiagramDTO {
    private List<CommonDiagramDTO.NodeDTO> nodes;
    private List<CommonDiagramDTO.SimpleLinkDTO> links;
    private CommonDiagramDTO.BasicMetadataDTO metadata;
}