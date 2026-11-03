package com.bidv.coretraining.controller;

import com.bidv.coretraining.dto.FlashcardDto;
import com.bidv.coretraining.enums.FlashcardRating;
import com.bidv.coretraining.enums.FlashcardType;
import com.bidv.coretraining.service.FlashcardLearningService;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import com.bidv.coretraining.security.CustomUserDetails;
import com.bidv.coretraining.entity.User;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class FlashcardControllerContractTest {
    @Test void dueTimesAreAbsoluteInstantsAndReviewsBindRatings() throws Exception {
        var service = mock(FlashcardLearningService.class);
        var progress = new FlashcardDto.Progress(Instant.parse("2026-11-03T07:50:00Z"), 0, 0, 1,
                FlashcardRating.AGAIN, false, false);
        var card = new FlashcardDto.Card(5L, "C1", "L1", "Bài 1", FlashcardType.TERM, "CIF", "Mã khách hàng",
                null, progress, false, null);
        when(service.review(5L, FlashcardRating.AGAIN, "learner")).thenReturn(card);
        when(service.lessonDeck("L1", "learner")).thenReturn(new FlashcardDto.Deck("L1", List.of(card),
                new FlashcardDto.Stats(1, 0, 0, 1, 0)));
        var mapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        var mvc = MockMvcBuilders.standaloneSetup(new FlashcardController(service))
                .setCustomArgumentResolvers(new LearnerPrincipal())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();

        mvc.perform(post("/api/flashcards/5/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":\"AGAIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progress.dueAt").value("2026-11-03T07:50:00Z"));
        mvc.perform(post("/api/flashcards/5/reviews").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/lessons/L1/flashcards"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stats.learningCount").value(1));
        verify(service).review(5L, FlashcardRating.AGAIN, "learner");
    }

    @Test void personalCardEndpointsValidateAndBindTheLearner() throws Exception {
        var service = mock(FlashcardLearningService.class);
        var card = new FlashcardDto.Card(9L, "C1", "L1", "Bài 1", FlashcardType.TERM, "GL", "Sổ cái",
                null, null, true, 0L);
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
