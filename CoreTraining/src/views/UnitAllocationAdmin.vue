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
          <span class="badge-count">{{ units.length }} đơn vị</span>
        </div>
        <p class="subtitle">
          Danh mục Đơn vị & Chi nhánh trong toàn hệ thống. Chọn đơn vị để phân bổ nhiều Lĩnh vực, Chương trình và Khóa học cùng lúc.
        </p>
      </div>
      <div class="header-actions">
        <button type="button" class="btn btn-secondary" @click="loadData" :disabled="loading">
          <RotateCcw :size="14" :class="{ 'spin-icon': loading }" />
          <span>Làm mới</span>
        </button>
      </div>
    </header>

    <!-- KPI SUMMARY CARDS -->
    <section class="kpi-cards-grid">
      <div class="kpi-stat-card">
        <div class="stat-icon-wrap icon-blue">
          <Building2 :size="20" />
        </div>
        <div class="stat-info">
          <span class="stat-label">Tổng số Đơn vị</span>
          <strong class="stat-value">{{ units.length }}</strong>
        </div>
      </div>

      <div class="kpi-stat-card">
        <div class="stat-icon-wrap icon-green">
          <Check :size="20" />
        </div>
        <div class="stat-info">
          <span class="stat-label">Đơn vị đã phân bổ</span>
          <strong class="stat-value">{{ allocatedUnitsCount }}</strong>
        </div>
      </div>

      <div class="kpi-stat-card">
        <div class="stat-icon-wrap icon-teal">
          <Globe :size="20" />
        </div>
        <div class="stat-info">
          <span class="stat-label">Lĩnh vực đang mở</span>
          <strong class="stat-value">{{ activeDomains.length }}</strong>
        </div>
      </div>

      <div class="kpi-stat-card">
        <div class="stat-icon-wrap icon-amber">
          <Folder :size="20" />
        </div>
        <div class="stat-info">
          <span class="stat-label">Chương trình đào tạo</span>
          <strong class="stat-value">{{ activePrograms.length }}</strong>
        </div>
      </div>
    </section>

    <!-- MAIN DIRECTORY PANEL -->
    <section class="card directory-panel">
      <!-- DIRECTORY TOOLBAR -->
      <div class="directory-toolbar">
        <div class="search-box-wrap">
          <Search :size="15" class="search-icon" />
          <input
            type="text"
            v-model="unitDirectoryQuery"
            placeholder="Tìm kiếm theo tên đơn vị, chi nhánh, phòng ban..."
            class="directory-search-input"
          />
          <button
            v-if="unitDirectoryQuery"
            type="button"
            class="btn-clear-search"
            @click="unitDirectoryQuery = ''"
          >
            <X :size="14" />
          </button>
        </div>

        <div class="filter-controls">
          <!-- Type Filter -->
          <div class="filter-pills">
            <button
              type="button"
              class="filter-pill"
              :class="{ active: typeFilter === 'ALL' }"
              @click="typeFilter = 'ALL'"
            >
              Tất cả ({{ units.length }})
            </button>
            <button
              type="button"
              class="filter-pill"
              :class="{ active: typeFilter === 'BRANCH' }"
              @click="typeFilter = 'BRANCH'"
            >
              Chi nhánh / Hội sở ({{ branchCount }})
            </button>
            <button
              type="button"
              class="filter-pill"
              :class="{ active: typeFilter === 'DEPARTMENT' }"
              @click="typeFilter = 'DEPARTMENT'"
            >
              Phòng ban ({{ deptCount }})
            </button>
          </div>

          <!-- Status Filter -->
          <div class="filter-pills status-pills">
            <button
              type="button"
              class="filter-pill"
              :class="{ active: statusFilter === 'ALL' }"
              @click="statusFilter = 'ALL'"
            >
              Tất cả trạng thái
            </button>
            <button
              type="button"
              class="filter-pill"
              :class="{ active: statusFilter === 'ALLOCATED' }"
              @click="statusFilter = 'ALLOCATED'"
            >
              Đã có phân bổ
            </button>
            <button
              type="button"
              class="filter-pill"
              :class="{ active: statusFilter === 'UNALLOCATED' }"
              @click="statusFilter = 'UNALLOCATED'"
            >
              Chưa phân bổ
            </button>
          </div>
        </div>
      </div>

      <!-- DIRECTORY TABLE -->
      <div class="table-responsive">
        <div v-if="loading" class="table-loading-state">
          <Loader2 :size="32" class="spin-icon" />
          <p>Đang tải danh sách đơn vị và phân bổ đào tạo...</p>
        </div>

        <div v-else-if="filteredDirectoryUnits.length === 0" class="table-empty-state">
          <Building2 :size="36" class="empty-icon" />
          <h4>Không tìm thấy đơn vị phù hợp</h4>
          <p>Thử tìm kiếm với từ khóa khác hoặc điều chỉnh lại bộ lọc.</p>
        </div>

        <table v-else class="table unit-directory-table">
          <thead>
            <tr>
              <th class="col-stt">STT</th>
              <th class="col-name">Đơn vị / Chi nhánh</th>
              <th class="col-type">Loại hình</th>
              <th class="col-stat text-center">Lĩnh vực</th>
              <th class="col-stat text-center">Chương trình</th>
              <th class="col-stat text-center">Khóa học</th>
              <th class="col-status text-center">Trạng thái</th>
              <th class="col-actions text-right">Thao tác</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(u, index) in pagedDirectoryUnits"
              :key="u.id"
              class="unit-row"
            >
              <td class="col-stt">{{ (unitDirectoryPage - 1) * unitDirectoryPageSize + index + 1 }}</td>
              <td class="col-name">
                <div class="unit-name-cell">
                  <div class="unit-avatar">
                    <Building2 :size="16" />
                  </div>
                  <div class="unit-info">
                    <span class="unit-main-name">{{ u.name }}</span>
                  </div>
                </div>
              </td>
              <td class="col-type">
                <span class="unit-type-tag" :class="u.type === 'DEPARTMENT' ? 'dept' : 'branch'">
                  {{ u.type === 'DEPARTMENT' ? 'Phòng ban' : 'Chi nhánh / Hội sở' }}
                </span>
              </td>
              <td class="col-stat text-center">
                <span class="badge-stat stat-domain">
                  {{ getUnitDomainCount(u.id) }}
                </span>
              </td>
              <td class="col-stat text-center">
                <span class="badge-stat stat-program">
                  {{ getUnitProgramCount(u.id) }}
                </span>
              </td>
              <td class="col-stat text-center">
                <span class="badge-stat stat-course">
                  {{ getUnitCourseCount(u.id) }}
                </span>
              </td>
              <td class="col-status text-center">
                <span
                  class="badge-allocation-status"
                  :class="isUnitAllocated(u.id) ? 'allocated' : 'unallocated'"
                >
                  <span class="dot"></span>
                  <span>{{ isUnitAllocated(u.id) ? 'Đã phân bổ' : 'Chưa phân bổ' }}</span>
                </span>
              </td>
              <td class="col-actions text-right">
                <div class="action-buttons-group">
                  <!-- PRIMARY ACTION: PHÂN BỔ ĐÀO TẠO (Mở Batch Allocation Modal) -->
                  <button
                    type="button"
                    class="btn-action-primary"
                    @click="openBatchModal(u)"
                    title="Mở modal phân bổ nhiều Lĩnh vực, Chương trình và Khóa học"
                  >
                    <FolderPlus :size="14" />
                    <span>Phân bổ đào tạo</span>
                  </button>

                  <!-- SECONDARY ACTION: XEM PHÂN BỔ -->
                  <button
                    type="button"
                    class="btn-action-view"
                    @click="openDetailDrawer(u)"
                    title="Xem chi tiết các nội dung đã phân bổ cho đơn vị này"
                  >
                    <Layers :size="14" />
                    <span>Xem phân bổ</span>
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- PAGINATION BAR -->
      <div v-if="filteredDirectoryUnits.length > unitDirectoryPageSize" class="directory-pagination">
        <span class="pagination-summary">
          Hiển thị {{ (unitDirectoryPage - 1) * unitDirectoryPageSize + 1 }}–{{ Math.min(unitDirectoryPage * unitDirectoryPageSize, filteredDirectoryUnits.length) }} trong tổng số {{ filteredDirectoryUnits.length }} đơn vị
        </span>
        <div class="pagination-buttons">
          <button
            type="button"
            class="page-nav-btn"
            :disabled="unitDirectoryPage <= 1"
            @click="unitDirectoryPage--"
          >
            Trước
          </button>
          <span class="page-current">Trang {{ unitDirectoryPage }} / {{ unitDirectoryTotalPages }}</span>
          <button
            type="button"
            class="page-nav-btn"
            :disabled="unitDirectoryPage >= unitDirectoryTotalPages"
            @click="unitDirectoryPage++"
          >
            Sau
          </button>
        </div>
      </div>
    </section>

    <!-- BATCH ALLOCATION MODAL (THE NEW MULTI-SELECT COMPONENT) -->
    <BatchAllocationModal
      :show="showBatchModal"
      :unit="batchTargetUnit"
      @close="closeBatchModal"
      @saved="handleBatchSaved"
    />

    <!-- UNIT DETAIL DRAWER: VIEW EXISTING ALLOCATIONS -->
    <div v-if="showDetailDrawer && currentDrawerUnit" class="detail-drawer-overlay" @click.self="closeDetailDrawer">
      <div class="detail-drawer-container">
        <!-- DRAWER HEADER -->
        <header class="drawer-header">
          <div class="drawer-header-left">
            <div class="drawer-icon">
              <Building2 :size="18" />
            </div>
            <div>
              <div class="drawer-title-row">
                <h4>{{ currentDrawerUnit.name }}</h4>
                <span class="unit-type-tag" :class="currentDrawerUnit.type === 'DEPARTMENT' ? 'dept' : 'branch'">
                  {{ currentDrawerUnit.type === 'DEPARTMENT' ? 'Phòng ban' : 'Chi nhánh' }}
                </span>
              </div>
              <p class="drawer-subtitle">Danh sách các nội dung đào tạo đang áp dụng cho đơn vị này</p>
            </div>
          </div>
          <div class="drawer-header-right">
            <button
              type="button"
              class="btn btn-primary btn-sm"
              @click="openBatchModalFromDrawer"
            >
              <FolderPlus :size="14" />
              <span>Phân bổ thêm</span>
            </button>
            <button type="button" class="btn-close-drawer" @click="closeDetailDrawer">
              <X :size="18" />
            </button>
          </div>
        </header>

        <!-- DRAWER TOOLBAR & KPI -->
        <div class="drawer-toolbar">
          <div class="drawer-filter-input-wrap">
            <Search :size="14" class="search-icon" />
            <input
              type="text"
              v-model="drawerFilterQuery"
              placeholder="Lọc theo tên, mã hoặc loại..."
              class="drawer-filter-input"
            />
          </div>

          <div class="drawer-grant-pills">
            <button
              type="button"
              class="pill-grant"
              :class="{ active: drawerGrantFilter === 'ALL' }"
              @click="drawerGrantFilter = 'ALL'"
            >
              Tất cả ({{ drawerAssignedAllocations.length }})
            </button>
            <button
              type="button"
              class="pill-grant mandatory"
              :class="{ active: drawerGrantFilter === 'MANDATORY' }"
              @click="drawerGrantFilter = 'MANDATORY'"
            >
              Bắt buộc ({{ drawerMandatoryCount }})
            </button>
            <button
              type="button"
              class="pill-grant optional"
              :class="{ active: drawerGrantFilter === 'OPTIONAL' }"
              @click="drawerGrantFilter = 'OPTIONAL'"
            >
              Tự chọn ({{ drawerOptionalCount }})
            </button>
          </div>
        </div>

        <!-- DRAWER CONTENT LIST -->
        <div class="drawer-body">
          <div v-if="loadingUnitCourses" class="drawer-loading">
            <Loader2 :size="24" class="spin-icon" />
            <span>Đang tải danh sách phân bổ...</span>
          </div>

          <div v-else-if="filteredDrawerAllocations.length === 0" class="drawer-empty">
            <Inbox :size="32" class="empty-icon" />
            <p v-if="drawerAssignedAllocations.length === 0">
              Đơn vị này chưa có nội dung đào tạo nào được phân bổ.
            </p>
            <p v-else>Không tìm thấy mục nào khớp với bộ lọc.</p>
            <button
              v-if="drawerAssignedAllocations.length === 0"
              type="button"
              class="btn btn-primary btn-sm"
              @click="openBatchModalFromDrawer"
            >
              <FolderPlus :size="14" />
              <span>Phân bổ nội dung đầu tiên</span>
            </button>
          </div>

          <div v-else class="drawer-items-list">
            <div
              v-for="item in filteredDrawerAllocations"
              :key="`${item.type}_${item.id}`"
              class="drawer-item-card"
            >
              <div class="item-card-left">
                <span class="item-type-badge" :class="item.type">
                  <Globe v-if="item.type === 'domain'" :size="12" />
                  <Folder v-else-if="item.type === 'program'" :size="12" />
                  <BookOpen v-else :size="12" />
                  <span>{{ item.typeLabel }}</span>
                </span>
                <div class="item-details">
                  <div class="item-title-row">
                    <span class="item-name">{{ item.name }}</span>
                    <span v-if="item.code" class="item-code">{{ item.code }}</span>
                  </div>
                  <span v-if="item.parentProgramName" class="item-parent">
                    Thuộc CT: {{ item.parentProgramName }}
                  </span>
                </div>
              </div>

              <div class="item-card-right">
                <!-- TOGGLE GRANT TYPE BUTTON -->
                <button
                  type="button"
                  class="btn-grant-toggle"
                  :class="item.grantType === 'MANDATORY' ? 'grant-mandatory' : 'grant-optional'"
                  :disabled="togglingId === item.id"
                  @click="handleToggleGrantType(item)"
                  title="Nhấp để đổi giữa Bắt buộc và Tự chọn"
                >
                  <Loader2 v-if="togglingId === item.id" :size="11" class="spin-icon" />
                  <span v-else class="dot"></span>
                  <span>{{ item.grantType === 'MANDATORY' ? 'Bắt buộc' : 'Tự chọn' }}</span>
                </button>

                <!-- REMOVE BUTTON -->
                <button
                  type="button"
                  class="btn-remove-allocation"
                  :disabled="deletingId === item.id"
                  @click="handleRemoveAllocation(item)"
                  title="Gỡ bỏ phân bổ khỏi đơn vị"
                >
                  <Loader2 v-if="deletingId === item.id" :size="13" class="spin-icon" />
                  <Trash2 v-else :size="14" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from "vue";
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
  Layers,
  ChevronRight,
  X,
  FolderPlus,
} from "lucide-vue-next";
import { trainingApi } from "../api/trainingApi";
import { axiosClient } from "../api/axiosClient";
import { showToast } from "../utils/toast";
import type { TrainingDomain, TrainingProgram, Course } from "../types";
import type { OrganizationUnit } from "../types/organization";
import BatchAllocationModal from "../components/BatchAllocationModal.vue";

