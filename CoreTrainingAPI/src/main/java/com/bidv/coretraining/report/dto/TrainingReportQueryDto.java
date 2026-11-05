package com.bidv.coretraining.report.dto;
public final class TrainingReportQueryDto {
 private TrainingReportQueryDto() {}
    public record Filter(String from, String to, String unit, String program, String course,
            String status,
            String view, String action, String search, int page, int size, String dimension, String key) {
        public static Filter defaults() {
            return new Filter("", "", "", "", "", "", "completion", "", "", 0, 20, "", "");
        }
    }

}
