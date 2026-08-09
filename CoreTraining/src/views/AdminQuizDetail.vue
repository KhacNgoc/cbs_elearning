<template>
  <main class="quiz-editor-page">
    <header class="quiz-editor-header">
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

    <section class="editor-card settings-card">
      <div class="section-heading">
        <span>1</span>
        <div><h2>Thông tin bài kiểm tra</h2><p>Chọn chủ đề và phạm vi áp dụng.</p></div>
      </div>
      <div class="settings-grid">
        <label>
          <span>Chủ đề</span>
          <select v-model="topicId" :disabled="!isNew">
            <option value="" disabled>Chọn chủ đề</option>
            <option v-for="topic in courseStore.topics" :key="topic.id" :value="topic.id">{{ topic.name }}</option>
          </select>
        </label>
        <label>
          <span>Phạm vi</span>
          <select v-model="lessonId" :disabled="!isNew || !topicId">
            <option value="">Bài kiểm tra tổng hợp của chủ đề</option>
            <option v-for="lesson in availableLessons" :key="lesson.id" :value="lesson.id">
              Bài học: {{ lesson.title }}
            </option>
          </select>
        </label>
        <label>
          <span>Ngưỡng điểm đạt</span>
          <div class="score-input">
            <input v-model.number="passingScore" type="number" min="1" max="100" />
            <strong>%</strong>
          </div>
          <small>Học viên cần đạt tối thiểu mức này để hoàn thành bài kiểm tra.</small>
        </label>
      </div>
      <div v-if="isNew" class="settings-action">
        <p>Phạm vi sẽ tự động áp dụng cho mọi câu hỏi được thêm hoặc import.</p>
        <button class="primary-button" :disabled="!topicId" @click="confirmScope">
          Tiếp tục soạn câu hỏi <ArrowRight :size="17" />
        </button>
      </div>
      <div v-else class="settings-action">
        <p>Thay đổi ngưỡng đạt sẽ áp dụng cho những lượt làm bài tiếp theo.</p>
        <button class="primary-button" :disabled="savingSettings" @click="saveSettings">
          {{ savingSettings ? "Đang lưu..." : "Lưu ngưỡng đạt" }}
        </button>
      </div>
    </section>

    <template v-if="!isNew">
      <section class="editor-card">
        <div class="section-heading question-section-heading">
          <span>2</span>
          <div><h2>Danh sách câu hỏi</h2><p>{{ questions.length }} câu hỏi trong bài kiểm tra.</p></div>
          <div class="question-tools">
            <button class="ai-button" :disabled="generating" @click="showAiPanel = !showAiPanel">
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
          <input v-model="searchQuery" type="search" placeholder="Tìm theo nội dung câu hỏi hoặc đáp án..." />
        </div>

        <section v-if="showAiPanel" class="ai-panel">
          <div class="ai-panel-heading">
            <div>
              <strong>Tạo bản nháp từ tài liệu của {{ lessonId ? "bài học" : "chủ đề" }}</strong>
              <small>AI tìm kiếm trên toàn bộ tài liệu của topic. Tên lesson chỉ được dùng để định hướng tìm kiếm.</small>
            </div>
            <button aria-label="Đóng" @click="showAiPanel = false"><X :size="18" /></button>
          </div>
          <div class="ai-settings">
            <label><span>Single choice</span><input v-model.number="aiConfig.singleChoiceCount" type="number" min="0" max="10" /></label>
            <label><span>Multiple choice</span><input v-model.number="aiConfig.multipleChoiceCount" type="number" min="0" max="10" /></label>
            <label><span>Số lựa chọn</span><input v-model.number="aiConfig.optionCount" type="number" min="3" max="6" /></label>
            <button class="primary-button" :disabled="generating" @click="generateQuestions">
              <LoaderCircle v-if="generating" class="spin" :size="17" />
              <Sparkles v-else :size="17" />
              {{ generating ? "Đang truy xuất và tạo..." : "Tạo bản nháp" }}
            </button>
          </div>
          <p v-for="warning in aiWarnings" :key="warning" class="ai-warning">{{ warning }}</p>
          <div v-if="aiDrafts.length" class="ai-drafts">
            <article v-for="(draft, index) in aiDrafts" :key="index" class="ai-draft-card">
              <label class="draft-select"><input v-model="draft.selected" type="checkbox" /> Chọn</label>
              <div class="draft-heading-actions">
                <span class="question-type-badge">{{ draft.type === "MULTIPLE_CHOICE" ? "Nhiều đáp án" : "Một đáp án" }}</span>
                <button v-if="editingDraftIndex !== index" class="draft-edit-button" @click="startDraftEditing(index)">
                  <Edit :size="15" /> Sửa
                </button>
              </div>
              <div v-if="editingDraftIndex === index" class="ai-draft-editor">
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
                    <input
                      :type="draft.type === 'MULTIPLE_CHOICE' ? 'checkbox' : 'radio'"
                      :checked="draft.correctAnswers.includes(optionIndex)"
                      :name="`draft-correct-answer-${index}`"
                      @change="toggleDraftCorrectAnswer(draft, optionIndex)"
                    />
                    <strong>{{ String.fromCharCode(65 + optionIndex) }}</strong>
                    <input v-model="draft.options[optionIndex]" type="text" :placeholder="`Nhập đáp án ${String.fromCharCode(65 + optionIndex)}`" />
                    <CheckCircle2 v-if="draft.correctAnswers.includes(optionIndex)" :size="17" />
                  </label>
                </div>
                <div class="draft-editor-actions">
                  <button class="secondary-button" @click="cancelDraftEditing">Hủy</button>
                  <button class="primary-button" @click="finishDraftEditing(index)">Hoàn tất chỉnh sửa</button>
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
            </article>
            <div class="ai-draft-actions">
              <button class="secondary-button" @click="discardAiDrafts">Bỏ bản nháp</button>
              <button class="primary-button" :disabled="savingDrafts || !selectedDraftCount" @click="saveAiDrafts">
                {{ savingDrafts ? "Đang lưu..." : `Lưu ${selectedDraftCount} câu đã chọn` }}
              </button>
            </div>
          </div>
        </section>

        <Transition name="editor-expand">
        <div v-if="showQuestionForm" class="question-editor">
          <div class="question-editor-title">
            <div><strong>{{ editingId ? "Chỉnh sửa câu hỏi" : "Câu hỏi mới" }}</strong><small>Chọn loại câu hỏi và đánh dấu đầy đủ đáp án đúng.</small></div>
            <button aria-label="Đóng" @click="closeQuestionForm"><X :size="18" /></button>
          </div>
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
          <div class="answer-editor">
            <label v-for="index in form.options.length" :key="index" :class="{ correct: form.correctAnswers.includes(index - 1) }">
              <input
                :type="form.type === 'MULTIPLE_CHOICE' ? 'checkbox' : 'radio'"
                :checked="form.correctAnswers.includes(index - 1)"
                name="editor-correct-answer"
                @change="toggleCorrectAnswer(index - 1)"
              />
              <strong>{{ String.fromCharCode(64 + index) }}</strong>
              <input v-model="form.options[index - 1]" type="text" :placeholder="`Nhập đáp án ${String.fromCharCode(64 + index)}`" />
              <CheckCircle2 v-if="form.correctAnswers.includes(index - 1)" :size="18" />
            </label>
          </div>
          <div class="form-actions">
            <button class="secondary-button" @click="closeQuestionForm">Hủy</button>
            <button class="primary-button" :disabled="saving" @click="saveQuestion">
              {{ saving ? "Đang lưu..." : "Lưu câu hỏi" }}
            </button>
          </div>
        </div>
        </Transition>

        <TransitionGroup
          v-if="filteredQuestions.length"
          name="question-item"
          tag="div"
          class="question-list"
        >
          <article v-for="(question, index) in filteredQuestions" :key="question.id" class="question-card">
            <div class="question-number">{{ index + 1 }}</div>
            <div class="question-content">
              <h3>{{ question.text }}</h3>
              <div class="answer-grid">
                <span v-for="(option, optionIndex) in question.options" :key="optionIndex"
                  :class="{ correct: questionCorrectAnswers(question).includes(optionIndex) }">
                  <strong>{{ String.fromCharCode(65 + optionIndex) }}</strong>{{ option }}
                  <CheckCircle2 v-if="questionCorrectAnswers(question).includes(optionIndex)" :size="15" />
                </span>
              </div>
            </div>
            <div class="question-actions">
              <button title="Sửa câu hỏi" @click="editQuestion(question)"><Edit :size="17" /></button>
              <button class="danger" title="Xóa câu hỏi" @click="deleteQuestion(question)"><Trash2 :size="17" /></button>
            </div>
          </article>
        </TransitionGroup>
        <div v-else class="empty-box">
          {{ searchQuery ? "Không tìm thấy câu hỏi phù hợp." : "Bài kiểm tra chưa có câu hỏi. Hãy thêm thủ công hoặc import CSV." }}
        </div>
      </section>
      <section class="danger-zone">
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
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowLeft, ArrowRight, CheckCircle2, Download, Edit, FileUp, LoaderCircle, Plus, Search, Sparkles, Trash2, X } from "lucide-vue-next";
import { useCourseStore } from "../stores/courseStore";
import { adminApi } from "../api/adminApi";
import type { GeneratedQuizQuestion, QuizGenerationSource } from "../api/adminApi";
import type { Question } from "../types";
import ConfirmModal from "../components/ConfirmModal.vue";