// State
const loading = ref(false);
const deletingId = ref("");
const togglingId = ref("");

const units = ref<OrganizationUnit[]>([]);
const domains = ref<TrainingDomain[]>([]);
const programs = ref<TrainingProgram[]>([]);
const allCoursesList = ref<Course[]>([]);
const unitAssignedCourses = ref<Course[]>([]);
const loadingUnitCourses = ref(false);

// Batch Modal State
const showBatchModal = ref(false);
const batchTargetUnit = ref<OrganizationUnit | null>(null);

function openBatchModal(unit: OrganizationUnit) {
  batchTargetUnit.value = unit;
  showBatchModal.value = true;
}

function closeBatchModal() {
  showBatchModal.value = false;
  batchTargetUnit.value = null;
}

async function handleBatchSaved() {
  await loadData();
  if (currentDrawerUnit.value) {
    await loadUnitCourses(currentDrawerUnit.value.id);
  }
}

// Detail Drawer State
const showDetailDrawer = ref(false);
const currentDrawerUnit = ref<OrganizationUnit | null>(null);
const drawerFilterQuery = ref("");
const drawerGrantFilter = ref<"ALL" | "MANDATORY" | "OPTIONAL">("ALL");

function openDetailDrawer(unit: OrganizationUnit) {
  currentDrawerUnit.value = unit;
  drawerFilterQuery.value = "";
  drawerGrantFilter.value = "ALL";
  showDetailDrawer.value = true;
  loadUnitCourses(unit.id);
}

