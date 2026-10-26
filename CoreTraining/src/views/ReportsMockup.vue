<template>
  <div class="reports-page">
    <header class="report-heading">
      <div><div class="eyebrow">QUẢN TRỊ ĐÀO TẠO</div><h1>{{ selected==='overview'?'Tổng quan đào tạo':`Thống kê ${currentView?.label.toLocaleLowerCase('vi')}` }}</h1></div>
      <div class="heading-actions"><button class="r-button" :disabled="loading" @click="loadReport">Tải lại</button><button class="r-button" :disabled="loading||!!loadError" @click="exportCsv"><Download :size="16"/> CSV chi tiết</button><button class="r-button primary" :disabled="loading||!!loadError" @click="printReport"><Printer :size="16"/> In / lưu PDF</button></div>
    </header>
    <p v-if="loading" role="status">Đang tải báo cáo...</p>
    <div v-if="loadError" role="alert" class="validation">{{ loadError }} <button class="r-button" @click="loadReport">Thử lại</button></div>
    <template v-if="!loading&&!loadError">
    <section class="filter-card" aria-label="Bộ lọc báo cáo">
      <div class="filter-title"><SlidersHorizontal :size="16"/><strong>Bộ lọc toàn cục</strong><button class="reset-button" @click="reset">Đặt lại bộ lọc</button></div>
      <div class="filters">
        <label>Hoàn thành từ ngày<input v-model="filters.from" type="date" :max="reportAsOf"/></label><label>Hoàn thành đến ngày<input v-model="filters.to" type="date" :max="reportAsOf"/></label>
        <label>Đơn vị<select v-model="filters.unit"><option value="">Tất cả đơn vị</option><option v-for="unit in units" :key="unit">{{ unit }}</option></select></label>
        <label>Phòng / nhóm<select v-model="filters.group"><option value="">Tất cả phòng / nhóm</option><option v-for="group in groups" :key="group">{{ group }}</option></select></label>
        <label>Chương trình<select v-model="filters.program"><option value="">Tất cả chương trình</option><option v-for="program in programs" :key="program">{{ program }}</option></select></label>
        <label>Khóa học<select v-model="filters.course"><option value="">Tất cả khóa học</option><option v-for="course in courses" :key="course">{{ course }}</option></select></label>
        <label>Trạng thái<select v-model="filters.status"><option value="">Tất cả trạng thái</option><option v-for="status in [...statuses,'Quá hạn']" :key="status">{{ status }}</option></select></label>
      </div>
      <p v-if="invalidDates" class="validation" role="alert">Chọn khoảng ngày hợp lệ, không vượt quá ngày chốt {{ displayDate(reportAsOf) }}.</p>
      <div class="filter-foot"><span><span class="scope-dot"></span>{{ rows.length }} lượt học · {{ learnerCount }} học viên</span></div>
      <p class="print-scope">Hoàn thành {{ displayDate(filters.from) }} – {{ displayDate(filters.to) }} · {{ filters.unit || 'Tất cả đơn vị' }} · {{ filters.group || 'Tất cả phòng / nhóm' }} · {{ filters.program || 'Tất cả chương trình' }} · {{ filters.course || 'Tất cả khóa học' }} · {{ filters.status || 'Tất cả trạng thái' }}</p>
    </section>

    <template v-if="selected==='overview'">
      <div class="section-label"><div><h2>KPI tổng quan</h2></div></div>
      <section class="kpi-grid" aria-label="Chỉ số điều hành"><article v-for="stat in kpis" :key="stat.label" class="kpi" :class="stat.tone"><div class="kpi-top"><span>{{ stat.label }}</span><component :is="stat.icon" :size="18"/></div><strong>{{ stat.value }}</strong><small>{{ stat.note }}</small></article></section>
      <div class="section-label"><div><h2>Tiến độ đào tạo</h2></div></div>
      <section class="report-card status-overview" aria-label="Tình hình học tập chung">
        <div class="status-heading"><div><h2>Tình hình học tập chung</h2></div><button class="text-button" @click="selectView('completion')">Xem chi tiết <ArrowUpRight :size="15"/></button></div>
        <div v-if="rows.length" class="status-track" aria-hidden="true"><span v-for="(status,index) in statuses" :key="status" :style="{width:`${countStatus(status)/rows.length*100}%`,background:statusColors[index]}"></span></div><p v-else class="no-data">Không có lượt học trong phạm vi đã chọn.</p>
        <div class="completion-legend"><button v-for="(status,index) in statuses" :key="status" @click="showStatus(status)"><i :style="{background:statusColors[index]}"></i>{{ status }} <strong>{{ countStatus(status) }}</strong><small>{{ percent(countStatus(status),rows.length) }}</small></button></div>
      </section>
      <section class="outcomes-grid training-progress">
        <article class="report-card"><div class="card-heading"><div><h3>Lượt hoàn thành theo tháng</h3><p></p></div><span class="chart-tag">{{ completedInPeriod.length }} lượt</span></div><ReportMockChart v-if="completedInPeriod.length" :config="trendChart" description="Lượt hoàn thành theo ngày được ghi nhận"/><div v-else class="chart-empty"><CalendarCheck :size="28"/><p>Chưa có lượt hoàn thành trong kỳ.</p></div><div class="trend-values"><span v-for="item in months" :key="item.key">{{ item.label }} <strong>{{ item.count }}</strong></span></div></article>
        <article class="report-card"><div class="card-heading"><div><h3>So sánh tiến độ phòng / nhóm</h3><p>Tỷ lệ hoàn thành, đang học và chưa bắt đầu trên lượt học.</p></div></div><ReportMockChart v-if="rows.length" :config="groupComparison" description="So sánh tỷ lệ trạng thái giữa các phòng nhóm"/><p v-else class="no-data">Chưa có dữ liệu so sánh.</p><div class="group-scale"><button v-for="group in aggregate('group')" :key="group.name" @click="filters.group=group.name;selectView('groups')">{{ group.name }} <strong>{{ group.complete }}/{{ group.total }} hoàn thành</strong></button></div></article>
      </section>
      <div class="section-label"><div><h2>Kết quả đào tạo</h2></div></div>
      <section class="outcomes-grid">
        <article class="report-card"><div class="card-heading"><div><h3>Kết quả kiểm tra</h3><p>So sánh trên cùng {{ tested.length }} lượt học đã có kết quả.</p></div><Target :size="19"/></div><div class="exam-metrics"><div><span>Đạt ngay lần đầu</span><strong>{{ percent(firstPassed.length,tested.length) }}</strong><small>{{ firstPassed.length }} / {{ tested.length }} lượt đã kiểm tra</small><div class="metric-track"><span :style="{width:`${rate(firstPassed.length,tested.length)}%`}"></span></div></div><div><span>Đã từng đạt</span><strong>{{ percent(passedRows.length,tested.length) }}</strong><small>{{ passedRows.length }} / {{ tested.length }} lượt đã kiểm tra</small><div class="metric-track"><span :style="{width:`${rate(passedRows.length,tested.length)}%`}"></span></div></div></div><div class="exam-summary"><button @click="openActions('failed')"><strong>{{ failedRows.length }}</strong><span>Chưa từng đạt</span><ArrowUpRight :size="15"/></button><button @click="openActions('untested')"><strong>{{ rows.length-tested.length }}</strong><span>Chưa kiểm tra</span><ArrowUpRight :size="15"/></button></div><div class="card-foot">Đạt theo ngưỡng riêng từng bài. Một lượt học có nhiều lần thi chỉ được đếm một lần trong mẫu.</div><button class="text-button exam-link" @click="selectView('scores')">Xem phân bố điểm & chi tiết <ArrowUpRight :size="15"/></button></article>
        <article class="report-card knowledge-readiness"><h3>Kết quả theo lĩnh vực</h3><div class="readiness-value"><strong>{{ summary.evaluatedPeople }}</strong><span>/ {{ summary.people }} học viên đã có kiểm tra</span></div><button class="text-button" @click="selectView('domains')">Xem báo cáo lĩnh vực <ArrowUpRight :size="15"/></button></article>
      </section>
      <LiveCourseRatings :printing="printing"/>
      <div class="section-label"><div><h2>Danh sách cần chú ý</h2></div></div>
      <section class="priority-grid">
        <article class="report-card"><div class="card-heading"><div><h3>Đơn vị có lượt học quá hạn</h3><p>Xếp theo tỷ lệ quá hạn trên lượt học có hạn hoàn thành.</p></div><span class="chart-tag danger">CẦN CHÚ Ý</span></div>
          <div v-if="unitStats.length" class="ranking-list"><button v-for="unit in unitStats" :key="unit.name" class="ranking-row" @click="openUnit(unit.name)"><div><strong>{{ unit.name }}</strong><span>{{ percent(unit.overdue,unit.withDeadline) }}</span></div><div class="ranking-track"><span :style="{width:`${rate(unit.overdue,unit.withDeadline)}%`}"></span></div><small>{{ unit.overdue }} quá hạn / {{ unit.withDeadline }} lượt có hạn · {{ unit.total-unit.withDeadline }} chưa có hạn</small></button></div><p v-else class="no-data">Không có dữ liệu đơn vị.</p>
          <div class="card-foot"><Info :size="13"/> Bấm đơn vị để xem lượt học quá hạn. Đơn vị chưa có hạn hoàn thành không được tính tỷ lệ.</div>
        </article>
        <article class="report-card"><div class="card-heading"><div><h3>Khóa học cần can thiệp</h3><p>{{ courseMode==='progress'?'Ưu tiên số lượt quá hạn, sau đó đến số lượt chưa hoàn thành.':'Ưu tiên số lượt đã kiểm tra nhưng chưa từng đạt.' }}</p></div></div>
          <div class="segmented" aria-label="Chế độ theo dõi khóa học"><button :aria-pressed="courseMode==='progress'" :class="{active:courseMode==='progress'}" @click="courseMode='progress'">Tiến độ học</button><button :aria-pressed="courseMode==='results'" :class="{active:courseMode==='results'}" @click="courseMode='results'">Kết quả kiểm tra</button></div>
          <div v-if="courseStats.length" class="course-rankings"><button v-for="course in courseStats" :key="course.name" class="course-row" @click="openCourse(course.name)"><div><strong>{{ course.name }}</strong><ArrowUpRight :size="14"/></div><div class="mini-stack" aria-hidden="true"><span v-for="(color,index) in courseMode==='progress'?statusColors:examColors" :key="color" :style="{background:color,width:`${(courseMode==='progress'?[course.complete,course.learning,course.notStarted]:[course.passed,course.failed,course.total-course.tested])[index]!/course.total*100}%`}"></span></div><small v-if="courseMode==='progress'"><b>{{ course.overdue }} quá hạn</b> · {{ course.total-course.complete }} chưa hoàn thành / {{ course.total }} lượt</small><small v-else><b>{{ course.failed }} chưa đạt</b> / {{ course.tested }} đã kiểm tra · {{ course.total-course.tested }} chưa kiểm tra</small></button></div><p v-else class="no-data">Không có dữ liệu khóa học.</p>
          <div class="mini-legend"><span v-for="(label,index) in courseMode==='progress'?statuses:examLabels" :key="label"><i :style="{background:(courseMode==='progress'?statusColors:examColors)[index]}"></i>{{ label }}</span></div><div class="card-foot">Tối đa 5 khóa · Bấm khóa học để mở danh sách cần hỗ trợ.</div>
        </article>
      </section>
    </template>
    <template v-else>
      <section class="detail-metric-row" aria-label="Chỉ số tổng quan báo cáo"><div v-for="stat in kpis" :key="stat.label"><span>{{ stat.label }}</span><strong>{{ stat.value }}</strong><small>{{ stat.note }}</small></div></section>

      <div class="detail-title"><h3>{{ currentView?.label }}</h3><p>{{ currentView?.caption }}</p></div>

      <article v-if="showDetailChart&&rows.length" class="report-card detail-chart"><div class="card-heading"><div><h3>{{ detailChartTitle }}</h3><p>{{ selected==='domains'?'Điểm gần nhất trung bình /100; số học viên được đánh giá ở bảng bên dưới.':selected==='scores'?'Điểm gần nhất cho mỗi lượt học. Ngưỡng đạt theo từng bài.':selected==='programs'?'Chỉ tính lượt khóa bắt buộc được giao, cùng mẫu số với bảng tiến độ chương trình.':'Tỷ lệ trạng thái theo đối tượng; quy mô thực tế được ghi ở bảng.' }}</p></div></div><ReportMockChart :config="detailChart" :description="detailChartTitle+'. Số liệu chi tiết trong bảng bên dưới.'"/></article>
    </template>
    <LiveCourseRatings v-if="selected==='courses'" :printing="printing"/>
    <section ref="tableSection" class="report-card detail-card" aria-label="Báo cáo chi tiết và danh sách xử lý">
      <div class="detail-heading"><div><h3>{{ selected==='overview'?'Ưu tiên hỗ trợ học viên':`Chi tiết ${currentView?.label.toLocaleLowerCase('vi')}` }}</h3><p>{{ selected==='overview'?'Chọn tình huống để xem từng lượt học cần xử lý.':'Tìm kiếm áp dụng cho bảng và CSV, không thay đổi biểu đồ.' }}</p></div><label class="search-input"><Search :size="15"/><input v-model="search" aria-label="Tìm trong báo cáo chi tiết" placeholder="Tìm học viên, khóa học..."/></label></div>
      <div v-if="selected==='overview'" class="action-tabs" aria-label="Tình huống cần xử lý"><button v-for="item in actionOptions" :key="item.id" :class="{active:actionType===item.id}" :aria-pressed="actionType===item.id" @click="actionType=item.id">{{ item.label }} <span>{{ item.count }}</span></button></div>
      <div class="table-scroll" tabindex="0" role="region" aria-label="Bảng báo cáo, cuộn ngang để xem thêm"><table><caption class="sr-only">Báo cáo — {{ selected==='overview'?'Danh sách cần xử lý':currentView?.label }}</caption><thead><tr><th v-for="heading in tableHeaders" :key="heading" scope="col">{{ heading }}</th></tr></thead><tbody><tr v-for="row in pagedTable" :key="row.id"><td v-for="(cell,index) in row.cells" :key="index"><span :class="{pill:statuses.includes(String(cell))||String(cell).includes('quá hạn'),good:cell==='Hoàn thành',bad:String(cell).includes('quá hạn')}">{{ cell }}</span></td><td v-if="row.record"><button class="text-button" :aria-label="`Chi tiết ${row.record.name} - ${row.record.courseName}`" @click="inspectRow=row.record">Chi tiết <ArrowUpRight :size="13"/></button></td><td v-else-if="row.drill"><button class="text-button" :aria-label="`Xem chi tiết ${row.drill.title}`" @click="dimensionRow=row.drill">Xem chi tiết <ArrowUpRight :size="13"/></button></td></tr><tr v-if="!searchedTable.length"><td :colspan="tableHeaders.length" class="table-empty"><strong>{{ rows.length?'Không có trường hợp phù hợp':'Không có dữ liệu trong phạm vi đã chọn' }}</strong><p>Thay đổi bộ lọc, tình huống hoặc tìm kiếm.</p></td></tr></tbody></table></div>
      <TablePagination v-if="!printing" v-model:page="page" v-model:page-size="pageSize" :total="searchedTable.length" :page-count="pageCount" class="report-pagination"/>
    </section>

    <dialog ref="dimensionDialog" class="learner-dialog dimension-dialog" @close="dimensionRow=undefined">
      <template v-if="dimensionRow"><div class="dialog-heading"><div><span class="eyebrow">BÁO CÁO CHI TIẾT</span><h2>{{ dimensionRow.title }}</h2><p>{{ dimensionRow.items.length }} lượt học trong phạm vi đã chọn</p></div><button class="r-button" aria-label="Đóng báo cáo chi tiết" @click="dimensionDialog?.close()"><X :size="18"/></button></div>
      <div class="drill-actions"><button v-if="dimensionRow.kind==='units'" class="r-button" @click="filters.unit=dimensionRow.title;selectView('groups');dimensionDialog?.close()">Xem phòng / nhóm trong đơn vị</button><button v-if="dimensionRow.kind==='groups'" class="r-button" @click="filters.group=dimensionRow.title;selectView('learners');dimensionDialog?.close()">Xem học viên trong nhóm</button><button v-if="dimensionRow.kind==='programs'" class="r-button" @click="filters.program=dimensionRow.title;selectView('courses');dimensionDialog?.close()">So sánh khóa học thành phần</button><button v-if="dimensionRow.kind==='courses'" class="r-button" @click="filters.course=dimensionRow.title;selectView('learners');dimensionDialog?.close()">Danh sách học viên khóa này</button></div>
      <h3>{{ dimensionRow.kind==='learners'?'Hồ sơ học tập · khóa được giao và tham gia':'Tiến độ từng khóa thành phần' }}</h3>
      <div class="table-scroll"><table><thead><tr><th>Khóa học</th><th>Bắt buộc / lượt giao</th><th>Hoàn thành</th><th>Tiến độ bắt buộc</th></tr></thead><tbody><tr v-for="course in visibleDimensionCourses" :key="course.name"><td>{{ course.name }}</td><td>{{ course.mandatory }} / {{ course.total }}</td><td>{{ course.complete }} / {{ course.total }}</td><td>{{ percent(course.mandatoryComplete,course.mandatory) }}</td></tr></tbody></table></div>
      <TablePagination v-if="!printing" v-model:page="dimensionPage" v-model:page-size="dimensionPageSize" :total="dimensionCourses.length" :page-count="dimensionPageCount" unit="khóa học" unit-label="Khóa học"/>
      <h3 class="drill-subtitle">Học viên & lịch sử kiểm tra</h3><div class="drill-records"><button v-for="item in dimensionRow.items" :key="item.id" @click="inspectRow=item"><span><strong>{{ item.name }}</strong><small>{{ item.courseName }} · {{ item.status }} · {{ item.progress }}% bài học</small></span><span>{{ item.score===null?'Chưa kiểm tra':`${item.score}/100 gần nhất` }} <ArrowUpRight :size="14"/></span></button></div>

      </template>
    </dialog>
    <dialog ref="detailDialog" class="learner-dialog" @close="inspectRow=null" @click="closeOnBackdrop"><template v-if="inspectRow"><div class="dialog-heading"><div><span class="eyebrow">CHI TIẾT LƯỢT HỌC</span><h2>{{ inspectRow.name }}</h2><p>{{ inspectRow.learnerId }} · {{ inspectRow.unit }}</p></div><button class="r-button" aria-label="Đóng chi tiết" @click="detailDialog?.close()"><X :size="18"/></button></div><h3>{{ inspectRow.courseName }}</h3><dl><div><dt>Trạng thái</dt><dd>{{ inspectRow.status }} · {{ inspectRow.progress }}% tiến độ bài học</dd></div><div><dt>Hạn hoàn thành</dt><dd>{{ inspectRow.dueDate?displayDate(inspectRow.dueDate):'Chưa quy định' }}</dd></div><div><dt>Tình huống</dt><dd>{{ reason(inspectRow) }}</dd></div></dl><h3>Lịch sử kiểm tra</h3><ol v-if="inspectRow.attempts.length" class="attempt-list"><li v-for="(attempt,index) in inspectRow.attempts" :key="index"><span>{{ attempt.assessment }} · Lần {{ index+1 }} · {{ displayDate(attempt.takenAt) }}</span><strong>{{ attempt.score }}/100 · {{ Math.round(attempt.score)>=(attempt.passingScore??inspectRow.passingScore)?'Đạt':'Chưa đạt' }}</strong></li></ol><p v-else>Chưa có lần kiểm tra nào.</p><details class="question-analysis"><summary>Phân tích câu hỏi</summary><p>Chưa hỗ trợ phân tích câu trả lời tại báo cáo này.</p></details><div class="dialog-note">Gợi ý: {{ isOverdue(inspectRow)?'Phối hợp đơn vị để xác nhận kế hoạch hoàn thành.':isFailed(inspectRow)?'Hướng dẫn ôn tập và kiểm tra lại.':'Theo dõi và hỗ trợ hoàn thành nội dung còn lại.' }}</div></template></dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue';