const route = useRoute();
const router = useRouter();
const courseStore = useCourseStore();
const isNew = computed(() => route.name === "AdminQuizCreate");
const topicId = ref("");
const lessonId = ref("");
const questions = ref<Question[]>([]);
const searchQuery = ref("");
const showQuestionForm = ref(false);
const editingId = ref<number | null>(null);
const saving = ref(false);
const savingSettings = ref(false);
const deletingQuiz = ref(false);
const showAiPanel = ref(false);
const generating = ref(false);
const savingDrafts = ref(false);
const aiWarnings = ref<string[]>([]);
const aiSources = ref<QuizGenerationSource[]>([]);
type AiDraft = GeneratedQuizQuestion & { selected: boolean };
const aiDrafts = ref<AiDraft[]>([]);
const editingDraftIndex = ref<number | null>(null);
const draftBackup = ref<GeneratedQuizQuestion | null>(null);
const aiConfig = reactive({ singleChoiceCount: 5, multipleChoiceCount: 0, optionCount: 4 });
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
  options: string[];
  type: "SINGLE_CHOICE" | "MULTIPLE_CHOICE";
  correctAnswers: number[];
}>({ text: "", options: ["", "", "", ""], type: "SINGLE_CHOICE", correctAnswers: [0] });

const availableLessons = computed(() => courseStore.topics.find((topic) => topic.id === topicId.value)?.lessons || []);
const pageTitle = computed(() => {
  const topic = courseStore.topics.find((item) => item.id === topicId.value);
  const lesson = availableLessons.value.find((item) => item.id === lessonId.value);
  return lesson?.title || (topic ? `Bài kiểm tra tổng hợp — ${topic.name}` : "Bài kiểm tra mới");
});
const filteredQuestions = computed(() => {
  const query = searchQuery.value.trim().toLocaleLowerCase("vi");
  if (!query) return questions.value;
  return questions.value.filter((question) =>
    `${question.text} ${question.options.join(" ")}`.toLocaleLowerCase("vi").includes(query),
  );
});
const selectedDraftCount = computed(() => aiDrafts.value.filter(draft => draft.selected).length);

