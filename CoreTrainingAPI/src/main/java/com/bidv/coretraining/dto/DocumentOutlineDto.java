package com.bidv.coretraining.dto;

import java.util.List;

/**
 * Indexed structure of a course document. {@code sections} are in document order;
 * {@code headings} are PDF headings with suggested page ranges (empty for Word).
 */
public record DocumentOutlineDto(String documentId, List<Integer> pages,
        List<String> sections, List<Excerpt> excerpts, List<Heading> headings) {
    public record Excerpt(int page, String section, String heading, String excerpt) {
    }

    public record Heading(String section, int level, int pageFrom, int pageTo) {
    }
}
