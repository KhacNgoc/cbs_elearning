<template>
  <main class="training-admin">
    <header class="page-heading">
      <div class="heading-with-back">
        <AdminBackButton v-if="backTo" :to="backTo" :label="backLabel" />
        <div class="heading-copy">
          <nav class="breadcrumb" aria-label="Đường dẫn">
            <RouterLink to="/admin/courses">Khóa học</RouterLink
            ><template v-if="course">
              /
              <RouterLink :to="`/admin/courses/${course.id}`">{{
                course.name
              }}</RouterLink></template
            ><span v-if="chapter"> / {{ chapter.title }}</span>
          </nav>
          <h1>{{ title }}</h1>
          <p v-if="course">
            {{ course.domainName }} / {{ course.programName }}
            <span class="status">{{ statusLabel(course.status) }}</span>
          </p>
          <p v-else>
            Tổ chức đào tạo theo lĩnh vực, chương trình, khóa và chương.
          </p>
        </div>
      </div>
      <div class="tools" v-if="course && !chapter">
        <button
          class="btn btn-secondary"
          @click="router.push(`/courses/${course.id}`)"
        >
          <Eye :size="17" /> Xem trước
        </button>
        <button
          v-if="managesCourses && course.status === 'DRAFT'"
          class="btn btn-primary"
          :disabled="busy"
          @click="changeStatus('PUBLISHED')"
        >
          Kiểm tra và xuất bản
        </button>
        <button
          v-else-if="managesCourses"
          class="btn btn-secondary"
          :disabled="busy"
          @click="changeStatus('DRAFT')"
        >
          Chuyển về nháp
        </button>
        <button
          v-if="managesCourses"
          class="btn btn-secondary"
          :disabled="busy"
          @click="changeStatus('ARCHIVED')"
        >
          Lưu trữ
        </button>
      </div>
      <button
        v-if="chapter && canEditCourse"
        class="btn btn-secondary"
        @click="openForm('chapter', chapter)"
      >
        <Pencil :size="16" /> Sửa chương
      </button>
    </header>

    <div v-if="mode === 'catalog'" class="catalog-layout">
      <section class="card catalog-domains">
        <div class="section-heading">
          <h2>Lĩnh vực đào tạo</h2>
          <button
            class="btn btn-secondary"
            v-if="accessUser.can('catalog', 'create') && canManageDomains"
            @click="openForm('domain')"
          >
            <Plus :size="16" /> Thêm
          </button>
        </div>
        <div class="table-scroll">
          <table class="admin-table">
            <colgroup>
              <col style="width: 72%" />
              <col style="width: 28%" />
            </colgroup>
            <thead>
              <tr>
                <th>Lĩnh vực</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="domain in paginatedDomains"
                :key="domain.id"
                :class="{ 'selected-domain': domain.id === domainId }"
              >
                <td>
                  <button
                    class="name-link"
                    :aria-pressed="domain.id === domainId"
                    @click="domainId = domain.id"
                  >
                    {{ domain.name }}</button
                  ><small
                    >{{ domain.code }} ·
                    {{
                      programs.filter((p) => p.domainId === domain.id).length
                    }}
                    chương trình</small
                  >
                </td>
                <td>
                  <button
                    v-if="domain.allowedActions?.includes('update')"
                    class="row-edit"
                    :aria-label="`Sửa lĩnh vực ${domain.name}`"
                    @click="openForm('domain', domain)"
                  >
                    <Pencil :size="15" />
                  </button>
                </td>
              </tr>
              <tr v-if="!domains.length">
                <td colspan="2" class="empty">Chưa có lĩnh vực đào tạo.</td>
              </tr>
            </tbody>
          </table>
        </div>
        <TablePagination
          v-model:page="domainPage"
          v-model:page-size="domainPageSize"
          :total="domains.length"
          :page-count="domainPageCount"
          unit="lĩnh vực"
          unit-label="Lĩnh vực"
          compact
        />
      </section>
      <section class="card">
        <div class="section-heading">
          <h2>Chương trình đào tạo</h2>
          <button
            class="btn btn-primary"
            :disabled="!domainId"
            v-if="accessUser.can('catalog', 'create')"
            @click="openForm('program')"
          >
            <Plus :size="16" /> Thêm chương trình
          </button>
        </div>
        <div class="search-box">
          <Search :size="18" /><input
            v-model="query"
            placeholder="Tìm mã hoặc tên chương trình..."
          />
        </div>
        <div class="table-scroll">
          <table class="admin-table">
            <colgroup>
              <col style="width: 40%" />
              <col style="width: 25%" />
              <col style="width: 15%" />
              <col style="width: 20%" />
            </colgroup>
            <thead>
              <tr>
                <th>Chương trình</th>
                <th>Đơn vị</th>
                <th>Trạng thái</th>
                <th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="program in paginatedPrograms" :key="program.id">
                <td>
                  <strong>{{ program.name }}</strong
                  ><small>{{ program.code }}</small>
                </td>
                <td>
                  {{
                    program.unitIds
                      ?.map((id) => units.find((u) => u.id === id)?.name || id)
                      .join(", ") || "Chưa gán đơn vị"
                  }}
                </td>
                <td>{{ statusLabel(program.status) }}</td>
                <td>
                  <button
                    class="row-edit"
                    v-if="program.allowedActions?.includes('update')"
                    @click="openForm('program', program)"
                  >
                    Sửa
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="!filteredPrograms.length" class="empty">
          Chưa có chương trình phù hợp.
        </p>
        <TablePagination
          v-model:page="programPage"
          v-model:page-size="programPageSize"
          :total="filteredPrograms.length"
          :page-count="programPageCount"
          unit="chương trình"
          unit-label="Chương trình"
        />
      </section>
    </div>

    <section v-else class="card list-card">
      <div class="section-heading">
        <h2>
          {{
            chapter
              ? "Danh sách bài học"
              : course
                ? "Danh sách chương"
                : "Danh sách khóa học"
          }}
        </h2>
        <button
          v-if="course && !chapter && canEditCourse"
          class="btn btn-secondary"
          @click="openForm('course', course)"
        >
          Thông tin khóa
        </button>
      </div>
      <p v-if="course && !canEditCourse" class="readonly-notice">
        Khóa học đang ở chế độ chỉ xem. Chuyển khóa về bản nháp để chỉnh sửa nội
        dung.
      </p>
      <div class="table-toolbar">
        <div class="search-box">
          <Search :size="18" /><input
            v-model="query"
            :placeholder="
              chapter
                ? 'Tìm mã hoặc tên bài học...'
                : course
                  ? 'Tìm mã, tên chương hoặc bài học...'
                  : 'Tìm mã hoặc tên khóa...'
            "
          />
        </div>
        <select v-model="statusFilter" aria-label="Lọc trạng thái">
          <option value="">Tất cả trạng thái</option>
          <option value="DRAFT">Bản nháp</option>
          <option value="PUBLISHED">Đã xuất bản</option>
          <option value="ARCHIVED">Đã lưu trữ</option>
        </select>
        <select
          v-if="!course"
          v-model="programFilter"
          aria-label="Lọc chương trình"
        >
          <option value="">Tất cả chương trình</option>
          <option
            v-for="program in programs"
            :key="program.id"
            :value="program.id"
          >
            {{ program.name }}
          </option>
        </select>
        <button
          v-if="
            (!course && accessUser.can('courses', 'create')) ||
            (course?.status === 'DRAFT' &&
              !!course.allowedActions?.includes('create'))
          "
          class="btn btn-primary"
          @click="openForm(chapter ? 'lesson' : course ? 'chapter' : 'course')"
        >
          <Plus :size="17" />
          {{
            chapter ? "Thêm bài học" : course ? "Thêm chương" : "Tạo khóa học"
          }}
        </button>
      </div>
      <div v-if="loading" class="empty">Đang tải dữ liệu...</div>
      <ChapterTreeTable
        v-else-if="course && !chapter"
        :key="course.id"
        :chapters="paginated as Chapter[]"
        :expanded-ids="expandedChapterIds"
        :can-edit="canEditCourse"
        :can-create="
          course?.status === 'DRAFT' &&
          accessUser.can('courses', 'create') &&
          !!course?.allowedActions?.includes('create')
        "
        :can-delete="
          course?.status === 'DRAFT' &&
          accessUser.can('courses', 'delete') &&
          !!course?.allowedActions?.includes('delete')
        "
        :busy="busy"
        :reorder-disabled="query !== '' || statusFilter !== ''"
        :total-chapters="chapters.length"
        :order-of="orderOf"
        :visible-lessons="visibleLessons"
        @toggle="toggleChapter"
        @open-chapter="openRow"
        @open-lesson="openLesson"
        @edit-chapter="editRow"
        @delete-chapter="askDelete"
        @move-chapter="move"
        @add-lesson="openLessonForm"
        @move-lesson="moveLesson"
        @delete-lesson="(lesson) => askDelete(lesson, 'lesson')"
      />
      <div v-else class="table-scroll">
        <table class="admin-table">
          <colgroup>
            <col v-if="course" style="width: 7%" />
            <col :style="{ width: course ? '33%' : '40%' }" />
            <col style="width: 18%" />
            <col style="width: 14%" />
            <col style="width: 28%" />
          </colgroup>
          <thead>
            <tr>
              <th v-if="course">STT</th>
              <th>
                {{ chapter ? "Bài học" : course ? "Chương" : "Khóa học" }}
              </th>
              <th>
                {{ chapter ? "Kiểm tra" : course ? "Bài học" : "Chương trình" }}
              </th>
              <th>Trạng thái</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in paginated" :key="row.id">
              <td v-if="course">{{ orderOf(row) + 1 }}</td>
              <td>
                <button class="name-link" @click="openRow(row)">
                  {{ rowName(row) }}</button
                ><small>{{ "code" in row ? row.code : row.id }}</small>
              </td>
              <td v-if="chapter">
                <span
                  class="status"
                  :class="{ amber: (row as Lesson).quizRequired }"
                  >{{
                    !(row as Lesson).hasQuiz
                      ? "Chưa có kiểm tra"
                      : (row as Lesson).quizRequired
                        ? "Bắt buộc"
                        : "Tùy chọn"
                  }}</span
                >
              </td>
              <td v-else-if="course">{{ (row as Chapter).lessons.length }}</td>
              <td v-else>{{ (row as Course).programName }}</td>
              <td>
                <span class="status">{{ statusLabel(row.status) }}</span>
              </td>
              <td>
                <div v-if="canEditRow(row)" class="row-actions">
                  <button
                    v-if="canEditCourse"
                    class="order-button"
                    :disabled="
                      busy ||
                      query !== '' ||
                      statusFilter !== '' ||
                      orderOf(row) === 0
                    "
                    @click="move(row, -1)"
                    aria-label="Di chuyển lên"
                  >
                    <ArrowUp :size="14" /></button
                  ><button
                    v-if="canEditCourse"
                    class="order-button"
                    :disabled="
                      busy ||
                      query !== '' ||
                      statusFilter !== '' ||
                      orderOf(row) === rows.length - 1
                    "
                    @click="move(row, 1)"
                    aria-label="Di chuyển xuống"
                  >
                    <ArrowDown :size="14" /></button
                  ><button
                    v-if="
                      course
                        ? canEditCourse
                        : (row as Course).allowedActions?.includes('update')
                    "
                    class="row-edit"
                    @click="editRow(row)"
                  >
                    Sửa</button
                  ><button
                    v-if="
                      course
                        ? course.allowedActions?.includes('delete')
                        : (row as Course).allowedActions?.includes('delete')
                    "
                    class="row-delete"
                    :disabled="busy"
                    @click="askDelete(row)"
                  >
                    Xóa
                  </button>
                </div>
                <span v-else class="readonly-label">Chỉ xem</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-if="!loading && !filtered.length" class="empty">
        <BookOpen :size="30" />
        <p>
          {{
            query
              ? "Không tìm thấy kết quả phù hợp."
              : chapter
                ? "Chương chưa có bài. Thêm bài học đầu tiên để bắt đầu soạn."
                : course
                  ? "Khóa chưa có chương. Thêm chương đầu tiên."
                  : "Tạo khóa đầu tiên và gán chương trình đào tạo."
          }}
        </p>
      </div>
      <TablePagination
        v-model:page="page"
        v-model:page-size="pageSize"
        :total="filtered.length"
        :page-count="pageCount"
        :unit="course && !chapter ? 'chương' : 'bản ghi'"
        :unit-label="course && !chapter ? 'Chương' : 'Bản ghi'"
      />
    </section>

    <AdminModal
      :open="!!formKind"
      :title="formTitle || ''"
      :busy="busy || uploadingImage"
      :submit-label="
        formKind === 'lesson'
          ? 'Tạo và soạn bài'
          : formKind === 'chapter' && !form.id
            ? 'Lưu và mở chương'
            : formKind === 'course' && !form.id
              ? 'Lưu nháp và soạn nội dung'
              : 'Lưu thay đổi'
      "
      @close="closeForm"
      @submit="save"
    >
      <p class="admin-modal-help">
        {{
          formKind === "lesson"
            ? `${course?.name} / ${lessonChapter?.title || chapter?.title}`
            : formKind === "chapter"
              ? `Khóa: ${course?.name}`
              : "Thông tin đào tạo"
        }}
      </p>
      <label class="admin-form-field"
        >Mã {{ formKind === "lesson" ? "bài học" : "" }} *<input
          v-model="form.code"
          required
          :disabled="formKind === 'lesson' && !!form.id" /></label
      ><label class="admin-form-field"
        >Tên *<input v-model="form.name" required
      /></label>
      <label class="admin-form-field" v-if="formKind === 'course'"
        >Chương trình đào tạo *<select v-model="form.programId" required>
          <option value="" disabled>Chọn chương trình</option>
          <option
            v-for="program in programs.filter((p) => p.status === 'ACTIVE')"
            :key="program.id"
            :value="program.id"
          >
            {{ domains.find((d) => d.id === program.domainId)?.name }} /
            {{ program.name }}
          </option>
        </select></label
      >
      <template v-if="formKind === 'course'">
        <div class="admin-form-grid">
          <label class="admin-form-field"
            >Phân hệ nghiệp vụ<input
              v-model="form.businessModule"
              maxlength="255"
              placeholder="Ví dụ: LN, CIF, GL" /></label
          ><label class="admin-form-field"
            >Mức độ<input
              v-model="form.level"
              maxlength="255"
              placeholder="Ví dụ: Cơ bản, nâng cao"
          /></label>
        </div>
        <label class="admin-form-field"
          >Từ khóa<input
            v-model="form.keywords"
            placeholder="Từ khóa tìm kiếm khóa học"
        /></label>
        <label class="admin-form-field"
          >Hạn hoàn thành mặc định<input
            v-model="form.completionDeadline"
            type="date"
          /><small
            >Áp dụng cho học viên tham gia khóa; để trống nếu không quy định
            hạn.</small
          ></label
        >
        <div class="admin-form-field">
          <span>Ảnh khóa học</span
          ><CourseThumbnail
            v-if="form.thumbnail"
            :thumbnail="form.thumbnail"
            :name="form.name || 'Ảnh khóa học'"
            class="course-image-preview"
          /><input
            type="file"
            accept="image/png,image/jpeg"
            :disabled="busy || uploadingImage || (!form.id && !form.programId)"
            @change="uploadImage"
          /><small
            >PNG hoặc JPEG, tối đa 5 MB. Chọn chương trình trước khi tải ảnh.
            Khuyến nghị tỷ lệ 16:9.</small
          ><button
            v-if="form.thumbnail"
            type="button"
            class="btn btn-secondary"
            :disabled="busy || uploadingImage"
            @click="form.thumbnail = ''"
          >
            Bỏ ảnh
          </button>
          <p v-if="uploadingImage" role="status">Đang tải ảnh...</p>
        </div>
      </template>
      <label class="admin-form-field" v-if="formKind === 'program'"
        >Lĩnh vực *<select v-model="form.domainId" required>
          <option v-for="domain in domains" :key="domain.id" :value="domain.id">
            {{ domain.name }}
          </option>
        </select></label
      >
      <div v-if="formKind === 'program'" class="admin-form-field">
        <span>Đơn vị của chương trình *</span
        ><OrganizationMultiTreeSelect
          v-model="form.unitIds"
          :units="units"
          :disabled="busy"
        /><small
          >Có thể chọn nhiều đơn vị. Khóa học kế thừa phạm vi của chương
          trình.</small
        >
      </div>
      <label class="admin-form-field"
        >{{ formKind === "lesson" ? "Gợi ý nghiệp vụ" : "Mô tả"
        }}<textarea v-model="form.description" rows="3" />
      </label>
      <label
        class="admin-form-field"
        v-if="['domain', 'program'].includes(formKind || '')"
        >Trạng thái<select v-model="form.status">
          <option value="ACTIVE">Đang sử dụng</option>
          <option value="ARCHIVED">Lưu trữ</option>
        </select></label
      >
      <p v-if="formKind === 'lesson'">
        Tạo bài xong sẽ mở trang soạn với hai tab Nội dung bài học và Bài kiểm
        tra.
      </p>
    </AdminModal>
    <ConfirmModal
      :open="!!deleteTarget"
      title="Xóa nội dung?"
      :message="deleteMessage"
      :loading="busy"
      confirm-label="Xóa"
      @cancel="deleteTarget = null"
      @confirm="remove"
    />
  </main>
