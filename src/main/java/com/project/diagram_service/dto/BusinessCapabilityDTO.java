package com.project.diagram_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a business capability with L1, L2, and L3 levels.
 * Used for retrieving all business capabilities from the dropdown endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusinessCapabilityDTO {
    private String l1;
    private String l2;
    private String l3;
}
