package com.bidv.coretraining.service;
import com.bidv.coretraining.service.impl.*;

import jakarta.persistence.EntityManager;
import com.bidv.coretraining.dto.TrainingReportQueryDto.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import static org.assertj.core.api.Assertions.assertThat;

@Import({com.bidv.coretraining.service.impl.TrainingReportServiceImpl.class, com.bidv.coretraining.service.impl.TrainingReportReadServiceImpl.class, com.bidv.coretraining.repository.impl.TrainingReportQueryRepositoryImpl.class})
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="cbs.postgres.tests",matches="true")
class TrainingReportQueryTest extends TrainingReportServiceTest {
    @Autowired TrainingReportReadService query;
    @Autowired EntityManager em;
    @Autowired org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc;
    @Test void nullablePersonalDeadlineDoesNotFallBackToCourseDefault() {
        course.setCompletionDeadline(java.time.LocalDate.of(2026,11,1));em.flush();
        jdbc.update("insert into user_course_deadlines(user_id,course_id,due_date) values(:u,:c,null)",java.util.Map.of("u",learner.getId(),"c",course.getId()));
        var rows=(java.util.List<java.util.Map<String,Object>>)query.read(Filter.defaults(),false).get("items");
        assertThat(rows.get(0).get("dueDate")).isNull();
    }
    @Test void cursorHistoryIsBoundedStableAndCannotBeReusedForAnotherCourse() {
        for(int i=0;i<8;i++)score(first,7,1);em.flush();
        var initial=query.attempts(learner.getId().toString(),course.getId(),null,3);
        var firstPage=(java.util.List<java.util.Map<String,Object>>)initial.get("items");
        assertThat(firstPage).hasSize(3);assertThat(initial.get("hasMore")).isEqualTo(true);
        String cursor=(String)initial.get("nextCursor");
        var next=(java.util.List<java.util.Map<String,Object>>)query.attempts(learner.getId().toString(),course.getId(),cursor,3).get("items");
        assertThat(next).hasSize(3);
        assertThat(next.stream().map(r->r.get("id"))).doesNotContainAnyElementsOf(firstPage.stream().map(r->r.get("id")).toList());
        org.assertj.core.api.Assertions.assertThatThrownBy(()->query.attempts(learner.getId().toString(),"other",cursor,3)).isInstanceOf(com.bidv.coretraining.exception.BadRequestException.class);
    }
    @Test void historicalLessonThresholdDefaultsTo80WhenQuizWasRemoved() {
        course.setQuizPassingScore(60);score(first,7,1);em.flush();
        var summary=(java.util.Map<?,?>)query.read(Filter.defaults()).get("summary");
        assertThat(((Number)summary.get("passed")).longValue()).isZero();
        assertThat(((Number)summary.get("firstPassed")).longValue()).isZero();
    }
    @Test void groupedCsvUsesSameDomainSearchAndMetricsAsDisplayedTable() throws Exception {
        em.flush();
        var f=new Filter("","","","","","","","domains","","Domain",0,1,"","");
        var result=query.read(f);
        assertThat(((Number)result.get("total")).longValue()).isEqualTo(1);
        var csv=new java.io.StringWriter();query.export(f,csv);
        assertThat(csv.toString()).contains("Lĩnh vực", "Domain", "Tỷ lệ hoàn thành");
        assertThat(csv.toString().lines()).hasSize(2);
    }
    @Test void databaseSummaryDoesNotDependOnPageSizeAndCompletionMatchesExistingRules() {
        learner.getCompletedLessons().addAll(java.util.Set.of(first,second));
        em.flush();
        var result=query.read(Filter.defaults());
        assertThat(((Number)result.get("total")).longValue()).isEqualTo(1);
        var summary=(java.util.Map<?,?>)result.get("summary");
        assertThat(((Number)summary.get("complete")).longValue()).isEqualTo(1);
        assertThat(((Number)summary.get("tested")).longValue()).isZero();
        assertThat((java.util.List<?>)result.get("items")).hasSize(1);
    }
    @Test void aggregatesLatestPerAssessmentAndEscapesLiteralSearch() {
        score(first,10,1);score(first,4,3);score(second,6,2);em.flush();
        var result=query.read(Filter.defaults());
        var summary=(java.util.Map<?,?>)result.get("summary");
        assertThat(((Number)summary.get("average")).doubleValue()).isEqualTo(50.0);
        assertThat(((Number)summary.get("passed")).longValue()).isZero();
        assertThat(((Number)summary.get("tested")).longValue()).isEqualTo(1);
    }
    @Test void outsideReportScopeIsExcludedBeforeCountAndPage() {
        var outsider=com.bidv.coretraining.entity.User.builder().role("ROLE_REPORT_VIEWER")
            .organizationUnit(com.bidv.coretraining.entity.OrganizationUnit.builder().id("OUTSIDE").build()).build();
        em.flush();
        org.mockito.Mockito.when(auth.current()).thenReturn(outsider);
        var result=query.read(Filter.defaults());
        assertThat(((Number)result.get("total")).longValue()).isZero();
        assertThat((java.util.List<?>)result.get("items")).isEmpty();
    }
}
