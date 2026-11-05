package com.bidv.coretraining.repository;

import java.time.Instant;
import java.util.Map;

/** Set-based writes for the study plan reminder job. */
public interface FlashcardPlanNativeRepository {
    /** One batched update; a null moment means the plan has no reminder left. */
    void updateNextReminders(Map<Long, Instant> nextReminders);
}
