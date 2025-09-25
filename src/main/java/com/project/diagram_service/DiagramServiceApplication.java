package com.project.diagram_service;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@EnableFeignClients
@Slf4j
public class DiagramServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiagramServiceApplication.class, args);
    }

    @Bean
    CommandLineRunner logEndpoints(RequestMappingHandlerMapping mapping) {
        return args -> mapping.getHandlerMethods().forEach((key, value) -> log.debug("Mapped: {}", key));
    }
}