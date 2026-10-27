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

                  <!-- SECONDARY ACTION: XEM PHÂN BỔ (Mở View Detail Modal to) -->
                  <button
                    type="button"
                    class="btn-action-view"
                    @click="openDetailModal(u)"
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

    <!-- BATCH ALLOCATION MODAL (THE MULTI-SELECT COMPONENT) -->
    <BatchAllocationModal
      :show="showBatchModal"
      :unit="batchTargetUnit"
      @close="closeBatchModal"
      @saved="handleBatchSaved"
    />

    <!-- UNIT DETAIL MODAL: VIEW EXISTING ALLOCATIONS (TO, RỘNG RÃI, THOÁNG MẮT) -->
    <div v-if="showDetailModal && currentDetailUnit" class="detail-modal-overlay" @click.self="closeDetailModal">
      <div class="detail-modal-container" role="dialog" aria-modal="true">
        <!-- MODAL HEADER -->
        <header class="detail-modal-header">
          <div class="modal-header-left">
            <div class="modal-unit-icon">
              <Building2 :size="20" />
            </div>
            <div>
              <div class="modal-unit-title-row">
                <h3>Chi tiết Phân bổ Đào tạo</h3>
                <span class="modal-unit-pill">{{ currentDetailUnit.name }}</span>
                <span class="unit-type-tag" :class="currentDetailUnit.type === 'DEPARTMENT' ? 'dept' : 'branch'">
                  {{ currentDetailUnit.type === 'DEPARTMENT' ? 'Phòng ban' : 'Chi nhánh / Hội sở' }}
                </span>
              </div>
              <p class="modal-unit-subtitle">
                Danh sách các nội dung đào tạo (Lĩnh vực, Chương trình, Khóa học) đang áp dụng cho đơn vị này
              </p>
            </div>
          </div>

          <div class="modal-header-right">
            <button
              type="button"
              class="btn btn-primary btn-sm"
              @click="openBatchModalFromDetail"
            >
              <FolderPlus :size="14" />
              <span>Phân bổ thêm</span>
            </button>
            <button type="button" class="btn-close-modal" @click="closeDetailModal" title="Đóng cửa sổ">
              <X :size="18" />
            </button>
          </div>
        </header>

        <!-- MODAL TOOLBAR: SEARCH, KPI, VIEW TOGGLE -->
        <div class="detail-modal-toolbar">
          <div class="toolbar-search-wrap">
            <Search :size="14" class="search-icon" />
            <input
              type="text"
              v-model="detailFilterQuery"
              placeholder="Tìm kiếm nội dung đã phân bổ theo tên, mã hoặc loại..."
              class="toolbar-search-input"
            />
            <button
              v-if="detailFilterQuery"
              type="button"
              class="btn-clear-search"
              @click="detailFilterQuery = ''"
            >
              <X :size="14" />
            </button>
          </div>

          <!-- View Mode & Grant Filters -->
          <div class="toolbar-controls">
            <!-- VIEW TOGGLE: TREE vs TABLE -->
            <div class="view-mode-tabs">
              <button
                type="button"
                class="view-tab-btn"
                :class="{ active: detailViewMode === 'tree' }"
                @click="detailViewMode = 'tree'"
                title="Xem theo Cây phân cấp"
              >
                <Layers :size="13" />
                <span>Cây phân cấp</span>
              </button>
              <button
                type="button"
                class="view-tab-btn"
                :class="{ active: detailViewMode === 'table' }"
                @click="detailViewMode = 'table'"
                title="Xem theo Bảng phẳng"
              >
                <ListOrdered :size="13" />
                <span>Bảng phẳng</span>
              </button>
            </div>

            <!-- GRANT PILLS -->
            <div class="grant-filter-pills">
              <button
                type="button"
                class="pill-grant"
                :class="{ active: detailGrantFilter === 'ALL' }"
                @click="detailGrantFilter = 'ALL'"
              >
                Tất cả ({{ modalAssignedAllocations.length }})
              </button>
              <button
                type="button"
                class="pill-grant mandatory"
                :class="{ active: detailGrantFilter === 'MANDATORY' }"
                @click="detailGrantFilter = 'MANDATORY'"
              >
                Bắt buộc ({{ modalMandatoryCount }})
              </button>
              <button
                type="button"
                class="pill-grant optional"
                :class="{ active: detailGrantFilter === 'OPTIONAL' }"
                @click="detailGrantFilter = 'OPTIONAL'"
              >
                Tự chọn ({{ modalOptionalCount }})
              </button>
            </div>
          </div>
        </div>

        <!-- MODAL BODY -->
        <div class="detail-modal-body">
          <div v-if="loadingUnitCourses" class="modal-loading-state">
            <Loader2 :size="32" class="spin-icon" />
            <p>Đang tải dữ liệu phân bổ của đơn vị...</p>
          </div>

          <div v-else-if="filteredModalAllocations.length === 0" class="modal-empty-state">
            <Inbox :size="38" class="empty-icon" />
            <h4 v-if="modalAssignedAllocations.length === 0">
              Đơn vị này chưa có nội dung đào tạo nào được phân bổ
            </h4>
            <h4 v-else>Không tìm thấy nội dung phù hợp với bộ lọc</h4>
            <p v-if="modalAssignedAllocations.length === 0">
              Nhấn nút bên dưới để chọn Lĩnh vực, Chương trình hoặc Khóa học phân bổ cho đơn vị.
            </p>
            <button
              v-if="modalAssignedAllocations.length === 0"
              type="button"
              class="btn btn-primary"
              @click="openBatchModalFromDetail"
            >
              <FolderPlus :size="15" />
              <span>Phân bổ nội dung đào tạo</span>
            </button>
          </div>

          <!-- VIEW MODE 1: CÂY PHÂN CẤP (TREE VIEW) -->
          <div v-else-if="detailViewMode === 'tree'" class="detail-tree-view">
            <!-- HIERARCHICAL TREE VIEW -->
            <div
              v-for="domainNode in assignedTreeData"
              :key="domainNode.id"
              class="assigned-domain-group"
            >
              <!-- DOMAIN HEADER -->
              <div class="assigned-domain-header">
                <div class="domain-header-left">
                  <Globe :size="16" class="domain-icon" />
                  <span class="domain-title">{{ domainNode.name }}</span>
                  <span v-if="domainNode.code" class="node-code">{{ domainNode.code }}</span>
                  <span v-if="domainNode.isExplicit" class="badge-explicit">Gán cấp Lĩnh vực</span>
                </div>

                <div class="domain-header-right">
                  <button
                    v-if="domainNode.isExplicit"
                    type="button"
                    class="btn-grant-toggle"
                    :class="domainNode.grantType === 'MANDATORY' ? 'grant-mandatory' : 'grant-optional'"
                    :disabled="togglingId === domainNode.id"
                    @click="handleToggleGrantType({ id: domainNode.id, type: 'domain', name: domainNode.name, grantType: domainNode.grantType })"
                    title="Bấm để đổi hình thức"
                  >
                    <Loader2 v-if="togglingId === domainNode.id" :size="11" class="spin-icon" />
                    <span v-else class="dot"></span>
                    <span>{{ domainNode.grantType === 'MANDATORY' ? 'Bắt buộc' : 'Tự chọn' }}</span>
                  </button>

                  <button
                    v-if="domainNode.isExplicit"
                    type="button"
                    class="btn-remove-allocation"
                    :disabled="deletingId === domainNode.id"
                    @click="handleRemoveAllocation({ id: domainNode.id, type: 'domain', name: domainNode.name })"
                    title="Gỡ bỏ phân bổ Lĩnh vực"
                  >
                    <Loader2 v-if="deletingId === domainNode.id" :size="13" class="spin-icon" />
                    <Trash2 v-else :size="14" />
                  </button>
                </div>
              </div>

              <!-- PROGRAMS UNDER DOMAIN -->
              <div class="assigned-programs-container">
                <div
                  v-for="progNode in domainNode.programs"
                  :key="progNode.id"
                  class="assigned-program-card"
                >
                  <div class="program-card-header">
                    <div class="prog-header-left">
                      <Folder :size="15" class="program-icon" />
                      <span class="program-title">{{ progNode.name }}</span>
                      <span v-if="progNode.code" class="node-code">{{ progNode.code }}</span>
                      <span v-if="progNode.isExplicit" class="badge-explicit">Gán cấp Chương trình</span>
                    </div>

                    <div class="prog-header-right">
                      <button
                        v-if="progNode.isExplicit"
                        type="button"
                        class="btn-grant-toggle"
                        :class="progNode.grantType === 'MANDATORY' ? 'grant-mandatory' : 'grant-optional'"
                        :disabled="togglingId === progNode.id"
                        @click="handleToggleGrantType({ id: progNode.id, type: 'program', name: progNode.name, grantType: progNode.grantType })"
                        title="Bấm để đổi hình thức"
                      >
                        <Loader2 v-if="togglingId === progNode.id" :size="11" class="spin-icon" />
                        <span v-else class="dot"></span>
                        <span>{{ progNode.grantType === 'MANDATORY' ? 'Bắt buộc' : 'Tự chọn' }}</span>
                      </button>

                      <button
                        v-if="progNode.isExplicit"
                        type="button"
                        class="btn-remove-allocation"
                        :disabled="deletingId === progNode.id"
                        @click="handleRemoveAllocation({ id: progNode.id, type: 'program', name: progNode.name })"
                        title="Gỡ bỏ phân bổ Chương trình"
                      >
                        <Loader2 v-if="deletingId === progNode.id" :size="13" class="spin-icon" />
                        <Trash2 v-else :size="14" />
                      </button>
                    </div>
                  </div>

                  <!-- COURSES UNDER PROGRAM -->
                  <div v-if="progNode.courses.length > 0" class="assigned-courses-list">
                    <div
                      v-for="course in progNode.courses"
                      :key="course.id"
                      class="assigned-course-row"
                    >
                      <div class="course-left">
                        <BookOpen :size="14" class="course-icon" />
                        <span class="course-title">{{ course.name }}</span>
                        <span v-if="course.code" class="node-code">{{ course.code }}</span>
                      </div>

                      <div class="course-right">
                        <button
                          type="button"
                          class="btn-grant-toggle"
                          :class="course.grantType === 'MANDATORY' ? 'grant-mandatory' : 'grant-optional'"
                          :disabled="togglingId === course.id"
                          @click="handleToggleGrantType({ id: course.id, type: 'course', name: course.name, grantType: course.grantType })"
                          title="Bấm để đổi hình thức"
                        >
                          <Loader2 v-if="togglingId === course.id" :size="11" class="spin-icon" />
                          <span v-else class="dot"></span>
                          <span>{{ course.grantType === 'MANDATORY' ? 'Bắt buộc' : 'Tự chọn' }}</span>
                        </button>

                        <button
                          type="button"
                          class="btn-remove-allocation"
                          :disabled="deletingId === course.id"
                          @click="handleRemoveAllocation({ id: course.id, type: 'course', name: course.name })"
                          title="Gỡ bỏ phân bổ Khóa học"
                        >
                          <Loader2 v-if="deletingId === course.id" :size="13" class="spin-icon" />
                          <Trash2 v-else :size="14" />
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- VIEW MODE 2: BẢNG DỮ LIỆU PHẲNG (TABLE VIEW) -->
          <div v-else class="detail-table-view">
            <table class="table modal-flat-table">
              <thead>
                <tr>
                  <th class="col-stt">STT</th>
                  <th class="col-level">Cấp độ</th>
                  <th class="col-code">Mã</th>
                  <th class="col-name">Tên nội dung đào tạo</th>
                  <th class="col-parent">Trực thuộc</th>
                  <th class="col-grant text-center">Hình thức áp dụng</th>
                  <th class="col-action text-right">Gỡ bỏ</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(item, idx) in filteredModalAllocations"
                  :key="`${item.type}_${item.id}`"
                >
                  <td class="col-stt">{{ idx + 1 }}</td>
                  <td class="col-level">
                    <span class="item-type-badge" :class="item.type">
                      <Globe v-if="item.type === 'domain'" :size="12" />
                      <Folder v-else-if="item.type === 'program'" :size="12" />
                      <BookOpen v-else :size="12" />
                      <span>{{ item.typeLabel }}</span>
                    </span>
                  </td>
                  <td class="col-code">
                    <span v-if="item.code" class="node-code">{{ item.code }}</span>
                    <span v-else class="text-muted">—</span>
                  </td>
                  <td class="col-name">
                    <strong class="item-table-name">{{ item.name }}</strong>
                  </td>
                  <td class="col-parent">
                    <span v-if="item.parentProgramName" class="parent-text">
                      {{ item.parentProgramName }}
                    </span>
                    <span v-else class="text-muted">—</span>
                  </td>
                  <td class="col-grant text-center">
                    <button
                      type="button"
                      class="btn-grant-toggle"
                      :class="item.grantType === 'MANDATORY' ? 'grant-mandatory' : 'grant-optional'"
                      :disabled="togglingId === item.id"
                      @click="handleToggleGrantType(item)"
                      title="Bấm để đổi Bắt buộc / Tự chọn"
                    >
                      <Loader2 v-if="togglingId === item.id" :size="11" class="spin-icon" />
                      <span v-else class="dot"></span>
                      <span>{{ item.grantType === 'MANDATORY' ? 'Bắt buộc' : 'Tự chọn' }}</span>
                    </button>
                  </td>
                  <td class="col-action text-right">
                    <button
                      type="button"
                      class="btn-remove-allocation"
                      :disabled="deletingId === item.id"
                      @click="handleRemoveAllocation(item)"
                      title="Gỡ bỏ phân bổ"
                    >
                      <Loader2 v-if="deletingId === item.id" :size="13" class="spin-icon" />
                      <Trash2 v-else :size="14" />
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- MODAL FOOTER -->
        <footer class="detail-modal-footer">
          <div class="footer-stats">
            <span class="stats-label">Đang áp dụng:</span>
            <span class="stats-item">
              <strong>{{ modalDomainCount }}</strong> Lĩnh vực
            </span>
            <span class="stats-separator">·</span>
            <span class="stats-item">
              <strong>{{ modalProgramCount }}</strong> Chương trình
            </span>
            <span class="stats-separator">·</span>
            <span class="stats-item">
              <strong>{{ modalCourseCount }}</strong> Khóa học
            </span>
            <span class="stats-total">
              (Tổng cộng <strong>{{ modalAssignedAllocations.length }}</strong> nội dung)
            </span>
          </div>

          <div class="footer-actions">
            <button type="button" class="btn btn-secondary" @click="closeDetailModal">
              Đóng
            </button>
            <button type="button" class="btn btn-primary" @click="openBatchModalFromDetail">
              <FolderPlus :size="14" />
              <span>Phân bổ thêm</span>
            </button>
          </div>
        </footer>
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
  ListOrdered,
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
  if (currentDetailUnit.value) {
    await loadUnitCourses(currentDetailUnit.value.id);
  }
}

