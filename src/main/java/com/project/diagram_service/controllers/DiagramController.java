package com.project.diagram_service.controllers;

import com.project.diagram_service.dto.SystemDependencyDTO;
import com.project.diagram_service.services.DiagramService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/v1/diagram")
@Slf4j
public class DiagramController {
    
    private final DiagramService diagramService;
    
    @Autowired
    public DiagramController(DiagramService diagramService) {
        this.diagramService = diagramService;
    }
    
    @GetMapping("/system-dependencies")
    public ResponseEntity<List<SystemDependencyDTO>> getSystemDependencies() {
        log.info("Received request for system dependencies");
        
        try {
            List<SystemDependencyDTO> dependencies = diagramService.getSystemDependencies();
            return ResponseEntity.ok(dependencies);
        } catch (Exception e) {
            log.error("Error getting system dependencies: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}