import type { ChartConfiguration } from 'chart.js';
import { Users, CircleCheck, Target, Download, Printer, Info, SlidersHorizontal, Search, ArrowUpRight, AlarmClock, CalendarCheck, X } from 'lucide-vue-next';
import ReportMockChart from '../components/ReportMockChart.vue';
import LiveCourseRatings from '../components/LiveCourseRatings.vue';
import TablePagination from '../components/TablePagination.vue';
import { useTablePagination } from '../composables/useTablePagination';
import { scrollWithin } from '../utils/scrollWithin';
import { reportViews } from '../utils/reportingViews';
import type { ReportView } from '../utils/reportingViews';
import { getTrainingReport } from '../api/trainingReportApi';
import type { ReportRow } from '../api/trainingReportApi';
import { useRoute, useRouter } from 'vue-router';
import { summarize, learningStatus, latestScore, hasPassed, firstAttemptPassed, overdue, formatRatio } from '../utils/reportingMetrics';
type View = ReportView|'overview';
type ActionType = 'overdue'|'dueSoon'|'failed'|'untested'|'incomplete';
type TableRow = {id:string;cells:(string|number)[];record?:ReportRow;drill?:{title:string;kind:View;items:ReportRow[]}};
const route=useRoute(),router=useRouter();
const selected=ref<View>('overview');
watch(()=>route.query.report,value=>{selected.value=reportViews.some(v=>v.id===value)?value as ReportView:'overview';search.value='';},{flush:'post'});
const currentView=computed(()=>reportViews.find(view=>view.id===selected.value));
const reportAsOf=ref(new Date().toLocaleDateString('en-CA'));
const defaults={from:'',to:'',unit:'',group:'',program:'',course:'',status:''};
const filters=reactive({...defaults});
const search=ref(''),printing=ref(false);
const actionType=ref<ActionType>('overdue'),courseMode=ref<'progress'|'results'>('progress');
const tableSection=ref<HTMLElement>(),inspectRow=ref<ReportRow|null>(null),detailDialog=ref<HTMLDialogElement>();
const dimensionRow=ref<TableRow['drill']>(),dimensionDialog=ref<HTMLDialogElement>();
const statuses=['Hoàn thành','Đang học','Chưa bắt đầu'];
selected.value=reportViews.some(v=>v.id===route.query.report)?route.query.report as ReportView:'overview';
const sourceRows=ref<ReportRow[]>([]),loading=ref(true),loadError=ref('');
let loadSequence=0;
async function loadReport(){const request=++loadSequence;loading.value=true;loadError.value='';sourceRows.value=[];try{const data=await getTrainingReport();if(request!==loadSequence)return;reportAsOf.value=data.asOf;sourceRows.value=data.rows.map(row=>({...row,date:'',status:learningStatus(row),score:latestScore(row),passed:hasPassed(row)}));}catch(e){if(request===loadSequence)loadError.value=(e as {response?:{status?:number}}).response?.status===403?'Bạn chưa có quyền xem báo cáo đào tạo.':'Không thể tải dữ liệu báo cáo. Vui lòng thử lại.';}finally{if(request===loadSequence)loading.value=false;}}

