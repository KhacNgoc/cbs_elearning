package com.bidv.coretraining.dto;

import com.bidv.coretraining.dto.*;
import java.util.*;

/** Data contracts for RagChatService. */
public final class RagChatDto {
    private RagChatDto() {}

    public record HistoryPage(List<RagChatHistoryDto> items,Long nextBeforeId,boolean hasMore) {}
}
