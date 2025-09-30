package com.project.diagram_service.client;

import com.project.diagram_service.dto.SystemDependencyDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;

/**
 * Client for communicating with the Core Service API.
 *
 * This client provides methods to interact with the core service's REST endpoints,
 * specifically for retrieving system dependency information. It uses Spring WebClient
 * for reactive HTTP communication.
 *
 * The client is configured with a base URL from application properties and handles
 * the conversion between reactive and blocking operations for ease of use in the
 * service layer.
 */
@Component
public class CoreServiceClient {
    
    private final WebClient webClient;
    
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
        this.webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .build();
    }
    
    /**
     * Retrieves all system dependencies from the core service.
     *
     * This method makes an HTTP GET request to the core service's system dependencies
     * endpoint and returns a list of all systems with their associated metadata,
     * solution overviews, and integration flows.
     *
     * The method uses WebClient's reactive capabilities but blocks to convert
     * the response to a synchronous operation for easier consumption by the service layer.
     *
     * @return a {@link List} of {@link SystemDependencyDTO} containing all system dependency data
     * @throws org.springframework.web.reactive.function.client.WebClientException if the HTTP request fails
     * @throws RuntimeException if there's an error during the request or response processing
     */
    public List<SystemDependencyDTO> getSystemDependencies() {
        return webClient.get()
            .uri("/api/v1/solution-review/system-dependencies")
            .retrieve()
            .bodyToFlux(SystemDependencyDTO.class)
            .collectList()
            .block(); // Convert reactive to blocking for simplicity
    }
}