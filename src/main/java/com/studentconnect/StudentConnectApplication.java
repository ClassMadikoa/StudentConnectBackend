package com.studentconnect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableAsync   // Required for @Async in NotificationService to work
public class StudentConnectApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudentConnectApplication.class, args);
    }

    /**
     * RestTemplate bean used by NotificationService to call the Expo Push API.
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
