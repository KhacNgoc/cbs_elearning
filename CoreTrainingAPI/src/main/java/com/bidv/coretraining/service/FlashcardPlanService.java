package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.FlashcardPlanDto;

import java.time.Instant;
import java.util.List;

/** Learners' flashcard study plans and the reminders they send. */
public interface FlashcardPlanService {
    List<FlashcardPlanDto.Plan> list(String username);

    FlashcardPlanDto.Plan create(FlashcardPlanDto.Request request, String username);

    FlashcardPlanDto.Plan update(Long id, FlashcardPlanDto.Request request, String username);

    void delete(Long id, String username);

    /** The plan's cards the learner can open right now, in course order. */
    FlashcardPlanDto.Session session(Long id, String username);

    /** Sends one batch of due reminders in one transaction and returns the batch's size. */
    int sendDueReminders(Instant now, int batchSize);
}
