package com.bidv.coretraining.document.dto;

/** Responses of upload endpoints; field names match the JSON the frontend reads. */
public final class UploadResultDto {
    private UploadResultDto() {
    }

    public record Video(String videoUrl) {
    }

    public record Image(String thumbnail) {
    }
}
