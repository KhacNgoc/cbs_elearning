<template>
  <div class="admin-portal-layout">
    <div class="admin-main-content">
      <SkeletonLoader v-if="loading" label="Đang tải dữ liệu cấu hình">
        <SkeletonLoader plain variant="stats" :rows="4" />
        <SkeletonLoader plain variant="table" :rows="6" :columns="5" />
      </SkeletonLoader>
      <div v-else class="admin-tab-content">
        <div class="tab-pane rag-tab-layout animate-fade-in">
          <!-- KHỐI 1: TẢI LÊN TÀI LIỆU (UPLOAD CARD) -->
          <div class="card rag-upload-card">
            <div class="card-header-row">
              <div class="header-left">
                <h3 class="card-title">TẢI LÊN TÀI LIỆU AI</h3>
              </div>
            </div>

            <div class="upload-controls-flow">
              <!-- Course Select Row -->
              <div class="course-picker-row">
                <label class="form-label">
                  Khóa học tiếp nhận tài liệu <span class="required-mark">*</span>:
                </label>
                <div ref="coursePickerRef" class="searchable-course-picker">
                  <button
                    type="button"
                    class="form-control course-select-trigger"
                    :class="{ 'is-open': courseDropdownOpen }"
                    @click="toggleCourseDropdown"
                  >
                    <span class="selected-course-label">
                      {{ selectedCourseName || "Chọn khóa học..." }}
                    </span>
                    <ChevronDown :size="16" class="dropdown-chevron" :class="{ rotated: courseDropdownOpen }" />
                  </button>
                  <div v-if="courseDropdownOpen" class="course-dropdown-popover" @click.stop>
                    <div class="course-dropdown-search">
                      <Search :size="15" />
                      <input
                        ref="courseSearchInputRef"
                        v-model="courseSearchQuery"
                        type="text"
                        placeholder="Gõ tên khóa học để tìm nhanh..."
                        @input="onCourseSearchInput"
                      />
                      <LoaderCircle v-if="courseSearchLoading" :size="14" class="spin-icon" />
                      <button
                        v-else-if="courseSearchQuery"
                        type="button"
                        class="btn-clear-search"
                        title="Xóa từ khóa"
                        @click="clearCourseSearch"
                      >
                        <X :size="13" />
                      </button>
                    </div>
                    <div class="course-dropdown-list">
                      <button
                        v-for="course in displayedCourseOptions"
                        :key="course.id"
                        type="button"
                        class="course-dropdown-option"
                        :class="{ 'is-selected': course.id === selectedCourseId }"
                        @click="selectCourseOption(course)"
                      >
                        <span class="course-option-name">{{ course.name }}</span>
                      </button>
                      <div v-if="displayedCourseOptions.length === 0 && !courseSearchLoading" class="course-dropdown-empty">
                        Không tìm thấy khóa học phù hợp.
                      </div>
                      <div
                        v-else-if="!courseSearchQuery && displayedCourseOptions.length >= COURSE_PICKER_LIMIT"
                        class="course-dropdown-empty"
                      >
                        Gõ tên hoặc mã để tìm các khóa học khác.
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Dropzone Upload Area -->
              <div
                v-if="allowedCourseAction('documents', 'create')"
                class="rag-dropzone-box"
                :class="{
                  'is-dragover': isDraggingOver,
                  'is-uploading': documentUploading,
                  'is-disabled': !selectedCourseId || selectedCourseId === ALL_COURSES
                }"
                @dragover.prevent="onDragOver"
                @dragleave.prevent="onDragLeave"
                @drop.prevent="onFileDrop"
                @click="triggerFileInput"
              >
                <input
                  ref="ragFileInput"
                  type="file"
                  hidden
                  accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                  @change="uploadDocument"
                />
                <div class="dropzone-icon-circle">
                  <LoaderCircle v-if="documentUploading" :size="24" class="spin-icon" />
                  <Upload v-else :size="24" />
                </div>
                <div class="dropzone-text-group">
                  <p class="dropzone-main-text">
                    <span v-if="documentUploading">Đang tải lên và xử lý embedding tài liệu...</span>
                    <span v-else>
                      <strong>Kéo thả tệp PDF, DOCX vào đây</strong> hoặc <span class="upload-link-highlight">nhấn để chọn tệp</span>
                    </span>
                  </p>
                  <span class="dropzone-hint-text">
                    Hỗ trợ PDF, DOCX (tối đa theo cấu hình máy chủ).
                  </span>
                </div>
                <button
                  type="button"
                  class="btn btn-primary btn-sm dropzone-btn"
                  :disabled="documentUploading || !selectedCourseId || selectedCourseId === ALL_COURSES"
                  @click.stop="triggerFileInput"
                >
                  <LoaderCircle v-if="documentUploading" :size="15" class="spin-icon" />
                  <Upload v-else :size="15" />
                  {{ documentUploading ? "ĐANG TẢI LÊN..." : "CHỌN TỆP" }}
                </button>
              </div>
            </div>
          </div><!-- KHỐI 2: DANH SÁCH & TÌM KIẾM TÀI LIỆU (DIRECTORY/SEARCH CARD) -->
          <div class="card rag-directory-card">
            <div class="card-header-row">
              <div class="header-left">
                <div class="title-with-badge">
                  <h3 class="card-title">DANH SÁCH TÀI LIỆU THEO KHÓA HỌC</h3>
                  <span class="rag-count-pill">{{ documentStats.total }} tài liệu</span>
                </div>
              </div>
              <div class="header-actions">
                <button
                  type="button"
                  class="btn btn-secondary btn-sm"
                  :disabled="documentListLoading"
                  title="Làm mới danh sách"
                  @click="loadDocuments()"
                >
                  <RefreshCw
                    :size="15"
                    :class="{ 'spin-icon': documentListLoading }"
                  />
                  <span>LÀM MỚI</span>
                </button>
              </div>
            </div>

            <!-- Toolbar: Search & Status Filter -->
            <div class="admin-table-toolbar rag-toolbar">
              <div class="admin-search-box rag-search">
                <Search :size="16" />
                <input
                  v-model="documentQuery"
                  type="search"
                  placeholder="Tìm kiếm tài liệu theo tên tệp..."
                  aria-label="Tìm tài liệu AI"
                />
                <button
                  v-if="documentQuery"
                  type="button"
                  class="btn-clear-search"
                  title="Xóa tìm kiếm"
                  @click="documentQuery = ''"
                >
                  <X :size="14" />
                </button>
              </div>

              <div class="filter-controls">
                <select
                  v-model="documentStatusFilter"
                  class="form-control select-control status-select"
                  aria-label="Lọc trạng thái"
                >
                  <option value="ALL">Tất cả trạng thái ({{ documentStats.total }})</option>
                  <option value="INDEXED">Đã index ({{ documentStats.indexed }})</option>
                  <option value="INDEXING">Đang embedding ({{ documentStats.indexing }})</option>
                  <option value="FAILED">Thất bại ({{ documentStats.failed }})</option>
                </select>
              </div>
            </div>

            <!-- Data Table -->
            <div class="admin-table-scroll">
              <table class="admin-table rag-table">
                <colgroup>
                  <col style="width: 36%" />
                  <col style="width: 12%" />
                  <col style="width: 15%" />
                  <col style="width: 10%" />
                  <col style="width: 15%" />
                  <col style="width: 12%" />
                </colgroup>
                <thead>
                  <tr>
                    <th>Tên tệp tài liệu</th>
                    <th>Dung lượng</th>
                    <th>Trạng thái</th>
                    <th>Chunks</th>
                    <th>Thời gian cập nhật</th>
                    <th class="actions-col text-center">Thao tác</th>
                  </tr>
                </thead>
                <tbody>
                  <!-- Loading State -->
                  <SkeletonTableRows
                    v-if="documentListLoading && courseDocuments.length === 0"
                    :columns="6"
                    :rows="5"
                    label="Đang tải danh sách tài liệu"
                  />

                  <!-- Data Rows -->
                  <tr v-for="document in courseDocuments" :key="document.id" style="text-align: center;">
                    <td>
                      <div class="doc-file-item">
                        <FileText :size="17" class="doc-file-icon" />
                        <div class="doc-file-details">
                          <strong>{{ document.fileName }}</strong>
                          <span
                            v-if="document.status === 'FAILED' && document.errorMessage"
                            class="doc-error-text"
                            :title="document.errorMessage"
                          >
                            {{ document.errorMessage }}
                          </span>
                        </div>
                      </div>
                    </td>
                    <td>{{ formatFileSize(document.sizeBytes) }}</td>
                    <td>
                      <span
                        class="rag-status"
                        :class="`status-${document.status.toLowerCase()}`"
                      >
                        {{ documentStatusLabel(document.status) }}
                      </span>
                    </td>
                    <td>{{ document.indexedChunks ?? "-" }}</td>
                    <td>
                      {{ formatDocumentDate(document.indexedAt || document.createdAt) }}
                    </td>
                    <td class="actions-col text-center">
                      <button
                        v-if="allowedCourseAction('documents', 'delete')"
                        type="button"
                        class="action-btn delete"
                        :disabled="
                          document.status === 'PENDING' ||
                          document.status === 'INDEXING' ||
                          confirmationModal.submitting
                        "
                        :title="
                          document.status === 'PENDING' ||
                          document.status === 'INDEXING'
                            ? 'Chờ embedding hoàn tất trước khi xóa'
                            : 'Xóa tài liệu và dữ liệu embedding'
                        "
                        @click="deleteCourseDocument(document)"
                      >
                        <Trash2 />
                      </button>
                    </td>
                  </tr>

                  <!-- Empty State -->
                  <tr v-if="!documentListLoading && documentStats.total === 0 && courseDocuments.length === 0">
                    <td colspan="6" class="admin-empty">
                      <FileText :size="32" class="empty-icon" style="opacity: 0.5; margin-bottom: 0.5rem;" />
                      <p>Khóa học này chưa có tài liệu tri thức nào.</p>
                      <small>Sử dụng khung bên trên để tải lên tệp PDF hoặc Word.</small>
                    </td>
                  </tr>

                  <tr v-if="!documentListLoading && documentStats.total > 0 && courseDocuments.length === 0">
                    <td colspan="6" class="admin-empty">
                      Không tìm thấy tài liệu phù hợp với bộ lọc tìm kiếm.
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <TablePagination
              :page="documentPage"
              :page-size="documentPageSize"
              :total="documentTotalElements"
              :page-count="documentPageCount"
              unit="tài liệu"
              unit-label="Tài liệu"
              @update:page="onDocumentPageChange"
              @update:page-size="onDocumentPageSizeChange"
            />
          </div>
        </div>
      </div>
    </div>

    <ConfirmModal
      :open="confirmationModal.show"
      :title="confirmationModal.title"
      :message="confirmationModal.message"
      :confirm-label="confirmationModal.confirmLabel"
      :loading="confirmationModal.submitting"
      @cancel="closeConfirmationModal"
      @confirm="confirmModalAction"
    />
  </div>
