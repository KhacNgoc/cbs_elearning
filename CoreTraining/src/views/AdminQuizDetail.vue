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

        <Transition name="editor-expand">
        <div v-if="showQuestionForm" class="question-editor">
          <div class="question-editor-title">
            <div><strong>{{ editingId ? "Chỉnh sửa câu hỏi" : "Câu hỏi mới" }}</strong><small>Nhập đủ 4 đáp án và chọn một đáp án đúng.</small></div>
            <button aria-label="Đóng" @click="closeQuestionForm"><X :size="18" /></button>
          </div>
          <label class="question-text-field">
            <span>Nội dung câu hỏi</span>
            <textarea v-model="form.text" rows="3" placeholder="Nhập câu hỏi rõ ràng, chỉ tập trung vào một nội dung..." />
          </label>
          <div class="answer-editor">
            <label v-for="index in 4" :key="index" :class="{ correct: form.correctAnswer === index - 1 }">
              <input v-model="form.correctAnswer" type="radio" :value="index - 1" name="editor-correct-answer" />
              <strong>{{ String.fromCharCode(64 + index) }}</strong>
              <input v-model="form.options[index - 1]" type="text" :placeholder="`Nhập đáp án ${String.fromCharCode(64 + index)}`" />
              <CheckCircle2 v-if="form.correctAnswer === index - 1" :size="18" />
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
                  :class="{ correct: optionIndex === question.correctAnswer }">
                  <strong>{{ String.fromCharCode(65 + optionIndex) }}</strong>{{ option }}
                  <CheckCircle2 v-if="optionIndex === question.correctAnswer" :size="15" />
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
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowLeft, ArrowRight, CheckCircle2, Download, Edit, FileUp, Plus, Search, Trash2, X } from "lucide-vue-next";
import { useCourseStore } from "../stores/courseStore";
import { quizApi } from "../api/quizApi";
import { adminApi } from "../api/adminApi";
import type { Question } from "../types";

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
const passingScore = ref(80);
const form = reactive({ text: "", options: ["", "", "", ""], correctAnswer: 0 });

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
  questions.value = (await quizApi.getQuizQuestions(topicId.value, lessonId.value || undefined)) as Question[];
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
const deleteQuiz = async () => {
  if (!confirm(`Xóa toàn bộ ${questions.value.length} câu hỏi và bài kiểm tra này?`)) return;
  deletingQuiz.value = true;
  try {
    await adminApi.deleteQuiz(topicId.value, lessonId.value || undefined);
    await courseStore.fetchTopics();
    router.push("/admin?tab=exams");
  } finally {
    deletingQuiz.value = false;
  }
};
const resetForm = () => {
  form.text = "";
  form.options.splice(0, 4, "", "", "", "");
  form.correctAnswer = 0;
  editingId.value = null;
};
const startAdding = () => { resetForm(); showQuestionForm.value = true; };
const closeQuestionForm = () => { showQuestionForm.value = false; resetForm(); };
const editQuestion = (question: Question) => {
  editingId.value = question.id;
  form.text = question.text;
  form.options.splice(0, 4, ...question.options);
  form.correctAnswer = question.correctAnswer;
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
  saving.value = true;
  const payload = { topicId: topicId.value, lessonId: lessonId.value || undefined, text, options, correctAnswer: form.correctAnswer };
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
const deleteQuestion = async (question: Question) => {
  if (!confirm("Xóa câu hỏi này khỏi bài kiểm tra?")) return;
  await adminApi.deleteQuestion(question.id);
  await loadQuestions();
  await courseStore.fetchTopics();
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
.import-button { position: relative; }
.import-button input { position: absolute; width: 1px; height: 1px; opacity: 0; }
.search-box { display: flex; align-items: center; gap: .5rem; max-width: 560px; margin-bottom: 1rem; padding: 0 .75rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); color: var(--text-muted); }
.search-box input { width: 100%; height: 42px; border: 0; outline: 0; }
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
  .settings-grid, .answer-editor, .answer-grid { grid-template-columns: 1fr; }
  .danger-zone { align-items: flex-start; flex-direction: column; }
  .question-tools { width: 100%; margin-left: 0; flex-wrap: wrap; }
  .question-card { grid-template-columns: 30px 1fr; }
  .question-actions { grid-column: 2; }
}
@media (prefers-reduced-motion: reduce) {
  .editor-expand-enter-active, .editor-expand-leave-active,
  .question-item-enter-active, .question-item-leave-active,
  .question-item-move { transition: none; }
}
</style>
