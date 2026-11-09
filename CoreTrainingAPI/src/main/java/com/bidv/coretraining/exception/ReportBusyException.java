package com.bidv.coretraining.exception;

/** Every report slot stayed taken for the whole wait. */
public class ReportBusyException extends RuntimeException {
    public static final int RETRY_AFTER_SECONDS = 15;

    public ReportBusyException() {
        super("Hệ thống đang xử lý nhiều báo cáo cùng lúc. Vui lòng thử lại sau ít phút.");
    }
}
