<template>
  <main class="quiz-editor-page" :class="{embedded}" :aria-busy="initialLoading">
    <header v-if="!embedded" class="quiz-editor-header">
      <button class="back-button" @click="router.push('/admin?tab=exams')">
        <ArrowLeft :size="19" />
        <span><strong>Quay lại</strong><small>Danh sách bài kiểm tra</small></span>
      </button>
      <div>
        <span class="eyebrow">{{ isNew ? "Tạo bài kiểm tra" : "Chi tiết bài kiểm tra" }}</span>
        <h1>{{ pageTitle }}</h1>
        <p>Thiết lập phạm vi một lần, sau đó quản lý toàn bộ câu hỏi bên dưới.</p>
      </div>
    </header>

    <section v-if="initialLoading" class="editor-card quiz-detail-loading" role="status" aria-live="polite">
      <LoaderCircle class="spin" :size="28" aria-hidden="true" />
      <p>Đang tải bài kiểm tra...</p>
    </section>
    <section v-else-if="loadError" class="editor-card quiz-detail-error" role="alert">
      <p>{{ loadError }}</p>
      <button class="secondary-button" @click="initialize">Thử lại</button>
    </section>
    <template v-else>
    <p v-if="courseId && !canEdit && !embedded" class="readonly-notice">Khóa học đang ở chế độ chỉ xem. Chuyển khóa về bản nháp để chỉnh sửa bài kiểm tra.</p>
    <section class="editor-card settings-card">
      <div class="section-heading">
        <span>1</span>
        <div><h2>Thông tin bài kiểm tra</h2><p>Chọn khóa học và phạm vi áp dụng.</p></div>
      </div>
      <div class="settings-grid">
        <label>
          <span>Khóa học</span>
          <select v-model="courseId" :disabled="!isNew">
            <option value="" disabled>Chọn khóa học</option>
            <option v-for="course in quizCourses" :key="course.id" :value="course.id">{{ course.name }}</option>
          </select>
        </label>
        <label>
          <span>Phạm vi</span>
          <select v-model="lessonId" :disabled="!isNew || !courseId || scopeLoading || !!scopeError">
            <option value="" disabled>Chọn bài học để đính kèm kiểm tra</option>
            <option v-for="lesson in availableLessons" :key="lesson.id" :value="lesson.id">
              Bài học: {{ lesson.title }}
            </option>
          </select>
          <small v-if="scopeLoading" role="status">Đang tải bài học...</small>
          <small v-if="scopeError" role="alert">{{ scopeError }} <button type="button" @click="loadLessonOptions">Thử lại</button></small>
        </label>
        <label>
          <span>Ngưỡng điểm đạt</span>
          <div class="score-input">
            <input v-model.number="passingScore" :disabled="!canAction('update') && !isNew" type="number" min="1" max="100" />
            <strong>%</strong>
          </div>
          <small>Học viên cần đạt tối thiểu mức này để hoàn thành bài kiểm tra.</small>
        </label>
      </div>
      <div class="required-quiz-setting"><ToggleSwitch v-model="requiredForProgress" :disabled="!canAction('update') && !isNew" label="Bắt buộc đạt để sang bài tiếp theo" /><small>Tắt tùy chọn: bài kiểm tra tự luyện, không chặn hoàn thành bài, chương hoặc khóa.</small></div>
      <div v-if="isNew && canEdit" class="settings-action">
        <p>Phạm vi sẽ tự động áp dụng cho mọi câu hỏi được thêm hoặc import.</p>
        <button class="primary-button" :disabled="!courseId || !lessonId || scopeLoading || !!scopeError" @click="confirmScope">
          Tiếp tục soạn câu hỏi <ArrowRight :size="17" />
        </button>
      </div>
      <div v-else-if="canAction('update')" class="settings-action">
        <p>{{ questions.length ? 'Thay đổi ngưỡng đạt sẽ áp dụng cho những lượt làm bài tiếp theo.' : 'Lưu ít nhất một câu hỏi để tạo bài kiểm tra và lưu cấu hình.' }}</p>
        <button class="primary-button" :disabled="savingSettings || !questions.length" @click="saveSettings"
          :title="!questions.length ? 'Thêm ít nhất một câu hỏi trước khi lưu cấu hình' : undefined">
          {{ savingSettings ? "Đang lưu..." : "Lưu ngưỡng đạt" }}
        </button>
      </div>
    </section>

    <template v-if="!isNew">
      <section class="editor-card">
        <div class="section-heading question-section-heading">
          <span>2</span>
          <div><h2>Danh sách câu hỏi</h2><p>{{ questions.length }} câu hỏi trong bài kiểm tra.</p></div>
          <div v-if="canAction('create')" class="question-tools">
            <button class="secondary-button" @click="showBank = true">Ngân hàng câu hỏi</button>
            <button v-if="canAction('update')" class="ai-button" :disabled="generating" @click="showAiPanel = !showAiPanel">
              <Sparkles :size="16" /> {{ generating ? "Đang tạo..." : "Tạo bằng AI" }}
            </button>
            <button class="secondary-button" @click="downloadTemplate"><Download :size="16" /> File mẫu</button>
            <label class="secondary-button import-button">
              <FileUp :size="16" /> Import CSV
              <input type="file" accept=".csv,text/csv" @change="importCsv" />
            </label>
            <button class="primary-button" @click="startAdding"><Plus :size="16" /> Thêm câu hỏi</button>
          </div>
        </div>

        <div class="search-box">
          <Search :size="18" />
          <input v-model="searchQuery" aria-label="Tìm câu hỏi" type="search" placeholder="Tìm theo nội dung câu hỏi hoặc đáp án..." />
        </div>

        <section v-if="canEdit && showAiPanel" class="ai-panel">
          <div class="ai-panel-heading">
            <div>
              <strong>Tạo bản nháp từ tài liệu của {{ lessonId ? "bài học" : "khóa học" }}</strong>
              <small>{{ lessonId ? 'Chọn và lưu nguồn kiến thức của bài học trước khi tạo câu hỏi.' : 'AI dùng tài liệu của khóa học đã chọn.' }}</small>
            </div>
            <button aria-label="Đóng" @click="showAiPanel = false"><X :size="18" /></button>
          </div>
          <LessonKnowledgeSources permission-page="exams" v-if="lessonId" :key="`${courseId}:${lessonId}`" :course-id="courseId" :lesson-id="lessonId" :disabled="generating || savingDrafts" @ready="handleSourceReady" />
          <div class="ai-settings">
            <label><span>Single choice</span><input v-model.number="aiConfig.singleChoiceCount" type="number" min="0" max="10" /></label>
            <label><span>Multiple choice</span><input v-model.number="aiConfig.multipleChoiceCount" type="number" min="0" max="10" /></label>
            <label><span>Số lựa chọn</span><input v-model.number="aiConfig.optionCount" type="number" min="3" max="6" /></label>
            <button class="primary-button" :disabled="generating || (Boolean(lessonId) && !sourceReady)" @click="generateQuestions">
              <LoaderCircle v-if="generating" class="spin" :size="17" />
              <Sparkles v-else :size="17" />
              {{ generating ? `Đang tạo... ${generationProgress?.percent ?? 0}%` : "Tạo bản nháp" }}
            </button>
          </div>
          <div v-if="generationProgress" class="ai-progress" :class="{ failed: generationProgress.failed }" role="status" aria-live="polite">
            <div class="ai-progress-header"><span>{{ generationProgress.label }}</span><strong>{{ generationProgress.percent }}%</strong></div>
            <div class="ai-progress-track" role="progressbar" aria-label="Tiến độ tạo câu hỏi" aria-valuemin="0" aria-valuemax="100" :aria-valuenow="generationProgress.percent">
              <div class="ai-progress-fill" :style="{ width: `${generationProgress.percent}%` }" />
            </div>
            <small v-if="generationProgress.detail">{{ generationProgress.detail }}</small>
          </div>
          <p v-for="warning in aiWarnings" :key="warning" class="ai-warning">{{ warning }}</p>
          <div v-if="aiDrafts.length" class="ai-drafts">
