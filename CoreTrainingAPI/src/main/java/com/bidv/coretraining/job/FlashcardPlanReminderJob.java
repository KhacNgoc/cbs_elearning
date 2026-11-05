package com.bidv.coretraining.job;

import com.bidv.coretraining.flashcard.service.FlashcardPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** Sends due reminders in short transactions, so a peak like everyone at 20:00 spreads over a few runs. */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlashcardPlanReminderJob {
    private static final int BATCH_SIZE = 200;
    private static final int MAX_BATCHES_PER_RUN = 10;

    private final FlashcardPlanService plans;

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void run() {
        Instant now = Instant.now();
        try {
            for (int batch = 0; batch < MAX_BATCHES_PER_RUN; batch++) {
                if (plans.sendDueReminders(now, BATCH_SIZE) < BATCH_SIZE)
                    return;
            }
        } catch (RuntimeException e) {
            log.error("Flashcard study reminders failed; due plans stay due for the next run", e);
        }
    }
}
