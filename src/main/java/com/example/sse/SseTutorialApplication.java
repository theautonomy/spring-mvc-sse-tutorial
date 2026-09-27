package com.example.sse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling // used by the chat heartbeat in step 5
@SpringBootApplication
public class SseTutorialApplication {

    public static void main(String[] args) {
        SpringApplication.run(SseTutorialApplication.class, args);
    }
}
