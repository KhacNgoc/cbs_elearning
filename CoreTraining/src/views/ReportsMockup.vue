<template>
  <div class="reports-page" :aria-busy="loading">
    <header class="report-heading">
      <div><div class="eyebrow">QUẢN TRỊ ĐÀO TẠO</div><h1>{{ selected==='overview'?'Tổng quan đào tạo':`Thống kê ${currentView?.label.toLocaleLowerCase('vi')}` }}</h1></div>
      <div class="heading-actions"><button class="r-button" :disabled="loading" @click="loadReport()">Tải lại</button><button class="r-button" :disabled="loading||!!loadError" @click="exportCsv"><Download :size="16"/> CSV chi tiết</button><button class="r-button primary" :disabled="loading||!!loadError" @click="printReport"><Printer :size="16"/> In / lưu PDF</button></div>
    </header>
    <SkeletonLoader v-if="loading&&!report" label="Đang tải báo cáo">
      <SkeletonLoader plain variant="stats" :rows="4"/>
      <SkeletonLoader plain variant="table" :rows="6" :columns="5"/>
    </SkeletonLoader>
    <div v-if="loadError" role="alert" class="validation">{{ loadError }} <button class="r-button" @click="loadReport()">Thử lại</button></div>
    <template v-if="report&&!loadError">
    <section class="filter-card" aria-label="Bộ lọc báo cáo">
      <div class="filter-title"><SlidersHorizontal :size="16"/><strong>Bộ lọc toàn cục</strong><button class="reset-button" @click="reset">Đặt lại bộ lọc</button></div>
      <div class="filters">
        <label>Hoàn thành từ ngày<input v-model="filters.from" type="date" :max="reportAsOf"/></label><label>Hoàn thành đến ngày<input v-model="filters.to" type="date" :max="reportAsOf"/></label>
        <label>Đơn vị<SearchSelect v-model="filters.unit" size="sm" aria-label="Đơn vị" all-label="Tất cả đơn vị" search-placeholder="Gõ tên đơn vị..." :options="unitOptions"/></label>
        <label>Chương trình<SearchSelect v-model="filters.program" size="sm" aria-label="Chương trình" all-label="Tất cả chương trình" search-placeholder="Gõ tên chương trình..." :options="programOptions"/></label>
        <label>Khóa học<SearchSelect v-model="filters.course" size="sm" aria-label="Khóa học" all-label="Tất cả khóa học" search-placeholder="Gõ tên khóa học..." :options="courseOptions"/></label>
        <label>Trạng thái<select v-model="filters.status"><option value="">Tất cả trạng thái</option><option v-for="status in [...statuses,'Quá hạn']" :key="status">{{ status }}</option></select></label>
      </div>
      <p v-if="invalidDates" class="validation" role="alert">Chọn khoảng ngày hợp lệ, không vượt quá ngày chốt {{ displayDate(reportAsOf) }}.</p>
      <div v-if="!invalidDates" class="filter-foot"><span><span class="scope-dot"></span>{{ summary.total }} lượt học · {{ learnerCount }} học viên</span></div>
      <p class="print-scope">Hoàn thành {{ displayDate(filters.from) }} – {{ displayDate(filters.to) }} · {{ filters.unit || 'Tất cả đơn vị' }} · {{ filters.program || 'Tất cả chương trình' }} · {{ filters.course || 'Tất cả khóa học' }} · {{ filters.status || 'Tất cả trạng thái' }}</p>
    </section>

    <template v-if="!invalidDates">
    <template v-if="selected==='overview'">
      <div class="section-label"><div><h2>KPI tổng quan</h2></div></div>
      <SkeletonLoader v-if="chartsLoading" plain variant="stats" :rows="5"/><section v-else class="kpi-grid" aria-label="Chỉ số điều hành"><article v-for="stat in kpis" :key="stat.label" class="kpi" :class="stat.tone"><div class="kpi-top"><span>{{ stat.label }}</span><component :is="stat.icon" :size="18"/></div><strong>{{ stat.value }}</strong><small>{{ stat.note }}</small></article></section>
      <div class="section-label"><div><h2>Tiến độ đào tạo</h2></div></div>
      <section class="report-card status-overview" aria-label="Tình hình học tập chung">
        <div class="status-heading"><div><h2>Tình hình học tập chung</h2></div><button class="text-button" @click="selectView('completion')">Xem chi tiết <ArrowUpRight :size="15"/></button></div>
        <SkeletonBlock v-if="chartsLoading" height="14px" radius="999px"/><div v-else-if="summary.total" class="status-track" aria-hidden="true"><span v-for="(status,index) in statuses" :key="status" :style="{width:`${countStatus(status)/summary.total*100}%`,background:statusColors[index]}"></span></div><p v-else class="no-data">Không có lượt học trong phạm vi đã chọn.</p>
        <div class="completion-legend"><button v-for="(status,index) in statuses" :key="status" @click="showStatus(status)"><i :style="{background:statusColors[index]}"></i>{{ status }} <strong>{{ countStatus(status) }}</strong><small>{{ percent(countStatus(status),summary.total) }}</small></button></div>
      </section>
      <section class="outcomes-grid training-progress">
        <article class="report-card"><div class="card-heading"><div><h3>Lượt hoàn thành theo tháng</h3><p></p></div><span class="chart-tag">{{ completedInPeriod.length }} lượt</span></div><SkeletonBlock v-if="chartsLoading" height="240px" radius="var(--radius-md)"/><ReportMockChart v-else-if="completedInPeriod.length" class="fill-chart" :config="trendChart" description="Lượt hoàn thành theo ngày được ghi nhận"/><div v-else class="chart-empty fill-chart"><CalendarCheck :size="28"/><p>Chưa có lượt hoàn thành trong kỳ.</p></div><div class="trend-values"><span v-for="item in months" :key="item.key">{{ item.label }} <strong>{{ item.count }}</strong></span></div></article>
        <article class="report-card"><div class="card-heading"><div><h3>So sánh tiến độ đơn vị</h3><p>Tỷ lệ hoàn thành, đang học và chưa bắt đầu trên lượt học. {{ overviewUnitsNote }}</p></div><GroupChartControls v-if="summary.total" v-model:sort="overviewUnitsSort" v-model:picked="overviewUnitsPicked" :sorts="statusSorts" :names="overviewUnitsNames" noun="đơn vị"/></div><SkeletonBlock v-if="chartsLoading" height="320px" radius="var(--radius-md)"/><ReportMockChart v-else-if="summary.total" :config="unitComparison" :height="overviewUnitsHeight" description="So sánh tỷ lệ trạng thái giữa các đơn vị"/><p v-else class="no-data">Chưa có dữ liệu so sánh.</p><div v-if="summary.total" class="card-foot"><Info :size="13"/> Bấm vào một đơn vị trên biểu đồ để xem báo cáo của đơn vị đó.</div></article>
      </section>
      <div class="section-label"><div><h2>Kết quả đào tạo</h2></div></div>
      <section class="outcomes-grid">
        <article class="report-card"><div class="card-heading"><div><h3>Kết quả kiểm tra</h3><p>So sánh trên cùng {{ summary.tested }} lượt học đã có kết quả.</p></div><Target :size="19"/></div><div class="exam-metrics"><div><span>Đạt ngay lần đầu</span><strong>{{ percent(summary.firstPassed,summary.tested) }}</strong><small>{{ summary.firstPassed }} / {{ summary.tested }} lượt đã kiểm tra</small><div class="metric-track"><span :style="{width:`${rate(summary.firstPassed,summary.tested)}%`}"></span></div></div><div><span>Đã từng đạt</span><strong>{{ percent(summary.passed,summary.tested) }}</strong><small>{{ summary.passed }} / {{ summary.tested }} lượt đã kiểm tra</small><div class="metric-track"><span :style="{width:`${rate(summary.passed,summary.tested)}%`}"></span></div></div></div><div class="exam-summary"><button @click="openActions('failed')"><strong>{{ summary.failed }}</strong><span>Chưa đạt yêu cầu kiểm tra</span><ArrowUpRight :size="15"/></button><button @click="openActions('untested')"><strong>{{ summary.total-summary.tested }}</strong><span>Chưa kiểm tra</span><ArrowUpRight :size="15"/></button></div><div class="card-foot">Đạt theo ngưỡng riêng từng bài. Một lượt học có nhiều lần thi chỉ được đếm một lần trong mẫu.</div><button class="text-button exam-link" @click="selectView('scores')">Xem phân bố điểm & chi tiết <ArrowUpRight :size="15"/></button></article>
        <article class="report-card knowledge-readiness"><h3>Kết quả theo lĩnh vực</h3><div class="readiness-value"><strong>{{ summary.evaluatedPeople }}</strong><span>/ {{ summary.people }} học viên đã có kiểm tra</span></div><h4 class="domain-low-title">Lĩnh vực có điểm thấp nhất</h4><SkeletonLoader v-if="chartsLoading" plain variant="list" :rows="3"/><div v-else-if="lowDomains.length" class="domain-low-list"><button v-for="domain in lowDomains" :key="domain.name" class="ranking-row" @click="selectView('domains')"><div><strong>{{ domain.name }}</strong><span>{{ domain.average!.toFixed(1) }}/100</span></div><div class="ranking-track"><span :style="{width:`${domain.average}%`}"></span></div><small>{{ domain.tested }} lượt đã kiểm tra · {{ percent(domain.passed,domain.tested) }} đạt</small></button></div><p v-else class="no-data">Chưa có lĩnh vực nào có kết quả kiểm tra.</p><button class="text-button" @click="selectView('domains')">Xem báo cáo lĩnh vực <ArrowUpRight :size="15"/></button></article>
      </section>
      <LiveCourseRatings :printing="printing"/>
      <div class="section-label"><div><h2>Danh sách cần chú ý</h2></div></div>
      <section class="priority-grid">
        <article class="report-card"><div class="card-heading"><div><h3>Đơn vị có lượt học quá hạn</h3><p>Xếp theo tỷ lệ quá hạn trên lượt học có hạn hoàn thành.</p></div><span class="chart-tag danger">CẦN CHÚ Ý</span></div>
          <SkeletonLoader v-if="chartsLoading" plain variant="list" :rows="5"/><div v-else-if="unitStats.length" class="ranking-list"><button v-for="unit in unitStats" :key="unit.name" class="ranking-row" @click="openUnit(unit.name || '')"><div><strong>{{ unit.name }}</strong><span>{{ percent(unit.overdue,unit.withDeadline) }}</span></div><div class="ranking-track"><span :style="{width:`${rate(unit.overdue,unit.withDeadline)}%`}"></span></div><small>{{ unit.overdue }} quá hạn / {{ unit.withDeadline }} lượt có hạn · {{ unit.total-unit.withDeadline }} chưa có hạn</small></button></div><p v-else class="no-data">Không có dữ liệu đơn vị.</p>
          <div class="card-foot"><Info :size="13"/> {{ unitStats.length<unitGroups.length?`Hiển thị ${unitStats.length}/${unitGroups.length} đơn vị có tỷ lệ quá hạn cao nhất. `:'' }}Bấm đơn vị để xem lượt học quá hạn. Đơn vị chưa có hạn hoàn thành không được tính tỷ lệ.</div>
        </article>
        <article class="report-card"><div class="card-heading"><div><h3>Khóa học cần can thiệp</h3><p>{{ courseMode==='progress'?'Ưu tiên số lượt quá hạn, sau đó đến số lượt chưa hoàn thành.':'Ưu tiên số lượt đã kiểm tra nhưng chưa từng đạt.' }}</p></div></div>
          <div class="segmented" aria-label="Chế độ theo dõi khóa học"><button :aria-pressed="courseMode==='progress'" :class="{active:courseMode==='progress'}" @click="courseMode='progress'">Tiến độ học</button><button :aria-pressed="courseMode==='results'" :class="{active:courseMode==='results'}" @click="courseMode='results'">Kết quả kiểm tra</button></div>
          <SkeletonLoader v-if="chartsLoading" plain variant="list" :rows="5"/><div v-else-if="courseStats.length" class="course-rankings"><button v-for="course in courseStats" :key="course.name" class="course-row" @click="openCourse(course.name || '')"><div><strong>{{ course.name }}</strong><ArrowUpRight :size="14"/></div><div class="mini-stack" aria-hidden="true"><span v-for="(color,index) in courseMode==='progress'?statusColors:examColors" :key="color" :style="{background:color,width:`${(courseMode==='progress'?[course.complete,course.learning,course.notStarted]:[course.passed,course.failed,course.total-course.tested])[index]!/course.total*100}%`}"></span></div><small v-if="courseMode==='progress'"><b>{{ course.overdue }} quá hạn</b> · {{ course.total-course.complete }} chưa hoàn thành / {{ course.total }} lượt</small><small v-else><b>{{ course.failed }} chưa đạt</b> / {{ course.tested }} đã kiểm tra · {{ course.total-course.tested }} chưa kiểm tra</small></button></div><p v-else class="no-data">Không có dữ liệu khóa học.</p>
          <div class="mini-legend"><span v-for="(label,index) in courseMode==='progress'?statuses:examLabels" :key="label"><i :style="{background:(courseMode==='progress'?statusColors:examColors)[index]}"></i>{{ label }}</span></div><div class="card-foot">Tối đa {{ ATTENTION_ROWS }} khóa · Bấm khóa học để mở danh sách cần hỗ trợ.</div>
        </article>
      </section>
    </template>
    <template v-else>
      <SkeletonLoader v-if="chartsLoading" plain variant="stats" :rows="5"/><section v-else class="detail-metric-row" aria-label="Chỉ số tổng quan báo cáo"><div v-for="stat in kpis" :key="stat.label"><span>{{ stat.label }}</span><strong>{{ stat.value }}</strong><small>{{ stat.note }}</small></div></section>

      <div class="detail-title"><h3>{{ currentView?.label }}</h3><p>{{ currentView?.caption }}</p></div>

      <article v-if="showDetailChart&&summary.total" class="report-card detail-chart"><div class="card-heading"><div><h3>{{ detailChartTitle }}</h3><p>{{ detailChartNote }}</p></div><GroupChartControls v-if="isGroupChart" v-model:sort="detailSort" v-model:picked="detailPicked" :sorts="groupSorts[selected]||[]" :names="detailNames" :noun="groupNouns[selected]||''"/></div><SkeletonBlock v-if="chartsLoading" height="320px" radius="var(--radius-md)"/><ReportMockChart v-else :config="detailChart" :height="isGroupChart?detailHeight:undefined" :description="detailChartTitle+'. Số liệu chi tiết trong bảng bên dưới.'"/></article>
    </template>
    <LiveCourseRatings v-if="selected==='courses'" :printing="printing"/>
    <section ref="tableSection" class="report-card detail-card" aria-label="Báo cáo chi tiết và danh sách xử lý">
      <div class="detail-heading"><div><h3>{{ selected==='overview'?'Ưu tiên hỗ trợ học viên':`Chi tiết ${currentView?.label.toLocaleLowerCase('vi')}` }}</h3><p>{{ selected==='overview'?'Chọn tình huống để xem từng lượt học cần xử lý.':'Tìm kiếm áp dụng cho bảng và CSV, không thay đổi biểu đồ.' }}</p></div><label class="search-input"><Search :size="15"/><input v-model="search" aria-label="Tìm trong báo cáo chi tiết" placeholder="Tìm học viên, khóa học..."/></label></div>
      <div v-if="selected==='overview'" class="action-tabs" aria-label="Tình huống cần xử lý"><button v-for="item in actionOptions" :key="item.id" :class="{active:actionType===item.id}" :aria-pressed="actionType===item.id" @click="actionType=item.id as ActionType">{{ item.label }} <span>{{ item.count }}</span></button></div>
      <div class="table-scroll" tabindex="0" role="region" aria-label="Bảng báo cáo, cuộn ngang để xem thêm"><table><caption class="sr-only">Báo cáo — {{ selected==='overview'?'Danh sách cần xử lý':currentView?.label }}</caption><thead><tr><th v-for="heading in tableHeaders" :key="heading" scope="col">{{ heading }}</th></tr></thead><tbody v-if="tableLoading" aria-hidden="true"><tr v-for="n in Math.min(pageSize,10)" :key="n" class="skeleton-row"><td v-for="c in tableHeaders.length" :key="c"><SkeletonBlock :width="`${55+((n*7+c*13)%40)}%`"/></td></tr></tbody><tbody v-else><tr v-for="row in pagedTable" :key="row.id"><td v-for="(cell,index) in row.cells" :key="index"><span :class="{pill:statuses.includes(String(cell))||String(cell).includes('quá hạn'),good:cell==='Hoàn thành',bad:String(cell).includes('quá hạn')}">{{ cell }}</span></td><td v-if="row.record"><button class="text-button" :aria-label="`Chi tiết ${row.record.name} - ${row.record.courseName}`" @click="inspectRow=row.record">Chi tiết <ArrowUpRight :size="13"/></button></td><td v-else-if="row.drill"><button class="text-button" :aria-label="`Xem chi tiết ${row.drill.title}`" @click="dimensionRow=row.drill">Xem chi tiết <ArrowUpRight :size="13"/></button></td></tr><tr v-if="!pagedTable.length"><td :colspan="tableHeaders.length" class="table-empty"><strong>{{ summary.total?'Không có trường hợp phù hợp':'Không có dữ liệu trong phạm vi đã chọn' }}</strong><p>Thay đổi bộ lọc, tình huống hoặc tìm kiếm.</p></td></tr></tbody></table></div>
      <TablePagination v-if="!printing" v-model:page="page" v-model:page-size="pageSize" :total="totalItems" :page-count="pageCount" class="report-pagination"/>
    </section>

    <dialog ref="dimensionDialog" class="learner-dialog dimension-dialog" @close="dimensionRow=undefined">
      <template v-if="dimensionRow"><div class="dialog-heading"><div><span class="eyebrow">BÁO CÁO CHI TIẾT</span><h2>{{ dimensionRow.title }}</h2><p>{{ dimensionRow.total }} lượt học trong phạm vi đã chọn</p></div><button class="r-button" aria-label="Đóng báo cáo chi tiết" @click="dimensionDialog?.close()"><X :size="18"/></button></div>
      <div class="dialog-body"><SkeletonLoader v-if="dimensionLoading" variant="table" :rows="4" :columns="3" label="Đang tải chi tiết"/><p v-if="dimensionError" role="alert">{{ dimensionError }} <button class="r-button" @click="loadDimension">Thử lại</button></p><div class="drill-actions"><button v-if="dimensionRow.kind==='units'" class="r-button" @click="filters.unit=dimensionRow.title;selectView('learners');dimensionDialog?.close()">Xem học viên trong đơn vị</button><button v-if="dimensionRow.kind==='programs'" class="r-button" @click="filters.program=dimensionRow.title;selectView('courses');dimensionDialog?.close()">So sánh khóa học thành phần</button><button v-if="dimensionRow.kind==='courses'" class="r-button" @click="filters.course=dimensionRow.title;selectView('learners');dimensionDialog?.close()">Danh sách học viên khóa này</button></div>
      <h3>{{ dimensionRow.kind==='learners'?'Hồ sơ học tập · khóa được giao và tham gia':'Tiến độ từng khóa thành phần' }}</h3>
      <div class="table-scroll"><table><thead><tr><th>Khóa học</th><th>Bắt buộc / lượt giao</th><th>Hoàn thành</th><th>Tiến độ bắt buộc</th></tr></thead><tbody><tr v-for="course in visibleDimensionCourses" :key="course.name"><td>{{ course.name }}</td><td>{{ course.mandatory }} / {{ course.total }}</td><td>{{ course.complete }} / {{ course.total }}</td><td>{{ percent(course.mandatoryComplete,course.mandatory) }}</td></tr></tbody></table></div>
      <TablePagination v-if="!printing" v-model:page="dimensionPage" v-model:page-size="dimensionPageSize" :total="dimensionTotal" :page-count="dimensionPageCount" unit="khóa học" unit-label="Khóa học"/>
      <h3 class="drill-subtitle">Học viên & lịch sử kiểm tra</h3><div class="drill-records"><button v-for="item in dimensionRow.items" :key="item.id" @click="inspectRow=item"><span><strong>{{ item.name }}</strong><small>{{ item.courseName }} · {{ item.status }} · {{ item.progress }}% bài học</small></span><span>{{ item.score===null?'Chưa kiểm tra':`${item.score}/100 gần nhất` }} <ArrowUpRight :size="14"/></span></button></div><TablePagination v-model:page="dimensionDetailsPage" :page-size="20" :total="dimensionDetailsTotal" :page-count="dimensionDetailsPageCount" :sizes="[20]" unit="lượt học"/></div>
      </template>
    </dialog>
    <dialog ref="detailDialog" class="learner-dialog" @close="inspectRow=null" @click="closeOnBackdrop"><template v-if="inspectRow"><div class="dialog-heading"><div><span class="eyebrow">CHI TIẾT LƯỢT HỌC</span><h2>{{ inspectRow.name }}</h2><p>{{ inspectRow.learnerId }} · {{ inspectRow.unit }}</p></div><button class="r-button" aria-label="Đóng chi tiết" @click="detailDialog?.close()"><X :size="18"/></button></div><div class="dialog-body"><h3>{{ inspectRow.courseName }}</h3><dl><div><dt>Trạng thái</dt><dd>{{ inspectRow.status }} · {{ inspectRow.progress }}% tiến độ bài học</dd></div><div><dt>Hạn hoàn thành</dt><dd>{{ inspectRow.dueDate?displayDate(inspectRow.dueDate):'Chưa quy định' }}</dd></div><div><dt>Tình huống</dt><dd>{{ reason(inspectRow) }}</dd></div></dl><h3>Lịch sử kiểm tra</h3><p v-if="historyError" role="alert">{{ historyError }} <button class="r-button" @click="loadAttempts()">Thử lại</button></p><ol v-if="inspectRow.attempts.length" class="attempt-list"><li v-for="(attempt,index) in inspectRow.attempts" :key="attempt.id??index"><span>{{ attempt.assessment }} · {{ displayDate(attempt.takenAt) }}</span><strong>{{ attempt.score }}/100 · {{ Math.round(attempt.score)>=(attempt.passingScore??inspectRow.passingScore)?'Đạt':'Chưa đạt' }}</strong><ReportAttemptAnalysis v-if="attempt.id!=null" :attempt-id="String(attempt.id)"/></li></ol><p v-else-if="!historyLoading&&!historyError">Chưa có lần kiểm tra nào.</p><SkeletonLoader v-if="historyLoading" variant="list" :rows="3" label="Đang tải lịch sử"/><button v-if="historyHasMore" class="r-button" :disabled="historyLoading" @click="loadAttempts(true)">Tải thêm lịch sử</button><div class="dialog-note">Gợi ý: {{ isOverdue(inspectRow)?'Phối hợp đơn vị để xác nhận kế hoạch hoàn thành.':inspectRow.requiredPending?'Nhắc học viên hoàn thành các bài kiểm tra bắt buộc còn lại.':isFailed(inspectRow)?'Hướng dẫn ôn tập và kiểm tra lại.':'Theo dõi và hỗ trợ hoàn thành nội dung còn lại.' }}</div></div></template></dialog>
    </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import SkeletonLoader from "../components/SkeletonLoader.vue";