<div class="admin-table-scroll"><table class="admin-data-table"><colgroup><col style="width:80px" /><col /></colgroup><thead><tr><th>Chọn</th><th>Bản nháp câu hỏi / đáp án</th></tr></thead><tbody>            <tr v-for="(draft,index) in paginatedDrafts" :key="(draftPage-1)*draftPageSize+index"><td>
              <ToggleSwitch v-model="draft.selected" :show-label="false" :label="`Chọn câu ${ (draftPage-1)*draftPageSize + index+1 }`" :disabled="savingDrafts" />
</td><td class="question-table-content"><div class="draft-heading-actions">
                <span class="question-type-badge">{{ draft.type === "MULTIPLE_CHOICE" ? "Nhiều đáp án" : "Một đáp án" }}</span>
                <button v-if="editingDraftIndex !== (draftPage-1)*draftPageSize+index" class="draft-edit-button" @click="startDraftEditing((draftPage-1)*draftPageSize+index)">
                  <Edit :size="15" /> Sửa
                </button>
              </div>
              <div v-if="editingDraftIndex === (draftPage-1)*draftPageSize+index" class="ai-draft-editor">
                <label class="question-text-field">
                  <span>Nội dung câu hỏi</span>
                  <textarea v-model="draft.text" rows="3" />
                </label>
                <label class="question-type-field">
                  <span>Loại câu hỏi</span>
                  <select v-model="draft.type" @change="normalizeDraftAnswers(draft)">
                    <option value="SINGLE_CHOICE">Single choice</option>
                    <option value="MULTIPLE_CHOICE">Multiple choice</option>
                  </select>
                </label>
                <div class="ai-draft-answer-editor">
                  <label v-for="(_, optionIndex) in draft.options" :key="optionIndex"
                    :class="{ correct: draft.correctAnswers.includes(optionIndex) }">
                    <ToggleSwitch :show-label="false" v-if="draft.type === 'MULTIPLE_CHOICE'" :model-value="draft.correctAnswers.includes(optionIndex)" :label="`Đáp án ${String.fromCharCode(65+optionIndex)} là đáp án đúng`" @update:model-value="toggleDraftCorrectAnswer(draft, optionIndex)" />
                    <input v-else type="radio" :checked="draft.correctAnswers.includes(optionIndex)" :name="`draft-correct-answer-${index}`" @change="toggleDraftCorrectAnswer(draft, optionIndex)" />
                    <strong>{{ String.fromCharCode(65 + optionIndex) }}</strong>
                    <input v-model="draft.options[optionIndex]" type="text" :placeholder="`Nhập đáp án ${String.fromCharCode(65 + optionIndex)}`" />
                    <CheckCircle2 v-if="draft.correctAnswers.includes(optionIndex)" :size="17" />
                  </label>
                </div>
                <div class="draft-editor-actions">
                  <button class="secondary-button" @click="cancelDraftEditing">Hủy</button>
                  <button class="primary-button" @click="finishDraftEditing((draftPage-1)*draftPageSize+index)">Hoàn tất chỉnh sửa</button>
                </div>
              </div>
              <template v-else>
                <h3>{{ draft.text }}</h3>
                <div class="answer-grid">
                  <span v-for="(option, optionIndex) in draft.options" :key="optionIndex"
                    :class="{ correct: draft.correctAnswers.includes(optionIndex) }">
                    <strong>{{ String.fromCharCode(65 + optionIndex) }}</strong>{{ option }}
                    <CheckCircle2 v-if="draft.correctAnswers.includes(optionIndex)" :size="15" />
                  </span>
                </div>
                <p v-if="draft.explanation" class="draft-explanation">{{ draft.explanation }}</p>
              </template>
              <small v-if="draft.sourceIds.length" class="draft-sources">
                Nguồn: {{ formatDraftSources(draft.sourceIds) }}
              </small>