onMounted(async () => {
  if (!courseStore.topics.length) await courseStore.fetchTopics();
  if (isNew.value) {
    topicId.value = typeof route.query.topicId === "string" ? route.query.topicId : courseStore.topics[0]?.id || "";
  } else {
    const scopeId = String(route.params.scopeId || "");
    if (route.params.scopeType === "lesson") {
      lessonId.value = scopeId;
      topicId.value = courseStore.topics.find((topic) => topic.lessons.some((lesson) => lesson.id === scopeId))?.id || "";
    } else topicId.value = scopeId;
    const topic = courseStore.topics.find((item) => item.id === topicId.value);
    const lesson = topic?.lessons.find((item) => item.id === lessonId.value);
    passingScore.value = lesson?.quizPassingScore ?? topic?.quizPassingScore ?? 80;
    await loadQuestions();
  }
});

watch(topicId, () => {
  if (lessonId.value && !availableLessons.value.some((lesson) => lesson.id === lessonId.value)) lessonId.value = "";
});

const loadQuestions = async () => {
  questions.value = await adminApi.getQuizQuestions(topicId.value, lessonId.value || undefined);
};
const confirmScope = () => router.replace(
  lessonId.value
    ? `/admin/quizzes/lesson/${encodeURIComponent(lessonId.value)}`
    : `/admin/quizzes/topic/${encodeURIComponent(topicId.value)}`,
);
const saveSettings = async () => {
  if (!Number.isInteger(passingScore.value) || passingScore.value < 1 || passingScore.value > 100) {
    alert("Ngưỡng đạt phải là số nguyên từ 1 đến 100.");
    return;
  }
  savingSettings.value = true;
  try {
    await adminApi.updateQuizSettings(topicId.value, lessonId.value || undefined, passingScore.value);
    await courseStore.fetchTopics();
    alert("Đã lưu ngưỡng điểm đạt.");
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
        await adminApi.deleteQuiz(topicId.value, lessonId.value || undefined);
        await courseStore.fetchTopics();
        router.push("/admin?tab=exams");
      } finally {
        deletingQuiz.value = false;
      }
    },
  );
};
const resetForm = () => {
  form.text = "";
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
  form.options.splice(0, form.options.length, ...question.options);
  form.type = question.type || ((question.correctAnswers?.length || 1) > 1 ? "MULTIPLE_CHOICE" : "SINGLE_CHOICE");
  form.correctAnswers = questionCorrectAnswers(question);
  showQuestionForm.value = true;
  window.scrollTo({ top: 360, behavior: "smooth" });
};
const saveQuestion = async () => {
  const text = form.text.trim();
  const options = form.options.map((option) => option.trim());
  if (!text || options.some((option) => !option)) {
    alert("Vui lòng nhập nội dung câu hỏi và đầy đủ 4 đáp án.");
    return;
  }
  if (form.type === "SINGLE_CHOICE" && form.correctAnswers.length !== 1) {
    alert("Câu single choice phải có đúng một đáp án đúng.");
    return;
  }
  if (form.type === "MULTIPLE_CHOICE" && (form.correctAnswers.length < 2 || form.correctAnswers.length >= options.length)) {
    alert("Câu multiple choice cần ít nhất hai đáp án đúng và không được chọn tất cả.");
    return;
  }
  saving.value = true;
  const payload = {
    topicId: topicId.value,
    lessonId: lessonId.value || undefined,
    text,
    options,
    type: form.type,
    correctAnswers: [...form.correctAnswers].sort(),
    correctAnswer: [...form.correctAnswers].sort()[0],
  };
  try {
    if (editingId.value) await adminApi.updateQuestion(editingId.value, { ...payload, id: editingId.value });
    else await adminApi.createQuestion(payload);
    await loadQuestions();
    await courseStore.fetchTopics();
    closeQuestionForm();
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
    alert(validationError);
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
  const total = aiConfig.singleChoiceCount + aiConfig.multipleChoiceCount;
  if (!Number.isInteger(total) || total < 1 || total > 20) {
    alert("Tổng số câu hỏi phải từ 1 đến 20.");
    return;
  }
  if (!Number.isInteger(aiConfig.optionCount) || aiConfig.optionCount < 3 || aiConfig.optionCount > 6) {
    alert("Số lựa chọn phải từ 3 đến 6.");
    return;
  }
  generating.value = true;
  aiWarnings.value = [];
  try {
    const result = await adminApi.generateQuizQuestions({
      topicId: topicId.value,
      lessonId: lessonId.value || undefined,
      ...aiConfig,
    });
    aiSources.value = result.sources || [];
    aiWarnings.value = result.warnings || [];
    editingDraftIndex.value = null;
    draftBackup.value = null;
    aiDrafts.value = (result.questions || []).map(question => ({
      ...cloneGeneratedQuestion(question),
      selected: true,
    }));
  } catch (error) {
    alert(error instanceof Error ? error.message : "Không thể tạo câu hỏi. Vui lòng thử lại.");
  } finally {
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
const saveAiDrafts = async () => {
  if (editingDraftIndex.value !== null && !finishDraftEditing(editingDraftIndex.value)) return;
  const selected = aiDrafts.value.filter(draft => draft.selected);
  if (!selected.length) return;
  const invalidIndex = aiDrafts.value.findIndex(draft => draft.selected && validateAiDraft(draft));
  if (invalidIndex >= 0) {
    alert(`Câu AI số ${invalidIndex + 1}: ${validateAiDraft(aiDrafts.value[invalidIndex])}`);
    startDraftEditing(invalidIndex);
    return;
  }
  savingDrafts.value = true;
  try {
    for (const draft of selected) {
      await adminApi.createQuestion({
        topicId: topicId.value,
        lessonId: lessonId.value || undefined,
        text: draft.text,
        options: draft.options,
        type: draft.type,
        correctAnswers: draft.correctAnswers,
        correctAnswer: draft.correctAnswers[0],
      });
    }
    await loadQuestions();
    await courseStore.fetchTopics();
    aiDrafts.value = [];
    showAiPanel.value = false;
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
      await loadQuestions();
      await courseStore.fetchTopics();
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
      return { topicId: topicId.value, lessonId: lessonId.value || undefined, text: row[0], options: row.slice(1, 5), correctAnswer: answer };
    });
    if (!payloads.length) errors.push("File không có dữ liệu.");
    if (errors.length) throw new Error(errors.slice(0, 12).join("\n"));
    await Promise.all(payloads.map((payload) => adminApi.createQuestion(payload)));
    await loadQuestions();
    await courseStore.fetchTopics();
    alert(`Đã import ${payloads.length} câu hỏi.`);
  } catch (error) {
    alert(error instanceof Error ? `File chưa đúng định dạng:\n${error.message}` : "Không thể đọc file.");
  } finally { input.value = ""; }
};
</script>

<style scoped>
.quiz-editor-page { width: min(1240px, calc(100% - 3rem)); min-height: 100%; margin: 0 auto; padding: 1.5rem; color: var(--text-color); }
.quiz-editor-header { display: grid; grid-template-columns: 180px 1fr; gap: 1rem; align-items: start; margin-bottom: 1.2rem; }
.back-button { display: flex; align-items: center; gap: .65rem; min-width: 170px; padding: .7rem .85rem; border: 1px solid var(--primary-color); border-radius: var(--radius-md); background: var(--primary-color); color: white; cursor: pointer; text-align: left; box-shadow: 0 5px 14px rgba(0,91,65,.18); }
.back-button:hover { filter: brightness(1.08); transform: translateY(-1px); }
.back-button span { display: flex; flex-direction: column; white-space: nowrap; }
.back-button strong { font-size: var(--admin-text-label); }
.back-button small { color: rgba(255,255,255,.78); font-size: var(--admin-text-caption); }
.eyebrow { color: var(--primary-color); font-size: var(--admin-text-caption); font-weight: 800; text-transform: uppercase; }
.quiz-editor-header h1 { margin: .25rem 0; font-size: var(--admin-heading-page); line-height: 1.25; }
.quiz-editor-header p, .section-heading p { margin: 0; color: var(--text-muted); font-size: var(--admin-text-body); }
.editor-card { margin-bottom: 2rem; padding: 0; border: 0; border-radius: 0; background: white; box-shadow: none; }
.section-heading { display: flex; align-items: center; gap: .7rem; margin-bottom: 1rem; }
.section-heading > span { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 50%; background: var(--primary-color); color: white; font-weight: 800; }
.section-heading h2 { margin: 0 0 .15rem; font-size: var(--admin-heading-section); line-height: 1.3; }
.settings-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 1rem; }
.settings-grid label > span, .question-text-field > span { display: block; margin-bottom: .35rem; font-size: var(--admin-text-label); font-weight: 700; }
.settings-grid label > small { display: block; margin-top: .35rem; color: var(--text-muted); line-height: 1.35; }
select, .score-input input, textarea, .question-text-field input { width: 100%; padding: .7rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); background: white; }
.score-input { position: relative; }
.score-input input { padding-right: 2.5rem; }
.score-input strong { position: absolute; right: .8rem; top: 50%; transform: translateY(-50%); color: var(--primary-color); }
select, textarea, .question-text-field input { width: 100%; padding: .7rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); background: white; }
.settings-action { display: flex; justify-content: space-between; align-items: center; margin-top: 1rem; padding-top: 1rem; border-top: 1px solid var(--border-color); }
.settings-action p { margin: 0; color: var(--text-muted); font-size: var(--admin-text-body); }
.primary-button, .secondary-button { display: inline-flex; align-items: center; justify-content: center; gap: .4rem; padding: .65rem .85rem; border-radius: var(--radius-sm); cursor: pointer; font-weight: 700; }
.primary-button { border: 1px solid var(--primary-color); background: var(--primary-color); color: white; }
.secondary-button { border: 1px solid var(--border-color); background: white; color: var(--primary-color); }
.primary-button:disabled { opacity: .5; cursor: not-allowed; }
.danger-zone { display: flex; justify-content: space-between; align-items: center; gap: 1rem; margin-top: 2rem; padding: 1rem; border: 1px solid #fecaca; border-radius: var(--radius-md); background: #fff7f7; }
.danger-zone p { margin: .25rem 0 0; color: #7f1d1d; font-size: var(--admin-text-body); }
.danger-zone button { display: inline-flex; align-items: center; gap: .4rem; flex: 0 0 auto; padding: .65rem .85rem; border: 1px solid #dc2626; border-radius: var(--radius-sm); background: white; color: #b91c1c; cursor: pointer; font-weight: 700; }
.danger-zone button:disabled { opacity: .55; cursor: wait; }
.question-section-heading { flex-wrap: wrap; }
.question-tools { display: flex; gap: .45rem; margin-left: auto; }
.ai-button { display: inline-flex; align-items: center; gap: .4rem; padding: .65rem .85rem; border: 1px solid #7c3aed; border-radius: var(--radius-sm); background: #f5f3ff; color: #6d28d9; cursor: pointer; font-weight: 800; }
.ai-button:disabled { opacity: .65; cursor: wait; }
.import-button { position: relative; }
.import-button input { position: absolute; width: 1px; height: 1px; opacity: 0; }
.search-box { display: flex; align-items: center; gap: .5rem; max-width: 560px; margin-bottom: 1rem; padding: 0 .75rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); color: var(--text-muted); }
.search-box input { width: 100%; height: 42px; border: 0; outline: 0; }
.ai-panel { margin-bottom: 1.2rem; padding: 1rem; border: 1px solid #ddd6fe; border-radius: var(--radius-md); background: #faf9ff; }
.ai-panel-heading { display: flex; justify-content: space-between; gap: 1rem; margin-bottom: .9rem; }
.ai-panel-heading > div { display: flex; flex-direction: column; gap: .2rem; }
.ai-panel-heading small { color: var(--text-muted); }
.ai-panel-heading > button { border: 0; background: transparent; cursor: pointer; }
.ai-settings { display: grid; grid-template-columns: repeat(3, minmax(120px, 1fr)) auto; gap: .7rem; align-items: end; }
.ai-settings label span, .question-type-field > span { display: block; margin-bottom: .3rem; font-size: var(--admin-text-label); font-weight: 700; }
.ai-settings input { width: 100%; padding: .65rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); }
.ai-warning { margin: .75rem 0 0; padding: .65rem .75rem; border-radius: var(--radius-sm); background: #fff7ed; color: #9a3412; }
.ai-drafts { display: grid; gap: .75rem; margin-top: 1rem; }
.ai-draft-card { position: relative; padding: 1rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); background: white; }
.ai-draft-card h3 { margin: .55rem 0 .75rem; padding-right: 4rem; font-size: var(--admin-text-body); }
.draft-select { position: absolute; top: 1rem; right: 1rem; display: flex; gap: .35rem; align-items: center; font-weight: 700; }
.draft-heading-actions { display: flex; align-items: center; gap: .5rem; padding-right: 5rem; }
.draft-edit-button { display: inline-flex; align-items: center; gap: .3rem; padding: .25rem .45rem; border: 1px solid #c4b5fd; border-radius: 6px; background: white; color: #6d28d9; cursor: pointer; font-weight: 700; }
.question-type-badge { display: inline-flex; padding: .2rem .45rem; border-radius: 999px; background: #ede9fe; color: #6d28d9; font-size: var(--admin-text-caption); font-weight: 800; }
.ai-draft-editor { margin-top: .8rem; padding: .85rem; border: 1px solid #ddd6fe; border-radius: var(--radius-sm); background: #faf9ff; }
.ai-draft-answer-editor { display: grid; grid-template-columns: 1fr 1fr; gap: .55rem; }
.ai-draft-answer-editor label { display: grid; grid-template-columns: 18px 24px 1fr 18px; align-items: center; gap: .4rem; padding: .55rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); background: white; }
.ai-draft-answer-editor label.correct { border-color: #7c3aed; }
.ai-draft-answer-editor label > input[type="text"] { width: 100%; border: 0; outline: 0; }
.draft-editor-actions { display: flex; justify-content: flex-end; gap: .5rem; margin-top: .75rem; }
.draft-explanation { margin: .7rem 0 .25rem; color: var(--text-muted); }
.draft-sources { color: #475569; }
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
.answer-editor label { display: grid; grid-template-columns: 18px 24px 1fr 18px; align-items: center; gap: .4rem; padding: .55rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); background: white; }
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
.question-number { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 8px; background: var(--primary-light); color: var(--primary-color); font-weight: 800; }
.question-content h3 { margin: .2rem 0 .7rem; font-size: var(--admin-text-body); line-height: 1.45; }
.answer-grid { display: grid; grid-template-columns: 1fr 1fr; gap: .4rem; }
.answer-grid span { display: flex; align-items: center; gap: .4rem; padding: .4rem .55rem; border-radius: 7px; background: #f8fafc; font-size: var(--admin-text-label); }
.answer-grid span.correct { background: var(--primary-light); color: var(--primary-color); }
.question-actions { display: flex; gap: .25rem; }
.question-actions .danger { color: #b42318; }
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
</style>
