package com.bidv.coretraining.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    @RestController static class RejectionController {
        @GetMapping("/invalid-codec") void invalid() { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Codec không hỗ trợ"); }
        @GetMapping("/too-large") void oversized() { throw new MaxUploadSizeExceededException(500_000_000); }
    }
    @Test void preservesValidationMessageAndStatus() throws Exception {
        MockMvcBuilders.standaloneSetup(new RejectionController()).setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(get("/invalid-codec")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Codec không hỗ trợ"));
    }
    @Test void uploadLimitReturns413() throws Exception {
        MockMvcBuilders.standaloneSetup(new RejectionController()).setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(get("/too-large")).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value("File hoặc tổng dung lượng tải lên vượt quá giới hạn cho phép"));
    }
}
