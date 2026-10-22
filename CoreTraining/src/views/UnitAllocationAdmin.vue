<template>
  <div class="unit-allocation-page">
    <!-- PAGE HEADER -->
    <header class="page-header">
      <div class="header-left">
        <h2>Phân bổ Đào tạo theo Đơn vị</h2>
        <p class="subtitle">
          Quản lý phân bổ nội dung đào tạo (Lĩnh vực, Chương trình, Khóa học) cho từng Đơn vị / Chi nhánh trong hệ thống
        </p>
      </div>
      <div class="header-actions">
        <button type="button" class="btn btn-secondary" @click="loadData" :disabled="loading">
          <RotateCcw :size="14" :class="{ 'spin-icon': loading }" />
          <span>Làm mới</span>
        </button>
      </div>
    </header>

    <!-- MAIN TWO-COLUMN LAYOUT -->
    <div class="allocation-layout">
      <!-- LEFT PANEL: ALLOCATION FORM -->
      <section class="card form-panel">
        <div class="panel-header">
          <Building2 :size="18" class="panel-icon" />
          <h3>Thiết lập Phân bổ mới</h3>
        </div>

        <form @submit.prevent="handleAssignAllocation" class="allocation-form">
          <!-- BƯỚC 1: CHỌN ĐƠN VỊ -->
          <div class="form-group">
            <label for="unit-select">
              1. Đơn vị áp dụng (*):
            </label>
            <select
              id="unit-select"
              v-model="selectedUnitId"
              class="form-control select-control"
              required
              @change="handleUnitChange"
            >
              <option value="">-- Chọn Đơn vị / Chi nhánh / Khối --</option>
              <option
                v-for="u in units"
                :key="u.id"
                :value="u.id"
              >
                {{ u.name }} ({{ u.type === 'DEPARTMENT' ? 'Phòng ban' : 'Chi nhánh/Hội sở' }})
              </option>
            </select>
          </div>

          <!-- BƯỚC 2: CHỌN LOẠI DANH MỤC ĐÀO TẠO -->
          <div class="form-group">
            <label>2. Cấp danh mục đào tạo (*):</label>
            <div class="category-type-selector">
              <label class="radio-card" :class="{ active: categoryType === 'domain' }">
                <input type="radio" value="domain" v-model="categoryType" />
                <Globe :size="15" />
                <span>Lĩnh vực</span>
              </label>
              <label class="radio-card" :class="{ active: categoryType === 'program' }">
                <input type="radio" value="program" v-model="categoryType" />
                <Folder :size="15" />
                <span>Chương trình</span>
              </label>
              <label class="radio-card" :class="{ active: categoryType === 'course' }">
                <input type="radio" value="course" v-model="categoryType" />
                <BookOpen :size="15" />
                <span>Khóa học</span>
              </label>
            </div>
          </div>

          <!-- BƯỚC 3: CHỌN NỘI DUNG TƯƠNG ỨNG -->
          <!-- CASE 3A: CHỌN LĨNH VỰC -->
          <div v-if="categoryType === 'domain'" class="form-group dynamic-group">
            <label>Chọn Lĩnh vực đào tạo (*):</label>
            <select
              v-model="targetDomainId"
              class="form-control select-control"
              required
            >
              <option value="">-- Chọn lĩnh vực --</option>
              <option
                v-for="d in activeDomains"
                :key="d.id"
                :value="d.id"
              >
                [{{ d.code }}] {{ d.name }}
              </option>
            </select>
          </div>

          <!-- CASE 3B: CHỌN CHƯƠNG TRÌNH (CHỈ HIỆN CHƯƠNG TRÌNH KHI ĐÃ CHỌN LĨNH VỰC) -->
          <div v-if="categoryType === 'program'" class="form-group dynamic-group course-cascade-box">
            <!-- BƯỚC A: LĨNH VỰC CHA -->
            <div class="sub-form-group">
              <label>Lĩnh vực (*):</label>
              <select
                v-model="programFilterDomainId"
                class="form-control select-control"
                required
                @change="handleProgramDomainFilterChange"
              >
                <option value="">-- Chọn Lĩnh vực --</option>
                <option
                  v-for="d in activeDomains"
                  :key="d.id"
                  :value="d.id"
                >
                  [{{ d.code }}] {{ d.name }}
                </option>
              </select>
            </div>

            <!-- BƯỚC B: CHƯƠNG TRÌNH ĐÀO TẠO (SEARCHABLE DROPDOWN KÈM INFINITE SCROLL) -->
            <div v-if="programFilterDomainId" class="sub-form-group">
              <label>Chọn Chương trình đào tạo (*):</label>
              <div class="searchable-dropdown-wrapper" ref="programDropdownWrapperRef">
                <button
                  type="button"
                  class="form-control select-control searchable-trigger"
                  :class="{ 'is-open': showProgramDropdown, 'has-value': !!targetProgramId }"
                  @click="toggleProgramDropdown"
                >
                  <span class="trigger-label">{{ selectedProgramDisplay }}</span>
                  <ChevronDown :size="15" class="dropdown-chevron" :class="{ 'rotated': showProgramDropdown }" />
                </button>

                <div v-if="showProgramDropdown" class="searchable-dropdown-menu">
                  <div class="dropdown-search-box">
                    <Search :size="13" class="search-icon" />
                    <input
                      type="text"
                      v-model="programSearchQuery"
                      class="dropdown-search-input"
                      placeholder="Tìm theo mã hoặc tên chương trình..."
                      @click.stop
                      autofocus
                    />
                    <Loader2 v-if="searchingPrograms" :size="13" class="spinner search-spinner" />
                    <button
                      v-else-if="programSearchQuery"
                      type="button"
                      class="btn-clear-search"
                      @click.stop="programSearchQuery = ''"
                    >
                      <X :size="12" />
                    </button>
                  </div>

                  <div class="dropdown-items-list" @scroll="handleProgramsScroll">
                    <div v-if="loadingPrograms" class="dropdown-state-row">
                      <Loader2 :size="14" class="spinner" />
                      <span>Đang tải danh sách chương trình...</span>
                    </div>
                    <div v-else-if="!pagedPrograms.length" class="dropdown-state-row empty-state-row">
                      {{ programSearchQuery ? "Không tìm thấy chương trình phù hợp." : "Lĩnh vực này chưa có chương trình nào." }}
                    </div>
                    <div
                      v-for="p in pagedPrograms"
                      :key="p.id"
                      class="dropdown-item"
                      :class="{ 'is-selected': p.id === targetProgramId }"
                      @click="selectProgram(p)"
                    >
                      <span class="item-code">[{{ p.code }}]</span>
                      <span class="item-name">{{ p.name }}</span>
                      <Check v-if="p.id === targetProgramId" :size="14" class="item-checked" />
                    </div>

                    <div v-if="loadingMorePrograms" class="dropdown-state-row loading-more">
                      <Loader2 :size="12" class="spinner" />
                      <span>Tải thêm 10 chương trình...</span>
                    </div>
                    <div v-else-if="!hasMorePrograms && pagedPrograms.length > 0" class="dropdown-state-row end-hint">
                      <span>Đã hiển thị {{ pagedPrograms.length }} / {{ totalPrograms }} chương trình</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- CASE 3C: CHỌN KHÓA HỌC (CHỈ HIỆN CẤP TIẾP THEO KHI CẤP TRƯỚC ĐƯỢC CHỌN) -->
          <div v-if="categoryType === 'course'" class="form-group dynamic-group course-cascade-box">
            <!-- BƯỚC A: LĨNH VỰC CHA -->
            <div class="sub-form-group">
              <label>Lĩnh vực (*):</label>
              <select
                v-model="courseFilterDomainId"
                class="form-control select-control"
                required
                @change="handleDomainFilterChange"
              >
                <option value="">-- Chọn Lĩnh vực --</option>
                <option
                  v-for="d in activeDomains"
                  :key="d.id"
                  :value="d.id"
                >
                  [{{ d.code }}] {{ d.name }}
                </option>
              </select>
            </div>

            <!-- BƯỚC B: CHƯƠNG TRÌNH ĐÀO TẠO (SEARCHABLE DROPDOWN KÈM INFINITE SCROLL) -->
            <div v-if="courseFilterDomainId" class="sub-form-group">
              <label>Chương trình đào tạo (*):</label>
              <div class="searchable-dropdown-wrapper" ref="programDropdownWrapperRef">
                <button
                  type="button"
                  class="form-control select-control searchable-trigger"
                  :class="{ 'is-open': showProgramDropdown, 'has-value': !!targetProgramId }"
                  @click="toggleProgramDropdown"
                >
                  <span class="trigger-label">{{ selectedProgramDisplay }}</span>
                  <ChevronDown :size="15" class="dropdown-chevron" :class="{ 'rotated': showProgramDropdown }" />
                </button>

                <div v-if="showProgramDropdown" class="searchable-dropdown-menu">
                  <div class="dropdown-search-box">
                    <Search :size="13" class="search-icon" />
                    <input
                      type="text"
                      v-model="programSearchQuery"
                      class="dropdown-search-input"
                      placeholder="Tìm theo mã hoặc tên chương trình..."
                      @click.stop
                      autofocus
                    />
                    <Loader2 v-if="searchingPrograms" :size="13" class="spinner search-spinner" />
                    <button
                      v-else-if="programSearchQuery"
                      type="button"
                      class="btn-clear-search"
                      @click.stop="programSearchQuery = ''"
                    >
                      <X :size="12" />
                    </button>
                  </div>

                  <div class="dropdown-items-list" @scroll="handleProgramsScroll">
                    <div v-if="loadingPrograms" class="dropdown-state-row">
                      <Loader2 :size="14" class="spinner" />
                      <span>Đang tải danh sách chương trình...</span>
                    </div>
                    <div v-else-if="!pagedPrograms.length" class="dropdown-state-row empty-state-row">
                      {{ programSearchQuery ? "Không tìm thấy chương trình phù hợp." : "Lĩnh vực này chưa có chương trình nào." }}
                    </div>
                    <div
                      v-for="p in pagedPrograms"
                      :key="p.id"
                      class="dropdown-item"
                      :class="{ 'is-selected': p.id === targetProgramId }"
                      @click="selectProgram(p)"
                    >
                      <span class="item-code">[{{ p.code }}]</span>
                      <span class="item-name">{{ p.name }}</span>
                      <Check v-if="p.id === targetProgramId" :size="14" class="item-checked" />
                    </div>

                    <div v-if="loadingMorePrograms" class="dropdown-state-row loading-more">
                      <Loader2 :size="12" class="spinner" />
                      <span>Tải thêm 10 chương trình...</span>
                    </div>
                    <div v-else-if="!hasMorePrograms && pagedPrograms.length > 0" class="dropdown-state-row end-hint">
                      <span>Đã hiển thị {{ pagedPrograms.length }} / {{ totalPrograms }} chương trình</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- BƯỚC C: KHÓA HỌC ÁP DỤNG (MẶC ĐỊNH ALL) -->
            <div v-if="targetProgramId" class="sub-courses-picker">
              <div class="picker-header">
                <label>Khóa học áp dụng (*):</label>
                <span class="course-count-badge">
                  <span v-if="selectAllCoursesInProgram">
                    Đã chọn TẤT CẢ ({{ totalProgramCourses }} khóa)
                  </span>
                  <span v-else>
                    Đã chọn {{ selectedSubCourseIds.length }} / {{ totalProgramCourses }} khóa
                  </span>
                </span>
              </div>

              <!-- Checkbox TẤT CẢ (ALL) & Ô TÌM KIẾM SERVER-SIDE -->
              <div class="all-and-search-row">
                <label class="all-courses-checkbox">
                  <input
                    type="checkbox"
                    v-model="selectAllCoursesInProgram"
                    @change="handleToggleAllCourses"
                  />
                  <span class="all-text">ALL</span>
                </label>

                <!-- Ô TÌM KIẾM KHÓA HỌC (SERVER-SIDE LIKE QUERY) -->
                <div class="course-search-box">
                  <Search :size="13" class="search-icon" />
                  <input
                    type="text"
                    v-model="courseSearchQuery"
                    class="course-search-input"
                    placeholder="Tìm theo mã hoặc tên khóa..."
                  />
                  <Loader2 v-if="searchingCourses" :size="13" class="spinner search-spinner" />
                </div>
              </div>

              <!-- DANH SÁCH KHÓA HỌC CON VỚI SỰ KIỆN CUỘN TẢI TIẾP (INFINITE SCROLL) -->
              <div v-if="loadingProgramCourses" class="picker-loading">
                <Loader2 :size="16" class="spinner" />
                <span>Đang tải khóa học...</span>
              </div>
              <div
                v-else
                class="courses-checkbox-list"
                @scroll="handleCoursesScroll"
              >
                <div v-if="!programCourses.length" class="empty-hint">
                  {{ courseSearchQuery ? "Không tìm thấy khóa học nào khớp từ khóa." : "Chương trình này hiện chưa có khóa học nào." }}
                </div>
                <label
                  v-for="c in programCourses"
                  :key="c.id"
                  class="course-checkbox-item"
                >
                  <input
                    type="checkbox"
                    :value="c.id"
                    v-model="selectedSubCourseIds"
                    @change="handleSubCourseCheckChange"
                  />
                  <span class="course-code-tag">[{{ c.code }}]</span>
                  <span class="course-name">{{ c.name }}</span>
                </label>

                <!-- SPINNER KHI CUỘN TẢI THÊM 10 ITEM -->
                <div v-if="loadingMoreCourses" class="scroll-loading-more">
                  <Loader2 :size="14" class="spinner" />
                  <span>Đang tải thêm 10 khóa tiếp theo...</span>
                </div>
                <div v-else-if="!hasMoreCourses && programCourses.length > 0" class="scroll-end-hint">
                  <span>Đã hiển thị {{ programCourses.length }} / {{ totalProgramCourses }} khóa học</span>
                </div>
              </div>
            </div>
          </div>

          <!-- BƯỚC 4: HÌNH THỨC ÁP DỤNG (DROPDOWN) -->
          <div class="form-group">
            <label for="grant-type-select">3. Hình thức áp dụng (*):</label>
            <select
              id="grant-type-select"
              v-model="grantType"
              class="form-control select-control"
              required
            >
              <option value="MANDATORY">Bắt buộc hoàn thành</option>
              <option value="OPTIONAL">Tự chọn / Tham khảo</option>
            </select>
          </div>

          <!-- SUBMIT ACTION -->
          <div class="form-actions">
            <button
              type="submit"
              class="btn btn-primary btn-submit"
              :disabled="saving || !selectedUnitId"
            >
              <Loader2 v-if="saving" :size="16" class="spinner" />
              <Check :size="16" v-else />
              <span>{{ saving ? "Đang lưu phân bổ..." : "Lưu Phân bổ cho Đơn vị" }}</span>
            </button>
          </div>
        </form>
      </section>

      <!-- RIGHT PANEL: CURRENT ALLOCATION MATRIX -->
      <section class="card matrix-panel">
        <div class="panel-header">
          <ListOrdered :size="18" class="panel-icon" />
          <div class="header-titles">
            <h3>Danh sách Đào tạo đã phân bổ</h3>
            <span v-if="currentUnit" class="current-unit-title">
              Đơn vị: <strong>{{ currentUnit.name }}</strong>
            </span>
          </div>
        </div>

        <div v-if="!selectedUnitId" class="empty-state select-unit-prompt">
          <Building2 :size="48" class="empty-icon" />
          <h4>Vui lòng chọn Đơn vị ở cột bên trái</h4>
          <p>Chọn một Đơn vị để xem và quản lý các nội dung đào tạo đã được phân bổ cho đơn vị đó.</p>
        </div>

        <div v-else-if="loading" class="empty-state">
          <Loader2 :size="36" class="spinner" />
          <p>Đang tải dữ liệu phân bổ...</p>
        </div>

        <div v-else-if="!assignedAllocations.length" class="empty-state">
          <Inbox :size="48" class="empty-icon" />
          <h4>Chưa có phân bổ nào cho đơn vị này</h4>
          <p>Sử dụng biểu mẫu bên trái để gán Lĩnh vực, Chương trình hoặc Khóa học cho đơn vị.</p>
        </div>

        <!-- ALLOCATION TABLE -->
        <div v-else class="allocation-table-wrapper">
          <table class="allocation-table">
            <thead>
              <tr>
                <th>Cấp bậc</th>
                <th>Mã</th>
                <th>Tên Nội dung Đào tạo</th>
                <th>Hình thức</th>
                <th>Thuộc Chương trình</th>
                <th class="actions-col">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in assignedAllocations"
                :key="`${item.type}_${item.id}`"
                :class="{ 'highlight-row': item.type === 'course' }"
              >
                <td>
                  <span class="type-pill" :class="item.type">
                    {{ item.typeLabel }}
                  </span>
                </td>
                <td class="code-col">
                  <code>{{ item.code }}</code>
                </td>
                <td class="name-col">
                  <strong>{{ item.name }}</strong>
                </td>
                <td>
                  <span class="mode-badge" :class="item.grantType.toLowerCase()">
                    {{ item.grantType === 'MANDATORY' ? 'Bắt buộc' : 'Tự chọn' }}
                  </span>
                </td>
                <td class="parent-col">
                  <span v-if="item.parentProgramName" class="parent-text">
                    {{ item.parentProgramName }}
                  </span>
                  <span v-else class="muted-dash">—</span>
                </td>
                <td class="actions-col">
                  <button
                    type="button"
                    class="btn-remove-allocation"
                    title="Gỡ bỏ phân bổ khỏi đơn vị này"
                    :disabled="deletingId === item.id"
                    @click="handleRemoveAllocation(item)"
                  >
                    <Trash2 :size="14" />
                    <span>Gỡ</span>
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from "vue";
import {
  Search,
  Building2,
  Globe,
  Folder,
  BookOpen,
  RotateCcw,
  Check,
  Trash2,
  Loader2,
  Inbox,
  ListOrdered,
  ChevronDown,
  X,
} from "lucide-vue-next";
import { trainingApi } from "../api/trainingApi";
import { axiosClient } from "../api/axiosClient";
import { showToast } from "../utils/toast";
import type { TrainingDomain, TrainingProgram, Course } from "../types";
import type { OrganizationUnit } from "../types/organization";

