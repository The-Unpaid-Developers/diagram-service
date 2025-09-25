package com.project.diagram_service.client;

import com.project.diagram_service.dto.SystemDependencyDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;

@Component
public class CoreServiceClient {
    
    private final WebClient webClient;
    
    public CoreServiceClient(@Value("${services.core-service.url}") String baseUrl) {
        this.webClient = WebClient.builder()
            .baseUrl(baseUrl)
            .build();
    }
    
    public List<SystemDependencyDTO> getSystemDependencies() {
        return webClient.get()
            .uri("/api/v1/solution-review/system-dependencies")
            .retrieve()
            .bodyToFlux(SystemDependencyDTO.class)
            .collectList()
            .block(); // Convert reactive to blocking for simplicity
    }
}