</template>

<script setup lang="ts">
// "Tài liệu AI" admin page (/admin?tab=documents): course documents indexed for the learning assistant.
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import {
  ChevronDown,
  FileText,
  LoaderCircle,
  RefreshCw,
  Search,
  Trash2,
  Upload,
  X,
} from "lucide-vue-next";
import SkeletonLoader from "../components/SkeletonLoader.vue";
import SkeletonTableRows from "../components/SkeletonTableRows.vue";
import TablePagination from "../components/TablePagination.vue";
import ConfirmModal from "../components/ConfirmModal.vue";
import { adminApi } from "../api/adminApi";
import type { CourseDocument, CourseSearchOption } from "../api/adminApi";
import { showToast } from "../utils/toast";

// The picker lists the first courses by name and finds the rest by search,
// instead of downloading every course with all its lessons and their content.
const COURSE_PICKER_LIMIT = 50;
// No course is selected until the course list arrives.
const ALL_COURSES = "__ALL__";

const loading = ref(false);
// Courses known to the picker: the first page plus any course picked from a search.
const courses = ref<CourseSearchOption[]>([]);
const selectedCourseId = ref(ALL_COURSES);
const allowedCourseAction = (page: string, action: string) =>
  courses.value
    .find((c) => c.id === selectedCourseId.value)
    ?.adminActions?.[page]?.includes(action) === true;