// State
const loading = ref(false);
const saving = ref(false);
const deletingId = ref("");

const units = ref<OrganizationUnit[]>([]);
const domains = ref<TrainingDomain[]>([]);
const programs = ref<TrainingProgram[]>([]);
const unitAssignedCourses = ref<Course[]>([]);
const programCourses = ref<Course[]>([]);
const loadingUnitCourses = ref(false);
const loadingProgramCourses = ref(false);

// Form State
const selectedUnitId = ref("");
const categoryType = ref<"domain" | "program" | "course">("program");
const targetDomainId = ref("");
const targetProgramId = ref("");

watch(categoryType, () => {
  targetDomainId.value = "";
  targetProgramId.value = "";
  selectedProgramObj.value = null;
  showProgramDropdown.value = false;
  courseFilterDomainId.value = "";
  courseSearchQuery.value = "";
  programFilterDomainId.value = "";
  programSearchQuery.value = "";
  pagedPrograms.value = [];
  selectedSubCourseIds.value = [];
  selectAllCoursesInProgram.value = true;
  programCourses.value = [];
  totalProgramCourses.value = 0;
  hasMoreCourses.value = false;
});

// Searchable Program Dropdown State
const programDropdownWrapperRef = ref<HTMLElement | null>(null);
const showProgramDropdown = ref(false);
const selectedProgramObj = ref<TrainingProgram | null>(null);
const pagedPrograms = ref<TrainingProgram[]>([]);
const programSearchQuery = ref("");
const programPage = ref(0);
const programPageSize = 10;
const totalPrograms = ref(0);
const hasMorePrograms = ref(false);
const loadingPrograms = ref(false);
const loadingMorePrograms = ref(false);
const searchingPrograms = ref(false);

