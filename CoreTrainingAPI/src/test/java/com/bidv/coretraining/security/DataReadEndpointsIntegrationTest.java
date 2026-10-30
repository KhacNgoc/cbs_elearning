package com.bidv.coretraining.security;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="cbs.postgres.tests",matches="true")
class DataReadEndpointsIntegrationTest extends AccessSecurityIntegrationTest {
 @Test void reportEndpointsUseOverviewPermissionAndRefuseLearners()throws Exception {
  var viewerCookie=login(viewer);
  mvc.perform(get("/api/admin/reports/training/read").cookie(viewerCookie)).andExpect(status().isOk()).andExpect(jsonPath("$.summary.total").value(1));
  mvc.perform(get("/api/admin/reports/training/attempts").param("learnerId",learner.getId().toString()).param("courseId",ca.getId()).cookie(viewerCookie)).andExpect(status().isOk());
  mvc.perform(get("/api/admin/reports/training/export").param("view","domains").cookie(viewerCookie)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Domain")));
  mvc.perform(get("/api/admin/reports/training/read").cookie(login(learner))).andExpect(status().isForbidden());
  mvc.perform(get("/api/admin/reports/training/read")).andExpect(status().isUnauthorized());
 }
 @Test void learnerAndAdminPagesUseRegisteredScopedEndpoints()throws Exception {
  var learnerCookie=login(learner);var adminCookie=login(ua);
  mvc.perform(get("/api/users/me/learning/dashboard/courses").param("size","1").cookie(learnerCookie)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
  mvc.perform(get("/api/users/me/awards").cookie(learnerCookie)).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
  mvc.perform(get("/api/users/me/assessments").cookie(learnerCookie)).andExpect(status().isOk());
  mvc.perform(get("/api/users/me/assessment-statistics").cookie(learnerCookie)).andExpect(status().isOk());
  mvc.perform(get("/api/leaderboard/page").param("size","1").cookie(learnerCookie)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
  mvc.perform(get("/api/users/me/learning/quiz-context").param("courseId",ca.getId()).param("lessonId",la.getId()).cookie(learnerCookie)).andExpect(status().isOk());
  mvc.perform(get("/api/admin/users/page").cookie(adminCookie)).andExpect(status().isOk());
  mvc.perform(get("/api/admin/enrollment-requests/page").cookie(adminCookie)).andExpect(status().isOk());
  mvc.perform(get("/api/admin/rbac/roles/page").cookie(adminCookie)).andExpect(status().isOk());
  mvc.perform(get("/api/admin/rbac/role-options/page").cookie(adminCookie)).andExpect(status().isOk());
 }
}
