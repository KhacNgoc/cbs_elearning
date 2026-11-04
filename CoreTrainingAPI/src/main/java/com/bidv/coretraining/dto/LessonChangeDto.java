package com.bidv.coretraining.dto;

/** Requests and responses of the lesson change-log endpoints. */
public final class LessonChangeDto {
    private LessonChangeDto() {
    }

    public record AiSummaryRequest(String oldContent, String newContent) {
    }

    public record ChangeNotifyRequest(String changeSummary, Boolean notifyLearners) {
    }

    public record CompletedLearnersCount(int count) {
    }

    public record AiSummary(String summary) {
    }

    public record NotifyResult(int notifiedCount) {
    }
}