const undeletedCourses = computed(() =>
  [...courses.value]
    .filter((c) => c.status !== "DELETED" && !c.code?.includes("_DEL_"))
    .sort((a, b) => a.name.localeCompare(b.name, "vi")),
);

// Course picker
const coursePickerRef = ref<HTMLElement | null>(null);
const courseSearchInputRef = ref<HTMLInputElement | null>(null);
const courseDropdownOpen = ref(false);
const courseSearchQuery = ref("");
const courseSearchLoading = ref(false);
const searchedCourseOptions = ref<CourseSearchOption[] | null>(null);
let courseSearchTimer: ReturnType<typeof setTimeout> | null = null;

const selectedCourseName = computed(() => {
  const found =
    undeletedCourses.value.find((c) => c.id === selectedCourseId.value) ||
    searchedCourseOptions.value?.find((c) => c.id === selectedCourseId.value);
  return found ? found.name : "";
});

const displayedCourseOptions = computed(() => {
  if (searchedCourseOptions.value !== null) {
    return searchedCourseOptions.value;
  }
  return undeletedCourses.value;
});

const toggleCourseDropdown = () => {
  courseDropdownOpen.value = !courseDropdownOpen.value;
  if (courseDropdownOpen.value) {
    void nextTick(() => {
      courseSearchInputRef.value?.focus();
    });
  }
};

