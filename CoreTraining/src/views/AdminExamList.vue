<template>
  <main class="exam-page">
    <header class="exam-heading"><h1>Bài kiểm tra</h1><p>Quản lý bài kiểm tra và ngân hàng câu hỏi theo khóa, bài học.</p></header>
    <section class="exam-list" aria-labelledby="exam-list-title" :aria-busy="loading">
      <div class="exam-list-heading">
        <div><h2 id="exam-list-title">Danh sách bài kiểm tra</h2><p>Chọn một bài kiểm tra để xem và quản lý câu hỏi bên trong.</p></div>
        <button v-if="user.can('exams', 'create')" class="btn btn-primary" @click="router.push({ name: 'AdminQuizCreate', query: courseId ? { courseId } : {} })">Thêm bài kiểm tra</button>
      </div>
      <div class="exam-filters">
        <label>Khóa học<select v-model="courseId"><option value="">Tất cả khóa học</option><option v-for="course in courses" :key="course.id" :value="course.id">{{ course.name }}</option></select></label>
        <label>Tìm bài kiểm tra<input v-model="search" type="search" placeholder="Tìm theo tên bài kiểm tra hoặc bài học..." /></label>
      </div>
      <div v-if="error" class="exam-error" role="alert">{{ error }} <button class="btn btn-secondary" @click="load">Thử lại</button></div>
      <div class="exam-table-scroll">
        <table><thead><tr><th>Bài kiểm tra</th><th>Số câu hỏi</th><th>Ngưỡng đạt</th><th>Thao tác</th></tr></thead>
          <tbody>
            <tr v-if="loading"><td colspan="4" role="status">Đang tải danh sách bài kiểm tra...</td></tr>
            <template v-else><tr v-for="quiz in rows" :key="`${quiz.courseId}:${quiz.lessonId || ''}`">
              <td><strong>{{ quiz.lessonTitle || `Bài kiểm tra tổng hợp — ${quiz.courseName}` }}</strong><small>{{ quiz.lessonId ? `Kiểm tra bài học · ${quiz.courseName}` : 'Kiểm tra khóa học' }}</small></td>
              <td>{{ quiz.questionCount }} câu hỏi</td><td>{{ quiz.passingScore }}%</td>
              <td><button class="exam-detail" :disabled="openingQuiz !== null" :aria-busy="openingQuiz === quizKey(quiz)" @click="openQuiz(quiz)">{{ openingQuiz === quizKey(quiz) ? 'Đang mở...' : 'Chi tiết' }}</button><span v-if="openingQuiz === quizKey(quiz)" class="exam-opening-status" role="status">Đang tải bài kiểm tra...</span></td>
            </tr><tr v-if="!rows.length && !error"><td colspan="4">Không có bài kiểm tra phù hợp.</td></tr></template>
          </tbody>
        </table>
      </div>
      <TablePagination :page="page" :page-size="size" :total="total" :page-count="Math.max(1, pages)" unit="bài kiểm tra" unit-label="Bài kiểm tra" @update:page="page = $event" @update:page-size="size = $event" />
    </section>
  </main>
</template>
<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount } from 'vue';
import { useRouter } from 'vue-router';
import { useUserStore } from '../stores/userStore';
import TablePagination from '../components/TablePagination.vue';
import { getQuizSummaries, type QuizSummary } from '../api/quizSummaryApi';

