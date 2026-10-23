<template>
  <div class="unit-allocation-page">
    <!-- BREADCRUMB BAR -->
    <nav class="breadcrumb-bar" aria-label="Breadcrumb">
      <span class="crumb">Hệ thống</span>
      <ChevronRight :size="12" class="crumb-separator" />
      <span class="crumb">Quản trị Đào tạo</span>
      <ChevronRight :size="12" class="crumb-separator" />
      <span class="crumb active">Phân bổ theo Đơn vị</span>
    </nav>

    <!-- PAGE HEADER -->
    <header class="page-header">
      <div class="header-left">
        <div class="title-with-badge">
          <h2>Phân bổ Đào tạo theo Đơn vị</h2>
          <span class="badge-live">
            <Sparkles :size="11" />
            <span>Ma trận quyền đào tạo</span>
          </span>
        </div>
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
          <div class="panel-header-title">
            <Building2 :size="16" class="panel-icon" />
            <h3>Thiết lập Phân bổ mới</h3>
          </div>
          <span class="step-guide-badge">4 bước thiết lập</span>
        </div>

        <form @submit.prevent="handleAssignAllocation" class="allocation-form">
          <!-- BƯỚC 1: CHỌN ĐƠN VỊ -->
          <div class="step-group">
            <div class="step-header">
              <span class="step-number">01</span>
              <label for="unit-select">Đơn vị nhận đào tạo (*)</label>
            </div>
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
          <div class="step-group">
            <div class="step-header">
              <span class="step-number">02</span>
              <label>Cấp danh mục phân bổ (*)</label>
            </div>
            <div class="category-type-selector">
              <label class="radio-card" :class="{ active: categoryType === 'domain' }">
                <input type="radio" value="domain" v-model="categoryType" />
                <Globe :size="15" />
                <span class="radio-card-title">Lĩnh vực</span>
                <span class="radio-card-sub">Cấp vĩ mô</span>
              </label>
              <label class="radio-card" :class="{ active: categoryType === 'program' }">
                <input type="radio" value="program" v-model="categoryType" />
                <Folder :size="15" />
                <span class="radio-card-title">Chương trình</span>
                <span class="radio-card-sub">Theo nhóm</span>
              </label>
              <label class="radio-card" :class="{ active: categoryType === 'course' }">
                <input type="radio" value="course" v-model="categoryType" />
                <BookOpen :size="15" />
                <span class="radio-card-title">Khóa học</span>
                <span class="radio-card-sub">Từng khóa lẻ</span>
              </label>
            </div>
          </div>

          <!-- BƯỚC 3: CHỌN NỘI DUNG TƯƠNG ỨNG -->
          <div class="step-group dynamic-group">
            <div class="step-header">
              <span class="step-number">03</span>
              <label>Nội dung chi tiết (*)</label>
            </div>

            <!-- CASE 3A: CHỌN LĨNH VỰC -->
            <div v-if="categoryType === 'domain'" class="sub-form-group">
              <label class="sub-label">Chọn Lĩnh vực đào tạo:</label>
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

            <!-- CASE 3B: CHỌN CHƯƠNG TRÌNH -->
            <div v-if="categoryType === 'program'" class="course-cascade-box">
              <div class="sub-form-group">
                <label class="sub-label">Lĩnh vực quản lý:</label>
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

              <div v-if="programFilterDomainId" class="sub-form-group">
                <label class="sub-label">Chương trình đào tạo:</label>
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

            <!-- CASE 3C: CHỌN KHÓA HỌC -->
            <div v-if="categoryType === 'course'" class="course-cascade-box">
              <div class="sub-form-group">
                <label class="sub-label">Lĩnh vực:</label>
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

              <div v-if="courseFilterDomainId" class="sub-form-group">
                <label class="sub-label">Chương trình đào tạo:</label>
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

              <!-- KHÓA HỌC CON -->
              <div v-if="targetProgramId" class="sub-courses-picker">
                <div class="picker-header">
                  <label class="picker-title">Khóa học áp dụng (*):</label>
                  <span class="course-count-badge">
                    <span v-if="selectAllCoursesInProgram">
                      TẤT CẢ ({{ totalProgramCourses }} khóa)
                    </span>
                    <span v-else>
                      {{ selectedSubCourseIds.length }} / {{ totalProgramCourses }} khóa
                    </span>
                  </span>
                </div>

                <div class="all-and-search-row">
                  <label class="all-courses-checkbox">
                    <input
                      type="checkbox"
                      v-model="selectAllCoursesInProgram"
                      @change="handleToggleAllCourses"
                    />
                    <span class="all-text">CHỌN TOÀN BỘ</span>
                  </label>

                  <div class="course-search-box">
                    <Search :size="13" class="search-icon" />
                    <input
                      type="text"
                      v-model="courseSearchQuery"
                      class="course-search-input"
                      placeholder="Lọc nhanh theo mã hoặc tên khóa..."
                    />
                    <Loader2 v-if="searchingCourses" :size="13" class="spinner search-spinner" />
                  </div>
                </div>

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
          </div>

          <!-- BƯỚC 4: HÌNH THỨC ÁP DỤNG -->
          <div class="step-group">
            <div class="step-header">
              <span class="step-number">04</span>
              <label>Hình thức áp dụng (*)</label>
            </div>
            <div class="grant-mode-cards">
              <label class="grant-mode-card" :class="{ selected: grantType === 'MANDATORY' }">
                <input type="radio" value="MANDATORY" v-model="grantType" />
                <div class="mode-card-radio"></div>
                <div class="mode-card-content">
                  <div class="mode-card-title">
                    <span class="dot mandatory-dot"></span>
                    <span>Bắt buộc hoàn thành</span>
                  </div>
                  <span class="mode-card-desc">Gắn KPI tính tỉ lệ tuân thủ & lộ trình cho nhân sự</span>
                </div>
              </label>

              <label class="grant-mode-card" :class="{ selected: grantType === 'OPTIONAL' }">
                <input type="radio" value="OPTIONAL" v-model="grantType" />
                <div class="mode-card-radio"></div>
                <div class="mode-card-content">
                  <div class="mode-card-title">
                    <span class="dot optional-dot"></span>
                    <span>Tự chọn / Tham khảo</span>
                  </div>
                  <span class="mode-card-desc">Kho tự học mở rộng, nhân viên tùy ý lựa chọn học</span>
                </div>
              </label>
            </div>
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
              <span>{{ saving ? "Đang lưu phân bổ..." : "Xác nhận Lưu Phân bổ" }}</span>
            </button>
          </div>
        </form>
      </section>

      <!-- RIGHT PANEL: CURRENT ALLOCATION MATRIX -->
      <section class="card matrix-panel">
        <div class="panel-header matrix-header">
          <div class="matrix-header-main">
            <div class="panel-header-title">
              <ListOrdered :size="16" class="panel-icon" />
              <h3>Danh sách Đào tạo đã phân bổ</h3>
            </div>
            <span v-if="currentUnit" class="current-unit-chip">
              <Building2 :size="13" />
              <span>{{ currentUnit.name }}</span>
            </span>
          </div>

          <!-- KPI CHIPS (Impeccable Style) -->
          <div v-if="selectedUnitId && assignedAllocations.length" class="kpi-chips-bar">
            <span class="kpi-chip kpi-total">
              <span class="kpi-label">Tổng số</span>
              <strong class="kpi-val">{{ assignedAllocations.length }}</strong>
            </span>
            <span class="kpi-chip kpi-mandatory">
              <span class="dot mandatory-dot"></span>
              <span class="kpi-label">Bắt buộc</span>
              <strong class="kpi-val">{{ mandatoryCount }}</strong>
            </span>
            <span class="kpi-chip kpi-optional">
              <span class="dot optional-dot"></span>
              <span class="kpi-label">Tự chọn</span>
              <strong class="kpi-val">{{ optionalCount }}</strong>
            </span>
          </div>
        </div>

        <!-- QUICK FILTER BAR -->
        <div v-if="selectedUnitId && assignedAllocations.length" class="matrix-filter-bar">
          <div class="quick-filter-input-wrap">
            <Search :size="14" class="quick-filter-icon" />
            <input
              type="text"
              v-model="tableFilterQuery"
              class="quick-filter-input"
              placeholder="Lọc nhanh danh sách theo mã, tên nội dung đào tạo..."
            />
            <button
              v-if="tableFilterQuery"
              type="button"
              class="btn-clear-filter"
              @click="tableFilterQuery = ''"
              title="Xóa lọc"
            >
              <X :size="12" />
            </button>
          </div>
          <span v-if="tableFilterQuery" class="filter-count-badge">
            Hiển thị {{ filteredAssignedAllocations.length }} / {{ assignedAllocations.length }}
          </span>
        </div>

        <!-- EMPTY STATE: CHƯA CHỌN ĐƠN VỊ -->
        <div v-if="!selectedUnitId" class="empty-state select-unit-prompt">
          <div class="empty-icon-ring">
            <Building2 :size="32" class="empty-icon" />
          </div>
          <h4>Vui lòng chọn Đơn vị ở cột bên trái</h4>
          <p>Chọn một Đơn vị / Chi nhánh để xem ma trận phân bổ các nội dung đào tạo đã áp dụng.</p>
        </div>

        <!-- EMPTY STATE: LOADING -->
        <div v-else-if="loading" class="empty-state">
          <Loader2 :size="32" class="spinner" />
          <p>Đang tải dữ liệu phân bổ...</p>
        </div>

        <!-- EMPTY STATE: CHƯA CÓ PHÂN BỔ -->
        <div v-else-if="!assignedAllocations.length" class="empty-state">
          <div class="empty-icon-ring">
            <Inbox :size="32" class="empty-icon" />
          </div>
          <h4>Chưa có phân bổ nào cho đơn vị này</h4>
          <p>Sử dụng 4 bước biểu mẫu bên trái để gán Lĩnh vực, Chương trình hoặc Khóa học cho đơn vị.</p>
        </div>

        <!-- EMPTY STATE: LỌC KHÔNG RA KẾT QUẢ -->
        <div v-else-if="!filteredAssignedAllocations.length" class="empty-state">
          <Filter :size="32" class="empty-icon" />
          <h4>Không tìm thấy nội dung phù hợp</h4>
          <p>Không có phân bổ nào khớp với từ khóa "<strong>{{ tableFilterQuery }}</strong>".</p>
          <button type="button" class="btn btn-secondary btn-sm" @click="tableFilterQuery = ''">
            Xóa bộ lọc
          </button>
        </div>

        <!-- ALLOCATION TABLE -->
        <div v-else class="allocation-table-wrapper">
          <table class="allocation-table">
            <thead>
              <tr>
                <th style="width: 110px;">Cấp bậc</th>
                <th style="width: 120px;">Mã nội dung</th>
                <th>Tên Nội dung Đào tạo</th>
                <th style="width: 140px;">Hình thức</th>
                <th>Thuộc Chương trình</th>
                <th class="actions-col" style="width: 80px;">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in filteredAssignedAllocations"
                :key="`${item.type}_${item.id}`"
                :class="{ 'highlight-row': item.type === 'course' }"
              >
                <td>
                  <span class="type-pill" :class="item.type">
                    <span class="pill-dot"></span>
                    <span>{{ item.typeLabel }}</span>
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
                    <span class="dot" :class="item.grantType === 'MANDATORY' ? 'mandatory-dot' : 'optional-dot'"></span>
                    <span>{{ item.grantType === 'MANDATORY' ? 'Bắt buộc' : 'Tự chọn' }}</span>
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
                    <Trash2 :size="13" />
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
  ChevronRight,
  X,
  Sparkles,
  Filter,
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

