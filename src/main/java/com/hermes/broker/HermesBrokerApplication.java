package com.hermes.broker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class HermesBrokerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HermesBrokerApplication.class, args);
    }
}
