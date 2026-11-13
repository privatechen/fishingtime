package com.fishingtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FishingTimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(FishingTimeApplication.class, args);
    }
}