</template>

<script setup lang="ts">
import { useUserStore } from "../stores/userStore";
const accessUser = useUserStore();
const managesCourses = computed(
  () =>
    accessUser.can("courses", "update") &&
    (!course.value || !!course.value.allowedActions?.includes("update")),
);
const units = ref<OrganizationUnit[]>([]);
const canManageDomains = ref(false);
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  Search,
  Plus,
  Pencil,
  BookOpen,
  Eye,
  ArrowUp,
  ArrowDown,
} from "lucide-vue-next";
import { trainingApi } from "../api/trainingApi";
import { adminApi } from "../api/adminApi";
import type {
  Course,
  Chapter,
  Lesson,
  TrainingDomain,
  TrainingProgram,
} from "../types";
import ConfirmModal from "../components/ConfirmModal.vue";
import AdminBackButton from "../components/AdminBackButton.vue";
import ChapterTreeTable from "../components/ChapterTreeTable.vue";
import OrganizationMultiTreeSelect from "../components/OrganizationMultiTreeSelect.vue";
import type { OrganizationUnit } from "../types/organization";
import { axiosClient } from "../api/axiosClient";
import AdminModal from "../components/AdminModal.vue";
import TablePagination from "../components/TablePagination.vue";
import CourseThumbnail from "../components/CourseThumbnail.vue";
import { showToast } from "../utils/toast";
type Row = Course | Chapter | Lesson;
type FormKind = "domain" | "program" | "course" | "chapter" | "lesson";
const route = useRoute(),
  router = useRouter();
