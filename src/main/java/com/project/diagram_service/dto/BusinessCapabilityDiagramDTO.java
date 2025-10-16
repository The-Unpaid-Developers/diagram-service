package com.project.diagram_service.dto;

import com.project.diagram_service.dto.CommonSolutionReviewDTO;
import lombok.Data;
import java.util.List;

/**
 * Data Transfer Object for business capability solution reviews.
 * Contains system code, solution overview, and business capabilities.
 */
@Data
public class BusinessCapabilityDiagramDTO {
    private String systemCode;
    private CommonSolutionReviewDTO.SolutionOverview solutionOverview;
    private List<BusinessCapability> businessCapabilities;
    
    @Data
    public static class BusinessCapability {
        private String id;
        private String l1Capability;
        private String l2Capability;
        private String l3Capability;
        private String remarks;
    }
}