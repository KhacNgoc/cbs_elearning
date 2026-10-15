package com.bidv.coretraining.dto;

import java.util.List;

public record DocumentOutlineDto(String documentId, List<Integer> pages,
        List<String> sections, List<Excerpt> excerpts) {
    public record Excerpt(int page, String section, String excerpt) {
    }
}
