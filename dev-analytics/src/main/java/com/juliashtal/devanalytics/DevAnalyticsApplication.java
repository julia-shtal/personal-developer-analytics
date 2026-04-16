package com.juliashtal.devanalytics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DevAnalyticsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevAnalyticsApplication.class, args);
    }

}