</td></tr>
</tbody></table></div><TablePagination v-model:page="draftPage" v-model:page-size="draftPageSize" :total="aiDrafts.length" :page-count="draftPageCount" unit="bản nháp" unit-label="Bản nháp" />
            <div class="ai-draft-actions">
              <button class="secondary-button" @click="discardAiDrafts">Bỏ bản nháp</button>
              <button class="primary-button" :disabled="savingDrafts || !selectedDraftCount" @click="saveAiDrafts">
                {{ savingDrafts ? "Đang lưu..." : `Lưu ${selectedDraftCount} câu đã chọn` }}
              </button>
            </div>
          </div>
        </section>

        <AdminModal :open="canEdit && showQuestionForm" :title="editingId?'Chỉnh sửa câu hỏi':'Thêm câu hỏi'" :busy="saving" :width="860" submit-label="Lưu câu hỏi" @close="closeQuestionForm" @submit="saveQuestion"><div class="question-editor modal-question-editor">
          <label class="question-text-field">
            <span>Nội dung câu hỏi</span>
            <textarea v-model="form.text" rows="3" placeholder="Nhập câu hỏi rõ ràng, chỉ tập trung vào một nội dung..." />
          </label>
          <label class="question-type-field">
            <span>Loại câu hỏi</span>
            <select v-model="form.type" @change="normalizeFormAnswers">
              <option value="SINGLE_CHOICE">Single choice</option>
              <option value="MULTIPLE_CHOICE">Multiple choice</option>
            </select>
          </label>
          <fieldset class="question-answer-section">
          <legend>Các lựa chọn trả lời</legend>
          <div class="answer-editor">
            <label v-for="index in form.options.length" :key="index" :class="{ correct: form.correctAnswers.includes(index - 1) }">
              <ToggleSwitch :show-label="false" v-if="form.type === 'MULTIPLE_CHOICE'" :model-value="form.correctAnswers.includes(index - 1)" :label="`Đáp án ${String.fromCharCode(64+index)} là đáp án đúng`" @update:model-value="toggleCorrectAnswer(index - 1)" />
                    <input v-else type="radio" :checked="form.correctAnswers.includes(index - 1)" name="editor-correct-answer" @change="toggleCorrectAnswer(index - 1)" />
              <strong>{{ String.fromCharCode(64 + index) }}</strong>
              <input v-model="form.options[index - 1]" type="text" :placeholder="`Nhập đáp án ${String.fromCharCode(64 + index)}`" />
              <CheckCircle2 v-if="form.correctAnswers.includes(index - 1)" :size="18" />
            </label>
          </div>
          </fieldset>
          <label class="question-text-field"><span>Giải thích đáp án</span><textarea v-model="form.explanation" rows="3" maxlength="20000" /></label>
        </div></AdminModal>
        <AdminModal :open="showBank" title="Thêm câu hỏi từ ngân hàng" :width="1200" @close="showBank=false"><QuestionBankPanel v-if="showBank" picker :lesson-id="lessonId" :existing-ids="questions.map(q=>q.bankQuestionId).filter((id):id is number=>id!=null)" @added="bankAdded" /><template #footer><button type="button" class="btn btn-secondary" @click="showBank=false">Đóng</button></template></AdminModal>


        <div class="admin-table-scroll">
          <table class="admin-data-table quiz-questions-table">
            <colgroup><col style="width:5%" /><col style="width:46%" /><col style="width:12%" /><col style="width:15%" /><col style="width:22%" /></colgroup>
            <thead><tr><th scope="col">STT</th><th scope="col">Câu hỏi / đáp án</th><th scope="col">Loại</th><th scope="col">Ngân hàng câu hỏi</th><th scope="col">Thao tác</th></tr></thead>
            <tbody>
              <tr v-for="(question,index) in paginatedQuestions" :key="question.id">
                <td>{{ (questionPage-1)*questionPageSize+index+1 }}</td>
                <td class="question-table-content"><strong>{{ question.text }}</strong><div class="answer-grid"><span v-for="(option,optionIndex) in question.options" :key="optionIndex" :class="{correct:questionCorrectAnswers(question).includes(optionIndex)}"><strong>{{ String.fromCharCode(65+optionIndex) }}</strong>{{ option }}<CheckCircle2 v-if="questionCorrectAnswers(question).includes(optionIndex)" :size="15" /></span></div></td>
                <td><span class="question-type-badge">{{ question.type==='MULTIPLE_CHOICE'?'Nhiều đáp án':'Một đáp án' }}</span></td>
                <td class="bank-status-cell">
                  <span class="bank-save-status" :class="{saved:question.bankQuestionId!=null}"><CheckCircle2 v-if="question.bankQuestionId!=null" :size="14" />{{ question.bankQuestionId!=null?'Đã lưu':'Chưa lưu' }}</span>
                  <small v-if="question.bankQuestionCode" class="bank-source-code">{{ question.bankQuestionCode }}</small>
                </td>
                <td>
                  <div v-if="canEdit || (canCapture && question.bankQuestionId==null)" class="admin-row-actions quiz-question-actions">
                    <button v-if="canAction('update')" type="button" class="admin-row-edit" @click="editQuestion(question)">Sửa</button>
                    <button v-if="canAction('delete')" type="button" class="admin-row-delete" @click="deleteQuestion(question)">Xóa</button>
                    <button v-if="canCapture && question.bankQuestionId==null" type="button" class="secondary-button bank-save-action" :disabled="capturing!==null" @click="captureQuestion(question)">{{ capturing===question.id?'Đang lưu…':'Lưu vào ngân hàng' }}</button>
                  </div>
                  <span v-else>Chỉ xem</span>
                </td>
              </tr>
              <tr v-if="!filteredQuestions.length"><td colspan="5" class="admin-empty">{{ searchQuery?'Không tìm thấy câu hỏi phù hợp.':'Bài kiểm tra chưa có câu hỏi.' }}</td></tr>
            </tbody>
          </table>
        </div>
        <TablePagination v-model:page="questionPage" v-model:page-size="questionPageSize" :total="filteredQuestions.length" :page-count="questionPageCount" unit="câu hỏi" unit-label="Câu hỏi" />
      </section>
      <section v-if="canAction('delete')" class="danger-zone">
        <div>
          <strong>Xóa bài kiểm tra</strong>
          <p>Xóa toàn bộ {{ questions.length }} câu hỏi của bài kiểm tra này. Thao tác không thể hoàn tác.</p>
        </div>
        <button :disabled="deletingQuiz" @click="deleteQuiz">
          <Trash2 :size="17" /> {{ deletingQuiz ? "Đang xóa..." : "Xóa bài kiểm tra" }}
        </button>
      </section>
    </template>
    <ConfirmModal
      :open="confirmationModal.show"
      :title="confirmationModal.title"
      :message="confirmationModal.message"
      :confirm-label="confirmationModal.confirmLabel"
      :loading="confirmationModal.submitting"
      @cancel="closeConfirmationModal"
      @confirm="confirmModalAction"
    />
    </template>
  </main>
</template>

<script setup lang="ts">
import {getQuizCourses, getQuizContext, type QuizCourseContext} from "../api/quizContextApi";
import { computed, onBeforeUnmount, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowLeft, ArrowRight, CheckCircle2, Download, Edit, FileUp, LoaderCircle, Plus, Search, Sparkles, Trash2, X } from "lucide-vue-next";

import { adminApi } from "../api/adminApi";
import type { GeneratedQuizQuestion, QuizGenerationJobSnapshot, QuizGenerationSource } from "../api/adminApi";
import { describeQuizJob, describeQuizJobDetail, QuizGenerationJobError } from "../utils/quizGenerationJob";
import type { Question, Lesson } from "../types";
import AdminModal from '../components/AdminModal.vue';
import ToggleSwitch from '../components/ToggleSwitch.vue';
import TablePagination from '../components/TablePagination.vue';
import {useTablePagination} from '../composables/useTablePagination';
import ConfirmModal from "../components/ConfirmModal.vue";
import LessonKnowledgeSources from "../components/LessonKnowledgeSources.vue";
import { showToast } from "../utils/toast";
import QuestionBankPanel from '../components/QuestionBankPanel.vue';
import {contentToolsApi,contentToolsError} from '../api/contentToolsApi';

const props=defineProps<{embedded?:boolean; lesson?:Lesson; readOnly?:boolean}>();
const emit=defineEmits<{saved:[]}>();
const requiredForProgress=ref(true);
const route = useRoute();
const router = useRouter();
const quizCourses = ref<QuizCourseContext[]>([]);
const isNew = computed(() => !props.lesson && route.name === "AdminQuizCreate");
const courseId = ref("");
const canAction=(action:string)=>!props.readOnly && quizCourses.value.find(c=>c.id===courseId.value)?.status==='DRAFT' && quizCourses.value.find(c=>c.id===courseId.value)?.adminActions?.exams?.includes(action)===true;
const canEdit=computed(()=>canAction('create')||canAction('update')||canAction('delete'));
const lessonId = ref("");
const questions = ref<Question[]>([]);
const initialLoading = ref(true);
const loadError = ref('');
const showBank=ref(false),capturing=ref<number|null>(null);
const canCapture=computed(()=>quizCourses.value.find(c=>c.id===courseId.value)?.adminActions?.exams?.includes('create'));
async function captureQuestion(question:Question){if(capturing.value!==null || question.bankQuestionId!=null || !canCapture.value)return;capturing.value=question.id;try{const saved=await contentToolsApi.capture(question.id);question.bankQuestionId=saved.id;question.bankQuestionCode=saved.code;showToast(`Đã lưu ${saved.code} vào ngân hàng câu hỏi.`);await loadQuestions();}catch(e){showToast(contentToolsError(e),'error');}finally{capturing.value=null;}}
async function bankAdded(){showBank.value=false;await loadQuestions();await refreshQuizContext();emit('saved');}
const searchQuery = ref("");
const showQuestionForm = ref(false);
const editingId = ref<number | null>(null);
const saving = ref(false);
const savingSettings = ref(false);
const deletingQuiz = ref(false);
const showAiPanel = ref(false);
const generating = ref(false);
const sourceReady = ref(false);
const savingDrafts = ref(false);
const aiWarnings = ref<string[]>([]);
const aiSources = ref<QuizGenerationSource[]>([]);
type AiDraft = GeneratedQuizQuestion & { selected: boolean };
const aiDrafts = ref<AiDraft[]>([]);
const editingDraftIndex = ref<number | null>(null);
const draftBackup = ref<GeneratedQuizQuestion | null>(null);
const aiConfig = reactive({ singleChoiceCount: 5, multipleChoiceCount: 0, optionCount: 4 });
const generationProgress = ref<{ percent: number; label: string; detail: string; failed: boolean } | null>(null);
let generationAbort: AbortController | null = null;
const showGenerationProgress = (job: QuizGenerationJobSnapshot) => {
  generationProgress.value = {
    percent: job.percent,
    label: describeQuizJob(job),
    detail: describeQuizJobDetail(job),
    failed: false,
  };
};
const confirmationModal = ref<{
  show: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  submitting: boolean;
  action: null | (() => Promise<void>);
}>({
  show: false,
  title: "",
  message: "",
  confirmLabel: "Xóa",
  submitting: false,
  action: null,
});
const passingScore = ref(80);
const form = reactive<{
  text: string;
  explanation: string;
  options: string[];
  type: "SINGLE_CHOICE" | "MULTIPLE_CHOICE";
  correctAnswers: number[];
}>({ text: "", explanation:"", options: ["", "", "", ""], type: "SINGLE_CHOICE", correctAnswers: [0] });

