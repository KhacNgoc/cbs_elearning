package com.bidv.coretraining.dto;

import java.util.List;

public record CommentReplyPageDto(List<CommentDto> content, String nextCursor, boolean hasMore) {}
