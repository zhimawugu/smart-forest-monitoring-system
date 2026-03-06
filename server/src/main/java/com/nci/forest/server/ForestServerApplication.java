package com.nci.forest.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot Application for Forest Monitoring gRPC Server
 */
@SpringBootApplication
public class ForestServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForestServerApplication.class, args);
    }
}