const availableLessons = computed(() => quizCourses.value.find((course) => course.id === courseId.value)?.lessons || []);
const pageTitle = computed(() => {
  const course = quizCourses.value.find((item) => item.id === courseId.value);
  const lesson = availableLessons.value.find((item) => item.id === lessonId.value);
  return lesson?.title || (course ? `Bài kiểm tra tổng hợp — ${course.name}` : "Bài kiểm tra mới");
});
const filteredQuestions = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase("vi");
  if (!query) return questions.value;
  return questions.value.filter((question) =>
    `${question.text} ${question.options.join(" ")}`.toLocaleLowerCase("vi").includes(query),
  );
});
const {page:questionPage,pageSize:questionPageSize,pageCount:questionPageCount,paginated:paginatedQuestions,reset:resetQuestionPage}=useTablePagination(filteredQuestions);
const {page:draftPage,pageSize:draftPageSize,pageCount:draftPageCount,paginated:paginatedDrafts}=useTablePagination(aiDrafts);
watch(searchQuery,resetQuestionPage);
watch([courseId,lessonId],resetQuestionPage);
const selectedDraftCount = computed(() => aiDrafts.value.filter(draft => draft.selected).length);

function retainContext(context: QuizCourseContext) {
  quizCourses.value = quizCourses.value.some(c => c.id === context.id)
    ? quizCourses.value.map(c => c.id === context.id ? context : c)
    : [...quizCourses.value, context];
}
async function refreshQuizContext() {
  retainContext(await getQuizContext({ courseId: courseId.value, lessonId: lessonId.value || undefined }));
}

let initialRequestId = 0;
let questionRequestId = 0;
let pendingCreationSettings: { courseId: string; lessonId: string; passingScore: number; requiredForProgress: boolean } | null = null;
async function initialize() {
  const id = ++initialRequestId;
  ++questionRequestId;
  ++scopeRequestId;
  scopeLoading.value = false;
  scopeError.value = '';
  initialLoading.value = true;
  loadError.value = '';
  questions.value = [];
  try {
  if (isNew.value) {
    const options = await getQuizCourses();
    if (id !== initialRequestId) return;
    quizCourses.value = options;
    courseId.value = typeof route.query.courseId === "string" ? route.query.courseId : quizCourses.value[0]?.id || "";
    lessonId.value = '';
    if (courseId.value) {
      const context = await getQuizContext({ courseId: courseId.value });
      if (id !== initialRequestId) return;
      retainContext(context);
    }
  } else {
    const scopeId = String(props.lesson?.id || route.params.scopeId || "");
    const lessonScope = !!props.lesson || route.params.scopeType === "lesson";
    const context = await getQuizContext(lessonScope ? { lessonId: scopeId } : { courseId: scopeId });
    if (id !== initialRequestId) return;
    quizCourses.value = [context];
    courseId.value = context.id;
    lessonId.value = lessonScope ? scopeId : '';
    const course = quizCourses.value.find((item) => item.id === courseId.value);
    const lesson = course?.lessons.find((item) => item.id === lessonId.value);
    passingScore.value = lesson?.quizPassingScore ?? course?.quizPassingScore ?? 80;
    requiredForProgress.value = lesson?.quizRequired ?? true;
    if (pendingCreationSettings?.courseId === context.id && pendingCreationSettings.lessonId === lessonId.value) {
      passingScore.value = pendingCreationSettings.passingScore;
      requiredForProgress.value = pendingCreationSettings.requiredForProgress;
    }
    await loadQuestions();
    if (id === initialRequestId) pendingCreationSettings = null;
  }
  } catch {
    if (id === initialRequestId) loadError.value = 'Không thể tải bài kiểm tra. Vui lòng thử lại.';
  } finally {
    if (id === initialRequestId) initialLoading.value = false;
  }
}

const scopeLoading = ref(false);
const scopeError = ref('');
let scopeRequestId = 0;
async function loadLessonOptions() {
  const id = ++scopeRequestId;
  scopeLoading.value = true;
  scopeError.value = '';
  try {
    const context = await getQuizContext({ courseId: courseId.value });
    if (id === scopeRequestId) retainContext(context);
  } catch {
    if (id === scopeRequestId) scopeError.value = 'Không thể tải bài học. Vui lòng thử lại.';
  } finally {
    if (id === scopeRequestId) scopeLoading.value = false;
  }
}
watch(courseId, () => {
  if (initialLoading.value || !isNew.value) return;
  lessonId.value = '';
  if (courseId.value) void loadLessonOptions();
});

