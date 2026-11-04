<template>
  <details class="question-analysis" @toggle="onToggle">
    <summary>Phân tích câu hỏi</summary>
    <p v-if="loading" role="status">Đang tải phân tích...</p>
    <p v-else-if="error" role="alert">{{ error }} <button class="retry" type="button" @click="load">Thử lại</button></p>
    <p v-else-if="analysis&&!analysis.available">{{ unavailableMessage }}</p>
    <template v-else-if="analysis">
      <p class="analysis-summary"><strong>{{ analysis.correct }}</strong> đúng · <strong>{{ analysis.incorrect }}</strong> sai · <strong>{{ analysis.unanswered }}</strong> bỏ trống / {{ analysis.questions.length }} câu</p>
      <p v-if="analysis.source==='CURRENT'" class="analysis-note">Hiển thị theo nội dung câu hỏi hiện tại vì lần kiểm tra này không lưu bản sao đề.</p>
      <ol class="analysis-questions" :start="(page-1)*pageSize+1">
        <li v-for="question in visibleQuestions" :key="question.id">
          <div class="question-head"><span class="question-text">{{ question.text }}</span><span class="question-result" :class="resultClass(question)">{{ resultLabel(question) }}</span></div>
          <ul class="question-options">
            <li v-for="(option,index) in question.options" :key="index" :class="{'is-correct':question.correctAnswers.includes(index),'is-wrong':question.selectedAnswers.includes(index)&&!question.correctAnswers.includes(index)}">
              <span>{{ option }}</span>
              <small v-if="optionLabel(question,index)">{{ optionLabel(question,index) }}</small>
            </li>
          </ul>
        </li>
      </ol>
      <TablePagination v-if="total>pageSize" v-model:page="page" :page-size="pageSize" :total="total" :page-count="pageCount" :sizes="[pageSize]" unit="câu hỏi" compact class="analysis-pagination"/>
    </template>
  </details>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import TablePagination from './TablePagination.vue';
import { useTablePagination } from '../composables/useTablePagination';
import { readReportAttemptAnalysis } from '../api/trainingReportApi';
import type { ReportAttemptAnalysis, ReportAttemptQuestion } from '../api/trainingReportApi';

const props = defineProps<{ attemptId: number }>();
const analysis = ref<ReportAttemptAnalysis|null>(null);
const loading = ref(false);
const error = ref('');
const questions = computed(() => analysis.value?.questions ?? []);
const { page, pageSize, total, pageCount, paginated: visibleQuestions } = useTablePagination(questions);
pageSize.value = 5;

const unavailableMessage = computed(() => analysis.value?.unavailableReason === 'QUIZ_CHANGED'
  ? 'Đề kiểm tra đã thay đổi sau lần làm bài này nên không thể phân tích chính xác.'
  : 'Lần kiểm tra này chưa lưu đáp án của học viên nên không thể phân tích.');

async function load() {
  loading.value = true;
  error.value = '';
  try {
    analysis.value = await readReportAttemptAnalysis(props.attemptId);
  } catch {
    error.value = 'Không thể tải phân tích. Vui lòng thử lại.';
  } finally {
    loading.value = false;
  }
}

function onToggle(event: Event) {
  if ((event.target as HTMLDetailsElement).open && !analysis.value && !loading.value) void load();
}

const resultLabel = (question: ReportAttemptQuestion) => question.correct ? 'Đúng' : question.answered ? 'Sai' : 'Bỏ trống';
const resultClass = (question: ReportAttemptQuestion) => question.correct ? 'is-correct' : question.answered ? 'is-wrong' : 'is-skipped';
function optionLabel(question: ReportAttemptQuestion, index: number) {
  const selected = question.selectedAnswers.includes(index), correct = question.correctAnswers.includes(index);
  return selected && correct ? 'Học viên chọn · Đáp án đúng' : selected ? 'Học viên chọn' : correct ? 'Đáp án đúng' : '';
}
</script>

<style scoped>
.question-analysis{flex:0 0 100%;width:100%;margin-top:6px;padding:12px;border:1px solid var(--border-color);border-radius:6px;font-size:12px}
.question-analysis summary{cursor:pointer;color:var(--primary-ink)}
.question-analysis p{margin-top:10px;font-size:11px}
.analysis-summary strong{font-size:12px}
.analysis-note{color:var(--text-muted)}
.retry{border:0;background:none;padding:0;font:inherit;color:var(--primary-ink);text-decoration:underline;cursor:pointer}
.analysis-questions{list-style:decimal;margin:12px 0 0;padding-left:18px;display:grid;gap:14px}
.analysis-questions>li{padding:0;border:0;display:list-item}
.analysis-pagination{margin-top:12px;min-height:0;padding-top:10px}
.question-head{display:flex;gap:10px;justify-content:space-between;align-items:flex-start}
.question-text{color:var(--text-main);font-weight:600;white-space:pre-line;overflow-wrap:anywhere}
.question-result{flex:none;padding:2px 7px;border-radius:999px;font-size:10px;font-weight:600}
.question-result.is-correct{background:var(--success-surface);color:var(--success-ink)}
.question-result.is-wrong{background:var(--danger-surface);color:var(--danger-ink)}
.question-result.is-skipped{background:var(--warning-surface);color:var(--warning-ink)}
.question-options{list-style:none;margin:8px 0 0;padding:0;display:grid;gap:4px}
.question-options li{display:flex;gap:10px;justify-content:space-between;padding:6px 8px;border:1px solid var(--border-color);border-radius:4px;font-size:11px;overflow-wrap:anywhere}
.question-options li.is-correct{background:var(--success-surface);color:var(--success-ink)}
.question-options li.is-wrong{background:var(--danger-surface);color:var(--danger-ink)}
.question-options small{flex:none;font-size:10px;font-weight:600}
@media(max-width:450px){.question-options li{flex-direction:column;gap:2px}.question-options small{flex:initial}}
</style>