// Detail Modal State (Replaced Drawer with Large Modal)
const showDetailModal = ref(false);
const currentDetailUnit = ref<OrganizationUnit | null>(null);
const detailFilterQuery = ref("");
const detailGrantFilter = ref<"ALL" | "MANDATORY" | "OPTIONAL">("ALL");
const detailViewMode = ref<"tree" | "table">("tree");

function openDetailModal(unit: OrganizationUnit) {
  currentDetailUnit.value = unit;
  detailFilterQuery.value = "";
  detailGrantFilter.value = "ALL";
  detailViewMode.value = "tree";
  showDetailModal.value = true;
  loadUnitCourses(unit.id);
}

function closeDetailModal() {
  showDetailModal.value = false;
  currentDetailUnit.value = null;
}

function openBatchModalFromDetail() {
  if (currentDetailUnit.value) {
    openBatchModal(currentDetailUnit.value);
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

// Lazy load courses for current detail unit
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

// Assigned allocations for Detail Modal
interface AllocationRow {
  id: string;
  type: "domain" | "program" | "course";
  typeLabel: string;
  code: string;
  name: string;
  grantType: "MANDATORY" | "OPTIONAL";
  parentProgramName?: string;
  domainId?: string;
  programId?: string;
}

const modalAssignedAllocations = computed<AllocationRow[]>(() => {
  if (!currentDetailUnit.value) return [];
  const uId = currentDetailUnit.value.id;
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
        domainId: p.domainId,
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
        programId: c.programId,
        domainId: prog?.domainId,
      });
    }
  }

  return rows;
});