const loadQuestions = async () => {
  const id = ++questionRequestId;
  const data = await adminApi.getQuizQuestions(courseId.value, lessonId.value || undefined);
  if (id === questionRequestId) questions.value = data;
};
watch(() => [route.name, route.params.scopeType, route.params.scopeId, props.lesson?.id], initialize, { immediate: true });
onBeforeUnmount(() => { ++initialRequestId; ++questionRequestId; ++scopeRequestId; generationAbort?.abort(); });
const confirmScope = () => {
  pendingCreationSettings = { courseId: courseId.value, lessonId: lessonId.value,
    passingScore: passingScore.value, requiredForProgress: requiredForProgress.value };
  return router.replace(lessonId.value
    ? `/admin/quizzes/lesson/${encodeURIComponent(lessonId.value)}`
    : `/admin/quizzes/course/${encodeURIComponent(courseId.value)}`);
};
const saveSettings = async () => {
  if (!Number.isInteger(passingScore.value) || passingScore.value < 1 || passingScore.value > 100) {
    showToast("Ngưỡng đạt phải là số nguyên từ 1 đến 100.", "warning");
    return;
  }
  savingSettings.value = true;
  try {
    await adminApi.updateQuizSettings(courseId.value, lessonId.value || undefined, passingScore.value, requiredForProgress.value);
    showToast("Đã lưu cấu hình bài kiểm tra.");
    await refreshQuizContext();
    emit("saved");
  } catch {
    showToast("Không thể hoàn tất lưu cấu hình bài kiểm tra. Vui lòng thử lại.", "error");
  } finally {
    savingSettings.value = false;
  }
};
const openConfirmationModal = (
  title: string,
  message: string,
  action: () => Promise<void>,
) => {
  confirmationModal.value = {
    show: true,
    title,
    message,
    confirmLabel: "Xóa",
    submitting: false,
    action,
  };
};
const closeConfirmationModal = () => {
  if (confirmationModal.value.submitting) return;
  confirmationModal.value.show = false;
  confirmationModal.value.action = null;
};
const confirmModalAction = async () => {
  const action = confirmationModal.value.action;
  if (!action || confirmationModal.value.submitting) return;
  confirmationModal.value.submitting = true;
  try {
    await action();
    confirmationModal.value.submitting = false;
    closeConfirmationModal();
  } catch {
    showToast("Không thể hoàn tất thao tác xóa. Vui lòng thử lại.", "error");
    confirmationModal.value.submitting = false;
  }
};
const deleteQuiz = () => {
  openConfirmationModal(
    "Xóa bài kiểm tra?",
    `Toàn bộ ${questions.value.length} câu hỏi và bài kiểm tra này sẽ bị xóa. Thao tác không thể hoàn tác.`,
    async () => {
      deletingQuiz.value = true;
      try {
        await adminApi.deleteQuiz(courseId.value, lessonId.value || undefined);
        showToast("Đã xóa bài kiểm tra.");
        await refreshQuizContext();
        if(props.embedded){await loadQuestions();emit("saved");}else router.push("/admin?tab=exams");
      } finally {
        deletingQuiz.value = false;
      }
    },
  );
};
const resetForm = () => {
  form.text = "";
  form.explanation="";
  form.options.splice(0, form.options.length, "", "", "", "");
  form.type = "SINGLE_CHOICE";
  form.correctAnswers = [0];
  editingId.value = null;
};
const startAdding = () => { resetForm(); showQuestionForm.value = true; };
const closeQuestionForm = () => { showQuestionForm.value = false; resetForm(); };
const editQuestion = (question: Question) => {
  editingId.value = question.id;
  form.text = question.text;
  form.explanation=question.explanation || '';
  form.options.splice(0, form.options.length, ...question.options);
  form.type = question.type || ((question.correctAnswers?.length || 1) > 1 ? "MULTIPLE_CHOICE" : "SINGLE_CHOICE");
  form.correctAnswers = questionCorrectAnswers(question);
  showQuestionForm.value = true;
};
const saveQuestion = async () => {
  const text = form.text.trim();
  const options = form.options.map((option) => option.trim());
  if (!text || options.some((option) => !option)) {
    showToast("Vui lòng nhập nội dung câu hỏi và đầy đủ 4 đáp án.", "warning");
    return;
  }
  if (form.type === "SINGLE_CHOICE" && form.correctAnswers.length !== 1) {
    showToast("Câu single choice phải có đúng một đáp án đúng.", "warning");
    return;
  }
  if (form.type === "MULTIPLE_CHOICE" && (form.correctAnswers.length < 2 || form.correctAnswers.length >= options.length)) {
    showToast("Câu multiple choice cần ít nhất hai đáp án đúng và không được chọn tất cả.", "warning");
    return;
  }
  saving.value = true;
  const payload = {
    courseId: courseId.value,
    lessonId: lessonId.value || undefined,
    text,
    explanation:form.explanation,
    options,
    type: form.type,
    correctAnswers: [...form.correctAnswers].sort(),
    correctAnswer: [...form.correctAnswers].sort()[0],
  };
  try {
    if (editingId.value) await adminApi.updateQuestion(editingId.value, { ...payload, id: editingId.value });
    else await adminApi.createQuestion(payload);
    showToast(editingId.value ? "Đã cập nhật câu hỏi." : "Đã thêm câu hỏi.");
    await loadQuestions();
    emit("saved");
    await refreshQuizContext();
    closeQuestionForm();
  } catch {
    showToast("Không thể hoàn tất lưu câu hỏi. Vui lòng thử lại.", "error");
  } finally {
    saving.value = false;
  }
};
const questionCorrectAnswers = (question: Question) =>
  question.correctAnswers?.length ? question.correctAnswers : [question.correctAnswer];
const normalizeFormAnswers = () => {
  if (form.type === "SINGLE_CHOICE") form.correctAnswers = [form.correctAnswers[0] ?? 0];
};
const toggleCorrectAnswer = (index: number) => {
  if (form.type === "SINGLE_CHOICE") {
    form.correctAnswers = [index];
    return;
  }
  form.correctAnswers = form.correctAnswers.includes(index)
    ? form.correctAnswers.filter(answer => answer !== index)
    : [...form.correctAnswers, index].sort();
};

const cloneGeneratedQuestion = (draft: GeneratedQuizQuestion): GeneratedQuizQuestion => ({
  ...draft,
  options: [...draft.options],
  correctAnswers: [...draft.correctAnswers],
  sourceIds: [...draft.sourceIds],
});
const validateAiDraft = (draft: GeneratedQuizQuestion): string | null => {
  const text = draft.text.trim();
  const options = draft.options.map(option => option.trim());
  if (!text) return "Nội dung câu hỏi không được để trống.";
  if (options.length < 3 || options.some(option => !option)) return "Vui lòng nhập đầy đủ các lựa chọn.";
  if (new Set(options.map(option => option.toLocaleLowerCase("vi"))).size !== options.length) {
    return "Các lựa chọn không được trùng nhau.";
  }
  if (draft.correctAnswers.some(index => index < 0 || index >= options.length)) {
    return "Đáp án đúng không hợp lệ.";
  }
  if (draft.type === "SINGLE_CHOICE" && draft.correctAnswers.length !== 1) {
    return "Câu single choice phải có đúng một đáp án đúng.";
  }
  if (draft.type === "MULTIPLE_CHOICE" && (draft.correctAnswers.length < 2 || draft.correctAnswers.length >= options.length)) {
    return "Câu multiple choice cần ít nhất hai đáp án đúng và không được chọn tất cả.";
  }
  return null;
};
const startDraftEditing = (index: number) => {
  if (editingDraftIndex.value !== null) cancelDraftEditing();
  draftBackup.value = cloneGeneratedQuestion(aiDrafts.value[index]);
  editingDraftIndex.value = index;
};
const cancelDraftEditing = () => {
  const index = editingDraftIndex.value;
  if (index !== null && draftBackup.value) {
    const selected = aiDrafts.value[index].selected;
    aiDrafts.value[index] = { ...cloneGeneratedQuestion(draftBackup.value), selected };
  }
  editingDraftIndex.value = null;
  draftBackup.value = null;
};
const finishDraftEditing = (index: number): boolean => {
  const draft = aiDrafts.value[index];
  const validationError = validateAiDraft(draft);
  if (validationError) {
    showToast(validationError, "warning");
    return false;
  }
  draft.text = draft.text.trim();
  draft.options = draft.options.map(option => option.trim());
  draft.correctAnswers = [...draft.correctAnswers].sort((left, right) => left - right);
  editingDraftIndex.value = null;
  draftBackup.value = null;
  return true;
};
const normalizeDraftAnswers = (draft: AiDraft) => {
  if (draft.type === "SINGLE_CHOICE") {
    draft.correctAnswers = [draft.correctAnswers[0] ?? 0];
  }
};
const toggleDraftCorrectAnswer = (draft: AiDraft, index: number) => {
  if (draft.type === "SINGLE_CHOICE") {
    draft.correctAnswers = [index];
    return;
  }
  draft.correctAnswers = draft.correctAnswers.includes(index)
    ? draft.correctAnswers.filter(answer => answer !== index)
    : [...draft.correctAnswers, index].sort((left, right) => left - right);
};