const onCourseSearchInput = () => {
  if (courseSearchTimer) clearTimeout(courseSearchTimer);
  const q = courseSearchQuery.value.trim();
  if (!q) {
    searchedCourseOptions.value = null;
    courseSearchLoading.value = false;
    return;
  }
  courseSearchLoading.value = true;
  courseSearchTimer = setTimeout(async () => {
    try {
      searchedCourseOptions.value = await adminApi.searchCourses(q, COURSE_PICKER_LIMIT, "documents");
    } catch (err) {
      console.error("Failed to search courses:", err);
    } finally {
      courseSearchLoading.value = false;
    }
  }, 250);
};

const clearCourseSearch = () => {
  courseSearchQuery.value = "";
  searchedCourseOptions.value = null;
  courseSearchLoading.value = false;
  void nextTick(() => {
    courseSearchInputRef.value?.focus();
  });
};

const selectCourseOption = (course: CourseSearchOption) => {
  courseDropdownOpen.value = false;
  // A course found by search joins the known courses, so its documents load and its actions apply.
  if (!courses.value.some((item) => item.id === course.id)) {
    courses.value = [...courses.value, course];
  }
  if (selectedCourseId.value !== course.id) {
    selectedCourseId.value = course.id;
  }
};

const handleOutsideCourseDropdownClick = (event: MouseEvent) => {
  if (
    courseDropdownOpen.value &&
    coursePickerRef.value &&
    !coursePickerRef.value.contains(event.target as Node)
  ) {
    courseDropdownOpen.value = false;
  }
};

// Documents of the selected course
const courseDocuments = ref<CourseDocument[]>([]);
const documentUploading = ref(false);
const documentListLoading = ref(false);
const ragFileInput = ref<HTMLInputElement | null>(null);
const isDraggingOver = ref(false);
const documentQuery = ref("");
const documentStatusFilter = ref<"ALL" | "INDEXED" | "INDEXING" | "FAILED">("ALL");
let documentPollingTimer: ReturnType<typeof setTimeout> | null = null;
let disposed = false;

const documentStats = ref({
  total: 0,
  indexed: 0,
  indexing: 0,
  failed: 0,
});
const documentPage = ref(1);
const documentPageSize = ref(10);
const documentTotalElements = ref(0);
const documentPageCount = ref(1);
const resetDocuments = () => {
  documentPage.value = 1;
};

let documentFetchDebounceTimer: ReturnType<typeof setTimeout> | null = null;
const scheduleLoadDocuments = (delayMs = 0) => {
  if (documentFetchDebounceTimer) clearTimeout(documentFetchDebounceTimer);
  documentFetchDebounceTimer = setTimeout(() => {
    documentFetchDebounceTimer = null;
    if (!disposed) void loadDocuments();
  }, delayMs);
};

