package com.medievalmarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MedievalMarketApplication {
    public static void main(String[] args) {
        SpringApplication.run(MedievalMarketApplication.class, args);
    }
}