const generateQuestions = async () => {
  if (lessonId.value && !sourceReady.value) {
    showToast("Hãy chọn và lưu nguồn kiến thức của bài học trước khi tạo câu hỏi.", "warning");
    return;
  }
  const total = aiConfig.singleChoiceCount + aiConfig.multipleChoiceCount;
  if (!Number.isInteger(total) || total < 1 || total > 20) {
    showToast("Tổng số câu hỏi phải từ 1 đến 20.", "warning");
    return;
  }
  if (!Number.isInteger(aiConfig.optionCount) || aiConfig.optionCount < 3 || aiConfig.optionCount > 6) {
    showToast("Số lựa chọn phải từ 3 đến 6.", "warning");
    return;
  }
  generating.value = true;
  aiWarnings.value = [];
  generationProgress.value = { percent: 0, label: "Đang gửi yêu cầu tạo câu hỏi", detail: "", failed: false };
  const abort = new AbortController();
  generationAbort = abort;
  try {
    const result = await adminApi.generateQuizWithProgress({
      courseId: courseId.value,
      lessonId: lessonId.value || undefined,
      ...aiConfig,
    }, { onProgress: showGenerationProgress, signal: abort.signal });
    generationProgress.value = null;
    aiSources.value = result.sources || [];
    aiWarnings.value = result.warnings || [];
    editingDraftIndex.value = null;
    draftBackup.value = null;
    aiDrafts.value = (result.questions || []).map(question => ({
      ...cloneGeneratedQuestion(question),
      selected: true,
    }));
  } catch (error) {
    if (error instanceof QuizGenerationJobError && error.kind === "cancelled") return;
    const message = error instanceof Error ? error.message : "Không thể tạo câu hỏi. Vui lòng thử lại.";
    generationProgress.value = { percent: generationProgress.value?.percent ?? 0, label: message, detail: "", failed: true };
    showToast(message, "error");
  } finally {
    if (generationAbort === abort) generationAbort = null;
    generating.value = false;
  }
};
const formatDraftSources = (sourceIds: string[]) => sourceIds.map(sourceId => {
  const source = aiSources.value.find(item => item.id === sourceId);
  return source ? `${source.documentName}, trang ${source.page}, ${source.section}` : sourceId;
}).join("; ");
const discardAiDrafts = () => {
  aiDrafts.value = [];
  editingDraftIndex.value = null;
  draftBackup.value = null;
};
const handleSourceReady = (ready: boolean) => {
  if (sourceReady.value && !ready && aiDrafts.value.length) {
    discardAiDrafts();
    aiSources.value = [];
    aiWarnings.value = ["Nguồn đã thay đổi. Lưu nguồn và tạo lại bản nháp để câu hỏi phù hợp với phạm vi mới."];
  }
  sourceReady.value = ready;
};
const saveAiDrafts = async () => {
  if (editingDraftIndex.value !== null && !finishDraftEditing(editingDraftIndex.value)) return;
  const selected = aiDrafts.value.filter(draft => draft.selected);
  if (!selected.length) return;
  const invalidIndex = aiDrafts.value.findIndex(draft => draft.selected && validateAiDraft(draft));
  if (invalidIndex >= 0) {
    showToast(`Câu AI số ${invalidIndex + 1}: ${validateAiDraft(aiDrafts.value[invalidIndex])}`, "warning");
    startDraftEditing(invalidIndex);
    return;
  }
  savingDrafts.value = true;
  try {
    for (const draft of selected) {
      await adminApi.createQuestion({
        courseId: courseId.value,
        lessonId: lessonId.value || undefined,
        text: draft.text,
        explanation: draft.explanation,
        options: draft.options,
        type: draft.type,
        correctAnswers: draft.correctAnswers,
        correctAnswer: draft.correctAnswers[0],
      });
    }
    await loadQuestions();
    emit("saved");
    await refreshQuizContext();
    showToast(`Đã thêm ${selected.length} câu hỏi AI vào bài kiểm tra.`);
    aiDrafts.value = [];
    showAiPanel.value = false;
  } catch {
    showToast("Chưa hoàn tất lưu câu hỏi AI. Kiểm tra danh sách câu hỏi trước khi thử lại.", "error");
  } finally {
    savingDrafts.value = false;
  }
};
const deleteQuestion = (question: Question) => {
  openConfirmationModal(
    "Xóa câu hỏi?",
    "Câu hỏi này sẽ bị xóa khỏi bài kiểm tra. Thao tác không thể hoàn tác.",
    async () => {
      await adminApi.deleteQuestion(question.id);
      showToast("Đã xóa câu hỏi.");
      await loadQuestions();
      await refreshQuizContext();
      emit("saved");
    },
  );
};

const csvEscape = (value: string) => `"${value.replace(/"/g, '""')}"`;
const downloadTemplate = () => {
  const rows = [
    ["question", "optionA", "optionB", "optionC", "optionD", "correctAnswer"],
    ["Câu hỏi mẫu?", "Đáp án A", "Đáp án B", "Đáp án C", "Đáp án D", "A"],
  ];
  const blob = new Blob(["\uFEFF" + rows.map((row) => row.map(csvEscape).join(",")).join("\r\n")], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const link = window.document.createElement("a");
  link.href = url;
  link.download = "mau-cau-hoi-bai-kiem-tra.csv";
  link.click();
  URL.revokeObjectURL(url);
};
const parseCsv = (source: string) => {
  const rows: string[][] = []; let row: string[] = []; let value = ""; let quoted = false;
  for (let i = 0; i < source.length; i++) {
    const char = source[i];
    if (char === '"') {
      if (quoted && source[i + 1] === '"') { value += '"'; i++; } else quoted = !quoted;
    } else if (char === "," && !quoted) { row.push(value.trim()); value = ""; }
    else if ((char === "\n" || char === "\r") && !quoted) {
      if (char === "\r" && source[i + 1] === "\n") i++;
      row.push(value.trim()); if (row.some(Boolean)) rows.push(row); row = []; value = "";
    } else value += char;
  }
  row.push(value.trim()); if (row.some(Boolean)) rows.push(row);
  return rows;
};
const importCsv = async (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0]; if (!file) return;
  try {
    const rows = parseCsv((await file.text()).replace(/^\uFEFF/, ""));
    const header = ["question", "optionA", "optionB", "optionC", "optionD", "correctAnswer"];
    if (header.some((item, index) => rows[0]?.[index] !== item)) throw new Error(`Tiêu đề cột phải là: ${header.join(", ")}`);
    const errors: string[] = [];
    const payloads = rows.slice(1).map((row, index) => {
      const answer = ["A", "B", "C", "D"].indexOf((row[5] || "").toUpperCase());
      if (row.length < 6 || row.slice(0, 5).some((value) => !value)) errors.push(`Dòng ${index + 2}: thiếu câu hỏi hoặc đáp án.`);
      if (answer < 0) errors.push(`Dòng ${index + 2}: đáp án đúng phải là A, B, C hoặc D.`);
      return { courseId: courseId.value, lessonId: lessonId.value || undefined, text: row[0], options: row.slice(1, 5), correctAnswer: answer };
    });
    if (!payloads.length) errors.push("File không có dữ liệu.");
    if (errors.length) throw new Error(errors.slice(0, 12).join("\n"));
    await Promise.all(payloads.map((payload) => adminApi.createQuestion(payload)));
    await loadQuestions();
    emit("saved");
    await refreshQuizContext();
    showToast(`Đã nhập ${payloads.length} câu hỏi từ CSV.`);
  } catch (error) {
    showToast(error instanceof Error ? error.message : "Không thể nhập câu hỏi từ file.", "error");
  } finally { input.value = ""; }
};
</script>

<style scoped>
.quiz-detail-loading, .quiz-detail-error { min-height: 220px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 1rem; text-align: center; }
.quiz-detail-loading { color: var(--primary-color); }
.quiz-detail-loading p, .quiz-detail-error p { margin: 0; color: var(--text-main); }
@media (prefers-reduced-motion: reduce) { .quiz-detail-loading .spin { animation: none; } }
.modal-question-editor{display:grid;gap:1.5rem;margin:0!important;padding:0!important;border:0!important;background:transparent!important}
.modal-question-editor>.question-text-field,.modal-question-editor>.question-type-field{display:grid;gap:.5rem;margin:0;min-width:0}
.modal-question-editor>.question-text-field>span,.modal-question-editor>.question-type-field>span{margin:0;line-height:1.5}
.modal-question-editor textarea{display:block;line-height:1.6}
.question-answer-section{border:0;padding:0;margin:0;min-width:0}
.question-answer-section legend{padding:0;margin-bottom:.75rem;font-size:var(--admin-text-label,.8125rem);font-weight:700;line-height:1.5}
.modal-question-editor .answer-editor{gap:.75rem;margin:0}
.modal-question-editor .answer-editor label{gap:.5rem;padding:.65rem}
.answer-editor .toggle-label,.ai-draft-answer-editor .toggle-label{font-size:.75rem}

