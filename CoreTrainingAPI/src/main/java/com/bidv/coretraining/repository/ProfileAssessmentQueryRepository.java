package com.bidv.coretraining.repository;
import com.bidv.coretraining.repository.support.LearnerQueryScope;
import com.bidv.coretraining.dto.ProfileAssessmentDto.*;
import com.bidv.coretraining.dto.LearnerWorkspaceDto.Attempt;
import java.util.*;
import java.time.LocalDateTime;
public interface ProfileAssessmentQueryRepository {
 record AwardsInventory(long total,List<Award> items) {}
 AwardsInventory awards(Long userId,int page,int size);
 Results results(LearnerQueryScope scope,int page,int size,String search);
 List<Attempt> history(LearnerQueryScope scope,String quiz,int revision,LocalDateTime time,String id,int limit);
 List<Competency> competencies(LearnerQueryScope scope);
}
