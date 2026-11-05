package com.bidv.coretraining.rag.service;

import org.springframework.http.*;

/** Business operations for RagIngestionService. */
public interface RagIngestionService {
    public void deleteIndex(String documentId, String courseId);

    public void ingestAsync(String documentId);
}