const currentActiveDomainId = computed(() => {
  return categoryType.value === "program"
    ? programFilterDomainId.value
    : courseFilterDomainId.value;
});

const selectedProgramDisplay = computed(() => {
  if (!targetProgramId.value) return "-- Chọn chương trình đào tạo --";
  if (selectedProgramObj.value) {
    return `[${selectedProgramObj.value.code}] ${selectedProgramObj.value.name}`;
  }
  const found = pagedPrograms.value.find((p) => p.id === targetProgramId.value) ||
                programs.value.find((p) => p.id === targetProgramId.value);
  if (found) return `[${found.code}] ${found.name}`;
  return "-- Chọn chương trình đào tạo --";
});

async function fetchPagedPrograms(reset = true) {
  const domainId = currentActiveDomainId.value;
  if (!domainId) {
    pagedPrograms.value = [];
    totalPrograms.value = 0;
    hasMorePrograms.value = false;
    return;
  }

  if (reset) {
    programPage.value = 0;
    loadingPrograms.value = true;
  } else {
    loadingMorePrograms.value = true;
  }

  try {
    const params: any = {
      domainId: domainId,
      page: programPage.value,
      size: programPageSize,
    };
    if (programSearchQuery.value.trim()) {
      params.search = programSearchQuery.value.trim();
    }
    const res = await axiosClient.get("/api/admin/programs/paged", { params });
    const data = res.data || {};
    const items: TrainingProgram[] = data.items || [];
    totalPrograms.value = data.totalElements || 0;
    hasMorePrograms.value = !!data.hasNext;

    if (reset) {
      pagedPrograms.value = items;
    } else {
      pagedPrograms.value = [...pagedPrograms.value, ...items];
    }
  } catch (err) {
    console.error("Lỗi khi tải danh sách chương trình:", err);
    showToast("Không thể tải danh sách chương trình", "error");
  } finally {
    loadingPrograms.value = false;
    loadingMorePrograms.value = false;
    searchingPrograms.value = false;
  }
}

