package com.bidv.coretraining.repository.projection;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** What sending one study plan's reminder needs, including its recipient. */
public interface FlashcardDueReminderView {
    Long getId();
    String getName();
    LocalDate getStartDate();
    LocalDate getEndDate();
    Integer getWeekdayMask();
    LocalTime getReminderTime();
    Boolean getActive();
    Instant getNextReminderAt();
    Long getUserId();
    String getUsername();
    String getUserStatus();
}