const onDocumentPageChange = (newPage: number) => {
  if (documentPage.value === newPage) return;
  documentPage.value = newPage;
  scheduleLoadDocuments(0);
};

const onDocumentPageSizeChange = (newSize: number) => {
  documentPageSize.value = newSize;
  documentPage.value = 1;
  scheduleLoadDocuments(0);
};

watch(documentQuery, () => {
  resetDocuments();
  scheduleLoadDocuments(300);
});

watch(documentStatusFilter, () => {
  resetDocuments();
  scheduleLoadDocuments(0);
});

watch(selectedCourseId, () => {
  resetDocuments();
  scheduleLoadDocuments(0);
});

const clearDocumentPolling = () => {
  if (documentPollingTimer) clearTimeout(documentPollingTimer);
  documentPollingTimer = null;
};

const loadDocuments = async (silent = false) => {
  clearDocumentPolling();
  if (!selectedCourseId.value || selectedCourseId.value === ALL_COURSES || !undeletedCourses.value.some((c) => c.id === selectedCourseId.value)) {
    if (undeletedCourses.value.length > 0) {
      selectedCourseId.value = undeletedCourses.value[0].id;
    } else {
      courseDocuments.value = [];
      documentTotalElements.value = 0;
      documentPageCount.value = 1;
      documentStats.value = { total: 0, indexed: 0, indexing: 0, failed: 0 };
      return;
    }
  }
  if (!silent) documentListLoading.value = true;
  try {
    const res = await adminApi.getCourseDocumentsPaged(selectedCourseId.value, {
      search: documentQuery.value.trim() || undefined,
      status: documentStatusFilter.value,
      page: Math.max(0, documentPage.value - 1),
      size: documentPageSize.value,
    });
    courseDocuments.value = res.items || [];
    documentTotalElements.value = res.totalElements ?? 0;
    documentPageCount.value = Math.max(1, res.totalPages ?? 1);
    if (res.stats) {
      documentStats.value = {
        total: res.stats.total ?? 0,
        indexed: res.stats.indexed ?? 0,
        indexing: res.stats.indexing ?? 0,
        failed: res.stats.failed ?? 0,
      };
    }
    if ((res.stats && res.stats.indexing > 0) || courseDocuments.value.some((d) => ["PENDING", "INDEXING"].includes(d.status))) {
      documentPollingTimer = setTimeout(() => {
        if (!disposed) void loadDocuments(true);
      }, 2500);
    }
  } catch (error) {
    console.error("Failed to load course documents:", error);
    showToast("Không thể tải trạng thái tài liệu.", "error");
  } finally {
    documentListLoading.value = false;
  }
};

const triggerFileInput = () => {
  if (
    ragFileInput.value &&
    !documentUploading.value &&
    selectedCourseId.value &&
    selectedCourseId.value !== ALL_COURSES
  ) {
    ragFileInput.value.click();
  }
};

const processUploadFile = async (file: File) => {
  if (
    !file ||
    !selectedCourseId.value ||
    selectedCourseId.value === ALL_COURSES
  ) {
    showToast("Vui lòng chọn một khóa học cụ thể để tải tài liệu.", "warning");
    return;
  }
  const extension = file.name.split(".").pop()?.toLowerCase();
  if (!extension || !["pdf", "docx"].includes(extension)) {
    showToast("Chỉ hỗ trợ tệp PDF hoặc DOCX.", "warning");
    return;
  }
  documentUploading.value = true;
  try {
    await adminApi.uploadCourseDocument(selectedCourseId.value, file);
    showToast("Đã nhận tài liệu. Hệ thống đang xử lý nội dung.", "success");
    await loadDocuments();
  } catch (error) {
    showToast("Tải hoặc xử lý tài liệu thất bại. Vui lòng thử lại.", "error");
    console.error("Failed to upload course document:", error);
  } finally {
    documentUploading.value = false;
    if (ragFileInput.value) {
      ragFileInput.value.value = "";
    }
  }
};

const uploadDocument = async (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (file) {
    await processUploadFile(file);
  }
};

const onDragOver = (e: DragEvent) => {
  e.preventDefault();
  if (!selectedCourseId.value || selectedCourseId.value === ALL_COURSES) return;
  isDraggingOver.value = true;
};

