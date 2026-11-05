package com.bidv.coretraining.quiz.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

/** Progress snapshot of an asynchronous AI quiz generation job. */
@Data
public class QuizGenerationJobDto {
    @JsonAlias("job_id")
    private String jobId;
    /** queued, running, completed or failed. */
    private String status;
    /** queued, retrieving, generating, verifying, finalizing, completed or failed. */
    private String stage;
    private String message;
    private int percent;
    @JsonAlias("requested_questions")
    private int requestedQuestions;
    @JsonAlias("accepted_questions")
    private int acceptedQuestions;
    private int attempt;
    @JsonAlias("max_attempts")
    private int maxAttempts;
    /** 1 = generating, 2 = verifying; phaseCount is 1 when verification is off. */
    private int phase;
    @JsonAlias("phase_count")
    private int phaseCount;
    /** Progress of the current phase, counted in questions. */
    @JsonAlias("completed_steps")
    private int completedSteps;
    @JsonAlias("total_steps")
    private int totalSteps;
    private String error;
    @JsonAlias("error_status")
    private Integer errorStatus;
    private QuizGenerationResponseDto result;
}
