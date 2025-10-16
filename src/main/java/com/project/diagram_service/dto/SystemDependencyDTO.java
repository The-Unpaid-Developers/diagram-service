package com.project.diagram_service.dto;

import com.project.diagram_service.dto.common.CommonSolutionReviewDTO;
import lombok.Data;
import java.util.List;

/**
 * Data Transfer Object for active Solution Reviews.
 * Contains only the essential fields: systemCode, solutionOverview, and integrationFlows.
 */
@Data
public class SystemDependencyDTO {
    private String systemCode;
    private CommonSolutionReviewDTO.SolutionOverview solutionOverview;
    private List<IntegrationFlow> integrationFlows;
    
    @Data
    public static class IntegrationFlow {
        private String id;
        private String componentName;
        private String counterpartSystemCode;
        private String counterpartSystemRole;
        private String integrationMethod;
        private String frequency;
        private String purpose;
        private String middleware;
    }
}