package com.bidv.coretraining.lesson.dto;

import java.util.List;

public record CommentReplyPageDto(List<CommentDto> content, String nextCursor, boolean hasMore) {}
