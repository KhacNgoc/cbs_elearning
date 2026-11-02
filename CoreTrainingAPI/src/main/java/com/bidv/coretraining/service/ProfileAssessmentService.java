package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.ProfileAssessmentDto.*;
public interface ProfileAssessmentService {
 Awards awards(int page,int size);
 Results results(int page,int size,String search);
 History history(String quiz,int revision,String cursor,int size);
 Stats stats();
}
