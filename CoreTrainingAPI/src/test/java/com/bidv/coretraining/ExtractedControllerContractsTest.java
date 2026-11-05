package com.bidv.coretraining;

import com.bidv.coretraining.learner.controller.LearnerDeadlineController;
import com.bidv.coretraining.learner.dto.LearnerDeadlineDto.*;
import com.bidv.coretraining.learner.service.LearnerDeadlineService;
import com.bidv.coretraining.lesson.controller.LessonKnowledgeSourceController;
import com.bidv.coretraining.lesson.dto.LessonKnowledgeSourceDto;
import com.bidv.coretraining.lesson.service.LessonKnowledgeSourceService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.LocalDate;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExtractedControllerContractsTest {
    @Test void deadlineBindingAndResponseFieldsRemainUnchanged() throws Exception {
        var service = mock(LearnerDeadlineService.class);
        var date = LocalDate.of(2027, 1, 1);
        when(service.list(7L)).thenReturn(List.of(new Deadline("course", "Course", date, date, false)));
        var mapper = Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        var mvc = MockMvcBuilders.standaloneSetup(new LearnerDeadlineController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
        mvc.perform(get("/api/admin/users/7/course-deadlines"))
                .andExpect(status().isOk()).andExpect(content().json("""
                        [{"courseId":"course","courseName":"Course","dueDate":"2027-01-01",
                          "defaultDate":"2027-01-01","overridden":false}]
                        """));
        mvc.perform(put("/api/admin/users/7/course-deadlines").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"course\",\"dueDate\":null}"))
                .andExpect(status().isOk()).andExpect(content().string(""));
        verify(service).save(7L, new Request("course", null));
    }

    @Test void knowledgeSourceRoutesUseAuthorizedServiceEntrypoints() throws Exception {
        var service = mock(LessonKnowledgeSourceService.class);
        var source = new LessonKnowledgeSourceDto("doc", 1, 2, null);
        when(service.listAuthorized("lesson")).thenReturn(List.of(source));
        when(service.saveAuthorized("lesson", List.of(source))).thenReturn(List.of(source));
        var mvc = MockMvcBuilders.standaloneSetup(new LessonKnowledgeSourceController(service)).build();
        mvc.perform(get("/api/admin/lessons/lesson/knowledge-sources"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].documentId").value("doc"));
        mvc.perform(put("/api/admin/lessons/lesson/knowledge-sources").contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"documentId\":\"doc\",\"pageFrom\":1,\"pageTo\":2,\"section\":null}]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].pageTo").value(2));
        mvc.perform(get("/api/admin/courses/course/documents/doc/outline")).andExpect(status().isOk());
        verify(service).listAuthorized("lesson");
        verify(service).saveAuthorized("lesson", List.of(source));
        verify(service).outlineAuthorized("course", "doc");
        verifyNoMoreInteractions(service);
    }
}