// Table quick filter & KPI metrics (Impeccable style)
const tableFilterQuery = ref("");

const filteredAssignedAllocations = computed(() => {
  if (!tableFilterQuery.value.trim()) return assignedAllocations.value;
  const q = tableFilterQuery.value.trim().toLowerCase();
  return assignedAllocations.value.filter(
    (item) =>
      item.name.toLowerCase().includes(q) ||
      item.code.toLowerCase().includes(q) ||
      item.typeLabel.toLowerCase().includes(q) ||
      (item.parentProgramName && item.parentProgramName.toLowerCase().includes(q))
  );
});

const mandatoryCount = computed(() =>
  assignedAllocations.value.filter((a) => a.grantType === "MANDATORY").length
);

const optionalCount = computed(() =>
  assignedAllocations.value.filter((a) => a.grantType === "OPTIONAL").length
);

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
/* ==========================================================================
   IMPECCABLE DESIGN SYSTEM: ENTERPRISE OPERATE THEME
   Deep Space Slate navy Ground, High Contrast, Micro-interactions
   ========================================================================== */

.unit-allocation-page {
  padding: 1.5rem 2rem 2.5rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  max-width: 1600px;
  margin: 0 auto;
  width: 100%;
  color: #e2e8f0;
}

/* BREADCRUMB BAR */
.breadcrumb-bar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.76rem;
  font-weight: 500;
  color: #64748b;
}

