package com.project.diagram_service.client;

import com.project.diagram_service.dto.SystemDependencyDTO;
import com.project.diagram_service.dto.BusinessCapabilityDiagramDTO;
import com.project.diagram_service.dto.BusinessCapabilityDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import java.util.List;

/**
 * Client for communicating with the Core Service API.
 *
 * This client provides methods to interact with the core service's REST endpoints,
 * specifically for retrieving system dependency information. It uses Spring RestTemplate
 * for synchronous HTTP communication, which is appropriate for traditional Spring MVC applications.
 *
 * The client is configured with a base URL from application properties and provides
 * simple, blocking HTTP operations that integrate well with the service layer.
 */
@Component
public class CoreServiceClient {
    
    private final RestTemplate restTemplate;
    private final String baseUrl;
    
    /**
     * Constructs a new CoreServiceClient with the specified base URL.
     *
     * The base URL is injected from application properties using the key
     * {@code services.core-service.url}. This allows for easy configuration
     * across different environments (dev, staging, production).
     *
     * @param baseUrl the base URL of the core service, injected from application properties
     */
    public CoreServiceClient(@Value("${services.core-service.url}") String baseUrl) {
        this.restTemplate = new RestTemplate();
        this.baseUrl = baseUrl;
    }
    
    /**
     * Retrieves all system dependencies from the core service.
     *
     * This method makes an HTTP GET request to the core service's system dependencies
     * endpoint and returns a list of all systems with their associated metadata,
     * solution overviews, and integration flows.
     *
     * The method uses RestTemplate for synchronous HTTP communication, which is
     * well-suited for traditional Spring MVC applications and provides simple
     * blocking operations.
     *
     * @return a {@link List} of {@link SystemDependencyDTO} containing all system dependency data
     * @throws org.springframework.web.client.RestClientException if the HTTP request fails
     * @throws RuntimeException if there's an error during the request or response processing
     */
    public List<SystemDependencyDTO> getSystemDependencies() {
        String url = baseUrl + "/api/v1/solution-review/system-dependencies";
        return restTemplate.exchange(
            url,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<SystemDependencyDTO>>() {}
        ).getBody();
    }
    
    /**
     * Retrieves all business capability solution reviews from the core service.
     *
     * This method makes an HTTP GET request to the core service's business capabilities
     * endpoint and returns a list of all active solution reviews with their essential fields:
     * system code, solution overview, and business capabilities.
     *
     * The method uses RestTemplate for synchronous HTTP communication and returns
     * only solution reviews with ACTIVE document state, providing business capability
     * mapping information for organizational analysis.
     *
     * @return a {@link List} of {@link BusinessCapabilityDiagramDTO} containing business capability data
     * @throws org.springframework.web.client.RestClientException if the HTTP request fails
     * @throws RuntimeException if there's an error during the request or response processing
     */
    public List<BusinessCapabilityDiagramDTO> getBusinessCapabilities() {
        String url = baseUrl + "/api/v1/solution-review/business-capabilities";
        return restTemplate.exchange(
            url,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<BusinessCapabilityDiagramDTO>>() {}
        ).getBody();
    }
    
    /**
     * Retrieves all business capabilities from the dropdown endpoint.
     *
     * This method makes an HTTP GET request to the core service's business capabilities
     * dropdown endpoint and returns a list of all business capabilities with their
     * L1, L2, and L3 hierarchy levels.
     *
     * This endpoint provides the complete set of business capabilities regardless of
     * whether they are associated with any systems, making it ideal for building a
     * comprehensive business capability tree structure.
     *
     * @return a {@link List} of {@link BusinessCapabilityDTO} containing all business capabilities
     * @throws org.springframework.web.client.RestClientException if the HTTP request fails
     * @throws RuntimeException if there's an error during the request or response processing
     */
    public List<BusinessCapabilityDTO> getAllBusinessCapabilities() {
        String url = baseUrl + "/api/v1/dropdowns/business-capabilities";
        return restTemplate.exchange(
            url,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<BusinessCapabilityDTO>>() {}
        ).getBody();
    }
}