function handleProgramsScroll(e: Event) {
  const el = e.target as HTMLElement;
  if (!el) return;
  const atBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 20;
  if (atBottom && hasMorePrograms.value && !loadingMorePrograms.value && !loadingPrograms.value) {
    programPage.value++;
    fetchPagedPrograms(false);
  }
}

let progSearchTimer: any = null;
watch(programSearchQuery, () => {
  if (progSearchTimer) clearTimeout(progSearchTimer);
  searchingPrograms.value = true;
  progSearchTimer = setTimeout(() => {
    fetchPagedPrograms(true);
  }, 300);
});

function toggleProgramDropdown() {
  showProgramDropdown.value = !showProgramDropdown.value;
  if (showProgramDropdown.value && !pagedPrograms.value.length) {
    fetchPagedPrograms(true);
  }
}

function selectProgram(p: TrainingProgram) {
  targetProgramId.value = p.id;
  selectedProgramObj.value = p;
  showProgramDropdown.value = false;
  if (categoryType.value === "course") {
    handleProgramChange();
  }
}

function onDocumentClick(e: MouseEvent) {
  if (
    programDropdownWrapperRef.value &&
    !programDropdownWrapperRef.value.contains(e.target as Node)
  ) {
    showProgramDropdown.value = false;
  }
}

onMounted(() => {
  document.addEventListener("click", onDocumentClick);
});

onUnmounted(() => {
  document.removeEventListener("click", onDocumentClick);
});
const courseFilterDomainId = ref("");
const programFilterDomainId = ref("");
const grantType = ref<"MANDATORY" | "OPTIONAL">("MANDATORY");

// Sub-course picker state for Course cascade with pagination & infinite scroll
const selectAllCoursesInProgram = ref(true);
const selectedSubCourseIds = ref<string[]>([]);
const coursePage = ref(0);
const coursePageSize = 10;
const totalProgramCourses = ref(0);
const hasMoreCourses = ref(false);
const loadingMoreCourses = ref(false);
const searchingCourses = ref(false);
const courseSearchQuery = ref("");

// Filter active (non-deleted) items
const activeDomains = computed(() => domains.value.filter((d) => d.status !== "DELETED"));
const activePrograms = computed(() => programs.value.filter((p) => p.status !== "DELETED"));

const currentUnit = computed(() => units.value.find((u) => u.id === selectedUnitId.value));



function handleDomainFilterChange() {
  targetProgramId.value = "";
  selectedProgramObj.value = null;
  selectedSubCourseIds.value = [];
  programCourses.value = [];
  programSearchQuery.value = "";
  pagedPrograms.value = [];
  showProgramDropdown.value = false;
  if (courseFilterDomainId.value) {
    fetchPagedPrograms(true);
  }
}

function handleProgramDomainFilterChange() {
  targetProgramId.value = "";
  selectedProgramObj.value = null;
  programSearchQuery.value = "";
  pagedPrograms.value = [];
  showProgramDropdown.value = false;
  if (programFilterDomainId.value) {
    fetchPagedPrograms(true);
  }
}

