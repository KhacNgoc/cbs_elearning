package com.bidv.coretraining.profile.service;

import com.bidv.coretraining.profile.dto.ProfileAssessmentDto.*;
public interface ProfileAssessmentService {
 Awards awards(int page,int size);
 Results results(int page,int size,String search);
 History history(String quiz,int revision,String cursor,int size);
 Stats stats();
}