function closeDetailDrawer() {
  showDetailDrawer.value = false;
  currentDrawerUnit.value = null;
}

function openBatchModalFromDrawer() {
  if (currentDrawerUnit.value) {
    openBatchModal(currentDrawerUnit.value);
  }
}

// Directory Search & Filters
const unitDirectoryQuery = ref("");
const typeFilter = ref<"ALL" | "BRANCH" | "DEPARTMENT">("ALL");
const statusFilter = ref<"ALL" | "ALLOCATED" | "UNALLOCATED">("ALL");
const unitDirectoryPage = ref(1);
const unitDirectoryPageSize = 10;

// Filter active (non-deleted) items
const activeDomains = computed(() => domains.value.filter((d) => d.status !== "DELETED"));
const activePrograms = computed(() => programs.value.filter((p) => p.status !== "DELETED"));

const branchCount = computed(() => units.value.filter((u) => u.type !== "DEPARTMENT").length);
const deptCount = computed(() => units.value.filter((u) => u.type === "DEPARTMENT").length);

const allocatedUnitsCount = computed(() => {
  return units.value.filter((u) => isUnitAllocated(u.id)).length;
});

function isUnitAllocated(unitId: string): boolean {
  return (
    getUnitDomainCount(unitId) > 0 ||
    getUnitProgramCount(unitId) > 0 ||
    getUnitCourseCount(unitId) > 0
  );
}