.question-table-content{min-width:320px;max-width:800px}.question-table-content .answer-grid{margin-top:.7rem}.question-type-badge{white-space:nowrap}.quiz-editor-page{width:100%!important;margin:0!important;padding:2rem!important;box-sizing:border-box}.quiz-editor-page.embedded{padding:0!important}@media(max-width:768px){.quiz-editor-page{padding:1rem!important}}
.quiz-editor-page :deep(.admin-data-table),
.quiz-editor-page :deep(.admin-data-table *) { font-family: var(--font-family); }
.quiz-editor-page :deep(.admin-data-table) { font-size: var(--admin-text-body, .875rem); line-height: 1.5; }
.quiz-editor-page :deep(.admin-data-table th) { font-size: var(--admin-text-label, .8125rem); font-weight: 600; }
.quiz-editor-page :deep(.admin-data-table td) { font-weight: 400; }
.quiz-editor-page :deep(.admin-data-table strong) { font-weight: 700; }
.quiz-editor-page .admin-data-table .answer-grid span { font-size: inherit; font-weight: 400; }
.quiz-editor-page .admin-data-table .question-type-badge,
.quiz-editor-page .admin-data-table .bank-save-status { font-size: var(--admin-text-label, .8125rem); font-weight: 600; }
.quiz-questions-table{min-width:960px}.quiz-question-actions{display:flex;align-items:center;flex-wrap:wrap;gap:.5rem}.quiz-question-actions button{white-space:nowrap}.bank-save-action{padding:.45rem .65rem;font-size:.8rem}.bank-save-action:disabled{opacity:.55;cursor:wait}.bank-save-status{display:inline-flex;align-items:center;gap:.35rem;padding:.3rem .55rem;border-radius:6px;background:var(--surface-f1f5f9);color:var(--text-muted);font-size:.78rem;white-space:nowrap}.bank-save-status.saved{background:var(--primary-light);color:var(--primary-ink)}.bank-source-code{display:block;margin-top:.4rem;color:var(--text-muted);font-size:.72rem;overflow-wrap:anywhere}