const statusColors=['#00856a','#e5b04c','#c4cfd8'],examColors=['#00856a','#cc6d59','#c4cfd8'];
const examLabels=['Đã từng đạt','Chưa từng đạt','Chưa kiểm tra'];
const unique=(key:keyof ReportRow)=>computed(()=>[...new Set(sourceRows.value.map(row=>String(row[key])))].sort((a,b)=>a.localeCompare(b,'vi')));
const units=unique('unit'),groups=unique('group'),programs=unique('program'),courses=unique('courseName');
const displayDate=(date:string)=>date?date.slice(0,10).split('-').reverse().join('/'):'—';
const invalidDates=computed(()=>!!(filters.from&&filters.to&&filters.from>filters.to)||filters.to>reportAsOf.value||filters.from>reportAsOf.value);
const rows=computed(()=>invalidDates.value?[]:sourceRows.value.filter(row=>(!filters.from||!!row.completedAt&&row.completedAt>=filters.from)&&(!filters.to||!!row.completedAt&&row.completedAt<=filters.to)&&(!filters.unit||row.unit===filters.unit)&&(!filters.group||row.group===filters.group)&&(!filters.program||row.program===filters.program)&&(!filters.course||row.courseName===filters.course)&&(!filters.status||(filters.status==='Quá hạn'?overdue(row,reportAsOf.value):row.status===filters.status))));
const rate=(n:number,total:number)=>total?Math.round(n/total*100):0;
const percent=formatRatio;
const isOverdue=(row:ReportRow)=>overdue(row,reportAsOf.value);
const summary=computed(()=>summarize(rows.value,reportAsOf.value));
const dueSoonLimit=computed(()=>new Date(Date.parse(reportAsOf.value)+7*86400000).toISOString().slice(0,10));
const isDueSoon=(row:ReportRow)=>row.status==='Chưa bắt đầu'&&!!row.dueDate&&row.dueDate>=reportAsOf.value&&row.dueDate<=dueSoonLimit.value;
const isFailed=(row:ReportRow)=>row.attempts.length>0&&!row.passed;
const countStatus=(status:string)=>rows.value.filter(row=>row.status===status).length;
const learnerCount=computed(()=>new Set(rows.value.map(row=>row.learnerId)).size);
const tested=computed(()=>rows.value.filter(row=>row.attempts.length));
const firstPassed=computed(()=>tested.value.filter(firstAttemptPassed));
const passedRows=computed(()=>tested.value.filter(row=>row.passed));
const failedRows=computed(()=>tested.value.filter(isFailed));
const overdueRows=computed(()=>rows.value.filter(isOverdue));
const kpis=computed(()=>[
  {label:'Học viên',value:summary.value.people,icon:Users,note:`${summary.value.total} lượt học trong phạm vi`,tone:'teal'},
  {label:'Tỷ lệ hoàn thành',value:percent(summary.value.complete,summary.value.total),icon:CircleCheck,note:`${summary.value.complete} / ${summary.value.total} lượt học`,tone:'blue'},
  {label:'Điểm kiểm tra TB',value:summary.value.average===null?'—':`${summary.value.average.toFixed(1)}/100`,icon:Target,note:`TB điểm gần nhất · ${summary.value.tested} lượt đã thi`,tone:'teal'},
  {label:'Tỷ lệ đạt kiểm tra',value:percent(summary.value.passed,summary.value.tested),icon:Target,note:`${summary.value.passed} / ${summary.value.tested} lượt đã thi`,tone:'blue'},
  {label:'Học viên quá hạn',value:percent(summary.value.overduePeople,summary.value.peopleWithDeadline),icon:AlarmClock,note:`${summary.value.overduePeople} / ${summary.value.peopleWithDeadline} người có hạn`,tone:'red'},
]);
function aggregate(key:keyof ReportRow){
  const map=new Map<string,ReportRow[]>();
  rows.value.forEach(row=>{const name=String(row[key]);map.set(name,[...(map.get(name)||[]),row]);});
  return [...map].map(([name,items])=>({name,...summarize(items,reportAsOf.value)}));
}
const groupComparison=computed<ChartConfiguration>(()=>({type:'bar',data:{labels:aggregate('group').map(g=>g.name),datasets:statuses.map((label,index)=>({label,data:aggregate('group').map(g=>[g.complete,g.learning,g.notStarted][index]!/g.total*100),backgroundColor:statusColors[index],maxBarThickness:25}))},options:{indexAxis:'y',plugins:{legend:{position:'bottom',labels:{boxWidth:8,usePointStyle:true}},tooltip:{callbacks:{label:c=>`${c.dataset.label}: ${Number(c.raw).toFixed(1)}%`}}},scales:{x:{min:0,max:100,stacked:true,ticks:{callback:v=>`${v}%`}},y:{stacked:true,grid:{display:false}}}}}));
const unitStats=computed(()=>aggregate('unit').sort((a,b)=>(b.withDeadline?b.overdue/b.withDeadline:-1)-(a.withDeadline?a.overdue/a.withDeadline:-1)||b.overdue-a.overdue));
const courseStats=computed(()=>aggregate('courseName').sort((a,b)=>courseMode.value==='progress'?b.overdue-a.overdue||(b.total-b.complete)-(a.total-a.complete):b.failed-a.failed||(b.total-b.tested)-(a.total-a.tested)).slice(0,5));
const completedInPeriod=computed(()=>rows.value.filter(row=>row.completedAt&&(!filters.from||row.completedAt>=filters.from)&&row.completedAt<=(filters.to||reportAsOf.value)));
const months=computed(()=>{
  if(invalidDates.value)return [];
  const result:{key:string;label:string;count:number}[]=[];
  let cursor=filters.from.slice(0,7)||completedInPeriod.value.map(row=>row.completedAt!).sort()[0]?.slice(0,7)||reportAsOf.value.slice(0,7);
  while(cursor<=(filters.to||reportAsOf.value).slice(0,7)&&result.length<120){
    const monthKey=cursor;
    result.push({key:monthKey,label:`T${Number(monthKey.slice(5))}/${monthKey.slice(0,4)}`,count:completedInPeriod.value.filter(row=>row.completedAt!.startsWith(monthKey)).length});
    const month=Number(cursor.slice(5));cursor=month===12?`${Number(cursor.slice(0,4))+1}-01`:`${cursor.slice(0,4)}-${String(month+1).padStart(2,'0')}`;
  }return result;
});
const trendChart=computed<ChartConfiguration>(()=>({type:'bar',data:{labels:months.value.map(m=>m.label),datasets:[{label:'Lượt hoàn thành',data:months.value.map(m=>m.count),backgroundColor:'#00856a',borderRadius:5,maxBarThickness:40}]},options:{plugins:{legend:{display:false}},scales:{y:{beginAtZero:true,ticks:{precision:0}},x:{grid:{display:false}}}}}));
const actionOptions=computed(()=>[
  {id:'overdue' as const,label:'Quá hạn',count:overdueRows.value.length},
  {id:'dueSoon' as const,label:'Sắp đến hạn, chưa bắt đầu',count:rows.value.filter(isDueSoon).length},
  {id:'failed' as const,label:'Đã thi, chưa từng đạt',count:failedRows.value.length},
  {id:'untested' as const,label:'Chưa kiểm tra',count:rows.value.length-tested.value.length},
  ...(actionType.value==='incomplete'?[{id:'incomplete' as const,label:'Chưa hoàn thành',count:rows.value.filter(row=>row.status!=='Hoàn thành').length}]:[]),
]);
const matchesAction=(row:ReportRow)=>({overdue:isOverdue,dueSoon:isDueSoon,failed:isFailed,untested:(r:ReportRow)=>!r.attempts.length,incomplete:(r:ReportRow)=>r.status!=='Hoàn thành'})[actionType.value](row);
function reason(row:ReportRow){
  if(isOverdue(row))return `${Math.floor((Date.parse(reportAsOf.value)-Date.parse(row.dueDate!))/86400000)} ngày quá hạn`;
  if(isDueSoon(row))return 'Đến hạn trong 7 ngày, chưa bắt đầu';
  if(isFailed(row))return 'Đã kiểm tra, chưa từng đạt';
  if(!row.attempts.length)return 'Chưa kiểm tra';
  return row.status==='Hoàn thành'?'Đã hoàn thành':'Tiếp tục theo dõi tiến độ';
}
const detailItems=computed(()=>selected.value==='overview'?rows.value.filter(matchesAction):rows.value);
const groupKey=computed(():keyof ReportRow=>({learners:'learnerId',courses:'courseName',programs:'program',groups:'group',units:'unit',domains:'domain',completion:'status'} as Partial<Record<View,keyof ReportRow>>)[selected.value]||'courseName');
const grouped=computed(()=>aggregate(groupKey.value));
const scoreBins=computed(()=>[{label:'Dưới 50',min:0,max:49},{label:'50–69',min:50,max:69},{label:'70–84',min:70,max:84},{label:'85–100',min:85,max:100}].map(bin=>({...bin,count:tested.value.filter(row=>row.score!>=bin.min&&row.score!<=bin.max).length})));
const showDetailChart=computed(()=>!['learners','completion','overview'].includes(selected.value));
const detailChartTitle=computed(()=>selected.value==='domains'?'Điểm kiểm tra gần nhất theo lĩnh vực':selected.value==='scores'?'Phân bố điểm kiểm tra gần nhất':selected.value==='programs'?'Tiến độ chương trình · khóa bắt buộc':'So sánh tiến độ học tập');
const detailChart=computed<ChartConfiguration>(()=>{
  if(selected.value==='scores')return {type:'bar',data:{labels:scoreBins.value.map(b=>b.label),datasets:[{label:'Lượt học',data:scoreBins.value.map(b=>b.count),backgroundColor:'#4a8fa8',borderRadius:4}]},options:{plugins:{legend:{display:false}},scales:{y:{beginAtZero:true,ticks:{precision:0}}}}};
  if(selected.value==='programs')return {type:'bar',data:{labels:grouped.value.map(g=>g.name),datasets:[{label:'Hoàn thành khóa bắt buộc (%)',data:grouped.value.map(g=>g.programProgress),backgroundColor:'#00856a',borderRadius:4,maxBarThickness:30}]},options:{indexAxis:'y',plugins:{legend:{display:false}},scales:{x:{min:0,max:100,ticks:{callback:v=>`${v}%`}},y:{grid:{display:false}}}}};
  if(selected.value==='domains')return {type:'bar',data:{labels:grouped.value.map(g=>g.name),datasets:[{label:'Điểm gần nhất trung bình /100',data:grouped.value.map(g=>g.average),backgroundColor:'#4a8fa8',borderRadius:4,maxBarThickness:30}]},options:{indexAxis:'y',plugins:{legend:{display:false}},scales:{x:{min:0,max:100},y:{grid:{display:false}}}}};
  return {type:'bar',data:{labels:grouped.value.map(g=>g.name),datasets:statuses.map((label,index)=>({label,data:grouped.value.map(g=>[g.complete,g.learning,g.notStarted][index]!/g.total*100),backgroundColor:statusColors[index],maxBarThickness:28}))},options:{indexAxis:'y',plugins:{legend:{position:'bottom',labels:{usePointStyle:true,boxWidth:8}},tooltip:{callbacks:{label:context=>`${context.dataset.label}: ${Number(context.raw).toFixed(1)}%`}}},scales:{x:{min:0,max:100,stacked:true,ticks:{callback:value=>`${value}%`}},y:{stacked:true,grid:{display:false}}}}};
});
const individualTable=computed(()=>['overview','completion','scores'].includes(selected.value));
const tableHeaders=computed(()=>selected.value==='scores'?['Học viên','Khóa học','Số lần thi','TB điểm gần nhất /100','Kết quả qua các lần thi','Thao tác']:individualTable.value?['Học viên','Đơn vị / nhóm','Khóa học','Trạng thái','Hạn hoàn thành','Kết quả kiểm tra','Cần theo dõi','Thao tác']:selected.value==='domains'?['Lĩnh vực','Học viên','Lượt học','Tỷ lệ hoàn thành','Đạt kiểm tra / đã thi','Điểm kiểm tra TB /100','Thao tác']:selected.value==='learners'?['Học viên','Khóa được giao / tham gia','Hoàn thành','Tỷ lệ hoàn thành','Đạt kiểm tra / đã thi','Điểm TB /100','Thao tác']:selected.value==='programs'?['Chương trình','Học viên','Khóa bắt buộc đã giao','Bắt buộc hoàn thành','Tiến độ chương trình','Đạt kiểm tra / đã thi','Thao tác']:selected.value==='courses'?['Khóa học','Ghi danh','Hoàn thành','Tỷ lệ hoàn thành','Đạt kiểm tra / đã thi','Điểm TB /100','Bỏ dở','Thao tác']:['Đối tượng','Học viên','Lượt học','Tỷ lệ hoàn thành','Tỷ lệ đạt kiểm tra','Học viên quá hạn / có hạn','Thao tác']);
const tableRows=computed(():TableRow[]=>{
  if(selected.value==='scores')return detailItems.value.map(row=>({id:row.id,record:row,cells:[`${row.name} · ${row.learnerId}`,row.courseName,row.attempts.length,row.score??'Chưa kiểm tra',row.passed?'Đã từng đạt':row.attempts.length?'Chưa từng đạt':'Chưa kiểm tra']}));
  if(individualTable.value)return detailItems.value.map(row=>({id:row.id,record:row,cells:[`${row.name} · ${row.learnerId}`,`${row.unit} / ${row.group}`,row.courseName,row.status,row.dueDate?displayDate(row.dueDate):'Chưa quy định',!row.attempts.length?'Chưa kiểm tra':`${row.passed?'Đã từng đạt':'Chưa từng đạt'} · gần nhất ${row.score}/100`,reason(row)]}));
  return [...grouped.value].sort((a,b)=>['groups','units'].includes(selected.value)?(b.completionRate??-1)-(a.completionRate??-1):0).map(g=>{
    const items=rows.value.filter(row=>String(row[groupKey.value])===g.name),first=items[0]!;
    const avg=g.average===null?'Chưa đánh giá':g.average.toFixed(1);
    const pass=`${g.passed} / ${g.tested} (${percent(g.passed,g.tested)})`;
    const label=selected.value==='learners'?`${first.name} · ${g.name}`:g.name;
    const cells=selected.value==='domains'?[label,g.people,g.total,percent(g.complete,g.total),pass,avg]:selected.value==='learners'?[label,`${g.mandatory} / ${g.total}`,g.complete,percent(g.complete,g.total),pass,avg]:selected.value==='programs'?[label,g.people,g.mandatory,g.mandatoryComplete,percent(g.mandatoryComplete,g.mandatory),pass]:selected.value==='courses'?[label,g.total,g.complete,percent(g.complete,g.total),pass,avg,'Chưa có dữ liệu']:[label,g.people,g.total,percent(g.complete,g.total),pass,`${g.overduePeople} / ${g.peopleWithDeadline} (${percent(g.overduePeople,g.peopleWithDeadline)})`];
    return {id:g.name,cells,drill:{title:label,kind:selected.value,items}};
  });
});
const searchedTable=computed(()=>tableRows.value.filter(row=>row.cells.some(cell=>String(cell).toLocaleLowerCase('vi').includes(search.value.trim().toLocaleLowerCase('vi')))));
const {page,pageSize,pageCount,paginated}=useTablePagination(searchedTable);
const pagedTable=computed(()=>printing.value?searchedTable.value:paginated.value);
watch([filters,selected,search,actionType],()=>{page.value=1;});
watch(inspectRow,async row=>{if(row){await nextTick();detailDialog.value?.showModal();}else detailDialog.value?.close();});
watch(dimensionRow,async row=>{if(row){await nextTick();dimensionDialog.value?.showModal();}else dimensionDialog.value?.close();});
const dimensionCourses=computed(()=>{
  const items=dimensionRow.value?.items||[];
  return [...new Set(items.map(r=>r.courseName))].map(name=>({name,...summarize(items.filter(r=>r.courseName===name),reportAsOf.value)}));
});
const {page:dimensionPage,pageSize:dimensionPageSize,pageCount:dimensionPageCount,paginated:paginatedDimensionCourses,reset:resetDimensionPage}=useTablePagination(dimensionCourses);
const visibleDimensionCourses=computed(()=>printing.value?dimensionCourses.value:paginatedDimensionCourses.value);
watch(dimensionRow,resetDimensionPage);
async function selectView(view:View){selected.value=view;search.value='';await router.replace({path:route.path,query:{...route.query,tab:undefined,report:view==='overview'?undefined:view}});}
function reset(){Object.assign(filters,defaults);search.value='';}
async function scrollToTable(){await nextTick();scrollWithin(tableSection.value);}
async function openActions(type:ActionType){actionType.value=type;await selectView('overview');await scrollToTable();}
function openUnit(unit:string){filters.unit=unit;openActions('overdue');}
function openCourse(course:string){filters.course=course;openActions(courseMode.value==='results'?'failed':rows.value.some(isOverdue)?'overdue':'incomplete');}
async function showStatus(status:string){filters.status=status;await selectView('completion');await scrollToTable();}
function closeOnBackdrop(event:MouseEvent){if(event.target===detailDialog.value)detailDialog.value?.close();}
function exportCsv(){
  const escape=(value:string|number)=>`"${String(value).replace(/^[=+@\-\t\r]/,"'$&").replaceAll('"','""')}"`;
  const headers=tableHeaders.value.slice(0,-1);
  const scope=[`Hoàn thành ${filters.from} - ${filters.to}`,`chốt ${reportAsOf.value}`,filters.unit||'Tất cả đơn vị',filters.group||'Tất cả nhóm',filters.program||'Tất cả chương trình',filters.course||'Tất cả khóa',selected.value==='overview'?actionOptions.value.find(a=>a.id===actionType.value)?.label:currentView.value?.label,filters.status||'Tất cả trạng thái',`tìm: ${search.value}`].join('; ');
  const csv='\uFEFF'+[[scope],headers,...searchedTable.value.map(row=>row.cells)].map(row=>row.map(escape).join(',')).join('\r\n');
  const url=URL.createObjectURL(new Blob([csv],{type:'text/csv;charset=utf-8;'}));const a=document.createElement('a');a.href=url;a.download=`bao-cao-${selected.value}.csv`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
}
const beforePrint=()=>{printing.value=true;},afterPrint=()=>{printing.value=false;};
onMounted(()=>{void loadReport();window.addEventListener('beforeprint',beforePrint);window.addEventListener('afterprint',afterPrint);});
onBeforeUnmount(()=>{loadSequence++;window.removeEventListener('beforeprint',beforePrint);window.removeEventListener('afterprint',afterPrint);});
async function printReport(){printing.value=true;await nextTick();window.print();printing.value=false;}
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
@media(max-width:600px){
  .report-pagination{padding:12px}
  .heading-actions,.drill-actions{flex-wrap:wrap;min-width:0}
  .heading-actions .r-button{flex:1 1 8rem}
  .drill-actions .r-button{white-space:normal;text-align:left}
  .filters{grid-template-columns:minmax(0,1fr)}
}
</style>






