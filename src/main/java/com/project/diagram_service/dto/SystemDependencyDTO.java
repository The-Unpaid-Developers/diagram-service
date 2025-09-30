package com.project.diagram_service.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Data Transfer Object for active Solution Reviews.
 * Contains only the essential fields: systemCode, solutionOverview, and integrationFlows.
 */
@Data
public class SystemDependencyDTO {
    private String systemCode;
    private SolutionOverview solutionOverview;
    private List<IntegrationFlow> integrationFlows;
    
    @Data
    public static class SolutionOverview {
        private String id;
        private SolutionDetails solutionDetails;
        private String reviewedBy;
        private String reviewType;
        private String approvalStatus;
        private String reviewStatus;
        private String conditions;
        private String businessUnit;
        private String businessDriver;
        private String valueOutcome;
        private List<String> applicationUsers;
        private List<Concern> concerns;
    }
    
    @Data
    public static class SolutionDetails {
        private String solutionName;
        private String projectName;
        private String solutionReviewCode;
        private String solutionArchitectName;
        private String deliveryProjectManagerName;
        private String itBusinessPartner;
    }
    
    @Data
    public static class Concern {
        private String id;
        private String type;
        private String description;
        private String impact;
        private String disposition;
        private String status;
        private LocalDateTime followUpDate;
    }
    
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