import SkeletonBlock from "../components/SkeletonBlock.vue";
import SearchSelect from "../components/SearchSelect.vue";
import { computed, reactive, ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue';
import type { ChartConfiguration } from 'chart.js';
import { Users, CircleCheck, Target, Download, Printer, Info, SlidersHorizontal, Search, ArrowUpRight, AlarmClock, CalendarCheck, X } from 'lucide-vue-next';
import ReportMockChart from '../components/ReportMockChart.vue';
import GroupChartControls from '../components/GroupChartControls.vue';
import { useGroupChart, pointMetrics, axisLabel, type GroupSort } from '../utils/reportGroups';
import LiveCourseRatings from '../components/LiveCourseRatings.vue';
import TablePagination from '../components/TablePagination.vue';
import ReportAttemptAnalysis from '../components/ReportAttemptAnalysis.vue';
import { scrollWithin } from '../utils/scrollWithin';
import { reportViews } from '../utils/reportingViews';
import type { ReportView } from '../utils/reportingViews';
import { readTrainingReport, readReportAttempts, exportTrainingReport } from '../api/trainingReportApi';
import type { ReportRow, ReportFact, ReportMetrics, ReportPage, ReportParams } from '../api/trainingReportApi';
import { useRoute, useRouter } from 'vue-router';
import { formatRatio } from '../utils/reportingMetrics';
type View = ReportView|'overview';
type ActionType = 'overdue'|'dueSoon'|'failed'|'untested'|'incomplete';
type Drill = {title:string;kind:View;key:string;items:ReportRow[];total:number};
type TableRow = {id:string;cells:(string|number)[];record?:ReportRow;drill?:Drill};
const route=useRoute(),router=useRouter();
const selected=ref<View>(reportViews.some(v=>v.id===route.query.report)?route.query.report as ReportView:'overview');
const currentView=computed(()=>reportViews.find(view=>view.id===selected.value));
const reportAsOf=ref(new Date().toLocaleDateString('en-CA'));
const defaults={from:'',to:'',unit:'',program:'',course:'',status:''};
const filters=reactive({...defaults});
const search=ref(''),printing=ref(false),printItems=ref<ReportPage['items']|null>(null);
const actionType=ref<ActionType>('overdue'),courseMode=ref<'progress'|'results'>('progress');
const tableSection=ref<HTMLElement>(),inspectRow=ref<ReportRow|null>(null),detailDialog=ref<HTMLDialogElement>();
const dimensionRow=ref<Drill>(),dimensionDialog=ref<HTMLDialogElement>();
const statuses=['Hoàn thành','Đang học','Chưa bắt đầu'];
const statusColors=['#00856a','#e5b04c','#c4cfd8'];
const examColors=['#00856a','#cc6d59','#c4cfd8'],examLabels=['Đã từng đạt','Chưa từng đạt','Chưa kiểm tra'];
const report=ref<ReportPage|null>(null),loading=ref(true),loadError=ref('');
// Summary, charts and rankings are re-read only when the scope changes; paging the table keeps them, so
// they show a skeleton only during a metadata read, while the table shows one during every read.
const chartsLoading=ref(false);
// The server runs a few whole-population reports at a time and answers 503 when every slot stays busy.
const BUSY='Hệ thống đang xử lý nhiều báo cáo cùng lúc. Vui lòng thử lại sau ít phút.';
const reportBusy=(e:unknown)=>(e as {response?:{status?:number}})?.response?.status===503;
const page=ref(1),pageSize=ref(10);
let sequence=0,timer:ReturnType<typeof setTimeout>|undefined,lastScope='';
const empty:ReportMetrics={total:0,people:0,complete:0,learning:0,notStarted:0,tested:0,passed:0,failed:0,firstPassed:0,average:null,withDeadline:0,overdue:0,overduePeople:0,peopleWithDeadline:0,mandatory:0,mandatoryComplete:0};
const summary=computed(()=>report.value?.summary||empty);
const choices=computed(()=>report.value?.choices||{});
const units=computed(()=>choices.value.units||[]),programs=computed(()=>choices.value.programs||[]),courses=computed(()=>choices.value.courses||[]);
// Filter values are names; thousands of courses are searched in SearchSelect rather than rendered as <option>s.
const asOptions=(names:string[])=>names.map(name=>({value:name,label:name}));
const unitOptions=computed(()=>asOptions(units.value)),programOptions=computed(()=>asOptions(programs.value)),courseOptions=computed(()=>asOptions(courses.value));
const invalidDates=computed(()=>!!(filters.from&&filters.to&&filters.from>filters.to)||filters.to>reportAsOf.value||filters.from>reportAsOf.value);
const params=():ReportParams=>({...filters,view:selected.value,action:actionType.value,search:search.value,page:page.value-1,size:pageSize.value});
async function loadReport(force=true){
 clearTimeout(timer);const request=++sequence;loading.value=true;loadError.value='';
 if(invalidDates.value){loading.value=false;chartsLoading.value=false;return;}
 const scope=JSON.stringify(filters),metadata=force||!report.value||lastScope!==scope;
 chartsLoading.value=metadata&&!!report.value;
 try{const data=await readTrainingReport({...params(),metadata});if(request!==sequence)return;reportAsOf.value=data.asOf;report.value={...report.value,...data,summary:data.summary||report.value?.summary,choices:metadata?data.choices:report.value?.choices};lastScope=scope;}
 catch(e){if(request===sequence)loadError.value=reportBusy(e)?BUSY:'Không thể tải dữ liệu báo cáo. Vui lòng thử lại.';}
 finally{if(request===sequence){loading.value=false;chartsLoading.value=false;}}
}
function schedule(){sequence++;clearTimeout(timer);timer=setTimeout(()=>void loadReport(false),250);}
watch([filters,selected,search,actionType,pageSize],()=>{page.value=1;schedule();},{deep:true});
watch(page,schedule);
watch(()=>route.query.report,value=>{selected.value=reportViews.some(v=>v.id===value)?value as ReportView:'overview';search.value='';});
const displayDate=(date:string)=>date?date.slice(0,10).split('-').reverse().join('/'):'—';
const percent=formatRatio,rate=(n:number,total:number)=>total?Math.round(n/total*100):0;
// Printing reads every page with loading on; the printed table must show those rows, not a skeleton.
const tableLoading=computed(()=>loading.value&&!printing.value&&!!report.value);
const totalItems=computed(()=>report.value?.total||0),pageCount=computed(()=>Math.max(1,Math.ceil(totalItems.value/pageSize.value)));
const learnerCount=computed(()=>summary.value.people);
function row(value:ReportFact):ReportRow{return {id:`${value.learnerId}:${value.courseId}`,learnerId:String(value.learnerId),name:value.name,unit:value.unit,courseId:value.courseId,courseName:value.courseName,program:value.program,domain:value.domain,progress:value.progress,mandatory:!!value.mandatory,dueDate:value.dueDate,completedAt:value.completedAt,date:'',status:value.status,score:value.average,passed:value.everPassed===1&&value.requiredPassed===1,requiredPending:value.attemptCount>0&&value.everPassed===1&&value.requiredPassed!==1,attempts:[],attemptCount:value.attemptCount,passingScore:value.passingScore,completed:!!value.completed};}
const isOverdue=(r:ReportRow)=>r.status!=='Hoàn thành'&&!!r.dueDate&&r.dueDate<reportAsOf.value;
const isFailed=(r:ReportRow)=>!!r.attemptCount&&!r.passed;
// A learner who passed everything they took but still owes a required quiz has not failed anything.
function testResult(r:ReportRow){if(!r.attemptCount)return 'Chưa kiểm tra';if(r.passed)return 'Đã từng đạt';return r.requiredPending?'Đạt bài đã làm, còn bài bắt buộc chưa làm':'Chưa từng đạt';}
function reason(r:ReportRow){if(isOverdue(r))return `${Math.floor((Date.parse(reportAsOf.value)-Date.parse(r.dueDate!))/86400000)} ngày quá hạn`;if(r.requiredPending)return 'Còn bài kiểm tra bắt buộc chưa làm';if(isFailed(r))return 'Đã kiểm tra, chưa từng đạt';if(!r.attemptCount)return 'Chưa kiểm tra';return r.status==='Hoàn thành'?'Đã hoàn thành':'Tiếp tục theo dõi tiến độ';}
const countStatus=(s:string)=>s===statuses[0]?summary.value.complete:s===statuses[1]?summary.value.learning:summary.value.notStarted;
const kpis=computed(()=>[
 {label:'Học viên',value:summary.value.people,icon:Users,note:`${summary.value.total} lượt học trong phạm vi`,tone:'teal'},
 {label:'Tỷ lệ hoàn thành',value:percent(summary.value.complete,summary.value.total),icon:CircleCheck,note:`${summary.value.complete} / ${summary.value.total} lượt học`,tone:'blue'},
 {label:'Điểm kiểm tra TB',value:summary.value.average===null?'—':`${summary.value.average.toFixed(1)}/100`,icon:Target,note:`TB điểm gần nhất · ${summary.value.tested} lượt đã thi`,tone:'teal'},
 {label:'Tỷ lệ đạt kiểm tra',value:percent(summary.value.passed,summary.value.tested),icon:Target,note:`${summary.value.passed} / ${summary.value.tested} lượt đã thi`,tone:'blue'},
 {label:'Học viên quá hạn',value:percent(summary.value.overduePeople,summary.value.peopleWithDeadline),icon:AlarmClock,note:`${summary.value.overduePeople} / ${summary.value.peopleWithDeadline} người có hạn`,tone:'red'},
]);
const metrics=(s:ReportMetrics)=>({...s,completionRate:s.total?s.complete/s.total*100:null,programProgress:s.mandatory?s.mandatoryComplete/s.mandatory*100:null});
const aggregates=(dimension:string)=>(report.value?.charts?.[dimension]||[]).map(metrics);
// Every group of a dimension. charts holds only the 20 biggest, so a ranking built on it misses smaller groups;
// an older backend without chartPoints still gets those 20.
const allGroups=(dimension:string)=>{const points=report.value?.chartPoints?.[dimension];return points?points.map(p=>metrics(pointMetrics(p))):aggregates(dimension);};
type Group=ReturnType<typeof metrics>;
const overdueShare=(g:Group)=>g.withDeadline?g.overdue/g.withDeadline:-1;
const unitGroups=computed(()=>allGroups('units'));
// Five rows like the course list beside it, so the two attention cards have the same height.
const ATTENTION_ROWS=5;
const unitStats=computed(()=>[...unitGroups.value].sort((a,b)=>overdueShare(b)-overdueShare(a)).slice(0,ATTENTION_ROWS));
const lowDomains=computed(()=>allGroups('domains').filter(g=>g.average!==null&&g.tested>0).sort((a,b)=>a.average!-b.average!).slice(0,3));
const courseStats=computed(()=>[...allGroups('courses')].sort((a,b)=>courseMode.value==='progress'?b.overdue-a.overdue||(b.total-b.complete)-(a.total-a.complete):b.failed-a.failed).slice(0,ATTENTION_ROWS));
const months=computed(()=>(report.value?.months||[]).map(m=>({key:`${m.year}-${m.month}`,label:`T${m.month}/${m.year}`,count:m.count})));
const completedInPeriod=computed(()=>({length:months.value.reduce((n,m)=>n+m.count,0)}));
type StatusCounts=Pick<ReportMetrics,'name'|'total'|'complete'|'learning'|'notStarted'>;
// Every axis label is drawn (group charts grow to fit them); long names are cut on the axis and shown whole in the tooltip.
const groupAxis={stacked:true,grid:{display:false},ticks:{autoSkip:false,callback(this:{getLabelForValue(v:number):string},value:string|number){return axisLabel(String(this.getLabelForValue(Number(value))));}}};
function comparison(items:StatusCounts[]):ChartConfiguration{const counts=(g:StatusCounts)=>[g.complete,g.learning,g.notStarted];return {type:'bar',data:{labels:items.map(g=>g.name||''),datasets:statuses.map((label,index)=>({label,data:items.map(g=>g.total?counts(g)[index]!/g.total*100:0),backgroundColor:statusColors[index],maxBarThickness:25}))},options:{indexAxis:'y',plugins:{legend:{position:'bottom',labels:{boxWidth:8,usePointStyle:true}},tooltip:{callbacks:{label:ctx=>{const g=items[ctx.dataIndex];return g?`${ctx.dataset.label}: ${Number(ctx.parsed.x).toFixed(1)}% (${counts(g)[ctx.datasetIndex]} / ${g.total} lượt)`:'';}}}},scales:{x:{min:0,max:100,stacked:true},y:groupAxis}}} as ChartConfiguration;}
/** One measure per group on a 0–100 scale (programme progress, average score). */
function groupBars(items:Group[],label:string,value:(g:Group)=>number|null,detail:(g:Group)=>string):ChartConfiguration{return {type:'bar',data:{labels:items.map(g=>g.name||''),datasets:[{label,data:items.map(value),backgroundColor:'#00856a',maxBarThickness:25}]},options:{indexAxis:'y',plugins:{tooltip:{callbacks:{label:ctx=>{const g=items[ctx.dataIndex];const v=g?value(g):null;return g?`${label}: ${v===null?'Chưa có dữ liệu':Number(v).toFixed(1)} · ${detail(g)}`:'';}}}},scales:{x:{min:0,max:100},y:{...groupAxis,stacked:false}}}} as ChartConfiguration;}
const byLowest=(value:(g:Group)=>number|null)=>(a:Group,b:Group)=>{const x=value(a),y=value(b);return x===null?(y===null?b.total-a.total:1):y===null?-1:x-y||b.total-a.total;};
const byVolume:GroupSort<Group>={id:'volume',label:'Nhiều lượt học nhất',note:'có nhiều lượt học nhất'};
const statusSorts:GroupSort<Group>[]=[byVolume,{id:'lowest',label:'Tỷ lệ hoàn thành thấp nhất',note:'có tỷ lệ hoàn thành thấp nhất',compare:byLowest(g=>g.completionRate)}];
const groupSorts:Record<string,GroupSort<Group>[]>={units:statusSorts,courses:statusSorts,
 programs:[byVolume,{id:'lowest',label:'Tiến độ thấp nhất',note:'có tiến độ khóa bắt buộc thấp nhất',compare:byLowest(g=>g.programProgress)}],
 domains:[byVolume,{id:'lowest',label:'Điểm thấp nhất',note:'có điểm trung bình thấp nhất',compare:byLowest(g=>g.average)}]};
const groupNouns:Record<string,string>={units:'đơn vị',courses:'khóa học',programs:'chương trình',domains:'lĩnh vực'};
// The detail chart of a dimension view, and the unit comparison of the overview, each keep their own order and pick.
const isGroupChart=computed(()=>selected.value in groupSorts);
const detailGroups=useGroupChart(()=>isGroupChart.value?allGroups(selected.value):[],{noun:()=>groupNouns[selected.value]||'',sorts:()=>groupSorts[selected.value]||[],fullList:'xem đầy đủ ở bảng',resetOn:selected});
const overviewUnits=useGroupChart(()=>unitGroups.value,{noun:()=>'đơn vị',sorts:()=>statusSorts,fullList:'xem đầy đủ ở báo cáo Đơn vị'});
// Clicking a unit's bar opens the unit report filtered to it (the chart used to repeat its units as a button list).
const unitComparison=computed(()=>{const shown=overviewUnits.shown.value,config=comparison(shown);
 config.options={...config.options,onClick:(_e,elements)=>{const g=shown[elements[0]?.index??-1];if(g){filters.unit=g.name||'';void selectView('units');}},
  onHover:(e,elements)=>{const target=e.native?.target as HTMLElement|undefined;if(target)target.style.cursor=elements.length?'pointer':'default';}};
 return config;});
const {sortId:detailSort,picked:detailPicked,names:detailNames,height:detailHeight}=detailGroups;
const {sortId:overviewUnitsSort,picked:overviewUnitsPicked,names:overviewUnitsNames,height:overviewUnitsHeight,note:overviewUnitsNote}=overviewUnits;
const trendChart=computed<ChartConfiguration>(()=>({type:'bar',data:{labels:months.value.map(m=>m.label),datasets:[{label:'Lượt hoàn thành',data:months.value.map(m=>m.count),backgroundColor:'#00856a',borderRadius:5}]},options:{plugins:{legend:{display:false}},scales:{y:{beginAtZero:true,ticks:{precision:0}}}}}));
const actionLabels:Record<string,string>={overdue:'Quá hạn',dueSoon:'Sắp đến hạn, chưa bắt đầu',failed:'Chưa đạt yêu cầu kiểm tra',untested:'Chưa kiểm tra',incomplete:'Chưa hoàn thành'};
const actionOptions=computed(()=>(report.value?.actions||[]).map(a=>({...a,label:actionLabels[a.id]})));
const showDetailChart=computed(()=>!['learners','completion','overview'].includes(selected.value));
const detailChartTitle=computed(()=>selected.value==='domains'?'Điểm kiểm tra gần nhất theo lĩnh vực':selected.value==='scores'?'Phân bố điểm kiểm tra gần nhất':selected.value==='programs'?'Tiến độ chương trình · khóa bắt buộc':'So sánh tiến độ học tập');
const detailChartNote=computed(()=>{const lead=selected.value==='domains'?'Điểm gần nhất trung bình /100; số học viên được đánh giá ở bảng bên dưới.':selected.value==='scores'?'Điểm gần nhất cho mỗi lượt học. Ngưỡng đạt theo từng bài.':selected.value==='programs'?'Chỉ tính lượt khóa bắt buộc được giao, cùng mẫu số với bảng tiến độ chương trình.':'Tỷ lệ trạng thái theo đối tượng.';return isGroupChart.value?`${lead} ${detailGroups.note.value}`:lead;});
const detailChart=computed<ChartConfiguration>(()=>{
 const data=aggregates(selected.value);
 if(selected.value==='scores')return {type:'bar',data:{labels:(report.value?.bins||[]).map(b=>b.name),datasets:[{label:'Lượt học',data:(report.value?.bins||[]).map(b=>b.count),backgroundColor:'#4a8fa8'}]}};
 const shown=detailGroups.shown.value;
 if(selected.value==='programs')return groupBars(shown,detailChartTitle.value,g=>g.programProgress,g=>`${g.mandatoryComplete}/${g.mandatory} lượt bắt buộc hoàn thành`);
 if(selected.value==='domains')return groupBars(shown,detailChartTitle.value,g=>g.average,g=>`${g.tested} lượt đã kiểm tra`);
 return isGroupChart.value?comparison(shown):comparison(data);
});
const individualTable=computed(()=>['overview','completion','scores'].includes(selected.value));
const tableHeaders=computed(()=>selected.value==='scores'?['Học viên','Khóa học','Số lần thi','TB điểm gần nhất /100','Kết quả qua các lần thi','Thao tác']:individualTable.value?['Học viên','Đơn vị','Khóa học','Trạng thái','Hạn hoàn thành','Kết quả kiểm tra','Cần theo dõi','Thao tác']:selected.value==='domains'?['Lĩnh vực','Học viên','Lượt học','Tỷ lệ hoàn thành','Đạt kiểm tra / đã thi','Điểm kiểm tra TB /100','Thao tác']:selected.value==='learners'?['Học viên','Khóa được giao / tham gia','Hoàn thành','Tỷ lệ hoàn thành','Đạt kiểm tra / đã thi','Điểm TB /100','Thao tác']:selected.value==='programs'?['Chương trình','Học viên','Khóa bắt buộc đã giao','Bắt buộc hoàn thành','Tiến độ chương trình','Đạt kiểm tra / đã thi','Thao tác']:selected.value==='courses'?['Khóa học','Ghi danh','Hoàn thành','Tỷ lệ hoàn thành','Đạt kiểm tra / đã thi','Điểm TB /100','Bỏ dở','Thao tác']:['Đối tượng','Học viên','Lượt học','Tỷ lệ hoàn thành','Tỷ lệ đạt kiểm tra','Học viên quá hạn / có hạn','Thao tác']);
const pagedTable=computed(():TableRow[]=>(printItems.value||report.value?.items||[]).map(item=>{
 if(individualTable.value){const r=row(item as ReportFact);return {id:r.id,record:r,cells:selected.value==='scores'?[`${r.name} · ${r.learnerId}`,r.courseName,r.attemptCount||0,r.score??'Chưa kiểm tra',testResult(r)]:[`${r.name} · ${r.learnerId}`,r.unit,r.courseName,r.status,r.dueDate?displayDate(r.dueDate):'Chưa quy định',r.attemptCount?`${testResult(r)} · gần nhất ${r.score}/100`:'Chưa kiểm tra',reason(r)]};}
 const g=item as ReportMetrics,label=selected.value==='learners'?`${g.learnerName} · ${g.name}`:g.name||'',avg=g.average===null?'Chưa đánh giá':g.average.toFixed(1),pass=`${g.passed} / ${g.tested} (${percent(g.passed,g.tested)})`;
 const cells=selected.value==='domains'?[label,g.people,g.total,percent(g.complete,g.total),pass,avg]:selected.value==='learners'?[label,`${g.mandatory} / ${g.total}`,g.complete,percent(g.complete,g.total),pass,avg]:selected.value==='programs'?[label,g.people,g.mandatory,g.mandatoryComplete,percent(g.mandatoryComplete,g.mandatory),pass]:selected.value==='courses'?[label,g.total,g.complete,percent(g.complete,g.total),pass,avg,'Chưa có dữ liệu']:[label,g.people,g.total,percent(g.complete,g.total),pass,`${g.overduePeople} / ${g.peopleWithDeadline} (${percent(g.overduePeople,g.peopleWithDeadline)})`];
 return {id:g.name||'',cells,drill:{title:label,kind:selected.value,key:g.name||'',items:[],total:g.total}};
}));
const dimensionDetailsPage=ref(1),dimensionDetailsTotal=ref(0);
const dimensionDetailsPageCount=computed(()=>Math.max(1,Math.ceil(dimensionDetailsTotal.value/20)));
const dimensionPage=ref(1),dimensionPageSize=ref(20),dimensionCourses=ref<ReportMetrics[]>([]),dimensionTotal=ref(0),dimensionLoading=ref(false),dimensionError=ref('');
const dimensionPageCount=computed(()=>Math.max(1,Math.ceil(dimensionTotal.value/dimensionPageSize.value)));
const visibleDimensionCourses=computed(()=>dimensionCourses.value);
let dimensionSequence=0;
async function loadDimension(){if(!dimensionRow.value)return;const request=++dimensionSequence;dimensionLoading.value=true;dimensionError.value='';const d=dimensionRow.value;
 try{const scope={...params(),dimension:d.kind,key:d.key,search:'',action:'',metadata:false,page:dimensionPage.value-1,size:dimensionPageSize.value};const [courses,details]=await Promise.all([readTrainingReport({...scope,view:'courses'}),readTrainingReport({...scope,view:'completion',page:dimensionDetailsPage.value-1,size:20})]);if(request!==dimensionSequence)return;dimensionCourses.value=courses.items as ReportMetrics[];dimensionTotal.value=courses.total;dimensionDetailsTotal.value=details.total;d.items=details.items.map(r=>row(r as ReportFact));}
 catch{if(request===dimensionSequence)dimensionError.value='Không thể tải chi tiết. Vui lòng thử lại.';}finally{if(request===dimensionSequence)dimensionLoading.value=false;}
}
watch(dimensionRow,async d=>{dimensionSequence++;dimensionCourses.value=[];dimensionTotal.value=0;if(d){dimensionPage.value=1;dimensionDetailsPage.value=1;await nextTick();dimensionDialog.value?.showModal();void loadDimension();}else dimensionDialog.value?.close();});
watch([dimensionPage,dimensionPageSize,dimensionDetailsPage],loadDimension);
const historyLoading=ref(false),historyError=ref(''),historyCursor=ref(''),historyHasMore=ref(false);let historySequence=0;
async function loadAttempts(more=false){const r=inspectRow.value;if(!r||historyLoading.value)return;const request=historySequence;historyLoading.value=true;historyError.value='';try{const data=await readReportAttempts(r.learnerId,r.courseId,more?historyCursor.value:undefined);if(request!==historySequence)return;r.attempts=more?[...r.attempts,...data.items]:data.items;historyCursor.value=data.nextCursor;historyHasMore.value=data.hasMore;}catch{if(request===historySequence)historyError.value='Không thể tải lịch sử. Vui lòng thử lại.';}finally{if(request===historySequence)historyLoading.value=false;}}
watch(inspectRow,async r=>{historySequence++;historyLoading.value=false;historyHasMore.value=false;historyError.value='';if(r){r.attempts=[];await nextTick();detailDialog.value?.showModal();void loadAttempts();}else detailDialog.value?.close();});
async function selectView(view:View){selected.value=view;search.value='';await router.replace({path:route.path,query:{...route.query,tab:undefined,report:view==='overview'?undefined:view}});}
function reset(){Object.assign(filters,defaults);search.value='';}
async function scrollToTable(){await nextTick();scrollWithin(tableSection.value);}
async function openActions(type:ActionType){actionType.value=type;await selectView('overview');await scrollToTable();}
function openUnit(unit:string){filters.unit=unit;void openActions('overdue');}
function openCourse(course:string){filters.course=course;void openActions(courseMode.value==='results'?'failed':'incomplete');}
async function showStatus(status:string){filters.status=status;await selectView('completion');await scrollToTable();}
function closeOnBackdrop(event:MouseEvent){if(event.target===detailDialog.value)detailDialog.value?.close();}
async function exportCsv(){try{const blob=await exportTrainingReport(params());const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=`bao-cao-${selected.value}.csv`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}catch(e){loadError.value=reportBusy(e)?BUSY:'Không thể xuất báo cáo. Vui lòng thử lại.';}}
async function printReport(){loading.value=true;try{const items:ReportPage['items']=[];for(let n=0;n<Math.ceil(totalItems.value/100);n++){const data=await readTrainingReport({...params(),page:n,size:100,metadata:false});items.push(...data.items);}printItems.value=items;printing.value=true;await nextTick();window.print();}catch{loadError.value='Không thể tải dữ liệu in. Vui lòng thử lại.';}finally{printItems.value=null;printing.value=false;loading.value=false;}}
onMounted(()=>void loadReport());
onBeforeUnmount(()=>{sequence++;historySequence++;dimensionSequence++;clearTimeout(timer);});
</script>

<style>
@media print {
  body:has(.reports-page),body:has(.reports-page) #app,body:has(.reports-page) .app-container,body:has(.reports-page) .main-content,body:has(.reports-page) .admin-shell,body:has(.reports-page) .admin-shell-content{display:block!important;height:auto!important;overflow:visible!important;margin:0!important;padding:0!important}
  body:has(.reports-page) .navbar,body:has(.reports-page) .admin-sidebar-frame,body:has(.reports-page) .theme-toggle,body:has(.reports-page) #learning-chatbot-launcher{display:none!important}
}
</style>
<style scoped src="../assets/styles/reporting-mockup.css"></style>
<style scoped>
.report-pagination{margin-top:0;padding:16px 20px}
/* Paired cards share a row height; a card's chart or list takes the spare height instead of leaving a gap. */
.training-progress>.report-card,.outcomes-grid>.report-card,.priority-grid>.report-card{display:flex;flex-direction:column}
.priority-grid .card-foot{margin-top:auto}
/* The exam card's summary sits at the bottom, level with the domain card's link. */
.outcomes-grid:not(.training-progress) .exam-summary{margin-top:auto}
.training-progress .fill-chart{flex:1 1 auto;height:auto!important;min-height:280px}
.training-progress .card-foot{margin-top:auto}
.knowledge-readiness .text-button{margin-top:auto;align-self:flex-start;padding-top:14px}
.domain-low-title{margin:20px 0 12px;font-size:12px;font-weight:600;color:var(--text-muted)}
.domain-low-list{display:grid;gap:14px;padding-bottom:6px}
/* Report tables fit their card: long names and results wrap instead of widening the table. The admin layout sets
   every td to its 14px body size; report tables keep a denser 12px. Only narrow screens scroll sideways. */
.reports-page .table-scroll table{white-space:normal}
.reports-page .table-scroll th,.reports-page .table-scroll td{font-size:12px;padding:12px 14px;overflow-wrap:break-word}
.reports-page .table-scroll th{white-space:nowrap}
.reports-page .table-scroll .pill,.reports-page .table-scroll .text-button{white-space:nowrap}
@media(max-width:768px){.reports-page .table-scroll table{min-width:760px}}
.card-heading:has(.group-chart-controls){flex-wrap:wrap}
.attempt-list li{flex-wrap:wrap}
@media(max-width:600px){
  .report-pagination{padding:12px}
  .heading-actions,.drill-actions{flex-wrap:wrap;min-width:0}
  .heading-actions .r-button{flex:1 1 8rem}
  .drill-actions .r-button{white-space:normal;text-align:left}
  .filters{grid-template-columns:minmax(0,1fr)}
}
</style>






