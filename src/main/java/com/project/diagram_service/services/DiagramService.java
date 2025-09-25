package com.project.diagram_service.services;

import com.project.diagram_service.client.CoreServiceClient;
import com.project.diagram_service.dto.SystemDependencyDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@Slf4j
public class DiagramService {
    
    private final CoreServiceClient coreServiceClient;
    
    @Autowired
    public DiagramService(CoreServiceClient coreServiceClient) {
        this.coreServiceClient = coreServiceClient;
    }
    
    public List<SystemDependencyDTO> getSystemDependencies() {
        log.info("Calling core service for system dependencies");
        
        try {
            List<SystemDependencyDTO> result = coreServiceClient.getSystemDependencies();
            log.info("Retrieved {} system dependencies", result.size());
            return result;
        } catch (Exception e) {
            log.error("Error calling core service: {}", e.getMessage());
            throw e;
        }
    }
}