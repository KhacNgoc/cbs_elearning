package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.FlashcardDto;
import com.bidv.coretraining.enums.FlashcardType;
import com.bidv.coretraining.service.FlashcardLearningService;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import com.bidv.coretraining.security.CustomUserDetails;
import com.bidv.coretraining.entity.User;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FlashcardControllerContractTest {
    @Test void lessonDeckReturnsCardsWithoutReviewState() throws Exception {
        var service = mock(FlashcardLearningService.class);
        var card = new FlashcardDto.Card(5L, "C1", "L1", "Bài 1", FlashcardType.TERM, "CIF", "Mã khách hàng",
                null, false, null);
        when(service.lessonDeck("L1", "learner")).thenReturn(new FlashcardDto.Deck("L1", List.of(card)));
        var mvc = MockMvcBuilders.standaloneSetup(new FlashcardController(service))
                .setCustomArgumentResolvers(new LearnerPrincipal()).build();

        mvc.perform(get("/api/lessons/L1/flashcards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards[0].front").value("CIF"))
                .andExpect(jsonPath("$.cards[0].progress").doesNotExist())
                .andExpect(jsonPath("$.stats").doesNotExist());
    }

    @Test void personalCardEndpointsValidateAndBindTheLearner() throws Exception {
        var service = mock(FlashcardLearningService.class);
        var card = new FlashcardDto.Card(9L, "C1", "L1", "Bài 1", FlashcardType.TERM, "GL", "Sổ cái",
                null, true, 0L);
        when(service.createPersonal(eq("L1"), any(), eq("learner"))).thenReturn(card);
        when(service.updatePersonal(eq(9L), any(), eq("learner"))).thenReturn(card);
        var mvc = MockMvcBuilders.standaloneSetup(new FlashcardController(service))
                .setCustomArgumentResolvers(new LearnerPrincipal()).build();

        mvc.perform(post("/api/lessons/L1/flashcards/mine").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TERM\",\"front\":\"GL\",\"back\":\"Sổ cái\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mine").value(true)).andExpect(jsonPath("$.version").value(0));
        mvc.perform(post("/api/lessons/L1/flashcards/mine").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TERM\",\"front\":\" \",\"back\":\"x\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/flashcards/mine/9").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"TERM\",\"front\":\"GL\",\"back\":\"Sổ cái\",\"version\":0}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/flashcards/mine/9")).andExpect(status().isNoContent());
        verify(service).deletePersonal(9L, "learner");
        verify(service, times(1)).createPersonal(eq("L1"), any(), eq("learner"));
    }

    private static final class LearnerPrincipal implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                NativeWebRequest request, WebDataBinderFactory binderFactory) {
            return new CustomUserDetails(User.builder().username("learner").password("x").build());
        }
    }
}