const onDragLeave = (e: DragEvent) => {
  e.preventDefault();
  isDraggingOver.value = false;
};

const onFileDrop = async (e: DragEvent) => {
  e.preventDefault();
  isDraggingOver.value = false;
  if (!selectedCourseId.value || selectedCourseId.value === ALL_COURSES) {
    showToast("Vui lòng chọn khóa học trước khi kéo thả tệp.", "warning");
    return;
  }
  const file = e.dataTransfer?.files?.[0];
  if (file) {
    await processUploadFile(file);
  }
};

const documentStatusLabel = (status: CourseDocument["status"]) =>
  ({
    PENDING: "Đang chờ",
    INDEXING: "Đang embedding",
    INDEXED: "Đã index",
    FAILED: "Thất bại",
  })[status];

const formatDocumentDate = (value?: string) =>
  value
    ? new Intl.DateTimeFormat("vi-VN", {
        dateStyle: "short",
        timeStyle: "short",
      }).format(new Date(value))
    : "-";

const formatFileSize = (size?: number) => {
  if (!size) return "";
  return size < 1024 * 1024
    ? `${Math.ceil(size / 1024)} KB`
    : `${(size / 1024 / 1024).toFixed(1)} MB`;
};

// Delete confirmation
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

const deleteCourseDocument = (document: CourseDocument) => {
  const courseId = selectedCourseId.value;
  const courseName =
    courses.value.find((course) => course.id === courseId)?.name ||
    courseId;
  openConfirmationModal(
    "Xóa tài liệu tri thức AI",
    `Bạn có thực sự muốn xóa tài liệu “${document.fileName}” khỏi khóa “${courseName}”? Tệp, dữ liệu embedding và liên kết nguồn của bài học sẽ bị xóa. Hành động này không thể hoàn tác.`,
    async () => {
      try {
        await adminApi.deleteCourseDocument(courseId, document.id);
        if (selectedCourseId.value === courseId) {
          if (courseDocuments.value.length <= 1 && documentPage.value > 1) {
            documentPage.value -= 1;
          }
          await loadDocuments();
        }
        showToast("Đã xóa tài liệu và dữ liệu embedding.");
      } catch (error: any) {
        showToast(
          error?.response?.data?.message ||
            "Không thể xóa tài liệu. Vui lòng thử lại.",
          "error",
        );
        throw error;
      }
    },
  );
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

async function loadCourses() {
  loading.value = true;
  try {
    courses.value = await adminApi.searchCourses(undefined, COURSE_PICKER_LIMIT, "documents");
    // Selecting the first course loads its documents through the selectedCourseId watcher.
    if (undeletedCourses.value.length > 0) selectedCourseId.value = undeletedCourses.value[0].id;
    else await loadDocuments();
  } catch (error) {
    console.error("Failed to load admin panel data:", error);
    showToast("Không thể tải dữ liệu quản trị. Vui lòng thử lại.", "error");
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void loadCourses();
  window.addEventListener("click", handleOutsideCourseDropdownClick);
});

onBeforeUnmount(() => {
  disposed = true;
  clearDocumentPolling();
  if (documentFetchDebounceTimer) clearTimeout(documentFetchDebounceTimer);
  if (courseSearchTimer) clearTimeout(courseSearchTimer);
  window.removeEventListener("click", handleOutsideCourseDropdownClick);
});
</script>

<style scoped>
.admin-table {
  font-size: 0.875rem;
}
.admin-table th {
  text-transform: none !important;
  font-size: 0.75rem;
  white-space: nowrap;
}
.card-title {
  text-transform: none !important;
}
.admin-table-scroll {
  width: 100%;
  overflow-x: auto;
}
.admin-table small {
  display: block;
  margin-top: 0.35rem;
  color: var(--text-muted);
  font-size: 0.75rem;
}

/* Redesigned Sidebar Layout */
.admin-portal-layout {
  display: flex;
  min-height: 100%;
  background-color: var(--surface-ffffff);
  font-family: var(--font-family);
}

/* Sidebar styling */

/* Main Content area */
.admin-main-content {
  flex: 1;
  min-width: 0;
  padding: 2.2rem;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 1.8rem;
}

/* Stats Cards Grid */

/* Charts Rows styling */

/* Real-time Sessions list Card */

/* Tables & CRUD styling */
.card-header-row {
  width: 100%;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 2rem;
  margin-bottom: 1.25rem;
}

.card-header-row .header-left {
  flex: 1;
  min-width: 0;
}

.card-header-row .card-title {
  margin: 0;
  line-height: 1.35;
}

.card-header-row > .btn {
  flex: 0 0 auto;
  margin-left: auto;
}

.admin-table {
  width: 100%;
  border-collapse: collapse;
  margin-top: 1rem;
}

.admin-table th {
  padding: 1rem;
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--text-muted);
  border-bottom: 2px solid var(--border-color);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.admin-table td {
  padding: 1rem;
  border-bottom: 1px solid var(--border-color);
  font-size: 0.85rem;
}

.admin-table tr:hover td {
  background-color: var(--bg-main);
}

.actions-col {
  text-align: right;
  width: 110px;
}

.action-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 0.35rem;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  transition: var(--transition);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.action-btn:hover {
  background-color: var(--bg-main);
  color: var(--primary-ink);
}

.action-btn.delete:hover {
  background-color: var(--surface-fee2e2);
  color: var(--ink-ef4444);
}

.action-btn svg {
  width: 16px;
  height: 16px;
}

/* User avatar helper */

/* Modal details */

.form-control {
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  padding: 0.6rem 0.8rem;
  font-size: 0.85rem;
  color: var(--text-main);
  background-color: var(--surface-ffffff);
  transition: var(--transition);
}

.form-control:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px var(--primary-light);
}