const loading = ref(false),
  busy = ref(false);
const domains = ref<TrainingDomain[]>([]),
  programs = ref<TrainingProgram[]>([]),
  courses = ref<Course[]>([]),
  chapters = ref<Chapter[]>([]);
const course = ref<Course | null>(null),
  chapter = ref<Chapter | null>(null),
  domainId = ref(""),
  query = ref(""),
  statusFilter = ref(""),
  programFilter = ref(""),
  page = ref(1);
const pageSize = ref(10);
const expandedChapterIds = ref(new Set<string>());
const lessonChapter = ref<Chapter | null>(null);
const focusApplied = ref(false);
function toggleChapter(id: string) {
  const expanded = new Set(expandedChapterIds.value);
  if (expanded.has(id)) expanded.delete(id);
  else expanded.add(id);
  expandedChapterIds.value = expanded;
}
const mode = computed(() =>
  route.path.endsWith("/catalog")
    ? "catalog"
    : route.params.chapterId
      ? "chapter"
      : route.params.courseId
        ? "course"
        : "list",
);
const backTo = computed(() =>
  mode.value === "chapter"
    ? `/admin/courses/${route.params.courseId}`
    : mode.value === "course"
      ? "/admin/courses"
      : "",
);
const backLabel = computed(() =>
  mode.value === "chapter"
    ? "Quay lại khóa học"
    : "Quay lại danh sách khóa học",
);
const title = computed(() =>
  mode.value === "catalog"
    ? "Danh mục đào tạo"
    : chapter.value?.title || course.value?.name || "Khóa học",
);
const rows = computed<Row[]>(
  () =>
    chapter.value?.lessons || (course.value ? chapters.value : courses.value),
);
const canEditCourse = computed(
  () => course.value?.status === "DRAFT" && managesCourses.value,
);
const canEditRow = (row: Row) =>
  course.value
    ? course.value.status === "DRAFT" &&
      course.value.allowedActions?.some((a) => a === "update" || a === "delete")
    : row.status === "DRAFT" &&
      (row as Course).allowedActions?.some(
        (a) => a === "update" || a === "delete",
      );