// Load metadata (Domains, Programs, Units) - KHÔNG load toàn bộ hàng nghìn khóa học lúc mở trang
async function loadData() {
  loading.value = true;
  try {
    const [catalog, optionsRes] = await Promise.all([
      trainingApi.catalog(),
      axiosClient.get("/api/admin/options/catalog"),
    ]);

    domains.value = catalog.domains || [];
    programs.value = catalog.programs || [];
    if (optionsRes.data?.units) {
      units.value = optionsRes.data.units;
    }
    if (selectedUnitId.value) {
      await loadUnitCourses();
    }
  } catch (err) {
    console.error("Lỗi khi tải dữ liệu phân bổ:", err);
    showToast("Không thể tải danh sách dữ liệu đào tạo", "error");
  } finally {
    loading.value = false;
  }
}

// Lazy load các khóa học được phân bổ cho đơn vị đang chọn
async function loadUnitCourses() {
  if (!selectedUnitId.value) {
    unitAssignedCourses.value = [];
    return;
  }
  loadingUnitCourses.value = true;
  try {
    const res = await axiosClient.get(`/api/admin/courses/by-unit/${selectedUnitId.value}`);
    unitAssignedCourses.value = res.data || [];
  } catch (err) {
    console.error("Lỗi khi tải khóa học của đơn vị:", err);
    unitAssignedCourses.value = [];
  } finally {
    loadingUnitCourses.value = false;
  }
}

onMounted(() => {
  loadData();
});

async function handleUnitChange() {
  await loadUnitCourses();
}

// Tải danh sách khóa học theo phân trang (10 item/lần) và hỗ trợ tìm kiếm server-side LIKE
async function fetchProgramCourses(reset = true) {
  if (!targetProgramId.value) {
    programCourses.value = [];
    totalProgramCourses.value = 0;
    hasMoreCourses.value = false;
    return;
  }

  if (reset) {
    coursePage.value = 0;
    loadingProgramCourses.value = true;
  } else {
    loadingMoreCourses.value = true;
  }

  try {
    const params: any = {
      page: coursePage.value,
      size: coursePageSize,
    };
    if (courseSearchQuery.value.trim()) {
      params.search = courseSearchQuery.value.trim();
    }
    const res = await axiosClient.get(
      `/api/admin/courses/by-program/${targetProgramId.value}`,
      { params }
    );
    const data = res.data || {};
    const items: Course[] = data.items || [];
    totalProgramCourses.value = data.totalElements || 0;
    hasMoreCourses.value = !!data.hasNext;

    if (reset) {
      programCourses.value = items;
      if (selectAllCoursesInProgram.value) {
        selectedSubCourseIds.value = items.map((c) => c.id);
      }
    } else {
      programCourses.value = [...programCourses.value, ...items];
      if (selectAllCoursesInProgram.value) {
        const set = new Set(selectedSubCourseIds.value);
        items.forEach((c) => set.add(c.id));
        selectedSubCourseIds.value = Array.from(set);
      }
    }
  } catch (err) {
    console.error("Lỗi khi tải khóa học của chương trình:", err);
    showToast("Không thể tải danh sách khóa học của chương trình", "error");
    if (reset) {
      programCourses.value = [];
      totalProgramCourses.value = 0;
      hasMoreCourses.value = false;
    }
  } finally {
    loadingProgramCourses.value = false;
    loadingMoreCourses.value = false;
    searchingCourses.value = false;
  }
}

// Xử lý sự kiện cuộn hết danh sách để tải thêm 10 khóa tiếp theo (Infinite Scroll)
function handleCoursesScroll(e: Event) {
  const el = e.target as HTMLElement;
  if (!el) return;
  const atBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 25;
  if (
    atBottom &&
    hasMoreCourses.value &&
    !loadingMoreCourses.value &&
    !loadingProgramCourses.value
  ) {
    coursePage.value++;
    fetchProgramCourses(false);
  }
}

// Debounce tìm kiếm khóa học từ server (350ms)
let courseSearchTimer: any = null;
watch(courseSearchQuery, () => {
  if (courseSearchTimer) clearTimeout(courseSearchTimer);
  searchingCourses.value = true;
  courseSearchTimer = setTimeout(() => {
    fetchProgramCourses(true);
  }, 350);
});

// Khi thay đổi Chương trình đào tạo
async function handleProgramChange() {
  courseSearchQuery.value = "";
  selectAllCoursesInProgram.value = true;
  selectedSubCourseIds.value = [];
  await fetchProgramCourses(true);
}

function handleToggleAllCourses() {
  if (selectAllCoursesInProgram.value) {
    selectedSubCourseIds.value = programCourses.value.map((c) => c.id);
  } else {
    selectedSubCourseIds.value = [];
  }
}

function handleSubCourseCheckChange() {
  selectAllCoursesInProgram.value =
    programCourses.value.length > 0 &&
    selectedSubCourseIds.value.length === totalProgramCourses.value;
}

// Assigned allocations for the selected unit
interface AllocationRow {
  id: string;
  type: "domain" | "program" | "course";
  typeLabel: string;
  code: string;
  name: string;
  grantType: "MANDATORY" | "OPTIONAL";
  parentProgramName?: string;
  rawObj: any;
}

