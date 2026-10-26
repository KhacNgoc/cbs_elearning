package com.bidv.coretraining.dto;

import java.util.List;

public record CommentPageDto(List<CommentDto> content, int page, boolean last, long totalElements) {}