const rowName = (r: Row) => ("name" in r ? r.name : r.title);
const orderOf = (r: Row) => rows.value.findIndex((item) => item.id === r.id);
const matchesText = (value: string) =>
  value
    .toLocaleLowerCase("vi")
    .includes(query.value.trim().toLocaleLowerCase("vi"));
function visibleLessons(target: Chapter): Lesson[] {
  const matchesChapter = matchesText(`${target.title} ${target.code}`);
  return target.lessons
    .filter(
      (lesson) =>
        (!statusFilter.value || lesson.status === statusFilter.value) &&
        (matchesChapter || matchesText(`${lesson.title} ${lesson.id}`)),
    )
    .sort(
      (a, b) =>
        (a.lessonOrder ?? 0) - (b.lessonOrder ?? 0) || a.id.localeCompare(b.id),
    );
}
const filtered = computed(() =>
  rows.value.filter((row) => {
    if (course.value && !chapter.value) {
      const target = row as Chapter;
      return (
        (matchesText(`${target.title} ${target.code}`) &&
          (!statusFilter.value || target.status === statusFilter.value)) ||
        visibleLessons(target).length > 0
      );
    }
    return (
      matchesText(`${rowName(row)} ${"code" in row ? row.code : row.id}`) &&
      (!statusFilter.value || row.status === statusFilter.value) &&
      (course.value ||
        !programFilter.value ||
        (row as Course).programId === programFilter.value)
    );
  }),
);
const filteredPrograms = computed(() =>
  programs.value.filter(
    (p) =>
      p.domainId === domainId.value &&
      `${p.code} ${p.name}`
        .toLocaleLowerCase("vi")
        .includes(query.value.toLocaleLowerCase("vi")),
  ),
);
const domainPage = ref(1),
  programPage = ref(1);