.crumb {
  color: #94a3b8;
  transition: color 0.15s ease;
}

.crumb-separator {
  color: #475569;
}

.crumb.active {
  color: #38bdf8;
  font-weight: 600;
}

/* PAGE HEADER */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  padding-bottom: 1.15rem;
  gap: 1rem;
}

.title-with-badge {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.35rem;
  flex-wrap: wrap;
}

.header-left h2 {
  font-size: 1.45rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: #f8fafc;
  margin: 0;
}

.badge-live {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.72rem;
  font-weight: 600;
  padding: 0.2rem 0.55rem;
  border-radius: 9999px;
  background: rgba(56, 189, 248, 0.1);
  color: #38bdf8;
  border: 1px solid rgba(56, 189, 248, 0.25);
  letter-spacing: 0.02em;
}

.subtitle {
  font-size: 0.84rem;
  color: #94a3b8;
  margin: 0;
  line-height: 1.45;
}

/* TWO-COLUMN LAYOUT */
.allocation-layout {
  display: grid;
  grid-template-columns: 480px minmax(0, 1fr);
  gap: 1.5rem;
  align-items: start;
}

@media (max-width: 1100px) {
  .allocation-layout {
    grid-template-columns: 1fr;
  }
}

/* CARDS */
.card {
  background: #0f172a;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 20px -2px rgba(0, 0, 0, 0.45);
  transition: border-color 0.2s ease;
}

