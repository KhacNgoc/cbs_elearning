package com.bidv.coretraining.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

/**
 * Progress snapshot of an asynchronous AI flashcard job. The RAG service shares its
 * job model with quiz generation, so its counters arrive as "questions".
 */
@Data
public class FlashcardGenerationJobDto {
    @JsonAlias("job_id")
    private String jobId;
    /** queued, running, completed or failed. */
    private String status;
    /** queued, retrieving, generating, verifying, finalizing, completed or failed. */
    private String stage;
    private String message;
    private int percent;
    @JsonAlias("requested_questions")
    private int requestedCards;
    @JsonAlias("accepted_questions")
    private int acceptedCards;
    private int attempt;
    @JsonAlias("max_attempts")
    private int maxAttempts;
    private int phase;
    @JsonAlias("phase_count")
    private int phaseCount;
    @JsonAlias("completed_steps")
    private int completedSteps;
    @JsonAlias("total_steps")
    private int totalSteps;
    private String error;
    @JsonAlias("error_status")
    private Integer errorStatus;
    private FlashcardGenerationResultDto result;
}