function getUnitDomainCount(uId: string): number {
  return activeDomains.value.filter(
    (d) =>
      d.mandatoryUnitIds?.includes(uId) ||
      d.optionalUnitIds?.includes(uId) ||
      d.unitIds?.includes(uId)
  ).length;
}

function getUnitProgramCount(uId: string): number {
  return activePrograms.value.filter(
    (p) =>
      p.mandatoryUnitIds?.includes(uId) ||
      p.optionalUnitIds?.includes(uId) ||
      p.unitIds?.includes(uId)
  ).length;
}

function getUnitCourseCount(uId: string): number {
  return allCoursesList.value.filter(
    (c) =>
      c.status !== "DELETED" &&
      (c.mandatoryUnitIds?.includes(uId) ||
        c.optionalUnitIds?.includes(uId) ||
        c.unitIds?.includes(uId))
  ).length;
}

const filteredDirectoryUnits = computed(() => {
  return units.value.filter((u) => {
    // Search
    if (unitDirectoryQuery.value.trim()) {
      const q = unitDirectoryQuery.value.trim().toLowerCase();
      if (!u.name.toLowerCase().includes(q)) return false;
    }

    // Type filter
    if (typeFilter.value === "DEPARTMENT" && u.type !== "DEPARTMENT") return false;
    if (typeFilter.value === "BRANCH" && u.type === "DEPARTMENT") return false;

    // Status filter
    const allocated = isUnitAllocated(u.id);
    if (statusFilter.value === "ALLOCATED" && !allocated) return false;
    if (statusFilter.value === "UNALLOCATED" && allocated) return false;

    return true;
  });
});

