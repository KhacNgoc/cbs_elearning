package com.bidv.coretraining.security;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.assertj.core.api.Assertions.assertThat;
class ReportReadOperationTest {
 @Test void reportReadExportAndHistoryUseOverviewPermission() {
  for(String suffix:new String[]{"","/read","/attempts","/export"}) {
   var request=new MockHttpServletRequest("GET","/api/admin/reports/training"+suffix);
   assertThat(AdminOperation.from(request).permission()).as(suffix).isEqualTo("overview:read");
  }
 }
 @Test void unregisteredReportPathIsStillDenied(){assertThat(AdminOperation.from(new MockHttpServletRequest("GET","/api/admin/reports/training/other")).permission()).isEqualTo("unknown:denied");}
}