const router = useRouter();
const user = useUserStore();
const courseId = ref('');
const search = ref('');
const page = ref(1);
const size = ref(10);
const rows = ref<QuizSummary[]>([]);
const courses = ref<{ id: string; name: string }[]>([]);
const total = ref(0);
const pages = ref(0);
const loading = ref(false);
const error = ref('');
const openingQuiz = ref<string | null>(null);
const quizKey = (quiz: QuizSummary) => `${quiz.courseId}:${quiz.lessonId || ''}`;
async function openQuiz(quiz: QuizSummary) {
  if (openingQuiz.value !== null) return;
  openingQuiz.value = quizKey(quiz);
  try {
    await router.push({ name: 'AdminQuizDetail', params: { scopeType: quiz.lessonId ? 'lesson' : 'course', scopeId: quiz.lessonId || quiz.courseId } });
  } catch {
    error.value = 'Không thể mở bài kiểm tra. Vui lòng thử lại.';
  } finally {
    openingQuiz.value = null;
  }
}
let requestId = 0;
let timer: ReturnType<typeof setTimeout> | undefined;
async function load() {
  clearTimeout(timer);
  const id = ++requestId;
  loading.value = true;
  error.value = '';
  try {
    const result = await getQuizSummaries({ courseId: courseId.value || undefined, search: search.value, page: page.value - 1, size: size.value });
    if (id !== requestId) return;
    rows.value = result.content;
    courses.value = result.courses;
    total.value = result.totalElements;
    pages.value = result.totalPages;
    if (page.value > Math.max(1, pages.value)) page.value = Math.max(1, pages.value);
  } catch {
    if (id === requestId) { rows.value = []; error.value = 'Không thể tải danh sách bài kiểm tra. Vui lòng thử lại.'; }
  } finally {
    if (id === requestId) loading.value = false;
  }
}
watch([courseId, size], () => { page.value = 1; });
watch(search, () => { page.value = 1; });
watch([courseId, size, page, search], () => {
  ++requestId; // Invalidate old responses during the search debounce too.
  clearTimeout(timer);
  loading.value = true;
  timer = setTimeout(load, 250);
});
onMounted(load);
onBeforeUnmount(() => { ++requestId; clearTimeout(timer); });
</script>
<style scoped>
.exam-page { padding: 2rem; min-width: 0; color: var(--text-main); }
.exam-heading { margin-bottom: 2rem; }
h1 { font-size: 1.5rem; margin: 0 0 .6rem; }
h2 { font-size: 1.1rem; margin: 0 0 .4rem; }
p { color: var(--text-muted); font-size: .85rem; margin: 0; }
.exam-list { background: var(--surface-ffffff); border: 1px solid var(--border-color); border-radius: 12px; padding: 1.5rem; }
.exam-list-heading { display: flex; align-items: center; justify-content: space-between; gap: 1rem; margin-bottom: 1.5rem; }
.exam-filters { display: grid; grid-template-columns: minmax(180px, 1fr) minmax(240px, 2fr); gap: 1rem; margin-bottom: 1.25rem; }
.exam-filters label { font-size: .8rem; font-weight: 600; }
.exam-filters input, .exam-filters select { box-sizing: border-box; display: block; width: 100%; min-height: 44px; margin-top: .4rem; padding: .65rem .8rem; border: 1px solid var(--border-color); border-radius: 8px; background: var(--surface-ffffff); color: var(--text-main); font: inherit; font-weight: 400; }
.exam-filters input:focus, .exam-filters select:focus { outline: 2px solid var(--primary-color); outline-offset: 1px; }
.exam-table-scroll { overflow-x: auto; }
table { border-collapse: collapse; width: 100%; font-size: .85rem; }
th, td { padding: 1rem; text-align: left; border-bottom: 1px solid var(--border-color); }
th { background: var(--bg-main); font-weight: 600; white-space: nowrap; }
td:first-child { width: 55%; }
td small { display: block; color: var(--text-muted); margin-top: .35rem; }
.exam-detail { padding: .4rem .7rem; border: 1px solid var(--border-color); border-radius: 6px; background: var(--surface-ffffff); color: var(--primary-color); cursor: pointer; font: inherit; }
.exam-detail:disabled { cursor: wait; opacity: .65; }
.exam-opening-status { display: block; margin-top: .35rem; color: var(--text-muted); font-size: .75rem; }
.exam-error { color: var(--ink-b42318); padding: .75rem 0; }
@media (max-width: 700px) { .exam-page { padding: 1rem; } .exam-list { padding: 1rem; } .exam-list-heading { align-items: flex-start; flex-direction: column; } .exam-filters { grid-template-columns: 1fr; } }
</style>