const modalMandatoryCount = computed(
  () => modalAssignedAllocations.value.filter((a) => a.grantType === "MANDATORY").length
);

const modalOptionalCount = computed(
  () => modalAssignedAllocations.value.filter((a) => a.grantType === "OPTIONAL").length
);

const modalDomainCount = computed(
  () => modalAssignedAllocations.value.filter((a) => a.type === "domain").length
);

const modalProgramCount = computed(
  () => modalAssignedAllocations.value.filter((a) => a.type === "program").length
);

const modalCourseCount = computed(
  () => modalAssignedAllocations.value.filter((a) => a.type === "course").length
);

const filteredModalAllocations = computed(() => {
  let list = modalAssignedAllocations.value;
  if (detailGrantFilter.value !== "ALL") {
    list = list.filter((item) => item.grantType === detailGrantFilter.value);
  }
  if (!detailFilterQuery.value.trim()) return list;
  const q = detailFilterQuery.value.trim().toLowerCase();
  return list.filter(
    (item) =>
      item.name.toLowerCase().includes(q) ||
      item.code.toLowerCase().includes(q) ||
      item.typeLabel.toLowerCase().includes(q) ||
      (item.parentProgramName && item.parentProgramName.toLowerCase().includes(q))
  );
});

// Tree View Data for Detail Modal
interface TreeCourseNode {
  id: string;
  name: string;
  code?: string;
  grantType: "MANDATORY" | "OPTIONAL";
}