.card:hover {
  border-color: rgba(255, 255, 255, 0.12);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.95rem 1.25rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  background: #131d31;
  gap: 0.75rem;
}

.panel-header-title {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.panel-header h3 {
  font-size: 0.95rem;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: #f1f5f9;
  margin: 0;
}

.panel-icon {
  color: #38bdf8;
  flex-shrink: 0;
}

.step-guide-badge {
  font-size: 0.7rem;
  font-weight: 600;
  padding: 0.15rem 0.5rem;
  border-radius: 9999px;
  background: rgba(255, 255, 255, 0.06);
  color: #94a3b8;
  border: 1px solid rgba(255, 255, 255, 0.08);
  font-variant-numeric: tabular-nums;
}

/* FORM PANEL */
.allocation-form {
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.step-group {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

.step-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.step-number {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.7rem;
  font-weight: 700;
  color: #38bdf8;
  background: rgba(56, 189, 248, 0.12);
  border: 1px solid rgba(56, 189, 248, 0.25);
  border-radius: 4px;
  padding: 0.08rem 0.35rem;
  letter-spacing: 0.05em;
}

.step-header label {
  font-size: 0.84rem;
  font-weight: 600;
  color: #f1f5f9;
}

.sub-label {
  font-size: 0.78rem;
  font-weight: 500;
  color: #94a3b8;
}

.sub-form-group {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

/* INPUTS & SELECTS */
.form-control {
  width: 100%;
  background: #131d31;
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #f8fafc;
  border-radius: 8px;
  padding: 0.65rem 0.85rem;
  font-size: 0.84rem;
  outline: none;
  transition: all 0.15s ease;
}

.form-control:focus {
  border-color: #38bdf8;
  box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.15);
  background: #17243c;
}

/* CATEGORY SELECTOR (STEP 2) */
.category-type-selector {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.55rem;
}

.radio-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  gap: 0.35rem;
  background: #131d31;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 0.75rem 0.5rem;
  cursor: pointer;
  color: #94a3b8;
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
  user-select: none;
}

.radio-card input {
  display: none;
}

.radio-card:hover {
  background: #1a2740;
  border-color: rgba(255, 255, 255, 0.18);
  color: #f1f5f9;
  transform: translateY(-1px);
}

.radio-card.active {
  background: rgba(56, 189, 248, 0.12);
  border-color: #38bdf8;
  color: #38bdf8;
  box-shadow: 0 0 12px rgba(56, 189, 248, 0.15);
}

.radio-card-title {
  font-size: 0.82rem;
  font-weight: 600;
  color: inherit;
}

.radio-card-sub {
  font-size: 0.68rem;
  color: #64748b;
}

.radio-card.active .radio-card-sub {
  color: #7dd3fc;
}

/* STEP 4: GRANT MODE CARDS */
.grant-mode-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.65rem;
}

.grant-mode-card {
  display: flex;
  align-items: flex-start;
  gap: 0.65rem;
  background: #131d31;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 0.75rem;
  cursor: pointer;
  transition: all 0.15s ease;
  user-select: none;
}

.grant-mode-card input {
  display: none;
}

.grant-mode-card:hover {
  background: #1a2740;
  border-color: rgba(255, 255, 255, 0.15);
}

.mode-card-radio {
  width: 14px;
  height: 14px;
  border-radius: 9999px;
  border: 1.5px solid #475569;
  margin-top: 2px;
  flex-shrink: 0;
  transition: all 0.15s ease;
  position: relative;
}

.grant-mode-card.selected .mode-card-radio {
  border-color: #38bdf8;
  background: #38bdf8;
  box-shadow: 0 0 6px rgba(56, 189, 248, 0.4);
}

.grant-mode-card.selected .mode-card-radio::after {
  content: "";
  position: absolute;
  top: 3px;
  left: 3px;
  width: 5px;
  height: 5px;
  border-radius: 9999px;
  background: #090e17;
}

.grant-mode-card.selected {
  background: rgba(56, 189, 248, 0.06);
  border-color: rgba(56, 189, 248, 0.4);
}

.mode-card-content {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.mode-card-title {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.82rem;
  font-weight: 600;
  color: #f1f5f9;
}

.mode-card-desc {
  font-size: 0.71rem;
  color: #94a3b8;
  line-height: 1.35;
}

/* DOT STATUS INDICATORS */
.dot {
  width: 6px;
  height: 6px;
  border-radius: 9999px;
  display: inline-block;
  flex-shrink: 0;
}

.mandatory-dot {
  background: #10b981;
  box-shadow: 0 0 6px rgba(16, 185, 129, 0.5);
}

.optional-dot {
  background: #f59e0b;
  box-shadow: 0 0 6px rgba(245, 158, 11, 0.5);
}

/* BUTTONS */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  padding: 0.6rem 1rem;
  font-size: 0.82rem;
  font-weight: 600;
  border-radius: 8px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.15s cubic-bezier(0.4, 0, 0.2, 1);
  text-decoration: none;
}

.btn:active:not(:disabled) {
  transform: scale(0.98);
}

.btn-primary {
  background: linear-gradient(135deg, #0284c7 0%, #0369a1 100%);
  color: #ffffff;
  border: 1px solid rgba(56, 189, 248, 0.3);
  box-shadow: 0 2px 8px rgba(2, 132, 199, 0.35);
}

.btn-primary:hover:not(:disabled) {
  background: linear-gradient(135deg, #0ea5e9 0%, #0284c7 100%);
  box-shadow: 0 4px 14px rgba(56, 189, 248, 0.4);
}

.btn-secondary {
  background: #131d31;
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #cbd5e1;
}

.btn-secondary:hover:not(:disabled) {
  background: #1a2740;
  border-color: rgba(255, 255, 255, 0.2);
  color: #ffffff;
}

.btn-submit {
  width: 100%;
  padding: 0.75rem;
  font-size: 0.88rem;
  letter-spacing: 0.01em;
}

.btn-sm {
  padding: 0.4rem 0.75rem;
  font-size: 0.76rem;
}

.btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  transform: none !important;
}

/* RIGHT PANEL: MATRIX & HEADER */
.matrix-header {
  flex-direction: column;
  align-items: stretch;
  gap: 0.75rem;
  padding: 1rem 1.25rem 0.85rem;
}

.matrix-header-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
}

.current-unit-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.76rem;
  font-weight: 600;
  background: rgba(56, 189, 248, 0.1);
  color: #38bdf8;
  border: 1px solid rgba(56, 189, 248, 0.25);
  padding: 0.25rem 0.65rem;
  border-radius: 9999px;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* KPI SUMMARY CHIPS (Impeccable style) */
.kpi-chips-bar {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding-top: 0.5rem;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
}

.kpi-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.74rem;
  padding: 0.2rem 0.6rem;
  border-radius: 6px;
  background: #090e17;
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.kpi-label {
  color: #94a3b8;
}

.kpi-val {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-weight: 700;
  font-size: 0.78rem;
  color: #f8fafc;
}

.kpi-total .kpi-val {
  color: #38bdf8;
}

.kpi-mandatory .kpi-val {
  color: #34d399;
}

.kpi-optional .kpi-val {
  color: #fbbf24;
}

/* QUICK FILTER BAR */
.matrix-filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.75rem 1.25rem;
  background: #0c1322;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.quick-filter-input-wrap {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: #131d31;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  padding: 0.4rem 0.65rem;
  flex: 1;
  max-width: 480px;
  transition: border-color 0.15s ease;
}

