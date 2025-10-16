package com.project.diagram_service.dto.common;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Common data transfer objects shared across different solution review DTOs.
 * These classes represent the standard structure of solution reviews in the core service.
 */
public class CommonSolutionReviewDTO {

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
        private String severity;
        private String raisedBy;
        private String raisedDate;
        private String resolvedBy;
        private String resolvedDate;
        private String comments;
        private LocalDateTime followUpDate;
    }
}