package com.project.diagram_service.dto;

import lombok.Data;

import java.util.List;

/**
 * Data Transfer Object for active Solution Reviews.
 * Contains only the essential fields: systemCode, solutionOverview, and integrationFlows.
 */
@Data
public class SystemDependencyDTO {
    private String systemCode;
    private Object solutionOverview;
    private List<Object> integrationFlows;
}