.quick-filter-input-wrap:focus-within {
  border-color: #38bdf8;
  box-shadow: 0 0 0 2px rgba(56, 189, 248, 0.15);
}

.quick-filter-icon {
  color: #64748b;
  flex-shrink: 0;
}

.quick-filter-input {
  background: transparent;
  border: none;
  outline: none;
  color: #f1f5f9;
  font-size: 0.8rem;
  width: 100%;
}

.quick-filter-input::placeholder {
  color: #64748b;
}

.btn-clear-filter {
  background: transparent;
  border: none;
  color: #64748b;
  cursor: pointer;
  display: flex;
  align-items: center;
  padding: 2px;
}

.btn-clear-filter:hover {
  color: #f8fafc;
}

.filter-count-badge {
  font-size: 0.72rem;
  color: #94a3b8;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

/* TABLE STYLING */
.allocation-table-wrapper {
  overflow-x: auto;
  max-height: 650px;
}

.allocation-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 0.82rem;
  text-align: left;
}

.allocation-table thead th {
  position: sticky;
  top: 0;
  z-index: 10;
  background: #131d31;
  color: #94a3b8;
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 0.75rem 1rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  white-space: nowrap;
}

.allocation-table tbody tr {
  transition: background-color 0.12s ease;
}

.allocation-table tbody tr:hover {
  background-color: rgba(56, 189, 248, 0.04);
}