const domainPageSize = ref(10),
  programPageSize = ref(10);
const domainPageCount = computed(() =>
  Math.max(1, Math.ceil(domains.value.length / domainPageSize.value)),
);
const programPageCount = computed(() =>
  Math.max(1, Math.ceil(filteredPrograms.value.length / programPageSize.value)),
);
const paginatedDomains = computed(() =>
  domains.value.slice(
    (domainPage.value - 1) * domainPageSize.value,
    domainPage.value * domainPageSize.value,
  ),
);
const paginatedPrograms = computed(() =>
  filteredPrograms.value.slice(
    (programPage.value - 1) * programPageSize.value,
    programPage.value * programPageSize.value,
  ),
);
watch([query, domainId, programPageSize], () => (programPage.value = 1));
watch(domainPageSize, () => (domainPage.value = 1));
watch(
  domainPageCount,
  (count) => (domainPage.value = Math.min(domainPage.value, count)),
);
watch(
  programPageCount,
  (count) => (programPage.value = Math.min(programPage.value, count)),
);
const pageCount = computed(() =>
  Math.max(1, Math.ceil(filtered.value.length / pageSize.value)),
);
const paginated = computed(() =>
  filtered.value.slice(
    (page.value - 1) * pageSize.value,
    page.value * pageSize.value,
  ),
);
watch(pageSize, () => (page.value = 1));
watch([query, statusFilter, programFilter], () => (page.value = 1));
let expansionBeforeFilter = new Set<string>();
watch(
  [query, statusFilter],
  ([text, status], [previousText, previousStatus]) => {
    if (!course.value || chapter.value) return;
    const active = Boolean(text.trim() || status);
    const wasActive = Boolean(previousText.trim() || previousStatus);
    if (active && !wasActive)
      expansionBeforeFilter = new Set(expandedChapterIds.value);
    if (active)
      expandedChapterIds.value = new Set(filtered.value.map((row) => row.id));
    else if (wasActive)
      expandedChapterIds.value = new Set(expansionBeforeFilter);
  },
);
watch(pageCount, (count) => (page.value = Math.min(page.value, count)));
const statusLabel = (status?: string) =>
  ({
    DRAFT: "Bản nháp",
    PUBLISHED: "Đã xuất bản",
    ARCHIVED: "Đã lưu trữ",
    ACTIVE: "Đang sử dụng",
  })[status || "DRAFT"] || status;
