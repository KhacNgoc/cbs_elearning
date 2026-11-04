package com.bidv.coretraining.dto;
import com.bidv.coretraining.mapper.CourseMapper;

import com.bidv.coretraining.entity.Course;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class CourseMapperTest {
    private final CourseMapper mapper = Mappers.getMapper(CourseMapper.class);

    @Test void mapsMandatoryOptionalAndCombinedUnitIds() {
        var course = Course.builder().id("C1").unitGrants(Map.of(
                "U1", "MANDATORY", "U2", "OPTIONAL", "U3", "mandatory")).build();

        var dto = mapper.toCourseDto(course);

        assertThat(dto.getMandatoryUnitIds()).containsExactlyInAnyOrder("U1", "U3");
        assertThat(dto.getOptionalUnitIds()).containsExactly("U2");
        assertThat(dto.getUnitIds()).containsExactlyInAnyOrder("U1", "U2", "U3");
        assertThat(course.getUnitGrants()).hasSize(3);
    }

    @Test void coursesWithoutGrantsReturnEmptySetsRatherThanNull() {
        for (var course : java.util.List.of(Course.builder().build(), Course.builder().unitGrants(null).build())) {
            var dto = mapper.toCourseDto(course);
            assertThat(dto.getMandatoryUnitIds()).isEmpty();
            assertThat(dto.getOptionalUnitIds()).isEmpty();
            assertThat(dto.getUnitIds()).isEmpty();
        }
    }

    @Test void courseListMappingAlsoIncludesUnitGrants() {
        var course = Course.builder().id("C1").unitGrants(Map.of("U1", "MANDATORY")).build();
        assertThat(mapper.toCourseDtoList(java.util.List.of(course)).get(0).getUnitIds()).containsExactly("U1");
        assertThat(mapper.toCourseDto(null)).isNull();
    }
}
