package com.bidv.coretraining.exception;

import com.bidv.coretraining.logging.RequestLoggingFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler({ org.springframework.dao.DataIntegrityViolationException.class,
            org.springframework.orm.ObjectOptimisticLockingFailureException.class })
    public ResponseEntity<ErrorResponse> handleConflict(Exception ex, WebRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.builder()
                .timestamp(LocalDateTime.now()).status(409).error("Conflict")
                .message(
                        "Mã hoặc thứ tự đã tồn tại, dữ liệu có liên kết hoặc đã được chỉnh sửa. Vui lòng tải lại và kiểm tra.")
                .path(request.getDescription(false).replace("uri=", "")).traceId(currentTraceId()).build());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex,
            WebRequest request) {
        log.warn("Resource not found: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .error(HttpStatus.NOT_FOUND.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .traceId(currentTraceId())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(BadRequestException ex, WebRequest request) {
        log.warn("Bad request: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .traceId(currentTraceId())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RagServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleRagUnavailable(RagServiceUnavailableException ex, WebRequest request) {
        log.error("RAG service unavailable: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .error(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .traceId(currentTraceId())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }

    // JSON is explicit because the CSV export asks for text/csv.
    @ExceptionHandler(ReportBusyException.class)
    public ResponseEntity<ErrorResponse> handleReportBusy(ReportBusyException ex, WebRequest request) {
        log.warn("Report rejected: all report slots busy");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(org.springframework.http.HttpHeaders.RETRY_AFTER, String.valueOf(ReportBusyException.RETRY_AFTER_SECONDS))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(ErrorResponse.builder().timestamp(LocalDateTime.now())
                        .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                        .error(HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase()).message(ex.getMessage())
                        .path(request.getDescription(false).replace("uri=", "")).traceId(currentTraceId()).build());
    }

    @ExceptionHandler(com.bidv.coretraining.security.LoginAttemptLimiter.LoginThrottledException.class)
    public ResponseEntity<ErrorResponse> handleLoginThrottled(
            com.bidv.coretraining.security.LoginAttemptLimiter.LoginThrottledException ex, WebRequest request) {
        log.warn("Login rejected: too many failed attempts");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(org.springframework.http.HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(ErrorResponse.builder().timestamp(LocalDateTime.now())
                        .status(HttpStatus.TOO_MANY_REQUESTS.value())
                        .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase()).message(ex.getMessage())
                        .path(request.getDescription(false).replace("uri=", "")).traceId(currentTraceId()).build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex,
            WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        log.warn("Request validation failed: {}", errors);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error("Validation Failed")
                .message(errors.toString())
                .path(request.getDescription(false).replace("uri=", ""))
                .traceId(currentTraceId())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        log.warn("Access denied: {}", ex.getMessage());
        String message = ex.getMessage();
        if (message == null || message.isBlank() || "Access Denied".equalsIgnoreCase(message)
                || "Forbidden".equalsIgnoreCase(message))
            message = "Bạn chưa có quyền thực hiện thao tác này. Vui lòng liên hệ quản trị viên.";
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error(HttpStatus.FORBIDDEN.getReasonPhrase())
                .message(message)
                .path(request.getDescription(false).replace("uri=", ""))
                .traceId(currentTraceId())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex, WebRequest request) {
        log.warn("Authentication failed: {}", ex.getMessage());
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED.value())
                .error(HttpStatus.UNAUTHORIZED.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .traceId(currentTraceId())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex, WebRequest request) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        String error = status == null ? "Request failed" : status.getReasonPhrase();
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(ex.getStatusCode().value())
                .error(error)
                .message(ex.getReason() == null ? error : ex.getReason())
                .path(request.getDescription(false).replace("uri=", ""))
                .traceId(currentTraceId())
                .build();
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex,
            WebRequest request) {
        return handleResponseStatusException(new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                "File hoặc tổng dung lượng tải lên vượt quá giới hạn cho phép"), request);
    }

    @ExceptionHandler({ org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.multipart.MultipartException.class,
            java.time.format.DateTimeParseException.class })
    public ResponseEntity<ErrorResponse> handleMalformedRequest(Exception ex, WebRequest request) {
        return clientError(HttpStatus.BAD_REQUEST, "Dữ liệu gửi lên không hợp lệ", ex, request);
    }

    @ExceptionHandler({ org.springframework.web.servlet.resource.NoResourceFoundException.class,
            org.springframework.web.servlet.NoHandlerFoundException.class })
    public ResponseEntity<ErrorResponse> handleNoRoute(Exception ex, WebRequest request) {
        return clientError(HttpStatus.NOT_FOUND, "Không tìm thấy đường dẫn yêu cầu", ex, request);
    }

    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleWrongMethod(Exception ex, WebRequest request) {
        return clientError(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức không được hỗ trợ", ex, request);
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleWrongContentType(Exception ex, WebRequest request) {
        return clientError(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Định dạng dữ liệu gửi lên không được hỗ trợ", ex, request);
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleNotAcceptable(Exception ex, WebRequest request) {
        return clientError(HttpStatus.NOT_ACCEPTABLE, "Không có định dạng phản hồi phù hợp", ex, request);
    }

    // The content type is explicit because the client's Accept header may be what caused the error.
    private ResponseEntity<ErrorResponse> clientError(HttpStatus status, String message, Exception ex, WebRequest request) {
        log.warn("Client request rejected status={} cause={}", status.value(), ex.getMessage());
        return ResponseEntity.status(status).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(ErrorResponse.builder().timestamp(LocalDateTime.now()).status(status.value())
                        .error(status.getReasonPhrase()).message(message)
                        .path(request.getDescription(false).replace("uri=", "")).traceId(currentTraceId()).build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(Exception ex, WebRequest request) {
        log.error("Unhandled application error", ex);
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message("Hệ thống đang gặp sự cố. Vui lòng thử lại sau.")
                .path(request.getDescription(false).replace("uri=", ""))
                .traceId(currentTraceId())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String currentTraceId() {
        return MDC.get(RequestLoggingFilter.TRACE_ID_MDC_KEY);
    }
}