const assignedAllocations = computed<AllocationRow[]>(() => {
  if (!selectedUnitId.value) return [];
  const uId = selectedUnitId.value;
  const rows: AllocationRow[] = [];

  // 1. Domains
  for (const d of activeDomains.value) {
    const isMan = d.mandatoryUnitIds?.includes(uId);
    const isOpt = d.optionalUnitIds?.includes(uId) || d.unitIds?.includes(uId);
    if (isMan || isOpt) {
      rows.push({
        id: d.id,
        type: "domain",
        typeLabel: "Lĩnh vực",
        code: d.code,
        name: d.name,
        grantType: isMan ? "MANDATORY" : "OPTIONAL",
        rawObj: d,
      });
    }
  }

  // 2. Programs
  for (const p of activePrograms.value) {
    const isMan = p.mandatoryUnitIds?.includes(uId);
    const isOpt = p.optionalUnitIds?.includes(uId) || p.unitIds?.includes(uId);
    if (isMan || isOpt) {
      rows.push({
        id: p.id,
        type: "program",
        typeLabel: "Chương trình",
        code: p.code,
        name: p.name,
        grantType: isMan ? "MANDATORY" : "OPTIONAL",
        rawObj: p,
      });
    }
  }

  // 3. Courses (Lazy loaded per unit)
  for (const c of unitAssignedCourses.value) {
    if (c.status === "DELETED") continue;
    const isMan = c.mandatoryUnitIds?.includes(uId);
    const isOpt = c.optionalUnitIds?.includes(uId) || c.unitIds?.includes(uId);
    if (isMan || isOpt) {
      const prog = programs.value.find((p) => p.id === c.programId);
      rows.push({
        id: c.id,
        type: "course",
        typeLabel: "Khóa học",
        code: c.code || "",
        name: c.name,
        grantType: isMan ? "MANDATORY" : "OPTIONAL",
        parentProgramName: prog?.name,
        rawObj: c,
      });
    }
  }

  return rows;
});

// Handle assign action
async function handleAssignAllocation() {
  if (!selectedUnitId.value) {
    showToast("Vui lòng chọn Đơn vị áp dụng!", "warning");
    return;
  }

  saving.value = true;
  const uId = selectedUnitId.value;

  try {
    if (categoryType.value === "domain") {
      if (!targetDomainId.value) {
        showToast("Vui lòng chọn Lĩnh vực!", "warning");
        saving.value = false;
        return;
      }
      const domain = domains.value.find((d) => d.id === targetDomainId.value);
      if (domain) {
        const manIds = new Set(domain.mandatoryUnitIds || []);
        const optIds = new Set(domain.optionalUnitIds || domain.unitIds || []);
        if (grantType.value === "MANDATORY") {
          manIds.add(uId);
          optIds.delete(uId);
        } else {
          optIds.add(uId);
          manIds.delete(uId);
        }
        await trainingApi.saveDomain({
          id: domain.id,
          name: domain.name,
          code: domain.code,
          mandatoryUnitIds: Array.from(manIds),
          optionalUnitIds: Array.from(optIds),
        });
        showToast(`Đã phân bổ lĩnh vực [${domain.name}] cho đơn vị!`, "success");
      }
    } else if (categoryType.value === "program") {
      if (!targetProgramId.value) {
        showToast("Vui lòng chọn Chương trình đào tạo!", "warning");
        saving.value = false;
        return;
      }
      const program = programs.value.find((p) => p.id === targetProgramId.value) || selectedProgramObj.value;
      if (program) {
        const manIds = new Set(program.mandatoryUnitIds || []);
        const optIds = new Set(program.optionalUnitIds || program.unitIds || []);
        if (grantType.value === "MANDATORY") {
          manIds.add(uId);
          optIds.delete(uId);
        } else {
          optIds.add(uId);
          manIds.delete(uId);
        }
        await trainingApi.saveProgram({
          id: program.id,
          name: program.name,
          code: program.code,
          domainId: program.domainId,
          mandatoryUnitIds: Array.from(manIds),
          optionalUnitIds: Array.from(optIds),
        });
        showToast(
          `Đã phân bổ chương trình [${program.name}] cho đơn vị!`,
          "success"
        );
      }
    } else if (categoryType.value === "course") {
      if (!targetProgramId.value) {
        showToast("Vui lòng chọn Chương trình đào tạo!", "warning");
        saving.value = false;
        return;
      }
      if (!selectAllCoursesInProgram.value && !selectedSubCourseIds.value.length) {
        showToast("Vui lòng chọn ít nhất 1 khóa học để phân bổ!", "warning");
        saving.value = false;
        return;
      }

      await axiosClient.post("/api/admin/courses/batch-allocate", {
        unitId: uId,
        grantType: grantType.value,
        programId: targetProgramId.value,
        allInProgram: selectAllCoursesInProgram.value,
        courseIds: selectAllCoursesInProgram.value ? [] : selectedSubCourseIds.value,
      });

      showToast(
        selectAllCoursesInProgram.value
          ? "Đã phân bổ toàn bộ khóa học trong chương trình cho đơn vị!"
          : `Đã phân bổ ${selectedSubCourseIds.value.length} khóa học cho đơn vị!`,
        "success"
      );
    }

    await loadData();
  } catch (err: any) {
    console.error("Lỗi khi lưu phân bổ:", err);
    showToast(err.response?.data?.message || "Lỗi khi lưu phân bổ cho đơn vị", "error");
  } finally {
    saving.value = false;
  }
}

// Handle remove allocation
async function handleRemoveAllocation(item: AllocationRow) {
  if (!confirm(`Bạn có chắc chắn muốn gỡ bỏ phân bổ [${item.name}] khỏi đơn vị này?`)) {
    return;
  }

  deletingId.value = item.id;
  const uId = selectedUnitId.value;

  try {
    if (item.type === "domain") {
      const manIds = (item.rawObj.mandatoryUnitIds || []).filter((id: string) => id !== uId);
      const optIds = (item.rawObj.optionalUnitIds || item.rawObj.unitIds || []).filter(
        (id: string) => id !== uId
      );
      await trainingApi.saveDomain({
        id: item.id,
        name: item.rawObj.name,
        code: item.rawObj.code,
        mandatoryUnitIds: manIds,
        optionalUnitIds: optIds,
      });
    } else if (item.type === "program") {
      const manIds = (item.rawObj.mandatoryUnitIds || []).filter((id: string) => id !== uId);
      const optIds = (item.rawObj.optionalUnitIds || item.rawObj.unitIds || []).filter(
        (id: string) => id !== uId
      );
      await trainingApi.saveProgram({
        id: item.id,
        name: item.rawObj.name,
        code: item.rawObj.code,
        domainId: item.rawObj.domainId,
        mandatoryUnitIds: manIds,
        optionalUnitIds: optIds,
      });
    } else if (item.type === "course") {
      const manIds = (item.rawObj.mandatoryUnitIds || []).filter((id: string) => id !== uId);
      const optIds = (item.rawObj.optionalUnitIds || item.rawObj.unitIds || []).filter(
        (id: string) => id !== uId
      );
      await axiosClient.put(`/api/admin/courses/${item.id}`, {
        name: item.rawObj.name,
        code: item.rawObj.code,
        programId: item.rawObj.programId,
        mandatoryUnitIds: manIds,
        optionalUnitIds: optIds,
      });
    }

    showToast(`Đã gỡ bỏ phân bổ [${item.name}]!`, "success");
    await loadData();
  } catch (err: any) {
    console.error("Lỗi khi gỡ phân bổ:", err);
    showToast(err.response?.data?.message || "Lỗi khi gỡ phân bổ", "error");
  } finally {
    deletingId.value = "";
  }
}
</script>

