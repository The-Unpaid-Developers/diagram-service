package com.project.diagram_service.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Common DTO Tests")
class CommonDTOTest {

    @Test
    @DisplayName("CommonSolutionReviewDTO parent class should be instantiable")
    void testCommonSolutionReviewDTOInstantiation() {
        // Test that the parent class can be instantiated
        CommonSolutionReviewDTO dto = new CommonSolutionReviewDTO();
        assertThat(dto).isNotNull();
    }

    @Test
    @DisplayName("CommonDiagramDTO parent class should be instantiable")
    void testCommonDiagramDTOInstantiation() {
        // Test that the parent class can be instantiated
        CommonDiagramDTO dto = new CommonDiagramDTO();
        assertThat(dto).isNotNull();
    }

    @Nested
    @DisplayName("CommonSolutionReviewDTO Tests")
    class CommonSolutionReviewDTOTest {

        @Test
        @DisplayName("SolutionOverview should set and get all properties correctly")
        void testSolutionOverview() {
            // Given
            CommonSolutionReviewDTO.SolutionOverview overview = new CommonSolutionReviewDTO.SolutionOverview();
            CommonSolutionReviewDTO.SolutionDetails details = new CommonSolutionReviewDTO.SolutionDetails();
            List<String> users = Arrays.asList("user1", "user2");
            List<CommonSolutionReviewDTO.Concern> concerns = Arrays.asList(new CommonSolutionReviewDTO.Concern());

            // When
            overview.setId("id-123");
            overview.setSolutionDetails(details);
            overview.setReviewedBy("John Doe");
            overview.setReviewType("ACTIVE");
            overview.setApprovalStatus("APPROVED");
            overview.setReviewStatus("COMPLETED");
            overview.setConditions("No conditions");
            overview.setBusinessUnit("IT");
            overview.setBusinessDriver("Efficiency improvement");
            overview.setValueOutcome("Cost reduction");
            overview.setApplicationUsers(users);
            overview.setConcerns(concerns);

            // Then
            assertThat(overview.getId()).isEqualTo("id-123");
            assertThat(overview.getSolutionDetails()).isEqualTo(details);
            assertThat(overview.getReviewedBy()).isEqualTo("John Doe");
            assertThat(overview.getReviewType()).isEqualTo("ACTIVE");
            assertThat(overview.getApprovalStatus()).isEqualTo("APPROVED");
            assertThat(overview.getReviewStatus()).isEqualTo("COMPLETED");
            assertThat(overview.getConditions()).isEqualTo("No conditions");
            assertThat(overview.getBusinessUnit()).isEqualTo("IT");
            assertThat(overview.getBusinessDriver()).isEqualTo("Efficiency improvement");
            assertThat(overview.getValueOutcome()).isEqualTo("Cost reduction");
            assertThat(overview.getApplicationUsers()).isEqualTo(users);
            assertThat(overview.getConcerns()).isEqualTo(concerns);
        }

        @Test
        @DisplayName("SolutionDetails should set and get all properties correctly")
        void testSolutionDetails() {
            // Given
            CommonSolutionReviewDTO.SolutionDetails details = new CommonSolutionReviewDTO.SolutionDetails();

            // When
            details.setSolutionName("Test Solution");
            details.setProjectName("Test Project");
            details.setSolutionReviewCode("SR-001");
            details.setSolutionArchitectName("Jane Smith");
            details.setDeliveryProjectManagerName("Bob Johnson");
            details.setItBusinessPartner("Alice Brown");

            // Then
            assertThat(details.getSolutionName()).isEqualTo("Test Solution");
            assertThat(details.getProjectName()).isEqualTo("Test Project");
            assertThat(details.getSolutionReviewCode()).isEqualTo("SR-001");
            assertThat(details.getSolutionArchitectName()).isEqualTo("Jane Smith");
            assertThat(details.getDeliveryProjectManagerName()).isEqualTo("Bob Johnson");
            assertThat(details.getItBusinessPartner()).isEqualTo("Alice Brown");
        }