.allocation-table td {
  padding: 0.8rem 1rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  color: #cbd5e1;
  vertical-align: middle;
}

/* TYPE PILL (Cấp bậc đào tạo) */
.type-pill {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.7rem;
  font-weight: 600;
  padding: 0.18rem 0.5rem;
  border-radius: 6px;
  white-space: nowrap;
}

.pill-dot {
  width: 5px;
  height: 5px;
  border-radius: 9999px;
}

.type-pill.domain {
  background: rgba(56, 189, 248, 0.1);
  color: #38bdf8;
  border: 1px solid rgba(56, 189, 248, 0.25);
}
.type-pill.domain .pill-dot { background: #38bdf8; }

.type-pill.program {
  background: rgba(168, 85, 247, 0.12);
  color: #c084fc;
  border: 1px solid rgba(168, 85, 247, 0.25);
}
.type-pill.program .pill-dot { background: #c084fc; }

.type-pill.course {
  background: rgba(34, 197, 94, 0.12);
  color: #4ade80;
  border: 1px solid rgba(34, 197, 94, 0.25);
}
.type-pill.course .pill-dot { background: #4ade80; }

/* CODE & NAME COLS */
.code-col code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.75rem;
  background: #131d31;
  color: #93c5fd;
  padding: 0.2rem 0.45rem;
  border-radius: 4px;
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.name-col strong {
  font-weight: 600;
  color: #f8fafc;
}

/* MODE BADGE (Bắt buộc / Tự chọn) */
.mode-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.74rem;
  font-weight: 600;
  padding: 0.2rem 0.55rem;
  border-radius: 6px;
  white-space: nowrap;
}

.mode-badge.mandatory {
  background: rgba(16, 185, 129, 0.1);
  color: #34d399;
  border: 1px solid rgba(16, 185, 129, 0.25);
}

.mode-badge.optional {
  background: rgba(245, 158, 11, 0.1);
  color: #fbbf24;
  border: 1px solid rgba(245, 158, 11, 0.25);
}

.parent-text {
  font-size: 0.78rem;
  color: #94a3b8;
}

.muted-dash {
  color: #475569;
}

/* ACTION BUTTON */
.actions-col {
  text-align: right;
}

.btn-remove-allocation {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  background: rgba(244, 63, 94, 0.1);
  color: #fda4af;
  border: 1px solid rgba(244, 63, 94, 0.25);
  padding: 0.25rem 0.55rem;
  border-radius: 6px;
  font-size: 0.74rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-remove-allocation:hover:not(:disabled) {
  background: #f43f5e;
  color: #ffffff;
  border-color: #f43f5e;
  box-shadow: 0 0 10px rgba(244, 63, 94, 0.4);
}

.btn-remove-allocation:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* EMPTY STATES */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4.5rem 2rem;
  text-align: center;
  color: #64748b;
  gap: 0.65rem;
}

.empty-icon-ring {
  width: 64px;
  height: 64px;
  border-radius: 9999px;
  background: #131d31;
  border: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 0.5rem;
  box-shadow: 0 0 24px rgba(0, 0, 0, 0.4);
}

.empty-icon {
  color: #38bdf8;
  opacity: 0.8;
}

.empty-state h4 {
  font-size: 1.02rem;
  font-weight: 600;
  color: #f1f5f9;
  margin: 0;
}

.empty-state p {
  font-size: 0.84rem;
  color: #94a3b8;
  max-width: 440px;
  margin: 0;
  line-height: 1.5;
}

/* SEARCHABLE PROGRAM DROPDOWN (STEP 3) */
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
  background: #131d31;
  border: 1px solid rgba(255, 255, 255, 0.1);
  color: #94a3b8;
  padding: 0.65rem 0.85rem;
  border-radius: 8px;
  font-size: 0.84rem;
  transition: all 0.15s ease;
}

.searchable-trigger.has-value {
  color: #f8fafc;
}

.searchable-trigger.is-open {
  border-color: #38bdf8;
  box-shadow: 0 0 0 3px rgba(56, 189, 248, 0.15);
  background: #17243c;
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
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  z-index: 70;
  background: #1a263d;
  border: 1px solid rgba(56, 189, 248, 0.25);
  border-radius: 8px;
  box-shadow: 0 12px 30px -5px rgba(0, 0, 0, 0.7);
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
  gap: 0.45rem;
  padding: 0.55rem 0.75rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  background: #131d31;
}

.dropdown-search-input {
  background: transparent;
  border: none;
  outline: none;
  color: #f8fafc;
  font-size: 0.8rem;
  width: 100%;
}

.dropdown-items-list {
  max-height: 220px;
  overflow-y: auto;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.55rem 0.85rem;
  cursor: pointer;
  font-size: 0.8rem;
  color: #cbd5e1;
  transition: background 0.12s;
}

.dropdown-item:hover {
  background: #243452;
  color: #f8fafc;
}

.dropdown-item.is-selected {
  background: rgba(56, 189, 248, 0.15);
  color: #38bdf8;
  font-weight: 600;
}

.item-code {
  color: #38bdf8;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.75rem;
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
  gap: 0.45rem;
  padding: 0.75rem;
  font-size: 0.76rem;
  color: #94a3b8;
}

.dropdown-state-row.empty-state-row {
  color: #64748b;
  padding: 1.1rem;
}

.dropdown-state-row.loading-more {
  color: #38bdf8;
  border-top: 1px dashed rgba(255, 255, 255, 0.1);
}

.dropdown-state-row.end-hint {
  color: #64748b;
  border-top: 1px dashed rgba(255, 255, 255, 0.1);
  font-size: 0.72rem;
}

/* SUB-COURSES PICKER (STEP 3C) */
.sub-courses-picker {
  margin-top: 0.5rem;
  background: #090e17;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
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

.picker-title {
  font-size: 0.8rem;
  font-weight: 600;
  color: #cbd5e1;
}

.course-count-badge {
  font-size: 0.72rem;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  background: rgba(56, 189, 248, 0.12);
  color: #38bdf8;
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
  border: 1px solid rgba(56, 189, 248, 0.2);
}

.all-and-search-row {
  display: flex;
  align-items: center;
  gap: 0.55rem;
}

.all-courses-checkbox {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  background: #131d31;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  padding: 0.45rem 0.7rem;
  cursor: pointer;
  user-select: none;
}

.all-courses-checkbox .all-text {
  font-size: 0.76rem;
  font-weight: 700;
  color: #38bdf8;
  letter-spacing: 0.02em;
}

.course-search-box {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  background: #131d31;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  padding: 0.45rem 0.65rem;
  flex: 1;
}

.course-search-input {
  background: transparent;
  border: none;
  color: #f1f5f9;
  font-size: 0.78rem;
  width: 100%;
  outline: none;
}

.course-search-input::placeholder {
  color: #64748b;
}

.courses-checkbox-list {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  max-height: 220px;
  overflow-y: auto;
  border-top: 1px dashed rgba(255, 255, 255, 0.1);
  padding-top: 0.5rem;
}

.course-checkbox-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.8rem;
  color: #cbd5e1;
  padding: 0.45rem 0.6rem;
  border-radius: 6px;
  background: #131d31;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.12s;
}

.course-checkbox-item:hover {
  background: #1a2740;
  border-color: rgba(255, 255, 255, 0.1);
  color: #f8fafc;
}

.course-code-tag {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  color: #38bdf8;
  font-size: 0.74rem;
}

.picker-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 1rem;
  color: #94a3b8;
  font-size: 0.8rem;
}

.scroll-loading-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  padding: 0.5rem;
  font-size: 0.75rem;
  color: #38bdf8;
  background: rgba(15, 23, 42, 0.5);
  border-radius: 4px;
}

.scroll-end-hint {
  text-align: center;
  padding: 0.45rem;
  font-size: 0.72rem;
  color: #64748b;
  border-top: 1px dashed rgba(255, 255, 255, 0.1);
  margin-top: 0.25rem;
}

/* ANIMATIONS & UTILS */
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
</style>