<style scoped>
.unit-allocation-page {
  padding: 1.5rem 2rem;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  max-width: 1500px;
  margin: 0 auto;
  width: 100%;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #1e293b;
  padding-bottom: 1rem;
}

.header-left h2 {
  font-size: 1.45rem;
  font-weight: 700;
  color: #f8fafc;
  margin: 0 0 0.35rem;
}

.subtitle {
  font-size: 0.86rem;
  color: #94a3b8;
  margin: 0;
}

.allocation-layout {
  display: grid;
  grid-template-columns: 460px 1fr;
  gap: 1.5rem;
  align-items: start;
}

@media (max-width: 1024px) {
  .allocation-layout {
    grid-template-columns: 1fr;
  }
}

.card {
  background: #0f172a;
  border: 1px solid #1e293b;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.3);
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 1rem 1.25rem;
  border-bottom: 1px solid #1e293b;
  background: rgba(30, 41, 59, 0.4);
}

.panel-header h3 {
  font-size: 1rem;
  font-weight: 600;
  color: #f1f5f9;
  margin: 0;
}

.panel-icon {
  color: #38bdf8;
}

.header-titles {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.current-unit-title {
  font-size: 0.78rem;
  color: #94a3b8;
}

.current-unit-title strong {
  color: #38bdf8;
}

/* FORM STYLING */
.allocation-form {
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.form-group label {
  font-size: 0.82rem;
  font-weight: 600;
  color: #e2e8f0;
}

.form-control {
  width: 100%;
  background: #1e293b;
  border: 1px solid #334155;
  color: #f8fafc;
  border-radius: 6px;
  padding: 0.6rem 0.8rem;
  font-size: 0.84rem;
  outline: none;
  transition: border-color 0.15s ease;
}

.form-control:focus {
  border-color: #38bdf8;
}

.category-type-selector {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.5rem;
}

.radio-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.35rem;
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 6px;
  padding: 0.65rem 0.4rem;
  cursor: pointer;
  font-size: 0.8rem;
  color: #94a3b8;
  transition: all 0.15s ease;
}

.radio-card input {
  display: none;
}

.radio-card:hover {
  background: #334155;
  color: #f1f5f9;
}

.radio-card.active {
  background: rgba(56, 189, 248, 0.15);
  border-color: #38bdf8;
  color: #38bdf8;
  font-weight: 600;
}

/* Sub-courses picker inside Program */
.sub-courses-picker {
  margin-top: 0.75rem;
  background: #09101d;
  border: 1px solid #22334d;
  border-radius: 6px;
  padding: 0.85rem;
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
}

.picker-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.course-count-badge {
  font-size: 0.7rem;
  background: rgba(56, 189, 248, 0.15);
  color: #38bdf8;
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
}

.all-courses-checkbox {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: rgba(30, 41, 59, 0.7);
  border: 1px solid #334155;
  border-radius: 4px;
  padding: 0.5rem 0.65rem;
  cursor: pointer;
}

.all-courses-checkbox .all-text {
  font-size: 0.8rem;
  font-weight: 600;
  color: #93c5fd;
}

.courses-checkbox-list {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  max-height: 220px;
  overflow-y: auto;
  border-top: 1px dashed #334155;
  padding-top: 0.5rem;
}

.select-hint {
  font-size: 0.74rem;
  color: #94a3b8;
  margin: 0;
}

.course-checkbox-item {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.78rem;
  color: #cbd5e1;
  padding: 0.35rem 0.5rem;
  border-radius: 4px;
  background: #111c2e;
  cursor: pointer;
}

.course-checkbox-item:hover {
  background: #1e293b;
  color: #f8fafc;
}

.course-code-tag {
  font-family: monospace;
  color: #64748b;
  font-size: 0.72rem;
}

/* Grant mode picker */
.grant-mode-picker {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.6rem;
}

.mode-label {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 6px;
  padding: 0.6rem;
  cursor: pointer;
  transition: all 0.15s ease;
}

.mode-label input {
  display: none;
}

.mode-label div {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.mode-label strong {
  font-size: 0.8rem;
  color: #e2e8f0;
}

.mode-label small {
  font-size: 0.68rem;
  color: #94a3b8;
}

.mode-label.selected {
  background: rgba(59, 130, 246, 0.15);
  border-color: #3b82f6;
}

.mode-label.selected strong {
  color: #93c5fd;
}

.form-actions {
  margin-top: 0.5rem;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  padding: 0.55rem 1rem;
  font-size: 0.82rem;
  font-weight: 500;
  border-radius: 6px;
  border: none;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-primary {
  background: #00856a;
  color: #ffffff;
}

.btn-primary:hover:not(:disabled) {
  background: #006b54;
}

.btn-submit {
  width: 100%;
  padding: 0.75rem;
  font-size: 0.88rem;
  font-weight: 600;
}

.btn-secondary {
  background: #1e293b;
  border: 1px solid #334155;
  color: #cbd5e1;
}

.btn-secondary:hover:not(:disabled) {
  background: #334155;
  color: #ffffff;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

/* MATRIX TABLE */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 2rem;
  text-align: center;
  color: #64748b;
  gap: 0.6rem;
}

.empty-icon {
  color: #334155;
  margin-bottom: 0.5rem;
}

.empty-state h4 {
  font-size: 1rem;
  font-weight: 600;
  color: #cbd5e1;
  margin: 0;
}

.empty-state p {
  font-size: 0.82rem;
  color: #64748b;
  max-width: 420px;
  margin: 0;
}

.allocation-table-wrapper {
  overflow-x: auto;
}

.allocation-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.82rem;
  text-align: left;
}

.allocation-table th {
  background: #111c2e;
  color: #94a3b8;
  font-weight: 600;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #1e293b;
  white-space: nowrap;
}

.allocation-table td {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #1e293b;
  color: #e2e8f0;
}

.allocation-table tbody tr:hover {
  background: rgba(30, 41, 59, 0.4);
}

.type-pill {
  font-size: 0.7rem;
  padding: 0.15rem 0.45rem;
  border-radius: 4px;
  font-weight: 600;
}

.type-pill.domain {
  background: rgba(56, 189, 248, 0.15);
  color: #38bdf8;
  border: 1px solid rgba(56, 189, 248, 0.3);
}

.type-pill.program {
  background: rgba(168, 85, 247, 0.15);
  color: #c084fc;
  border: 1px solid rgba(168, 85, 247, 0.3);
}

.type-pill.course {
  background: rgba(34, 197, 94, 0.15);
  color: #4ade80;
  border: 1px solid rgba(34, 197, 94, 0.3);
}

.code-col code {
  font-size: 0.76rem;
  background: #1e293b;
  color: #cbd5e1;
  padding: 0.15rem 0.35rem;
  border-radius: 4px;
}

.name-col strong {
  font-weight: 500;
  color: #f8fafc;
}

.mode-badge {
  font-size: 0.72rem;
  padding: 0.15rem 0.45rem;
  border-radius: 4px;
  font-weight: 500;
}

.mode-badge.mandatory {
  background: rgba(59, 130, 246, 0.15);
  color: #93c5fd;
  border: 1px solid rgba(59, 130, 246, 0.3);
}

.mode-badge.optional {
  background: rgba(148, 163, 184, 0.15);
  color: #94a3b8;
  border: 1px solid rgba(148, 163, 184, 0.3);
}

.parent-text {
  font-size: 0.78rem;
  color: #94a3b8;
}

.muted-dash {
  color: #475569;
}

.btn-remove-allocation {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  background: rgba(239, 68, 68, 0.15);
  color: #f87171;
  border: 1px solid rgba(239, 68, 68, 0.3);
  padding: 0.25rem 0.5rem;
  border-radius: 4px;
  font-size: 0.72rem;
  cursor: pointer;
  transition: all 0.12s ease;
}

.btn-remove-allocation:hover:not(:disabled) {
  background: #ef4444;
  color: #ffffff;
}

.spinner {
  animation: spin 1s linear infinite;
}

.spin-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.course-cascade-box {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.sub-form-group {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.sub-form-group label {
  font-size: 0.8rem;
  color: #94a3b8;
  font-weight: 500;
}


.all-and-search-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.course-search-box {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  background: #0f172a;
  border: 1px solid #334155;
  border-radius: 4px;
  padding: 0.35rem 0.55rem;
  flex: 1;
}

.course-search-box .search-icon {
  color: #64748b;
  flex-shrink: 0;
}

.course-search-input {
  background: transparent;
  border: none;
  color: #f1f5f9;
  font-size: 0.76rem;
  width: 100%;
  outline: none;
}

.course-search-input::placeholder {
  color: #64748b;
}


.picker-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 1rem;
  color: #94a3b8;
  font-size: 0.78rem;
}

.scroll-loading-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  padding: 0.5rem;
  font-size: 0.75rem;
  color: #38bdf8;
  background: rgba(15, 23, 42, 0.4);
  border-radius: 4px;
}

.scroll-end-hint {
  text-align: center;
  padding: 0.45rem;
  font-size: 0.72rem;
  color: #64748b;
  border-top: 1px dashed #334155;
  margin-top: 0.25rem;
}

.search-spinner {
  color: #38bdf8;
  flex-shrink: 0;
}

.searchable-dropdown-wrapper {
  position: relative;
  width: 100%;
}

.searchable-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  text-align: left;
  cursor: pointer;
  background: #0f172a;
  border: 1px solid #334155;
  color: #94a3b8;
  padding: 0.55rem 0.85rem;
  border-radius: 6px;
  font-size: 0.82rem;
  transition: all 0.15s ease;
}

.searchable-trigger.has-value {
  color: #f8fafc;
}

.searchable-trigger.is-open {
  border-color: #38bdf8;
  box-shadow: 0 0 0 2px rgba(56, 189, 248, 0.2);
}

.dropdown-chevron {
  color: #64748b;
  transition: transform 0.2s ease;
  flex-shrink: 0;
}

.dropdown-chevron.rotated {
  transform: rotate(180deg);
  color: #38bdf8;
}

.searchable-dropdown-menu {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  z-index: 60;
  background: #1e293b;
  border: 1px solid #475569;
  border-radius: 6px;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.5);
  overflow: hidden;
  animation: fadeInDown 0.15s ease;
}