.required-quiz-setting{display:block;margin:1rem 0;color:var(--primary-ink);font-weight:600}.required-quiz-setting input{margin-right:.5rem}.required-quiz-setting small{display:block;margin-top:.5rem;font-weight:400;color:var(--text-muted)}
.quiz-editor-page { width: min(1240px, calc(100% - 3rem)); min-height: 100%; margin: 0 auto; padding: 1.5rem; color: var(--text-color); }
.quiz-editor-header { display: grid; grid-template-columns: 180px 1fr; gap: 1rem; align-items: start; margin-bottom: 1.2rem; }
.back-button { display: flex; align-items: center; gap: .65rem; min-width: 170px; padding: .7rem .85rem; border: 1px solid var(--primary-color); border-radius: var(--radius-md); background: var(--primary-color); color: white; cursor: pointer; text-align: left; box-shadow: 0 5px 14px rgba(0,91,65,.18); }
.back-button:hover { filter: brightness(1.08); transform: translateY(-1px); }
.back-button span { display: flex; flex-direction: column; white-space: nowrap; }
.back-button strong { font-size: var(--admin-text-label); }
.back-button small { color: rgba(255,255,255,.78); font-size: var(--admin-text-caption); }
.eyebrow { color: var(--primary-ink); font-size: var(--admin-text-caption); font-weight: 800; text-transform: uppercase; }
.quiz-editor-header h1 { margin: .25rem 0; font-size: var(--admin-heading-page); line-height: 1.25; }
.quiz-editor-header p, .section-heading p { margin: 0; color: var(--text-muted); font-size: var(--admin-text-body); }
.editor-card { margin-bottom: 2rem; padding: 0; border: 0; border-radius: 0; background: var(--surface-ffffff); box-shadow: none; }
.section-heading { display: flex; align-items: center; gap: .7rem; margin-bottom: 1rem; }
.section-heading > span { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 50%; background: var(--primary-color); color: white; font-weight: 800; }
.section-heading h2 { margin: 0 0 .15rem; font-size: var(--admin-heading-section); line-height: 1.3; }
.settings-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; }
.settings-grid label > span, .question-text-field > span { display: block; margin-bottom: .35rem; font-size: var(--admin-text-label); font-weight: 700; }
.settings-grid label > small { display: block; margin-top: .35rem; color: var(--text-muted); line-height: 1.35; }
select, .score-input input, textarea, .question-text-field input { width: 100%; padding: .7rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); background: var(--surface-ffffff); }
.score-input { position: relative; }
.score-input input { padding-right: 2.5rem; }
.score-input strong { position: absolute; right: .8rem; top: 50%; transform: translateY(-50%); color: var(--primary-ink); }
select, textarea, .question-text-field input { width: 100%; padding: .7rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); background: var(--surface-ffffff); }
.settings-action { display: flex; justify-content: space-between; align-items: center; margin-top: 1rem; padding-top: 1rem; border-top: 1px solid var(--border-color); }
.settings-action p { margin: 0; color: var(--text-muted); font-size: var(--admin-text-body); }
.primary-button, .secondary-button { display: inline-flex; align-items: center; justify-content: center; gap: .4rem; padding: .65rem .85rem; border-radius: var(--radius-sm); cursor: pointer; font-weight: 700; }
.quiz-editor-page :deep(button),
.quiz-editor-page :deep(input),
.quiz-editor-page :deep(select),
.quiz-editor-page :deep(textarea) { font-family: var(--font-family); }
.primary-button, .secondary-button, .ai-button { font-size: var(--admin-text-label, .8125rem); font-weight: 700; }
.primary-button { border: 1px solid var(--primary-color); background: var(--primary-color); color: white; }
.secondary-button { border: 1px solid var(--border-color); background: var(--surface-ffffff); color: var(--primary-ink); }
.primary-button:disabled { opacity: .5; cursor: not-allowed; }
.danger-zone { display: flex; justify-content: space-between; align-items: center; gap: 1rem; margin-top: 2rem; padding: 1rem; border: 1px solid var(--line-fecaca); border-radius: var(--radius-md); background: var(--surface-fff7f7); }
.danger-zone p { margin: .25rem 0 0; color: var(--ink-7f1d1d); font-size: var(--admin-text-body); }
.danger-zone button { display: inline-flex; align-items: center; gap: .4rem; flex: 0 0 auto; padding: .65rem .85rem; border: 1px solid #dc2626; border-radius: var(--radius-sm); background: var(--surface-ffffff); color: var(--ink-b91c1c); cursor: pointer; font-weight: 700; }
.danger-zone button:disabled { opacity: .55; cursor: wait; }
.question-section-heading { flex-wrap: wrap; }
.question-tools { display: flex; gap: .45rem; margin-left: auto; }
.ai-button { display: inline-flex; align-items: center; gap: .4rem; padding: .65rem .85rem; border: 1px solid #7c3aed; border-radius: var(--radius-sm); background: var(--surface-f5f3ff); color: var(--ink-6d28d9); cursor: pointer; }
.ai-button:disabled { opacity: .65; cursor: wait; }
.import-button { position: relative; }
.import-button input { position: absolute; width: 1px; height: 1px; opacity: 0; }
.search-box { display: flex; align-items: center; gap: .5rem; max-width: 560px; margin-bottom: 1rem; padding: 0 .75rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); color: var(--text-muted); }
.search-box input { width: 100%; height: 42px; border: 0; outline: 0; }
.ai-panel { margin-bottom: 1.2rem; padding: 1rem; border: 1px solid var(--line-ddd6fe); border-radius: var(--radius-md); background: var(--surface-faf9ff); }
.ai-panel-heading { display: flex; justify-content: space-between; gap: 1rem; margin-bottom: .9rem; }
.ai-panel-heading > div { display: flex; flex-direction: column; gap: .2rem; }
.ai-panel-heading small { color: var(--text-muted); }
.ai-panel-heading > button { border: 0; background: transparent; cursor: pointer; }
.ai-settings { display: grid; grid-template-columns: repeat(3, minmax(120px, 1fr)) auto; gap: .7rem; align-items: end; }
.ai-settings label span, .question-type-field > span { display: block; margin-bottom: .3rem; font-size: var(--admin-text-label); font-weight: 700; }
.ai-settings input { width: 100%; padding: .65rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); }
.ai-progress { margin: .85rem 0 0; padding: .7rem .8rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); }
.ai-progress-header { display: flex; justify-content: space-between; gap: .75rem; margin-bottom: .45rem; font-size: var(--admin-text-label); font-weight: 700; }
.ai-progress-track { height: 8px; overflow: hidden; border-radius: 999px; background: var(--border-color); }
.ai-progress-fill { height: 100%; border-radius: inherit; background: var(--primary-color); transition: width .4s ease; }
.ai-progress small { display: block; margin-top: .4rem; color: var(--text-muted); }
.ai-progress.failed .ai-progress-header { color: var(--ink-9a3412); }
.ai-progress.failed .ai-progress-fill { background: var(--ink-9a3412); }
.ai-warning { margin: .75rem 0 0; padding: .65rem .75rem; border-radius: var(--radius-sm); background: var(--surface-fff7ed); color: var(--ink-9a3412); }
.ai-drafts { display: grid; gap: .75rem; margin-top: 1rem; }
.ai-draft-card { position: relative; padding: 1rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); background: var(--surface-ffffff); }
.ai-draft-card h3 { margin: .55rem 0 .75rem; padding-right: 4rem; font-size: var(--admin-text-body); }
.draft-select { position: absolute; top: 1rem; right: 1rem; display: flex; gap: .35rem; align-items: center; font-weight: 700; }
.draft-heading-actions { display: flex; align-items: center; gap: .5rem; padding-right: 5rem; }
.draft-edit-button { display: inline-flex; align-items: center; gap: .3rem; padding: .25rem .45rem; border: 1px solid var(--line-c4b5fd); border-radius: 6px; background: var(--surface-ffffff); color: var(--ink-6d28d9); cursor: pointer; font-weight: 700; }
.question-type-badge { display: inline-flex; padding: .2rem .45rem; border-radius: 999px; background: var(--surface-ede9fe); color: var(--ink-6d28d9); font-size: var(--admin-text-caption); font-weight: 800; }
.ai-draft-editor { margin-top: .8rem; padding: .85rem; border: 1px solid var(--line-ddd6fe); border-radius: var(--radius-sm); background: var(--surface-faf9ff); }
.ai-draft-answer-editor { display: grid; grid-template-columns: 1fr 1fr; gap: .55rem; }
.ai-draft-answer-editor label { display: grid; grid-template-columns: 18px 24px 1fr 18px; align-items: center; gap: .4rem; padding: .55rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); background: var(--surface-ffffff); }
.ai-draft-answer-editor label.correct { border-color: #7c3aed; }
.ai-draft-answer-editor label > input[type="text"] { width: 100%; border: 0; outline: 0; }
.draft-editor-actions { display: flex; justify-content: flex-end; gap: .5rem; margin-top: .75rem; }
.draft-explanation { margin: .7rem 0 .25rem; color: var(--text-muted); }
.draft-sources { color: var(--ink-475569); }
.ai-draft-actions { display: flex; justify-content: flex-end; gap: .6rem; }
.question-type-field { display: block; max-width: 280px; margin-bottom: .75rem; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.question-editor { margin-bottom: 1rem; padding: 1rem; border: 1px solid var(--primary-color); border-radius: var(--radius-md); background: var(--primary-light); }
.editor-expand-enter-active, .editor-expand-leave-active { transition: opacity .22s ease, transform .22s ease; transform-origin: top center; }
.editor-expand-enter-from, .editor-expand-leave-to { opacity: 0; transform: translateY(-10px) scale(.985); }
.question-editor-title { display: flex; justify-content: space-between; margin-bottom: .8rem; }
.question-editor-title div { display: flex; flex-direction: column; }
.question-editor-title small { color: var(--text-muted); }
.question-editor-title button, .question-actions button { border: 0; background: transparent; cursor: pointer; color: var(--text-muted); }
.answer-editor { display: grid; grid-template-columns: 1fr 1fr; gap: .55rem; margin-top: .7rem; }
.answer-editor label { display: grid; grid-template-columns: 18px 24px 1fr 18px; align-items: center; gap: .4rem; padding: .55rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); background: var(--surface-ffffff); }
.answer-editor label.correct { border-color: var(--primary-color); }
.answer-editor label > input[type="text"] { width: 100%; border: 0; outline: 0; }
.form-actions { display: flex; justify-content: flex-end; gap: .5rem; margin-top: .8rem; }
.question-list { position: relative; display: grid; gap: .65rem; }
.question-item-enter-active { transition: opacity .28s ease, transform .28s ease, background-color .5s ease; }
.question-item-leave-active { position: absolute; transition: opacity .2s ease, transform .2s ease; }
.question-item-enter-from { opacity: 0; transform: translateY(-12px) scale(.99); background: var(--primary-light); }
.question-item-leave-to { opacity: 0; transform: translateX(12px); }
.question-item-move { transition: transform .28s ease; }
.question-card { display: grid; grid-template-columns: 34px 1fr auto; gap: .75rem; padding: .9rem; border: 1px solid var(--border-color); border-radius: var(--radius-md); }
.question-number { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 8px; background: var(--primary-light); color: var(--primary-ink); font-weight: 800; }
.question-content h3 { margin: .2rem 0 .7rem; font-size: var(--admin-text-body); line-height: 1.45; }
.answer-grid { display: grid; grid-template-columns: 1fr 1fr; gap: .4rem; }
.answer-grid span { display: flex; align-items: center; gap: .4rem; padding: .4rem .55rem; border-radius: 7px; background: var(--surface-f8fafc); font-size: var(--admin-text-label); }
.answer-grid span.correct { background: var(--primary-light); color: var(--primary-ink); }
.question-actions { display: flex; gap: .25rem; }
.question-actions .danger { color: var(--ink-b42318); }
.empty-box { padding: 2rem; border: 1px dashed var(--border-color); border-radius: var(--radius-md); color: var(--text-muted); text-align: center; }
@media (max-width: 760px) {
  .quiz-editor-page { width: 100%; padding: 1rem; }
  .quiz-editor-header { grid-template-columns: 1fr; }
  .settings-grid, .answer-editor, .answer-grid, .ai-draft-answer-editor { grid-template-columns: 1fr; }
  .danger-zone { align-items: flex-start; flex-direction: column; }
  .question-tools { width: 100%; margin-left: 0; flex-wrap: wrap; }
  .ai-settings { grid-template-columns: 1fr; }
  .question-card { grid-template-columns: 30px 1fr; }
  .question-actions { grid-column: 2; }
}
@media (prefers-reduced-motion: reduce) {
  .editor-expand-enter-active, .editor-expand-leave-active,
  .question-item-enter-active, .question-item-leave-active,
  .question-item-move { transition: none; }
}
.readonly-notice{padding:1rem;margin-bottom:1rem;border-radius:8px;background:var(--surface-fff5de);color:var(--ink-805b00);font-size:.875rem}
</style>