        @Test
        @DisplayName("Concern should set and get all properties correctly")
        void testConcern() {
            // Given
            CommonSolutionReviewDTO.Concern concern = new CommonSolutionReviewDTO.Concern();
            LocalDateTime followUpDate = LocalDateTime.now();

            // When
            concern.setId("concern-1");
            concern.setType("SECURITY");
            concern.setDescription("Security vulnerability");
            concern.setImpact("HIGH");
            concern.setDisposition("MITIGATE");
            concern.setStatus("OPEN");
            concern.setSeverity("CRITICAL");
            concern.setRaisedBy("Security Team");
            concern.setRaisedDate("2025-01-01");
            concern.setResolvedBy("Dev Team");
            concern.setResolvedDate("2025-01-15");
            concern.setComments("Fixed in latest version");
            concern.setFollowUpDate(followUpDate);

            // Then
            assertThat(concern.getId()).isEqualTo("concern-1");
            assertThat(concern.getType()).isEqualTo("SECURITY");
            assertThat(concern.getDescription()).isEqualTo("Security vulnerability");
            assertThat(concern.getImpact()).isEqualTo("HIGH");
            assertThat(concern.getDisposition()).isEqualTo("MITIGATE");
            assertThat(concern.getStatus()).isEqualTo("OPEN");
            assertThat(concern.getSeverity()).isEqualTo("CRITICAL");
            assertThat(concern.getRaisedBy()).isEqualTo("Security Team");
            assertThat(concern.getRaisedDate()).isEqualTo("2025-01-01");
            assertThat(concern.getResolvedBy()).isEqualTo("Dev Team");
            assertThat(concern.getResolvedDate()).isEqualTo("2025-01-15");
            assertThat(concern.getComments()).isEqualTo("Fixed in latest version");
            assertThat(concern.getFollowUpDate()).isEqualTo(followUpDate);
        }
    }

    @Nested
    @DisplayName("CommonDiagramDTO Tests")
    class CommonDiagramDTOTest {

        @Test
        @DisplayName("NodeDTO should set and get all properties correctly")
        void testNodeDTO() {
            // Given
            CommonDiagramDTO.NodeDTO node = new CommonDiagramDTO.NodeDTO();

            // When
            node.setId("node-1");
            node.setName("Test Node");
            node.setType("SERVICE");
            node.setCriticality("HIGH");
            node.setUrl("http://example.com");

            // Then
            assertThat(node.getId()).isEqualTo("node-1");
            assertThat(node.getName()).isEqualTo("Test Node");
            assertThat(node.getType()).isEqualTo("SERVICE");
            assertThat(node.getCriticality()).isEqualTo("HIGH");
            assertThat(node.getUrl()).isEqualTo("http://example.com");
        }

        @Test
        @DisplayName("SimpleLinkDTO should set and get all properties correctly")
        void testSimpleLinkDTO() {
            // Given
            CommonDiagramDTO.SimpleLinkDTO link = new CommonDiagramDTO.SimpleLinkDTO();

            // When
            link.setSource("source-1");
            link.setTarget("target-1");
            link.setCount(5);

            // Then
            assertThat(link.getSource()).isEqualTo("source-1");
            assertThat(link.getTarget()).isEqualTo("target-1");
            assertThat(link.getCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("DetailedLinkDTO should set and get all properties correctly")
        void testDetailedLinkDTO() {
            // Given
            CommonDiagramDTO.DetailedLinkDTO link = new CommonDiagramDTO.DetailedLinkDTO();

            // When
            link.setSource("source-1");
            link.setTarget("target-1");
            link.setPattern("REST_API");
            link.setFrequency("Daily");
            link.setRole("CONSUMER");
            link.setMiddleware("API_GATEWAY");

            // Then
            assertThat(link.getSource()).isEqualTo("source-1");
            assertThat(link.getTarget()).isEqualTo("target-1");
            assertThat(link.getPattern()).isEqualTo("REST_API");
            assertThat(link.getFrequency()).isEqualTo("Daily");
            assertThat(link.getRole()).isEqualTo("CONSUMER");
            assertThat(link.getMiddleware()).isEqualTo("API_GATEWAY");
        }

        @Test
        @DisplayName("BasicMetadataDTO should set and get all properties correctly")
        void testBasicMetadataDTO() {
            // Given
            CommonDiagramDTO.BasicMetadataDTO metadata = new CommonDiagramDTO.BasicMetadataDTO();
            LocalDate date = LocalDate.of(2025, 1, 15);

            // When
            metadata.setGeneratedDate(date);

            // Then
            assertThat(metadata.getGeneratedDate()).isEqualTo(date);
        }

        @Test
        @DisplayName("ExtendedMetadataDTO should set and get all properties correctly")
        void testExtendedMetadataDTO() {
            // Given
            CommonDiagramDTO.ExtendedMetadataDTO metadata = new CommonDiagramDTO.ExtendedMetadataDTO();
            LocalDate date = LocalDate.of(2025, 1, 15);
            List<String> middleware = Arrays.asList("API_GATEWAY", "MESSAGE_QUEUE");

            // When
            metadata.setCode("META-001");
            metadata.setReview("Comprehensive review");
            metadata.setIntegrationMiddleware(middleware);
            metadata.setGeneratedDate(date);

            // Then
            assertThat(metadata.getCode()).isEqualTo("META-001");
            assertThat(metadata.getReview()).isEqualTo("Comprehensive review");
            assertThat(metadata.getIntegrationMiddleware()).isEqualTo(middleware);
            assertThat(metadata.getGeneratedDate()).isEqualTo(date);
        }
    }
}