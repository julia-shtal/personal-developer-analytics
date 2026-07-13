package com.juliashtal.devanalytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot entry point. Enables scheduling for the metric, token-cleanup, and AI brief jobs.
 */
@SpringBootApplication
@EnableScheduling
public class DevAnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevAnalyticsApplication.class, args);
    }

}
