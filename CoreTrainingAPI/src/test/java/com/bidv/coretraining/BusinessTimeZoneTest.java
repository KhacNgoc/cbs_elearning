package com.bidv.coretraining;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessTimeZoneTest {
    @Test
    void businessDatesFollowVietnamTimeEvenWhenTheHostRunsUtc() {
        TimeZone previous = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            CoreTrainingApiApplication.useBusinessTimeZone();
            assertThat(ZoneId.systemDefault()).isEqualTo(ZoneId.of("Asia/Ho_Chi_Minh"));
            assertThat(LocalDate.now()).isEqualTo(LocalDate.now(Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"))));
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