interface TreeProgramNode {
  id: string;
  name: string;
  code?: string;
  isExplicit: boolean;
  grantType: "MANDATORY" | "OPTIONAL";
  courses: TreeCourseNode[];
}

interface TreeDomainNode {
  id: string;
  name: string;
  code?: string;
  isExplicit: boolean;
  grantType: "MANDATORY" | "OPTIONAL";
  programs: TreeProgramNode[];
}

const assignedTreeData = computed<TreeDomainNode[]>(() => {
  const filteredList = filteredModalAllocations.value;
  if (filteredList.length === 0) return [];

  const domainMap = new Map<string, TreeDomainNode>();

  // Process all filtered items
  for (const item of filteredList) {
    if (item.type === "domain") {
      if (!domainMap.has(item.id)) {
        domainMap.set(item.id, {
          id: item.id,
          name: item.name,
          code: item.code,
          isExplicit: true,
          grantType: item.grantType,
          programs: [],
        });
      } else {
        const d = domainMap.get(item.id)!;
        d.isExplicit = true;
        d.grantType = item.grantType;
      }
    } else if (item.type === "program") {
      const dId = item.domainId || "other";
      let domainNode = domainMap.get(dId);
      if (!domainNode) {
        const rawDomain = activeDomains.value.find((d) => d.id === dId);
        domainNode = {
          id: dId,
          name: rawDomain ? rawDomain.name : "Lĩnh vực khác",
          code: rawDomain?.code,
          isExplicit: false,
          grantType: "MANDATORY",
          programs: [],
        };
        domainMap.set(dId, domainNode);
      }

      let progNode = domainNode.programs.find((p) => p.id === item.id);
      if (!progNode) {
        progNode = {
          id: item.id,
          name: item.name,
          code: item.code,
          isExplicit: true,
          grantType: item.grantType,
          courses: [],
        };
        domainNode.programs.push(progNode);
      } else {
        progNode.isExplicit = true;
        progNode.grantType = item.grantType;
      }
    } else if (item.type === "course") {
      const pId = item.programId || "other_program";
      const dId = item.domainId || "other";

      let domainNode = domainMap.get(dId);
      if (!domainNode) {
        const rawDomain = activeDomains.value.find((d) => d.id === dId);
        domainNode = {
          id: dId,
          name: rawDomain ? rawDomain.name : "Lĩnh vực khác",
          code: rawDomain?.code,
          isExplicit: false,
          grantType: "MANDATORY",
          programs: [],
        };
        domainMap.set(dId, domainNode);
      }

      let progNode = domainNode.programs.find((p) => p.id === pId);
      if (!progNode) {
        const rawProg = activePrograms.value.find((p) => p.id === pId);
        progNode = {
          id: pId,
          name: rawProg ? rawProg.name : (item.parentProgramName || "Chương trình khác"),
          code: rawProg?.code,
          isExplicit: false,
          grantType: "MANDATORY",
          courses: [],
        };
        domainNode.programs.push(progNode);
      }

      if (!progNode.courses.some((c) => c.id === item.id)) {
        progNode.courses.push({
          id: item.id,
          name: item.name,
          code: item.code,
          grantType: item.grantType,
        });
      }
    }
  }

  return Array.from(domainMap.values());
});

