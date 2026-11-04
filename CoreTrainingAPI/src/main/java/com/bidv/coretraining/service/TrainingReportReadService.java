package com.bidv.coretraining.service;

import com.bidv.coretraining.dto.TrainingReportQueryDto.Filter;
import java.util.*;
public interface TrainingReportReadService {
    Map<String,Object> read(Map<String,String> params);
    Map<String,Object> read(Filter filter);
    Map<String,Object> read(Filter filter, boolean metadata);
    Map<String,Object> attempts(String learner, String course, String before, int size);
    void export(Map<String,String> params, java.io.Writer writer) throws java.io.IOException;
    void export(Filter filter, java.io.Writer writer) throws java.io.IOException;
}
