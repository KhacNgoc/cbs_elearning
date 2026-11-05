package com.bidv.coretraining.report.repository;
import com.bidv.coretraining.report.dto.TrainingReportQueryDto.Filter;
import java.util.*;
import java.util.function.Consumer;
public interface TrainingReportQueryRepository {
    record Unit(String id, String parent, boolean active) {}
    record RoleScope(Set<String> unitIds, boolean assignedCoursesOnly) {}
    record Scope(boolean system, Long actorId, List<RoleScope> roles) {}
    record AttemptBoundary(java.time.LocalDateTime time, long id) {}
    List<Unit> units();
    Map<String,Object> read(Scope scope, Filter filter, boolean metadata);
    void export(Scope scope, Filter filter, Consumer<Map<String,Object>> row);
    List<Map<String,Object>> attempts(Scope scope, long learner, String course, AttemptBoundary boundary, int limit);
    boolean attemptVisible(Scope scope, long attemptId);
}