function serverError(e: unknown) {
  return (
    (e as { response?: { data?: { message?: string } } }).response?.data
      ?.message ||
    (e instanceof Error ? e.message : "Không thể thực hiện. Vui lòng thử lại.")
  );
}
async function load() {
  loading.value = true;
  try {
    const catalog =
      mode.value === "catalog"
        ? await trainingApi.catalog()
        : (await axiosClient.get("/api/admin/options/courses")).data;
    const options =
      mode.value === "catalog"
        ? (await axiosClient.get("/api/admin/options/catalog")).data
        : catalog;
    units.value = options.units;
    canManageDomains.value = !!options.canManageDomains;
    domains.value = catalog.domains;
    programs.value = catalog.programs;
    domainId.value ||= domains.value[0]?.id || "";
    courses.value = mode.value === "catalog" ? [] : await trainingApi.courses();
    course.value =
      courses.value.find((item) => item.id === route.params.courseId) || null;
    chapter.value = null;
    chapters.value = course.value
      ? await trainingApi.chapters(course.value.id)
      : [];
    const focusId =
      typeof route.query.chapter === "string" ? route.query.chapter : "";
    if (focusId && !focusApplied.value) {
      const index = chapters.value.findIndex((item) => item.id === focusId);
      if (index >= 0) {
        expandedChapterIds.value = new Set([
          ...expandedChapterIds.value,
          focusId,
        ]);
        page.value = Math.floor(index / pageSize.value) + 1;
        focusApplied.value = true;
      }
    }
  } catch (failure) {
    showToast(serverError(failure), "error");
  } finally {
    loading.value = false;
  }
}
watch(
  () => route.fullPath,
  () => {
    expansionBeforeFilter = new Set();
    query.value = "";
    statusFilter.value = "";
    page.value = 1;
    expandedChapterIds.value = new Set();
    focusApplied.value = false;
    void load();
  },
  { immediate: true },
);
const formKind = ref<FormKind | null>(null);
const form = ref({
  id: "",
  code: "",
  name: "",
  description: "",
  programId: "",
  domainId: "",
  status: "ACTIVE",
  unitIds: [] as string[],
  version: undefined as number | undefined,
  thumbnail: "",
  businessModule: "",
  level: "",
  keywords: "",
  completionDeadline: "",
});
const uploadingImage = ref(false);
async function uploadImage(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  if (
    !["image/png", "image/jpeg"].includes(file.type) ||
    file.size > 5 * 1024 * 1024
  ) {
    showToast("Chọn ảnh PNG/JPEG không vượt quá 5 MB.", "error");
    input.value = "";
    return;
  }
  uploadingImage.value = true;
  try {
    form.value.thumbnail = (
      await trainingApi.uploadCourseImage(
        file,
        form.value.id,
        form.value.programId,
      )
    ).thumbnail;
  } catch (e) {
    showToast(serverError(e), "error");
  } finally {
    uploadingImage.value = false;
    input.value = "";
  }
}
const formTitle = computed(
  () =>
    `${form.value.id ? "Sửa" : "Tạo"} ${{ domain: "lĩnh vực", program: "chương trình", course: "khóa học", chapter: "chương", lesson: "bài học" }[formKind.value || "course"]}`,
);
function openForm(
  kind: FormKind,
  r?: Partial<Row | TrainingDomain | TrainingProgram>,
) {
  formKind.value = kind;
  const item = r as
    | Partial<Course & Chapter & Lesson & TrainingProgram>
    | undefined;
  form.value = {
    id: item?.id || "",
    code: item?.code || (kind === "lesson" ? item?.id || "" : ""),
    name: item?.name || item?.title || "",
    description: item?.description || item?.hint || "",
    programId: item?.programId || "",
    domainId: item?.domainId || domainId.value,
    status: item?.status || "ACTIVE",
    unitIds: [...(item?.unitIds || [])],
    version: item?.version,
    thumbnail: item?.thumbnail || "",
    businessModule: item?.businessModule || "",
    level: item?.level || "",
    keywords: item?.keywords || "",
    completionDeadline: item?.completionDeadline || "",
  };
}
function closeForm() {
  if (!busy.value && !uploadingImage.value) formKind.value = null;
}
function openRow(r: Row) {
  if (chapter.value) openLesson(r as Lesson);
  else if (course.value) toggleChapter(r.id);
  else router.push(`/admin/courses/${r.id}`);
}
function openLesson(lesson: Lesson) {
  void router.push(`/admin/lessons/${encodeURIComponent(lesson.id)}/edit`);
}
function openLessonForm(target: Chapter) {
  if (busy.value || !canEditCourse.value) return;
  lessonChapter.value = target;
  expandedChapterIds.value = new Set([...expandedChapterIds.value, target.id]);
  openForm("lesson");
}
function editRow(r: Row) {
  if (chapter.value) openRow(r);
  else openForm(course.value ? "chapter" : "course", r);
}
async function save() {
  if (busy.value || uploadingImage.value || !formKind.value) return;
  busy.value = true;
  const f = form.value,
    kind = formKind.value;
  let destination = "";
  try {
    if (kind === "domain")
      await trainingApi.saveDomain({ ...f, id: f.id || undefined });
    else if (kind === "program")
      await trainingApi.saveProgram({ ...f, id: f.id || undefined });
    else if (kind === "course") {
      const saved = await trainingApi.saveCourse({
        ...f,
        id: f.id || undefined,
        iconName: "BookOpen",
        completionDeadline: f.completionDeadline || null,
      });
      destination = `/admin/courses/${saved.id}`;
    } else if (kind === "chapter") {
      const saved = await trainingApi.saveChapter(course.value!.id, {
        ...f,
        id: f.id || undefined,
        title: f.name,
      });
      if (!f.id)
        destination = `/admin/courses/${encodeURIComponent(course.value!.id)}?chapter=${encodeURIComponent(saved.id)}`;
    } else if (kind === "lesson") {
      const saved = await adminApi.createLesson({
        id: f.code,
        title: f.name,
        courseId: course.value!.id,
        chapterId: (lessonChapter.value || chapter.value)!.id,
        hint: f.description,
        videoUrl: "",
        videoSource: "URL",
        documents: [],
        procedureSteps: [],
      });
      destination = `/admin/lessons/${saved.id}/edit`;
    }
    formKind.value = null;
    const label = {
      domain: "lĩnh vực đào tạo",
      program: "chương trình đào tạo",
      course: "khóa học",
      chapter: "chương",
      lesson: "bài học",
    }[kind];
    showToast(`${f.id ? "Cập nhật" : "Tạo"} ${label} thành công.`);
    if (destination && destination !== route.path)
      await router.push(destination);
    else await load();
  } catch (e) {
    showToast(serverError(e), "error");
  } finally {
    busy.value = false;
  }
}
const deleteTarget = ref<Row | null>(null);
const deleteMessage = computed(() => {
  const r = deleteTarget.value;
  if (!r) return "";
  const count = "lessons" in r ? r.lessons.length : 0;
  return `Xóa “${rowName(r)}”? ${count ? `${count} bài học và nội dung trực thuộc cũng sẽ bị xóa. ` : ""}Thao tác không thể hoàn tác.`;
});
const deleteKind = ref<"course" | "chapter" | "lesson">("course");
function askDelete(r: Row, kind?: "course" | "chapter" | "lesson") {
  deleteKind.value =
    kind || (chapter.value ? "lesson" : course.value ? "chapter" : "course");
  deleteTarget.value = r;
}
async function remove() {
  if (!deleteTarget.value) return;
  busy.value = true;
  try {
    const name = rowName(deleteTarget.value);
    if (deleteKind.value === "lesson")
      await trainingApi.deleteLesson(deleteTarget.value.id);
    else if (deleteKind.value === "chapter")
      await trainingApi.deleteChapter(deleteTarget.value.id);
    else await adminApi.deleteCourse(deleteTarget.value.id);
    deleteTarget.value = null;
    showToast(`Đã xóa “${name}”.`);
    await load();
    page.value = Math.min(page.value, pageCount.value);
  } catch (e) {
    deleteTarget.value = null;
    showToast(serverError(e), "error");
  } finally {
    busy.value = false;
  }
}
async function move(row: Row, offset: number) {
  busy.value = true;
  try {
    const ids = rows.value.map((r) => r.id),
      i = ids.indexOf(row.id);
    const [id] = ids.splice(i, 1);
    ids.splice(i + offset, 0, id!);
    if (chapter.value) await trainingApi.reorderLessons(chapter.value.id, ids);
    else await trainingApi.reorderChapters(course.value!.id, ids);
    showToast("Đã cập nhật thứ tự.");
    await load();
  } catch (e) {
    showToast(serverError(e), "error");
  } finally {
    busy.value = false;
  }
}
async function moveLesson(target: Chapter, lesson: Lesson, offset: number) {
  if (busy.value || !canEditCourse.value) return;
  const ids = [...target.lessons]
    .sort(
      (a, b) =>
        (a.lessonOrder ?? 0) - (b.lessonOrder ?? 0) || a.id.localeCompare(b.id),
    )
    .map((item) => item.id);
  const index = ids.indexOf(lesson.id);
  if (index < 0 || index + offset < 0 || index + offset >= ids.length) return;
  const [id] = ids.splice(index, 1);
  ids.splice(index + offset, 0, id!);
  busy.value = true;
  try {
    await trainingApi.reorderLessons(target.id, ids);
    showToast("Đã cập nhật thứ tự bài học trong chương.");
    await load();
  } catch (failure) {
    showToast(serverError(failure), "error");
  } finally {
    busy.value = false;
  }
}
async function changeStatus(status: string) {
  busy.value = true;
  try {
    await trainingApi.setStatus(course.value!.id, status);
    showToast(
      status === "PUBLISHED"
        ? "Đã xuất bản khóa học."
        : status === "ARCHIVED"
          ? "Đã lưu trữ khóa học."
          : "Đã chuyển khóa học về bản nháp.",
    );
    await load();
  } catch (e) {
    showToast(serverError(e), "error");
  } finally {
    busy.value = false;
  }
}
</script>

