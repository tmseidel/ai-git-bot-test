package com.example.tasktracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AppBeans {

    /**
     * Clock is a bean so services can be tested with
     * {@code Clock.fixed(...)} without changing the constructor signature.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
