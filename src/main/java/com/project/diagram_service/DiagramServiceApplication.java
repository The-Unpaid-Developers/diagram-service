package com.project.diagram_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@Slf4j
public class DiagramServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiagramServiceApplication.class, args);
        log.info("Diagram Service started successfully!");
    }
}