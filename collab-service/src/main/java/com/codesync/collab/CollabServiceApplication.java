package com.codesync.collab;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
@org.springframework.scheduling.annotation.EnableScheduling
public class CollabServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CollabServiceApplication.class, args);
    }
}