.required-mark {
  color: var(--ink-dc2626);
}

@media (max-width: 700px) {

  .card-header-row {
    align-items: stretch;
    flex-direction: column;
    gap: 1rem;
  }

  .card-header-row > .btn {
    width: 100%;
    justify-content: center;
    margin-left: 0;
  }
}

/* RAG 2-Block Layout */
.rag-tab-layout {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

/* KHỐI 1: Upload Card */
.rag-upload-card {
  padding: 1.5rem 1.8rem;
  background: var(--surface-ffffff);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.rag-upload-card .card-header-row {
  margin-bottom: 1.25rem;
}

.upload-controls-flow {
  display: flex;
  flex-direction: column;
  gap: 1.15rem;
}

.course-picker-row {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.course-picker-row .form-label {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 0;
  white-space: nowrap;
}

.searchable-course-picker {
  position: relative;
  flex: 1;
  min-width: 280px;
}

.course-select-trigger {
  width: 100%;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0 0.85rem;
  text-align: left;
  cursor: pointer;
  background: var(--bg-input, var(--bg-surface));
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm, 8px);
  color: var(--text-main);
  font-size: 0.875rem;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.course-select-trigger:hover,
.course-select-trigger.is-open {
  border-color: var(--primary-color);
}

.selected-course-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.dropdown-chevron {
  flex-shrink: 0;
  color: var(--text-muted);
  transition: transform 0.15s ease;
}

.dropdown-chevron.rotated {
  transform: rotate(180deg);
}

.course-dropdown-popover {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  z-index: 60;
  /* --bg-card follows the light and dark themes; --bg-surface is not defined anywhere. */
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.28);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.course-dropdown-search {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.55rem 0.75rem;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-subtle, rgba(15, 23, 42, 0.4));
  color: var(--text-muted);
}

.course-dropdown-search input {
  flex: 1;
  border: none;
  background: transparent;
  color: var(--text-main);
  font-size: 0.84rem;
  outline: none;
}

.course-dropdown-list {
  max-height: 240px;
  overflow-y: auto;
  padding: 0.3rem;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.course-dropdown-option {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 0.5rem 0.65rem;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-main);
  font-size: 0.85rem;
  text-align: left;
  cursor: pointer;
  transition: background-color 0.12s ease, color 0.12s ease;
}

.course-dropdown-option:hover {
  background: var(--primary-light, rgba(16, 185, 129, 0.12));
  color: var(--primary-ink, #10b981);
}

.course-dropdown-option.is-selected {
  background: var(--primary-light, rgba(16, 185, 129, 0.18));
  color: var(--primary-ink, #10b981);
  font-weight: 600;
}

.course-option-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-dropdown-empty {
  padding: 1rem;
  text-align: center;
  color: var(--text-muted);
  font-size: 0.82rem;
}

.rag-dropzone-box {
  display: flex;
  align-items: center;
  gap: 1.25rem;
  padding: 1.15rem 1.6rem;
  border: 1.5px dashed var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-subtle, #f8fafc);
  cursor: pointer;
  transition: all 0.2s ease;
}

.rag-dropzone-box:hover,
.rag-dropzone-box.is-dragover {
  border-color: var(--primary);
  background: rgba(0, 91, 65, 0.04);
}

.rag-dropzone-box.is-disabled {
  opacity: 0.6;
  cursor: not-allowed;
  border-color: var(--border-color);
  background: var(--bg-subtle, #f8fafc);
}

.dropzone-icon-circle {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: rgba(0, 91, 65, 0.08);
  color: var(--primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.dropzone-text-group {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.dropzone-main-text {
  font-size: 0.875rem;
  color: var(--text-main);
  margin: 0;
}

.upload-link-highlight {
  color: var(--primary);
  font-weight: 600;
  cursor: pointer;
}

.dropzone-hint-text {
  font-size: 0.775rem;
  color: var(--text-muted);
}

.dropzone-btn {
  flex-shrink: 0;
  white-space: nowrap;
  padding: 0.55rem 1.15rem;
  font-weight: 600;
}

/* KHỐI 2: Directory / Search & Table Card */
.rag-directory-card {
  padding: 1.5rem 1.8rem;
  background: var(--surface-ffffff);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-sm);
}

.rag-directory-card .card-header-row {
  margin-bottom: 1.25rem;
}

.title-with-badge {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.rag-count-pill {
  font-size: 0.75rem;
  font-weight: 700;
  padding: 0.2rem 0.6rem;
  border-radius: 999px;
  background: rgba(0, 91, 65, 0.1);
  color: var(--primary);
}

.rag-toolbar {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 1.25rem;
  flex-wrap: wrap;
}

.rag-toolbar .rag-search {
  flex: 1;
  min-width: 240px;
  position: relative;
}

.btn-clear-search {
  background: none;
  border: none;
  padding: 0.2rem;
  cursor: pointer;
  color: var(--text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-clear-search:hover {
  color: var(--text-main);
}

.rag-toolbar .filter-controls .status-select {
  min-width: 200px;
  height: 42px;
}

.doc-file-item {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  min-width: 0;
}

.doc-file-icon {
  flex-shrink: 0;
  color: var(--primary);
}

.doc-file-details {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.doc-file-details strong {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.doc-error-text {
  color: var(--ink-b91c1c, #dc2626);
  font-size: 0.75rem;
  margin-top: 0.15rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.rag-status {
  display: inline-flex;
  align-items: center;
  padding: 0.25rem 0.55rem;
  border-radius: 999px;
  background: var(--surface-eef2f7);
  color: var(--ink-475569);
  font-size: 0.75rem;
  font-weight: 700;
  white-space: nowrap;
}

.rag-status.status-indexing,
.rag-status.status-pending {
  background: var(--surface-fff7d6);
  color: var(--ink-806710);
}

.rag-status.status-indexed {
  background: var(--surface-dcfce7);
  color: var(--ink-166534);
}

.rag-status.status-failed {
  background: var(--surface-fee2e2);
  color: var(--ink-b91c1c);
}

:root[data-theme="dark"] .rag-upload-card,
:root[data-theme="dark"] .rag-directory-card {
  background: var(--bg-card);
  border-color: var(--border-color);
}

:root[data-theme="dark"] .rag-dropzone-box {
  background: rgba(255, 255, 255, 0.02);
  border-color: var(--border-color);
}

:root[data-theme="dark"] .rag-dropzone-box:hover,
:root[data-theme="dark"] .rag-dropzone-box.is-dragover {
  border-color: var(--primary);
  background: rgba(0, 91, 65, 0.15);
}

:root[data-theme="dark"] .dropzone-icon-circle {
  background: rgba(255, 255, 255, 0.06);
}

.spin-icon {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