<style scoped>
.row-edit,
.row-delete {
  white-space: nowrap;
}

.selected-domain td {
  background: var(--primary-light);
}
.catalog-layout {
  grid-template-columns: minmax(310px, 0.8fr) minmax(0, 1.8fr) !important;
}
@media (max-width: 1200px) {
  .catalog-layout {
    grid-template-columns: 1fr !important;
  }
}

.training-admin {
  padding: 2rem;
  min-width: 0;
}
.page-heading,
.section-heading,
.tools,
.table-toolbar,
.pagination,
.row-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}
.page-heading {
  margin-bottom: 1.6rem;
  align-items: flex-start;
}
.page-heading h1 {
  font-size: 1.5rem;
  margin: 0.6rem 0;
}
.page-heading p,
.breadcrumb {
  font-size: 0.85rem;
  color: var(--text-muted);
}
.breadcrumb a {
  color: var(--primary-ink);
}
.section-heading {
  margin-bottom: 1.25rem;
}
.section-heading h2 {
  font-size: 1.125rem;
}
.card {
  padding: 1.5rem;
}
.catalog-layout {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 1.25rem;
}
.domain-row {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem;
  border: 0;
  border-radius: 12px;
  background: var(--surface-ffffff);
  text-align: left;
  color: var(--text-main);
  cursor: pointer;
}
.domain-row.active {
  background: var(--primary-light);
  color: var(--primary-ink);
}
small {
  display: block;
  color: var(--text-muted);
  font-size: 0.75rem;
  margin-top: 0.35rem;
}
.search-box {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex: 1;
  min-width: 160px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 0.6rem 0.8rem;
  color: var(--text-muted);
  background: var(--surface-ffffff);
}
.search-box input {
  width: 100%;
  border: 0;
  outline: 0;
}
.table-toolbar {
  margin-bottom: 1.5rem;
  flex-wrap: wrap;
}
.table-toolbar select {
  padding: 0.6rem;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-main);
  background: var(--surface-ffffff);
}
.table-scroll {
  overflow-x: auto;
}
.admin-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
}
.admin-table th {
  background: var(--bg-main);
  color: var(--text-muted);
  font-size: 0.75rem;
  text-align: left;
  padding: 1rem;
}
.admin-table td {
  padding: 1rem;
  border-bottom: 1px solid var(--border-color);
  vertical-align: middle;
}
.name-link {
  background: none;
  border: 0;
  color: var(--primary-ink);
  font-weight: 700;
  cursor: pointer;
  text-align: left;
  font-size: inherit;
}
.row-actions {
  gap: 0.45rem;
  justify-content: flex-start;
  white-space: nowrap;
}
.row-edit,
.row-delete,
.order-button {
  padding: 0.4rem 0.7rem;
  border: 1px solid var(--primary-color);
  border-radius: 6px;
  background: var(--surface-ffffff);
  color: var(--primary-ink);
  cursor: pointer;
}
.row-delete {
  border-color: var(--line-f0b7b7);
  color: var(--ink-b91c1c);
}
.order-button {
  padding: 0.3rem;
  color: var(--text-muted);
  border-color: var(--border-color);
}
button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.status {
  padding: 0.35rem 0.6rem;
  border-radius: 6px;
  font-size: 0.75rem;
  background: var(--primary-light);
  color: var(--primary-ink);
  white-space: nowrap;
}
.status.amber {
  background: var(--surface-fff5de);
  color: var(--ink-805b00);
}
.empty {
  padding: 2rem;
  text-align: center;
  color: var(--text-muted);
}
.empty svg {
  margin: auto;
}
.pagination {
  padding-top: 1.25rem;
  font-size: 0.8rem;
  color: var(--text-muted);
}
.pagination button {
  padding: 0.4rem 0.75rem;
  margin-left: 0.5rem;
  border: 1px solid var(--border-color);
  background: var(--surface-ffffff);
  border-radius: 6px;
}
.notice {
  padding: 0.8rem 1rem;
  background: var(--primary-light);
  color: var(--primary-ink);
  border-radius: 8px;
  margin-bottom: 1rem;
  display: flex;
  justify-content: space-between;
}
.notice.error,
.error {
  color: var(--ink-b91c1c);
  background: var(--surface-fff1f2);
}
.notice button,
.training-modal header button {
  border: 0;
  background: none;
  cursor: pointer;
}
.training-modal-overlay {
  position: fixed;
  inset: 0;
  background: #0f172a66;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.5rem;
}
.training-modal {
  width: 540px;
  max-height: 90vh;
  overflow-y: auto;
}
.training-modal header,
.training-modal footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
}
.training-modal h2 {
  font-size: 1.25rem;
}
.training-modal p {
  font-size: 0.85rem;
  color: var(--text-muted);
  margin: 1rem 0;
}
.training-modal label {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin: 1rem 0;
  font-size: 0.875rem;
  font-weight: 600;
}
.training-modal input,
.training-modal textarea,
.training-modal select {
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 0.7rem;
  width: 100%;
  font-weight: 400;
}
.training-modal footer {
  justify-content: flex-end;
  margin-top: 1.5rem;
}
@media (max-width: 1000px) {
  .catalog-layout {
    grid-template-columns: 1fr;
  }
  .page-heading {
    flex-direction: column;
  }
  .training-admin {
    padding: 1rem;
  }
  .tools {
    flex-wrap: wrap;
  }
}
/* Keep the catalog panels within the available admin workspace. */
.training-admin {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: auto;
}
.page-heading,
.notice {
  flex-shrink: 0;
}
.catalog-layout {
  flex: 1;
  min-height: 0;
  grid-template-columns: minmax(240px, 280px) minmax(0, 1fr);
}
.catalog-layout > .card,
.list-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}
.list-card {
  flex: 1;
}
.section-heading,
.table-toolbar,
.pagination {
  flex-shrink: 0;
}
.catalog-layout .search-box {
  flex: 0 0 auto;
  min-width: 0;
}
.search-box input {
  min-width: 0;
}
.table-scroll,
.domain-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  overscroll-behavior: contain;
}
.admin-table th {
  position: sticky;
  top: 0;
  z-index: 1;
}
.admin-table td {
  overflow-wrap: anywhere;
}
.pagination {
  margin-top: auto;
  border-top: 1px solid var(--border-color);
  gap: 0.75rem;
  flex-wrap: nowrap;
  min-height: 57px;
}
.pagination > span {
  min-width: 0;
  overflow-wrap: anywhere;
}
.pagination > div {
  display: flex;
  gap: 0.5rem;
  flex-shrink: 0;
}
.pagination button {
  display: grid;
  place-items: center;
  margin: 0;
  padding: 0;
  width: 36px;
  height: 36px;
  cursor: pointer;
  color: var(--primary-ink);
}
.pagination-controls {
  align-items: center;
}
.page-size-control {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  white-space: nowrap;
  font-size: 0.8rem;
  color: var(--text-muted);
}
.page-size-control select {
  min-height: 36px;
  padding: 0.35rem 0.55rem;
  border: 1px solid var(--border-color);
  border-radius: 6px;
  background: var(--surface-ffffff);
  color: var(--text-main);
  font: inherit;
  cursor: pointer;
}
@media (max-width: 600px) {
  .list-pagination {
    flex-wrap: wrap;
  }
  .list-pagination .pagination-controls {
    margin-left: auto;
  }
}
.domain-pagination {
  align-items: center;
  flex-direction: row;
  font-size: 0.75rem;
  gap: 0.5rem;
}
.domain-item {
  display: flex;
  align-items: center;
  border-radius: 12px;
  margin-bottom: 0.5rem;
}
.domain-item.active {
  background: var(--primary-light);
  color: var(--primary-ink);
}
.domain-row {
  flex: 1;
  min-width: 0;
  background: transparent;
  color: inherit;
  overflow-wrap: anywhere;
}
.domain-edit {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  margin-right: 0.5rem;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--primary-ink);
  cursor: pointer;
}
.domain-item:hover {
  background: var(--primary-light);
}
.row-edit:hover:not(:disabled),
.order-button:hover:not(:disabled),
.pagination button:hover:not(:disabled),
.domain-edit:hover {
  background: var(--primary-light);
  border-color: var(--primary-color);
}
.row-delete:hover:not(:disabled) {
  background: var(--surface-fff1f2);
  border-color: #b91c1c;
}
.name-link:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}
.section-heading h2 {
  min-width: 0;
  overflow-wrap: anywhere;
}
.section-heading .btn {
  flex-shrink: 0;
}
@media (max-width: 1000px) {
  .training-admin {
    height: auto;
    min-height: 100%;
    overflow: visible;
  }
  .catalog-layout {
    grid-template-columns: 1fr;
    flex: none;
  }
  .catalog-layout > .card {
    min-height: 360px;
    max-height: 600px;
  }
  .catalog-layout > .catalog-domains {
    min-height: 260px;
    max-height: 420px;
  }
  .domain-pagination {
    flex-direction: row;
    align-items: center;
  }
  .list-card {
    min-height: 420px;
    max-height: 800px;
    flex: none;
  }
}
@media (max-width: 560px) {
  .card {
    padding: 1rem;
  }
  .section-heading {
    align-items: flex-start;
    flex-wrap: wrap;
  }
  .table-toolbar {
    align-items: stretch;
    gap: 0.75rem;
  }
  .table-toolbar .search-box,
  .table-toolbar select,
  .table-toolbar > .btn {
    width: 100%;
    min-width: 0;
  }
  .admin-table {
    min-width: 480px;
  }
  .tools {
    width: 100%;
    gap: 0.5rem;
  }
  .tools .btn {
    flex: 1;
    padding: 0.65rem 0.75rem;
  }
  .training-modal-overlay {
    padding: 0.75rem;
  }
  .training-modal {
    width: 100%;
    max-height: calc(100dvh - 1.5rem);
  }
}
.readonly-notice {
  padding: 0.75rem 1rem;
  margin-bottom: 1rem;
  border-radius: 8px;
  background: var(--surface-fff5de);
  color: var(--ink-805b00);
  flex-shrink: 0;
}
.readonly-label {
  color: var(--text-muted);
  font-size: 0.8rem;
}
.heading-with-back {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  min-width: 0;
}
.heading-copy {
  min-width: 0;
}
.heading-copy h1,
.heading-copy .breadcrumb {
  overflow-wrap: anywhere;
}
@media (max-width: 560px) {
  .heading-with-back {
    gap: 0.75rem;
  }
}
</style>
