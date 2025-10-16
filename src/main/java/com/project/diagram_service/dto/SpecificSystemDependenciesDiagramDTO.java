package com.project.diagram_service.dto;

import com.project.diagram_service.dto.CommonDiagramDTO;
import lombok.Data;
import java.util.List;

@Data
public class SpecificSystemDependenciesDiagramDTO {
    private List<CommonDiagramDTO.NodeDTO> nodes;
    private List<CommonDiagramDTO.DetailedLinkDTO> links;
    private CommonDiagramDTO.ExtendedMetadataDTO metadata;
}