const unitDirectoryTotalPages = computed(() =>
  Math.max(1, Math.ceil(filteredDirectoryUnits.value.length / unitDirectoryPageSize))
);

const pagedDirectoryUnits = computed(() => {
  const start = (unitDirectoryPage.value - 1) * unitDirectoryPageSize;
  return filteredDirectoryUnits.value.slice(start, start + unitDirectoryPageSize);
});

watch([unitDirectoryQuery, typeFilter, statusFilter], () => {
  unitDirectoryPage.value = 1;
});

// Load metadata (Domains, Programs, Units, Courses)
async function loadData() {
  loading.value = true;
  try {
    const [catalog, optionsRes, coursesData] = await Promise.all([
      trainingApi.catalog(),
      axiosClient.get("/api/admin/options/catalog"),
      trainingApi.courses(),
    ]);

    domains.value = catalog.domains || [];
    programs.value = catalog.programs || [];
    allCoursesList.value = coursesData || [];
    if (optionsRes.data?.units) {
      units.value = optionsRes.data.units;
    }
  } catch (err) {
    console.error("Lỗi khi tải dữ liệu phân bổ:", err);
    showToast("Không thể tải danh sách dữ liệu đào tạo", "error");
  } finally {
    loading.value = false;
  }
}

// Lazy load courses for current drawer unit
async function loadUnitCourses(unitId: string) {
  loadingUnitCourses.value = true;
  try {
    const res = await axiosClient.get(`/api/admin/courses/by-unit/${unitId}`);
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

// Assigned allocations for Drawer
interface AllocationRow {
  id: string;
  type: "domain" | "program" | "course";
  typeLabel: string;
  code: string;
  name: string;
  grantType: "MANDATORY" | "OPTIONAL";
  parentProgramName?: string;
}

const drawerAssignedAllocations = computed<AllocationRow[]>(() => {
  if (!currentDrawerUnit.value) return [];
  const uId = currentDrawerUnit.value.id;
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
      });
    }
  }

  // 3. Courses
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
      });
    }
  }

  return rows;
});

const drawerMandatoryCount = computed(
  () => drawerAssignedAllocations.value.filter((a) => a.grantType === "MANDATORY").length
);

const drawerOptionalCount = computed(
  () => drawerAssignedAllocations.value.filter((a) => a.grantType === "OPTIONAL").length
);

const filteredDrawerAllocations = computed(() => {
  let list = drawerAssignedAllocations.value;
  if (drawerGrantFilter.value !== "ALL") {
    list = list.filter((item) => item.grantType === drawerGrantFilter.value);
  }
  if (!drawerFilterQuery.value.trim()) return list;
  const q = drawerFilterQuery.value.trim().toLowerCase();
  return list.filter(
    (item) =>
      item.name.toLowerCase().includes(q) ||
      item.code.toLowerCase().includes(q) ||
      item.typeLabel.toLowerCase().includes(q) ||
      (item.parentProgramName && item.parentProgramName.toLowerCase().includes(q))
  );
});

