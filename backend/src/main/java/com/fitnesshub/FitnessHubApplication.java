package com.fitnesshub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FitnessHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(FitnessHubApplication.class, args);
    }
}