// Toggle grant type
async function handleToggleGrantType(item: { id: string; type: string; name: string; grantType: string }) {
  if (!currentDetailUnit.value || togglingId.value) return;
  const uId = currentDetailUnit.value.id;
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
async function handleRemoveAllocation(item: { id: string; type: string; name: string }) {
  if (!currentDetailUnit.value) return;
  if (!confirm(`Bạn có chắc chắn muốn gỡ bỏ phân bổ [${item.name}] khỏi đơn vị này?`)) {
    return;
  }

  deletingId.value = item.id;
  const uId = currentDetailUnit.value.id;

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

/* DETAIL MODAL STYLES (LARGE MODAL POPUP) */
.detail-modal-overlay {
  position: fixed;
  inset: 0;
  background-color: rgba(15, 23, 42, 0.65);
  backdrop-filter: blur(4px);
  z-index: 9998;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.5rem;
}

.detail-modal-container {
  background: #ffffff;
  border-radius: 12px;
  width: 100%;
  max-width: 1050px;
  height: 88vh;
  display: flex;
  flex-direction: column;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.25);
  border: 1px solid #e2e8f0;
  overflow: hidden;
  animation: modalScaleIn 0.2s ease-out;
}

@keyframes modalScaleIn {
  from {
    opacity: 0;
    transform: scale(0.97);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.detail-modal-header {
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #f8fafc;
}

.modal-header-left {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.modal-unit-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  background: rgba(0, 90, 84, 0.12);
  color: #005a54;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.modal-unit-title-row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.modal-unit-title-row h3 {
  margin: 0;
  font-size: 1.15rem;
  font-weight: 700;
  color: #0f172a;
}

.modal-unit-pill {
  font-size: 0.82rem;
  font-weight: 600;
  background: #005a54;
  color: #ffffff;
  padding: 0.2rem 0.6rem;
  border-radius: 6px;
}

.modal-unit-subtitle {
  margin: 0.25rem 0 0 0;
  font-size: 0.8rem;
  color: #64748b;
}

.modal-header-right {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.btn-close-modal {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: transparent;
  color: #64748b;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-close-modal:hover {
  background: #f1f5f9;
  color: #0f172a;
}

/* MODAL TOOLBAR */
.detail-modal-toolbar {
  padding: 0.85rem 1.5rem;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  background: #ffffff;
}

@media (min-width: 850px) {
  .detail-modal-toolbar {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
}

.toolbar-search-wrap {
  position: relative;
  flex: 1;
  max-width: 440px;
}

.toolbar-search-input {
  width: 100%;
  padding: 0.45rem 2rem 0.45rem 2.2rem;
  border-radius: 8px;
  border: 1px solid #cbd5e1;
  font-size: 0.82rem;
  outline: none;
  background: #f8fafc;
}

.toolbar-search-input:focus {
  border-color: #005a54;
  background: #ffffff;
}

.toolbar-controls {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  flex-wrap: wrap;
}

.view-mode-tabs {
  display: flex;
  background: #f1f5f9;
  padding: 0.2rem;
  border-radius: 6px;
  gap: 0.2rem;
}

.view-tab-btn {
  border: none;
  background: transparent;
  padding: 0.25rem 0.6rem;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 500;
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 0.35rem;
  cursor: pointer;
  transition: all 0.15s;
}

.view-tab-btn.active {
  background: #ffffff;
  color: #005a54;
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.grant-filter-pills {
  display: flex;
  gap: 0.35rem;
}

.pill-grant {
  border: none;
  background: #f1f5f9;
  padding: 0.25rem 0.6rem;
  border-radius: 20px;
  font-size: 0.72rem;
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

/* MODAL BODY */
.detail-modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 1.25rem 1.5rem;
  background: #f8fafc;
}

.modal-loading-state,
.modal-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem 1rem;
  gap: 0.75rem;
  color: #64748b;
  text-align: center;
}

/* TREE VIEW IN DETAIL MODAL */
.detail-tree-view {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.assigned-domain-group {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
}

.assigned-domain-header {
  padding: 0.75rem 1rem;
  background: #f1f5f9;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.domain-header-left {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.domain-title {
  font-size: 0.95rem;
  font-weight: 700;
  color: #0f172a;
}

.domain-icon {
  color: #0d9488;
}

.domain-header-right {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.assigned-programs-container {
  padding: 0.75rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.assigned-program-card {
  background: #fafafa;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
}

.program-card-header {
  padding: 0.6rem 0.85rem;
  background: #f8fafc;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f1f5f9;
}

.prog-header-left {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.program-title {
  font-size: 0.88rem;
  font-weight: 600;
  color: #1e293b;
}

.program-icon {
  color: #0284c7;
}

.prog-header-right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.badge-explicit {
  font-size: 0.68rem;
  font-weight: 600;
  background: #e0f2fe;
  color: #0369a1;
  padding: 0.1rem 0.45rem;
  border-radius: 4px;
}

.assigned-courses-list {
  padding: 0.4rem 0.85rem;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.assigned-course-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.4rem 0.5rem;
  border-radius: 6px;
  background: #ffffff;
  border: 1px solid #f1f5f9;
}

.assigned-course-row:hover {
  background: #f8fafc;
  border-color: #e2e8f0;
}

.course-left {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.course-title {
  font-size: 0.82rem;
  color: #334155;
  font-weight: 500;
}

.course-icon {
  color: #10b981;
}

.course-right {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

/* FLAT TABLE VIEW IN DETAIL MODAL */
.detail-table-view {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: hidden;
}

.modal-flat-table {
  width: 100%;
  border-collapse: collapse;
}

.modal-flat-table th {
  padding: 0.75rem 1rem;
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  color: #64748b;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.modal-flat-table td {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #f1f5f9;
  font-size: 0.82rem;
  vertical-align: middle;
}

.item-table-name {
  color: #0f172a;
}

.parent-text {
  color: #64748b;
  font-size: 0.78rem;
}

.text-muted {
  color: #94a3b8;
}

.node-code {
  font-size: 0.7rem;
  font-family: monospace;
  background: #e2e8f0;
  color: #475569;
  padding: 0.1rem 0.35rem;
  border-radius: 4px;
}

.item-type-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.15rem 0.45rem;
  border-radius: 4px;
  font-size: 0.68rem;
  font-weight: 600;
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

/* BUTTONS */
.btn-grant-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.22rem 0.6rem;
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

.btn-grant-toggle.grant-mandatory:hover {
  background: #fee2e2;
}

.btn-grant-toggle.grant-optional {
  background: #f0fdf4;
  color: #15803d;
  border-color: #bbf7d0;
}

.btn-grant-toggle.grant-optional .dot {
  background: #16a34a;
}

.btn-grant-toggle.grant-optional:hover {
  background: #dcfce7;
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

/* MODAL FOOTER */
.detail-modal-footer {
  padding: 0.9rem 1.5rem;
  background: #ffffff;
  border-top: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.footer-stats {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.82rem;
  color: #475569;
}

.stats-label {
  color: #94a3b8;
}

.stats-separator {
  color: #cbd5e1;
}

.stats-total {
  margin-left: 0.35rem;
  color: #005a54;
}

.footer-actions {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.btn {
  padding: 0.45rem 0.95rem;
  border-radius: 8px;
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  transition: all 0.15s ease;
}

.btn-secondary {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #475569;
}

.btn-secondary:hover {
  background: #f1f5f9;
  color: #0f172a;
}

.btn-primary {
  border: none;
  background: #005a54;
  color: #ffffff;
}

.btn-primary:hover {
  background: #004540;
}

.btn-sm {
  padding: 0.35rem 0.75rem;
  font-size: 0.78rem;
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