// Toggle grant type
async function handleToggleGrantType(item: AllocationRow) {
  if (!currentDrawerUnit.value || togglingId.value) return;
  const uId = currentDrawerUnit.value.id;
  const nextGrantType = item.grantType === "MANDATORY" ? "OPTIONAL" : "MANDATORY";
  togglingId.value = item.id;

  try {
    await axiosClient.patch("/api/admin/unit-allocations/grant-type", {
      unitId: uId,
      categoryType: item.type,
      targetId: item.id,
      grantType: nextGrantType,
    });

    showToast(
      `Đã chuyển [${item.name}] sang hình thức ${nextGrantType === "MANDATORY" ? "Bắt buộc" : "Tự chọn"}!`,
      "success"
    );
    await loadData();
    await loadUnitCourses(uId);
  } catch (err: any) {
    console.error("Lỗi khi đổi hình thức áp dụng:", err);
    showToast(err.response?.data?.message || "Không thể đổi hình thức áp dụng", "error");
  } finally {
    togglingId.value = "";
  }
}

// Remove allocation
async function handleRemoveAllocation(item: AllocationRow) {
  if (!currentDrawerUnit.value) return;
  if (!confirm(`Bạn có chắc chắn muốn gỡ bỏ phân bổ [${item.name}] khỏi đơn vị này?`)) {
    return;
  }

  deletingId.value = item.id;
  const uId = currentDrawerUnit.value.id;

  try {
    await axiosClient.delete("/api/admin/unit-allocations", {
      params: {
        unitId: uId,
        categoryType: item.type,
        targetId: item.id,
      },
    });

    showToast(`Đã gỡ bỏ phân bổ [${item.name}] khỏi đơn vị!`, "success");
    await loadData();
    await loadUnitCourses(uId);
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
  padding: 1.5rem;
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

/* BREADCRUMB */
.breadcrumb-bar {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.8rem;
  color: var(--text-secondary, #64748b);
}

.crumb.active {
  color: var(--text-primary, #0f172a);
  font-weight: 600;
}

.crumb-separator {
  color: #94a3b8;
}

/* PAGE HEADER */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.title-with-badge {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.title-with-badge h2 {
  margin: 0;
  font-size: 1.45rem;
  font-weight: 700;
  color: var(--text-primary, #0f172a);
}

.badge-count {
  font-size: 0.75rem;
  font-weight: 600;
  background: rgba(0, 90, 84, 0.1);
  color: #005a54;
  padding: 0.2rem 0.6rem;
  border-radius: 20px;
}

.subtitle {
  margin: 0.35rem 0 0 0;
  font-size: 0.85rem;
  color: var(--text-secondary, #64748b);
}

/* KPI CARDS */
.kpi-cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1rem;
}

.kpi-stat-card {
  background: var(--bg-surface, #ffffff);
  border: 1px solid var(--border-color, #e2e8f0);
  border-radius: 10px;
  padding: 1rem 1.25rem;
  display: flex;
  align-items: center;
  gap: 1rem;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.stat-icon-wrap {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-icon-wrap.icon-blue {
  background: #e0f2fe;
  color: #0284c7;
}

.stat-icon-wrap.icon-green {
  background: #ecfdf5;
  color: #10b981;
}

.stat-icon-wrap.icon-teal {
  background: #ccfbf1;
  color: #0d9488;
}

.stat-icon-wrap.icon-amber {
  background: #fef3c7;
  color: #d97706;
}

.stat-info {
  display: flex;
  flex-direction: column;
}

.stat-label {
  font-size: 0.78rem;
  color: var(--text-secondary, #64748b);
  font-weight: 500;
}

.stat-value {
  font-size: 1.35rem;
  font-weight: 700;
  color: var(--text-primary, #0f172a);
}

/* CARD DIRECTORY PANEL */
.card.directory-panel {
  background: var(--bg-surface, #ffffff);
  border: 1px solid var(--border-color, #e2e8f0);
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

/* TOOLBAR */
.directory-toolbar {
  padding: 1.25rem;
  border-bottom: 1px solid var(--border-color, #e2e8f0);
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

@media (min-width: 900px) {
  .directory-toolbar {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
}

.search-box-wrap {
  position: relative;
  flex: 1;
  max-width: 420px;
}

.search-icon {
  position: absolute;
  left: 0.85rem;
  top: 50%;
  transform: translateY(-50%);
  color: #94a3b8;
}

.directory-search-input {
  width: 100%;
  padding: 0.55rem 2.2rem 0.55rem 2.4rem;
  border-radius: 8px;
  border: 1px solid var(--border-color, #cbd5e1);
  font-size: 0.85rem;
  outline: none;
  background: #f8fafc;
  transition: all 0.15s ease;
}

.directory-search-input:focus {
  border-color: #005a54;
  background: #ffffff;
}

.btn-clear-search {
  position: absolute;
  right: 0.75rem;
  top: 50%;
  transform: translateY(-50%);
  border: none;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
}

.filter-controls {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  align-items: center;
}

.filter-pills {
  display: flex;
  background: #f1f5f9;
  padding: 0.25rem;
  border-radius: 8px;
  gap: 0.25rem;
}

.filter-pill {
  border: none;
  background: transparent;
  padding: 0.35rem 0.75rem;
  font-size: 0.78rem;
  font-weight: 500;
  color: #64748b;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.filter-pill.active {
  background: #ffffff;
  color: #005a54;
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

/* TABLE */
.table-responsive {
  overflow-x: auto;
}

.unit-directory-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
}

.unit-directory-table th {
  padding: 0.85rem 1rem;
  font-size: 0.78rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.03em;
  color: #64748b;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.unit-directory-table td {
  padding: 0.95rem 1rem;
  border-bottom: 1px solid #f1f5f9;
  font-size: 0.85rem;
  vertical-align: middle;
}

.unit-row:hover {
  background: #f8fafc;
}

.col-stt {
  width: 50px;
  color: #94a3b8;
  font-weight: 500;
}

.unit-name-cell {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.unit-avatar {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: #e6f4f2;
  color: #005a54;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.unit-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.unit-main-name {
  font-weight: 600;
  color: #0f172a;
}

.unit-code-badge {
  font-size: 0.72rem;
  font-family: monospace;
  background: #f1f5f9;
  color: #475569;
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
}

.unit-type-tag {
  font-size: 0.72rem;
  font-weight: 600;
  padding: 0.2rem 0.55rem;
  border-radius: 4px;
  display: inline-block;
}

.unit-type-tag.dept {
  background: #e0f2fe;
  color: #0369a1;
}

.unit-type-tag.branch {
  background: #fef3c7;
  color: #b45309;
}

.badge-stat {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 24px;
  padding: 0 0.45rem;
  border-radius: 6px;
  font-size: 0.78rem;
  font-weight: 700;
}

.stat-domain {
  background: #f0fdf4;
  color: #166534;
}

.stat-program {
  background: #f0f9ff;
  color: #075985;
}

.stat-course {
  background: #faf5ff;
  color: #6b21a8;
}

.badge-allocation-status {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.75rem;
  font-weight: 600;
  padding: 0.25rem 0.65rem;
  border-radius: 20px;
}

.badge-allocation-status .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.badge-allocation-status.allocated {
  background: #ecfdf5;
  color: #047857;
}

.badge-allocation-status.allocated .dot {
  background: #10b981;
}

.badge-allocation-status.unallocated {
  background: #f1f5f9;
  color: #64748b;
}

.badge-allocation-status.unallocated .dot {
  background: #94a3b8;
}

/* ACTION BUTTONS IN TABLE */
.action-buttons-group {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}

.btn-action-primary {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  background: #005a54;
  color: #ffffff;
  border: none;
  padding: 0.38rem 0.8rem;
  border-radius: 6px;
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-action-primary:hover {
  background: #004540;
  box-shadow: 0 2px 4px rgba(0, 90, 84, 0.2);
}

.btn-action-view {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  background: #ffffff;
  color: #475569;
  border: 1px solid #cbd5e1;
  padding: 0.38rem 0.75rem;
  border-radius: 6px;
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-action-view:hover {
  background: #f8fafc;
  color: #0f172a;
  border-color: #94a3b8;
}

/* PAGINATION */
.directory-pagination {
  padding: 0.85rem 1.25rem;
  border-top: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 0.82rem;
  color: #64748b;
  background: #f8fafc;
}

.pagination-buttons {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.page-nav-btn {
  padding: 0.3rem 0.65rem;
  border-radius: 6px;
  border: 1px solid #cbd5e1;
  background: #ffffff;
  font-size: 0.78rem;
  font-weight: 500;
  cursor: pointer;
}

.page-nav-btn:hover:not(:disabled) {
  background: #f1f5f9;
}

.page-nav-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.page-current {
  font-weight: 600;
  color: #1e293b;
}

/* DRAWER STYLES */
.detail-drawer-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.6);
  backdrop-filter: blur(2px);
  z-index: 999;
  display: flex;
  justify-content: flex-end;
}

.detail-drawer-container {
  width: 100%;
  max-width: 580px;
  height: 100%;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  box-shadow: -10px 0 25px rgba(0, 0, 0, 0.15);
  animation: slideInRight 0.25s ease-out;
}

@keyframes slideInRight {
  from {
    transform: translateX(100%);
  }
  to {
    transform: translateX(0);
  }
}

.drawer-header {
  padding: 1.25rem;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #f8fafc;
}

.drawer-header-left {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.drawer-icon {
  width: 38px;
  height: 38px;
  border-radius: 8px;
  background: rgba(0, 90, 84, 0.12);
  color: #005a54;
  display: flex;
  align-items: center;
  justify-content: center;
}

.drawer-title-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.drawer-title-row h4 {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
  color: #0f172a;
}

.drawer-subtitle {
  margin: 0.2rem 0 0 0;
  font-size: 0.78rem;
  color: #64748b;
}

.drawer-header-right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.btn-close-drawer {
  width: 32px;
  height: 32px;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: #64748b;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-close-drawer:hover {
  background: #e2e8f0;
  color: #0f172a;
}

.drawer-toolbar {
  padding: 0.85rem 1.25rem;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  background: #ffffff;
}

.drawer-filter-input-wrap {
  position: relative;
  width: 100%;
}

.drawer-filter-input {
  width: 100%;
  padding: 0.45rem 1rem 0.45rem 2.2rem;
  border-radius: 6px;
  border: 1px solid #cbd5e1;
  font-size: 0.82rem;
  outline: none;
}

.drawer-grant-pills {
  display: flex;
  gap: 0.4rem;
}

.pill-grant {
  border: none;
  background: #f1f5f9;
  padding: 0.25rem 0.65rem;
  border-radius: 20px;
  font-size: 0.75rem;
  font-weight: 500;
  color: #64748b;
  cursor: pointer;
}

.pill-grant.active {
  background: #0f172a;
  color: #ffffff;
  font-weight: 600;
}

.pill-grant.mandatory.active {
  background: #b91c1c;
  color: #ffffff;
}

.pill-grant.optional.active {
  background: #15803d;
  color: #ffffff;
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: 1rem 1.25rem;
}

.drawer-loading,
.drawer-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem 1rem;
  gap: 0.75rem;
  color: #64748b;
  text-align: center;
}

.drawer-items-list {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.drawer-item-card {
  padding: 0.75rem 0.85rem;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  transition: all 0.15s ease;
}

.drawer-item-card:hover {
  border-color: #cbd5e1;
  background: #f8fafc;
}

.item-card-left {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
  flex: 1;
}

.item-type-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.15rem 0.45rem;
  border-radius: 4px;
  font-size: 0.68rem;
  font-weight: 600;
  flex-shrink: 0;
  margin-top: 2px;
}

.item-type-badge.domain {
  background: #ccfbf1;
  color: #0f766e;
}

.item-type-badge.program {
  background: #e0f2fe;
  color: #0369a1;
}

.item-type-badge.course {
  background: #f3e8ff;
  color: #7e22ce;
}

.item-details {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.item-title-row {
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.item-name {
  font-size: 0.85rem;
  font-weight: 600;
  color: #0f172a;
}

.item-code {
  font-size: 0.7rem;
  font-family: monospace;
  background: #f1f5f9;
  color: #475569;
  padding: 0.05rem 0.35rem;
  border-radius: 4px;
}

.item-parent {
  font-size: 0.75rem;
  color: #94a3b8;
}

.item-card-right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-shrink: 0;
}

.btn-grant-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.25rem 0.6rem;
  border-radius: 20px;
  font-size: 0.72rem;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.15s ease;
}

.btn-grant-toggle .dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
}

.btn-grant-toggle.grant-mandatory {
  background: #fef2f2;
  color: #b91c1c;
  border-color: #fecaca;
}

.btn-grant-toggle.grant-mandatory .dot {
  background: #dc2626;
}

.btn-grant-toggle.grant-optional {
  background: #f0fdf4;
  color: #15803d;
  border-color: #bbf7d0;
}

.btn-grant-toggle.grant-optional .dot {
  background: #16a34a;
}

.btn-remove-allocation {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  border: 1px solid #fee2e2;
  background: #fff5f5;
  color: #dc2626;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s;
}

.btn-remove-allocation:hover:not(:disabled) {
  background: #fee2e2;
}

.table-loading-state,
.table-empty-state {
  padding: 3rem 1rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.75rem;
  color: #64748b;
  text-align: center;
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