@keyframes fadeInDown {
  from {
    opacity: 0;
    transform: translateY(-4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.dropdown-search-box {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.45rem 0.65rem;
  border-bottom: 1px solid #334155;
  background: #0f172a;
}

.dropdown-search-input {
  background: transparent;
  border: none;
  outline: none;
  color: #f8fafc;
  font-size: 0.78rem;
  width: 100%;
}

.btn-clear-search {
  background: transparent;
  border: none;
  color: #64748b;
  cursor: pointer;
  display: flex;
  align-items: center;
  padding: 2px;
}

.btn-clear-search:hover {
  color: #f8fafc;
}

.dropdown-items-list {
  max-height: 220px;
  overflow-y: auto;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.5rem 0.75rem;
  cursor: pointer;
  font-size: 0.78rem;
  color: #cbd5e1;
  transition: background 0.12s;
}

.dropdown-item:hover {
  background: #334155;
  color: #f8fafc;
}

.dropdown-item.is-selected {
  background: rgba(14, 165, 233, 0.15);
  color: #38bdf8;
  font-weight: 500;
}

.item-code {
  color: #38bdf8;
  font-family: monospace;
  font-size: 0.74rem;
}

.item-name {
  flex: 1;
}

.item-checked {
  color: #38bdf8;
  flex-shrink: 0;
}

.dropdown-state-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  padding: 0.65rem;
  font-size: 0.74rem;
  color: #94a3b8;
}

.dropdown-state-row.empty-state-row {
  color: #64748b;
  padding: 0.9rem;
}

.dropdown-state-row.loading-more {
  color: #38bdf8;
  border-top: 1px dashed #334155;
}

.dropdown-state-row.end-hint {
  color: #64748b;
  border-top: 1px dashed #334155;
  font-size: 0.7rem;
}

</style>
