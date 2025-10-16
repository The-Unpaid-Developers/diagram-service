package com.project.diagram_service.dto;

import lombok.Data;
import java.time.LocalDate;
import java.util.List;

/**
 * Common DTO classes for diagram-related data structures.
 * This package contains shared classes used across multiple diagram DTOs
 * to eliminate code duplication and improve maintainability.
 */
public class CommonDiagramDTO {

    /**
     * Standard node representation for diagrams
     */
    @Data
    public static class NodeDTO {
        private String id;
        private String name;
        private String type;
        private String criticality;
        private String url; // Optional - not all diagrams need this
    }

    /**
     * Basic link representation for simple count-based connections
     */
    @Data
    public static class SimpleLinkDTO {
        private String source;
        private String target;
        private int count;
    }

    /**
     * Detailed link representation for complex integration patterns
     */
    @Data
    public static class DetailedLinkDTO {
        private String source;
        private String target;
        private String pattern;
        private String frequency;
        private String role;
        private String middleware; // Optional - for path diagrams
    }

    /**
     * Basic metadata for diagrams
     */
    @Data
    public static class BasicMetadataDTO {
        private LocalDate generatedDate;
    }

    /**
     * Extended metadata for detailed diagrams
     */
    @Data
    public static class ExtendedMetadataDTO {
        private String code;
        private String review;
        private List<String> integrationMiddleware;
        private LocalDate generatedDate;
    }
}