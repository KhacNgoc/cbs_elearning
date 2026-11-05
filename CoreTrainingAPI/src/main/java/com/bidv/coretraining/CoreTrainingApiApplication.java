package com.bidv.coretraining;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.TimeZone;

@SpringBootApplication
@EnableAsync
public class CoreTrainingApiApplication {
    /**
     * Business timestamps are stored without a zone and business dates come from LocalDate.now(), so the JVM zone
     * decides what they mean. Pinned here because container images default to UTC.
     */
    public static final String BUSINESS_TIME_ZONE = "Asia/Ho_Chi_Minh";

    public static void main(String[] args) {
        useBusinessTimeZone();
        SpringApplication.run(CoreTrainingApiApplication.class, args);
    }

    static void useBusinessTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(BUSINESS_TIME_ZONE));
    }
}
