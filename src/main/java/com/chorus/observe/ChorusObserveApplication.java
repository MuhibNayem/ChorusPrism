package com.chorus.observe;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone Spring Boot application entry point for Chorus Observe Server.
 * Can also be imported as a library via {@link com.chorus.observe.config.ChorusObserveAutoConfiguration}.
 */
@SpringBootApplication
public class ChorusObserveApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChorusObserveApplication.class, args);
    }
}
