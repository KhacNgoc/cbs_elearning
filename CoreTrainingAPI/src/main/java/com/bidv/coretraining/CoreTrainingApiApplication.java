package com.bidv.coretraining;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CoreTrainingApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(CoreTrainingApiApplication.class, args);
    }
}
