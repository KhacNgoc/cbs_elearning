package com.bidv.coretraining.dto;

import com.bidv.coretraining.enums.FlashcardType;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.util.List;

@Data
public class GeneratedFlashcardDto {
    private FlashcardType type;
    private String front;
    private String back;
    @JsonAlias("source_ids")
    private List<String> sourceIds;
}
