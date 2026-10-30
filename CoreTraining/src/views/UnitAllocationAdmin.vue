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
              </label>
              <label class="radio-card" :class="{ active: categoryType === 'program' }">
                <input type="radio" value="program" v-model="categoryType" />
                <Folder :size="15" />
                <span class="radio-card-title">Chương trình</span>
              </label>
              <label class="radio-card" :class="{ active: categoryType === 'course' }">
                <input type="radio" value="course" v-model="categoryType" />
                <BookOpen :size="15" />
                <span class="radio-card-title">Khóa học</span>
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
                  {{ d.name }}
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
                    {{ d.name }}
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
                        placeholder="Tìm theo tên hoặc mã chương trình..."
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
                    {{ d.name }}
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
                        placeholder="Tìm theo tên hoặc mã chương trình..."
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
                      placeholder="Lọc nhanh theo tên khóa học..."
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
              <label for="grant-type-select">Hình thức áp dụng (*)</label>
            </div>
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
              <span>{{ saving ? "Đang lưu phân bổ..." : "Xác nhận Lưu Phân bổ" }}</span>
            </button>
          </div>
        </form>
      </section>

      <!-- RIGHT PANEL: CURRENT ALLOCATION MATRIX & TREE -->
      <section class="card matrix-panel">
        <div class="panel-header matrix-header">
          <div class="matrix-header-main">
            <div class="panel-header-title">
              <Layers v-if="matrixViewMode === 'tree'" :size="16" class="panel-icon" />
              <ListOrdered v-else :size="16" class="panel-icon" />
              <h3>Danh sách Đào tạo đã phân bổ</h3>
            </div>

            <div class="matrix-header-right">
              <span v-if="currentUnit" class="current-unit-chip">
                <Building2 :size="13" />
                <span>{{ currentUnit.name }}</span>
                <button
                  type="button"
                  class="chip-clear-unit"
                  title="Đổi đơn vị khác"
                  @click="clearSelectedUnit"
                >
                  <X :size="12" />
                </button>
              </span>

              <!-- VIEW SWITCHER: TREE vs TABLE -->
              <div v-if="selectedUnitId && assignedAllocations.length" class="matrix-view-Order-tabs">
                <button
                  type="button"
                  class="matrix-view-btn"
                  :class="{ active: matrixViewMode === 'tree' }"
                  @click="matrixViewMode = 'tree'"
                  title="Hiển thị dạng Cây cấu trúc giống Danh mục đào tạo"
                >
                  <Layers :size="13" />
                  <span>Cây phân cấp</span>
                </button>
                <button
                  type="button"
                  class="matrix-view-btn"
                  :class="{ active: matrixViewMode === 'table' }"
                  @click="matrixViewMode = 'table'"
                  title="Hiển thị dạng Bảng phẳng truyền thống"
                >
                  <ListOrdered :size="13" />
                  <span>Bảng phẳng</span>
                </button>
              </div>
            </div>
          </div>

          <!-- INTERACTIVE KPI FILTER CHIPS (Impeccable Style) -->
          <div v-if="selectedUnitId && assignedAllocations.length" class="kpi-chips-bar">
            <button
              type="button"
              class="kpi-chip kpi-total"
              :class="{ 'is-active-filter': grantFilter === 'ALL' }"
              @click="grantFilter = 'ALL'"
              title="Hiển thị tất cả phân bổ"
            >
              <span class="kpi-label">Tổng số phân bổ</span>
              <strong class="kpi-val">{{ assignedAllocations.length }}</strong>
            </button>
            <button
              type="button"
              class="kpi-chip kpi-mandatory"
              :class="{ 'is-active-filter': grantFilter === 'MANDATORY' }"
              @click="grantFilter = grantFilter === 'MANDATORY' ? 'ALL' : 'MANDATORY'"
              title="Lọc các nội dung Bắt buộc hoàn thành"
            >
              <span class="dot mandatory-dot"></span>
              <span class="kpi-label">Bắt buộc</span>
              <strong class="kpi-val">{{ mandatoryCount }}</strong>
            </button>
            <button
              type="button"
              class="kpi-chip kpi-optional"
              :class="{ 'is-active-filter': grantFilter === 'OPTIONAL' }"
              @click="grantFilter = grantFilter === 'OPTIONAL' ? 'ALL' : 'OPTIONAL'"
              title="Lọc các nội dung Tự chọn / Tham khảo"
            >
              <span class="dot optional-dot"></span>
              <span class="kpi-label">Tự chọn</span>
              <strong class="kpi-val">{{ optionalCount }}</strong>
            </button>
            <span class="kpi-hint-text">
              <Info :size="12" />
              <span>Rê chuột vào tên mục để xem mô tả · Bấm nhãn hình thức để đổi nhanh</span>
            </span>
          </div>
        </div>

        <!-- QUICK FILTER & TREE TOOLBAR -->
        <div v-if="selectedUnitId && assignedAllocations.length" class="matrix-filter-bar">
          <div class="quick-filter-input-wrap">
            <Search :size="14" class="quick-filter-icon" />
            <input
              type="text"
              v-model="tableFilterQuery"
              class="quick-filter-input"
              placeholder="Tìm theo mã, tên Lĩnh vực, Chương trình, Khóa học hoặc mô tả..."
            />
            <button
              v-if="tableFilterQuery"
              type="button"
              class="btn-clear-filter"
              @click="tableFilterQuery = ''"
              title="Xóa từ khóa"
            >
              <X :size="12" />
            </button>
          </div>

          <div class="matrix-toolbar-actions">
            <span v-if="tableFilterQuery || grantFilter !== 'ALL'" class="filter-count-badge">
              Lọc: {{ filteredAssignedAllocations.length }} / {{ assignedAllocations.length }} mục
            </span>
            <button
              v-if="matrixViewMode === 'tree'"
              type="button"
              class="btn-tree-expand-toggle"
              @click="toggleAllTreeNodes"
              :title="allTreeExpanded ? 'Thu gọn tất cả nhánh' : 'Mở rộng tất cả nhánh'"
            >
              <component :is="allTreeExpanded ? Minimize2 : Maximize2" :size="13" />
              <span>{{ allTreeExpanded ? "Thu gọn" : "Mở rộng" }}</span>
            </button>
          </div>
        </div>

        <!-- STATE 1: CHƯA CHỌN ĐƠN VỊ -> INTERACTIVE UNIT DIRECTORY (Impeccable Onboarding Empty State) -->
        <div v-if="!selectedUnitId" class="unit-directory-state">
          <div class="directory-intro">
            <div class="directory-intro-left">
              <div class="empty-icon-ring compact-ring">
                <Building2 :size="22" class="empty-icon" />
              </div>
              <div>
                <h4>Chọn Đơn vị để xem & quản lý Cây phân bổ đào tạo</h4>
                <p>Chọn nhanh một Đơn vị / Chi nhánh bên dưới hoặc từ Bước 01 ở cột bên trái.</p>
              </div>
            </div>
            <div class="directory-search-box">
              <Search :size="14" class="search-icon" />
              <input
                type="text"
                v-model="unitDirectoryQuery"
                placeholder="Tìm nhanh đơn vị, chi nhánh..."
                class="directory-search-input"
              />
              <button
                v-if="unitDirectoryQuery"
                type="button"
                class="btn-clear-filter"
                @click="unitDirectoryQuery = ''"
              >
                <X :size="12" />
              </button>
            </div>
          </div>

          <div class="unit-cards-grid">
            <button
              v-for="u in pagedDirectoryUnits"
              :key="u.id"
              type="button"
              class="unit-quick-card"
              @click="selectUnitFromDirectory(u.id)"
            >
              <div class="unit-card-top">
                <span class="unit-card-icon">
                  <Building2 :size="16" />
                </span>
                <span class="unit-type-tag" :class="u.type === 'DEPARTMENT' ? 'dept' : 'branch'">
                  {{ u.type === 'DEPARTMENT' ? 'Phòng ban' : 'Chi nhánh / Hội sở' }}
                </span>
              </div>
              <div class="unit-card-name">{{ u.name }}</div>
              <div class="unit-card-metrics">
                <span class="metric-pill">
                  <Globe :size="11" />
                  <span>{{ getUnitDomainCount(u.id) }} Lĩnh vực</span>
                </span>
                <span class="metric-pill">
                  <Folder :size="11" />
                  <span>{{ getUnitProgramCount(u.id) }} Chương trình</span>
                </span>
              </div>
            </button>
          </div>

          <!-- Directory Pagination -->
          <div v-if="filteredDirectoryUnits.length > unitDirectoryPageSize" class="matrix-pagination-bar">
            <span class="pagination-summary">
              Hiển thị {{ (unitDirectoryPage - 1) * unitDirectoryPageSize + 1 }}–{{ Math.min(unitDirectoryPage * unitDirectoryPageSize, filteredDirectoryUnits.length) }} / {{ filteredDirectoryUnits.length }} đơn vị
            </span>
            <div class="pagination-controls">
              <button
                type="button"
                class="page-btn"
                :disabled="unitDirectoryPage <= 1"
                @click="unitDirectoryPage--"
              >
                Trước
              </button>
              <span class="page-indicator">Trang {{ unitDirectoryPage }} / {{ unitDirectoryTotalPages }}</span>
              <button
                type="button"
                class="page-btn"
                :disabled="unitDirectoryPage >= unitDirectoryTotalPages"
                @click="unitDirectoryPage++"
              >
                Sau
              </button>
            </div>
          </div>
        </div>

        <!-- STATE 2: LOADING -->
        <div v-else-if="loading || loadingUnitCourses" class="empty-state">
          <Loader2 :size="32" class="spinner" />
          <p>Đang đồng bộ cấu trúc phân bổ đào tạo của đơn vị...</p>
        </div>

        <!-- STATE 3: ĐƠN VỊ CHƯA CÓ PHÂN BỔ -->
        <div v-else-if="!assignedAllocations.length" class="empty-state">
          <div class="empty-icon-ring">
            <Inbox :size="32" class="empty-icon" />
          </div>
          <h4>Chưa có phân bổ nào cho {{ currentUnit?.name || 'đơn vị này' }}</h4>
          <p>Sử dụng biểu mẫu 4 bước ở cột bên trái để gán Lĩnh vực, Chương trình hoặc Khóa học đầu tiên.</p>
        </div>

        <!-- STATE 4: LỌC KHÔNG RA KẾT QUẢ -->
        <div v-else-if="!filteredAssignedAllocations.length" class="empty-state">
          <Filter :size="32" class="empty-icon" />
          <h4>Không tìm thấy nội dung phù hợp</h4>
          <p>
            Không có mục phân bổ nào khớp với bộ lọc hiện tại.
          </p>
          <button
            type="button"
            class="btn btn-secondary btn-sm"
            @click="tableFilterQuery = ''; grantFilter = 'ALL'"
          >
            Đặt lại bộ lọc
          </button>
        </div>

        <!-- STATE 5A: HIỂN THỊ DẠNG CÂY PHÂN CẤP (CURRICULUM TREE VIEW) -->
        <div v-else-if="matrixViewMode === 'tree'" class="allocation-tree-wrapper">
          <div class="allocation-tree-scroll">
            <div
              v-for="dNode in pagedAllocationTreeDomains"
              :key="dNode.domain.id"
              class="alloc-tree-branch domain-branch"
            >
              <!-- LEVEL 1: DOMAIN NODE -->
              <div
                class="alloc-tree-node domain-node"
                :class="{ expanded: isTreeExpanded(dNode.domain.id), 'is-direct': !!dNode.directAllocation }"
                @click="toggleTreeNode(dNode.domain.id)"
              >
                <button
                  type="button"
                  class="tree-chevron-btn"
                  @click.stop="toggleTreeNode(dNode.domain.id)"
                  :aria-label="isTreeExpanded(dNode.domain.id) ? 'Thu gọn lĩnh vực' : 'Mở rộng lĩnh vực'"
                >
                  <ChevronRight :size="15" :class="{ rotated: isTreeExpanded(dNode.domain.id) }" />
                </button>

                <span class="node-level-icon domain-icon">
                  <Globe :size="16" />
                </span>

                <div
                  class="node-main-info"
                  @mouseenter="showNodeTooltip($event, 'domain', dNode.domain)"
                  @mouseleave="hideNodeTooltip"
                >
                  <span class="node-title-text">{{ dNode.domain.name }}</span>
                </div>

                <!-- Right Meta & Actions -->
                <div class="node-meta-actions" @click.stop>
                  <span class="child-count-tag">
                    {{ dNode.programs.length }} chương trình
                  </span>

                  <button
                    v-if="dNode.directAllocation"
                    type="button"
                    class="mode-badge interactive-mode-badge"
                    :class="dNode.directAllocation.grantType.toLowerCase()"
                    :disabled="togglingId === dNode.domain.id"
                    title="Bấm để chuyển đổi nhanh Bắt buộc / Tự chọn"
                    @click="handleToggleGrantType(dNode.directAllocation)"
                  >
                    <Loader2 v-if="togglingId === dNode.domain.id" :size="11" class="spinner" />
                    <span
                      v-else
                      class="dot"
                      :class="dNode.directAllocation.grantType === 'MANDATORY' ? 'mandatory-dot' : 'optional-dot'"
                    ></span>
                    <span>{{ dNode.directAllocation.grantType === 'MANDATORY' ? 'Bắt buộc' : 'Tự chọn' }}</span>
                  </button>

                  <button
                    v-if="dNode.directAllocation"
                    type="button"
                    class="btn-remove-allocation btn-remove-node"
                    title="Gỡ phân bổ cấp Lĩnh vực này"
                    :disabled="deletingId === dNode.domain.id"
                    @click="handleRemoveAllocation(dNode.directAllocation)"
                  >
                    <Trash2 :size="13" />
                    <span>Gỡ</span>
                  </button>
                </div>
              </div>

              <!-- LEVEL 2: PROGRAM NODES -->
              <div v-if="isTreeExpanded(dNode.domain.id)" class="alloc-tree-children">
                <div v-if="!dNode.programs.length" class="tree-empty-leaf">
                  <span>Lĩnh vực này hiện chưa có chương trình đào tạo nào trong danh mục.</span>
                </div>

                <div
                  v-for="pNode in dNode.programs"
                  :key="pNode.program.id"
                  class="alloc-tree-branch program-branch"
                >
                  <div
                    class="alloc-tree-node program-node"
                    :class="{ expanded: isTreeExpanded(pNode.program.id), 'is-direct': !!pNode.directAllocation }"
                    @click="toggleTreeNode(pNode.program.id)"
                  >
                    <button
                      type="button"
                      class="tree-chevron-btn"
                      @click.stop="toggleTreeNode(pNode.program.id)"
                    >
                      <ChevronRight :size="14" :class="{ rotated: isTreeExpanded(pNode.program.id) }" />
                    </button>

                    <span class="node-level-icon program-icon">
                      <Folder :size="15" />
                    </span>

                    <div
                      class="node-main-info"
                      @mouseenter="showNodeTooltip($event, 'program', pNode.program)"
                      @mouseleave="hideNodeTooltip"
                    >
                      <span class="node-title-text">{{ pNode.program.name }}</span>
                    </div>

                    <div class="node-meta-actions" @click.stop>
                      <span v-if="isProgramCourseCountKnown(pNode)" class="child-count-tag">
                        <Loader2 v-if="loadingTreeProgramIds[pNode.program.id]" :size="10" class="spinner" />
                        <span v-else>{{ getProgramTotalCourseCount(pNode) }} khóa học</span>
                      </span>

                      <button
                        v-if="pNode.directAllocation"
                        type="button"
                        class="mode-badge interactive-mode-badge"
                        :class="pNode.directAllocation.grantType.toLowerCase()"
                        :disabled="togglingId === pNode.program.id"
                        title="Bấm để chuyển đổi nhanh Bắt buộc / Tự chọn cho Chương trình này"
                        @click="handleToggleGrantType(pNode.directAllocation)"
                      >
                        <Loader2 v-if="togglingId === pNode.program.id" :size="11" class="spinner" />
                        <span
                          v-else
                          class="dot"
                          :class="pNode.directAllocation.grantType === 'MANDATORY' ? 'mandatory-dot' : 'optional-dot'"
                        ></span>
                        <span>{{ pNode.directAllocation.grantType === 'MANDATORY' ? 'Bắt buộc' : 'Tự chọn' }}</span>
                      </button>

                      <button
                        v-if="pNode.directAllocation"
                        type="button"
                        class="btn-remove-allocation btn-remove-node"
                        title="Gỡ phân bổ Chương trình này"
                        :disabled="deletingId === pNode.program.id"
                        @click="handleRemoveAllocation(pNode.directAllocation)"
                      >
                        <Trash2 :size="13" />
                        <span>Gỡ</span>
                      </button>
                    </div>
                  </div>

                  <!-- LEVEL 3: COURSE NODES (With Tier-2 In-Branch Lazy Pagination) -->
                  <div v-if="isTreeExpanded(pNode.program.id)" class="alloc-tree-children course-children">
                    <div v-if="loadingTreeProgramIds[pNode.program.id] && !pNode.courses.length" class="tree-loading-leaf">
                      <Loader2 :size="13" class="spinner" />
                      <span>Đang tải danh sách khóa học trong chương trình...</span>
                    </div>
                    <div v-else-if="!pNode.courses.length" class="tree-empty-leaf">
                      <span>Chương trình này hiện chưa có khóa học nào trong danh mục.</span>
                    </div>

                    <div
                      v-for="cRow in getVisibleCoursesForProgram(pNode.program.id, pNode.courses)"
                      :key="cRow.id"
                      class="alloc-tree-node course-node"
                    >
                      <span class="course-tree-Dot"></span>
                      <span class="node-level-icon course-icon">
                        <BookOpen :size="14" />
                      </span>

                      <div
                        class="node-main-info"
                        @mouseenter="showNodeTooltip($event, 'course', cRow.rawObj)"
                        @mouseleave="hideNodeTooltip"
                      >
                        <span class="node-title-text">{{ cRow.name }}</span>
                      </div>

                      <div class="node-meta-actions" @click.stop>
                        <!-- Nếu khóa học được gán riêng lẻ: cho phép bấm đổi nhanh và gỡ riêng -->
                        <template v-if="!cRow.isInherited">
                          <button
                            type="button"
                            class="mode-badge interactive-mode-badge"
                            :class="cRow.grantType.toLowerCase()"
                            :disabled="togglingId === cRow.id"
                            title="Bấm để chuyển đổi nhanh Bắt buộc / Tự chọn cho Khóa học này"
                            @click="handleToggleGrantType(cRow)"
                          >
                            <Loader2 v-if="togglingId === cRow.id" :size="11" class="spinner" />
                            <span
                              v-else
                              class="dot"
                              :class="cRow.grantType === 'MANDATORY' ? 'mandatory-dot' : 'optional-dot'"
                            ></span>
                            <span>{{ cRow.grantType === 'MANDATORY' ? 'Bắt buộc' : 'Tự chọn' }}</span>
                          </button>

                          <button
                            type="button"
                            class="btn-remove-allocation btn-remove-node"
                            title="Gỡ phân bổ Khóa học này khỏi đơn vị"
                            :disabled="deletingId === cRow.id"
                            @click="handleRemoveAllocation(cRow)"
                          >
                            <Trash2 :size="13" />
                            <span>Gỡ</span>
                          </button>
                        </template>
                      </div>
                    </div>

                    <!-- TIER-2 IN-BRANCH COURSE PAGINATION / SERVER-SIDE LOAD MORE -->
                    <div
                      v-if="getProgramTotalCourseCount(pNode) > DEFAULT_COURSE_BRANCH_SIZE"
                      class="in-branch-pagination-row"
                    >
                      <span class="in-branch-count">
                        Đang hiển thị {{ getVisibleCoursesForProgram(pNode.program.id, pNode.courses).length }} / {{ getProgramTotalCourseCount(pNode) }} khóa học trong chương trình
                      </span>
                      <div class="in-branch-actions">
                        <button
                          v-if="getVisibleCoursesForProgram(pNode.program.id, pNode.courses).length < getProgramTotalCourseCount(pNode)"
                          type="button"
                          class="btn-in-branch-more"
                          :disabled="loadingMoreTreeProgramIds[pNode.program.id]"
                          @click.stop="expandCourseBranchLimit(pNode)"
                        >
                          <Loader2 v-if="loadingMoreTreeProgramIds[pNode.program.id]" :size="11" class="spinner" />
                          <span>
                            + Xem thêm {{ Math.min(DEFAULT_COURSE_BRANCH_SIZE, getProgramTotalCourseCount(pNode) - getVisibleCoursesForProgram(pNode.program.id, pNode.courses).length) }} khóa học
                          </span>
                        </button>
                        <button
                          v-if="getCourseBranchLimit(pNode.program.id) > DEFAULT_COURSE_BRANCH_SIZE"
                          type="button"
                          class="btn-in-branch-collapse"
                          @click.stop="resetCourseBranchLimit(pNode.program.id)"
                        >
                          Thu gọn bớt
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- TIER-1 ROOT TREE PAGINATION FOOTER -->
          <div class="matrix-pagination-bar">
            <div class="pagination-summary">
              <span>
                Hiển thị <strong>{{ pagedAllocationTreeDomains.length }}</strong> / <strong>{{ allocationTreeDomains.length }}</strong> nhánh Lĩnh vực
                ({{ filteredAssignedAllocations.length }} mục phân bổ)
              </span>
            </div>

            <div class="pagination-controls">
              <label class="page-size-label">
                <span>Hiển thị:</span>
                <select v-model.number="treeDomainPageSize" class="page-size-select">
                  <option :value="5">5 lĩnh vực / trang</option>
                  <option :value="10">10 lĩnh vực / trang</option>
                  <option :value="20">20 lĩnh vực / trang</option>
                </select>
              </label>

              <div class="page-nav-group">
                <button
                  type="button"
                  class="page-btn"
                  :disabled="treeDomainPage <= 1"
                  @click="treeDomainPage--"
                >
                  Trước
                </button>
                <span class="page-indicator">
                  Trang {{ treeDomainPage }} / {{ treeDomainTotalPages }}
                </span>
                <button
                  type="button"
                  class="page-btn"
                  :disabled="treeDomainPage >= treeDomainTotalPages"
                  @click="treeDomainPage++"
                >
                  Sau
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- STATE 5B: HIỂN THỊ DẠNG BẢNG PHẲNG (TRADITIONAL TABLE VIEW + PAGINATION) -->
        <div v-else class="allocation-table-container">
          <div class="allocation-table-wrapper">
            <table class="allocation-table">
              <thead>
                <tr>
                  <th style="width: 110px;">Cấp bậc</th>
                  <th style="width: 120px;">Mã nội dung</th>
                  <th>Tên Nội dung Đào tạo</th>
                  <th style="width: 145px;">Hình thức</th>
                  <th>Thuộc Chương trình</th>
                  <th class="actions-col" style="width: 80px;">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="item in pagedFlatAllocations"
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
                  <td
                    class="name-col"
                    @mouseenter="showNodeTooltip($event, item.type, item.rawObj)"
                    @mouseleave="hideNodeTooltip"
                  >
                    <div class="table-name-with-info">
                      <strong>{{ item.name }}</strong>
                    </div>
                  </td>
                  <td>
                    <button
                      type="button"
                      class="mode-badge interactive-mode-badge"
                      :class="item.grantType.toLowerCase()"
                      :disabled="togglingId === item.id"
                      title="Bấm để đổi nhanh Bắt buộc / Tự chọn"
                      @click="handleToggleGrantType(item)"
                    >
                      <Loader2 v-if="togglingId === item.id" :size="11" class="spinner" />
                      <span
                        v-else
                        class="dot"
                        :class="item.grantType === 'MANDATORY' ? 'mandatory-dot' : 'optional-dot'"
                      ></span>
                      <span>{{ item.grantType === 'MANDATORY' ? 'Bắt buộc' : 'Tự chọn' }}</span>
                    </button>
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

          <!-- FLAT TABLE PAGINATION FOOTER -->
          <div class="matrix-pagination-bar">
            <div class="pagination-summary">
              <span>
                Hiển thị <strong>{{ (flatTablePage - 1) * flatTablePageSize + 1 }}–{{ Math.min(flatTablePage * flatTablePageSize, filteredAssignedAllocations.length) }}</strong>
                trên tổng số <strong>{{ filteredAssignedAllocations.length }}</strong> mục
              </span>
            </div>
            <div class="pagination-controls">
              <label class="page-size-label">
                <span>Dòng / trang:</span>
                <select v-model.number="flatTablePageSize" class="page-size-select">
                  <option :value="10">10 dòng</option>
                  <option :value="20">20 dòng</option>
                  <option :value="50">50 dòng</option>
                </select>
              </label>
              <div class="page-nav-group">
                <button
                  type="button"
                  class="page-btn"
                  :disabled="flatTablePage <= 1"
                  @click="flatTablePage--"
                >
                  Trước
                </button>
                <span class="page-indicator">
                  Trang {{ flatTablePage }} / {{ flatTableTotalPages }}
                </span>
                <button
                  type="button"
                  class="page-btn"
                  :disabled="flatTablePage >= flatTableTotalPages"
                  @click="flatTablePage++"
                >
                  Sau
                </button>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- FLOATING RICH HOVER DESCRIPTION TOOLTIP (Impeccable Fixed Portal-like Overlay) -->
    <Teleport to="body">
      <Transition name="tooltip-fade">
        <div
          v-if="hoverTooltip.visible"
          class="impeccable-desc-popover"
          :style="{ top: `${hoverTooltip.y}px`, left: `${hoverTooltip.x}px` }"
        >
          <div class="popover-header">
            <span class="popover-type-badge" :class="hoverTooltip.type">
              {{ hoverTooltip.typeLabel }}
            </span>
            <span v-if="hoverTooltip.businessModule" class="popover-module">
              Phân hệ {{ hoverTooltip.businessModule }}
            </span>
          </div>
          <div class="popover-title">{{ hoverTooltip.title }}</div>
          <div class="popover-desc-box">
            <span class="popover-desc-label">Mục tiêu / Mô tả nội dung:</span>
            <p class="popover-desc-text" :class="{ 'is-empty': !hoverTooltip.description }">
              {{ hoverTooltip.description || "Chưa có mô tả chi tiết cho mục đào tạo này." }}
            </p>
          </div>
          <div v-if="hoverTooltip.level || hoverTooltip.deadline" class="popover-footer-meta">
            <span v-if="hoverTooltip.level" class="meta-item">
              Cấp độ: <strong>{{ formatLevelLabel(hoverTooltip.level) }}</strong>
            </span>
            <span v-if="hoverTooltip.deadline" class="meta-item">
              Hạn khuyến nghị: <strong>{{ hoverTooltip.deadline }}</strong>
            </span>
          </div>
        </div>
      </Transition>
    </Teleport>
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
  Layers,
  Info,
  Maximize2,
  Minimize2,
  ChevronDown,
  ChevronRight,
  X,
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
const togglingId = ref("");

const units = ref<OrganizationUnit[]>([]);
const domains = ref<TrainingDomain[]>([]);
const programs = ref<TrainingProgram[]>([]);
const unitAssignedCourses = ref<Course[]>([]);
const programCourses = ref<Course[]>([]);
const loadingUnitCourses = ref(false);
const loadingProgramCourses = ref(false);

// Right Panel View Mode & Filters
const matrixViewMode = ref<"tree" | "table">("tree");
const grantFilter = ref<"ALL" | "MANDATORY" | "OPTIONAL">("ALL");

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
    return selectedProgramObj.value.name;
  }
  const found = pagedPrograms.value.find((p) => p.id === targetProgramId.value) ||
                programs.value.find((p) => p.id === targetProgramId.value);
  if (found) return found.name;
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
  isInherited?: boolean;
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
  let list = assignedAllocations.value;
  if (grantFilter.value !== "ALL") {
    list = list.filter((item) => item.grantType === grantFilter.value);
  }
  if (!tableFilterQuery.value.trim()) return list;
  const q = tableFilterQuery.value.trim().toLowerCase();
  return list.filter(
    (item) =>
      item.name.toLowerCase().includes(q) ||
      item.code.toLowerCase().includes(q) ||
      item.typeLabel.toLowerCase().includes(q) ||
      (item.parentProgramName && item.parentProgramName.toLowerCase().includes(q)) ||
      (item.rawObj?.description && String(item.rawObj.description).toLowerCase().includes(q))
  );
});

const mandatoryCount = computed(() =>
  assignedAllocations.value.filter((a) => a.grantType === "MANDATORY").length
);

const optionalCount = computed(() =>
  assignedAllocations.value.filter((a) => a.grantType === "OPTIONAL").length
);

// ================= 1. INTERACTIVE UNIT DIRECTORY (ONBOARDING EMPTY STATE) =================
const unitDirectoryQuery = ref("");
const unitDirectoryPage = ref(1);
const unitDirectoryPageSize = 8;

const filteredDirectoryUnits = computed(() => {
  if (!unitDirectoryQuery.value.trim()) return units.value;
  const q = unitDirectoryQuery.value.trim().toLowerCase();
  return units.value.filter((u) => u.name.toLowerCase().includes(q));
});

const unitDirectoryTotalPages = computed(() =>
  Math.max(1, Math.ceil(filteredDirectoryUnits.value.length / unitDirectoryPageSize))
);

const pagedDirectoryUnits = computed(() => {
  const start = (unitDirectoryPage.value - 1) * unitDirectoryPageSize;
  return filteredDirectoryUnits.value.slice(start, start + unitDirectoryPageSize);
});

watch(unitDirectoryQuery, () => {
  unitDirectoryPage.value = 1;
});

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

async function selectUnitFromDirectory(uId: string) {
  selectedUnitId.value = uId;
  await handleUnitChange();
}

function clearSelectedUnit() {
  selectedUnitId.value = "";
  unitAssignedCourses.value = [];
  tableFilterQuery.value = "";
  grantFilter.value = "ALL";
}

// ================= 2. 3-LEVEL CURRICULUM ALLOCATION TREE & 2-TIER PAGINATION =================
interface TreeProgramNode {
  program: TrainingProgram;
  directAllocation?: AllocationRow;
  courses: AllocationRow[];
}

interface TreeDomainNode {
  domain: TrainingDomain;
  directAllocation?: AllocationRow;
  programs: TreeProgramNode[];
  totalCourses: number;
}

const expandedTreeNodeIds = ref<Set<string>>(new Set());
const allTreeExpanded = ref(true);

// Cache phân trang Lazy-Fetch Khóa học của từng Chương trình khi hiển thị trên Cây phân bổ
const DEFAULT_COURSE_BRANCH_SIZE = 6;
const treeProgramCoursesMap = ref<Record<string, Course[]>>({});
const treeProgramTotalCoursesMap = ref<Record<string, number>>({});
const treeProgramPageMap = ref<Record<string, number>>({});
const treeProgramHasNextMap = ref<Record<string, boolean>>({});
const loadingTreeProgramIds = ref<Record<string, boolean>>({});
const loadingMoreTreeProgramIds = ref<Record<string, boolean>>({});
const courseBranchLimits = ref<Record<string, number>>({});

async function ensureProgramCoursesLoaded(programId: string) {
  if (
    !programId ||
    treeProgramCoursesMap.value[programId] !== undefined ||
    loadingTreeProgramIds.value[programId]
  ) {
    return;
  }
  loadingTreeProgramIds.value = { ...loadingTreeProgramIds.value, [programId]: true };
  try {
    const res = await axiosClient.get(`/api/admin/courses/by-program/${programId}`, {
      params: { page: 0, size: DEFAULT_COURSE_BRANCH_SIZE },
    });
    const data = res.data || {};
    const items: Course[] = (data.items || (Array.isArray(data) ? data : [])).filter(
      (c: Course) => c.status !== "DELETED"
    );
    treeProgramCoursesMap.value = {
      ...treeProgramCoursesMap.value,
      [programId]: items,
    };
    treeProgramTotalCoursesMap.value = {
      ...treeProgramTotalCoursesMap.value,
      [programId]: typeof data.totalElements === "number" ? data.totalElements : items.length,
    };
    treeProgramPageMap.value = {
      ...treeProgramPageMap.value,
      [programId]: 0,
    };
    treeProgramHasNextMap.value = {
      ...treeProgramHasNextMap.value,
      [programId]: !!data.hasNext,
    };
  } catch (err) {
    console.error(`Lỗi tải khóa học cho chương trình ${programId}:`, err);
    treeProgramCoursesMap.value = { ...treeProgramCoursesMap.value, [programId]: [] };
    treeProgramTotalCoursesMap.value = { ...treeProgramTotalCoursesMap.value, [programId]: 0 };
    treeProgramHasNextMap.value = { ...treeProgramHasNextMap.value, [programId]: false };
  } finally {
    loadingTreeProgramIds.value = { ...loadingTreeProgramIds.value, [programId]: false };
  }
}

async function fetchMoreProgramCoursesForTree(programId: string) {
  if (
    !programId ||
    loadingMoreTreeProgramIds.value[programId] ||
    !treeProgramHasNextMap.value[programId]
  ) {
    return;
  }
  const nextPage = (treeProgramPageMap.value[programId] ?? 0) + 1;
  loadingMoreTreeProgramIds.value = { ...loadingMoreTreeProgramIds.value, [programId]: true };
  try {
    const res = await axiosClient.get(`/api/admin/courses/by-program/${programId}`, {
      params: { page: nextPage, size: DEFAULT_COURSE_BRANCH_SIZE },
    });
    const data = res.data || {};
    const newItems: Course[] = (data.items || []).filter(
      (c: Course) => c.status !== "DELETED"
    );
    const existing = treeProgramCoursesMap.value[programId] || [];
    const existingIds = new Set(existing.map((c) => c.id));
    const merged = [...existing, ...newItems.filter((c) => !existingIds.has(c.id))];

    treeProgramCoursesMap.value = {
      ...treeProgramCoursesMap.value,
      [programId]: merged,
    };
    if (typeof data.totalElements === "number") {
      treeProgramTotalCoursesMap.value = {
        ...treeProgramTotalCoursesMap.value,
        [programId]: data.totalElements,
      };
    }
    treeProgramPageMap.value = {
      ...treeProgramPageMap.value,
      [programId]: nextPage,
    };
    treeProgramHasNextMap.value = {
      ...treeProgramHasNextMap.value,
      [programId]: !!data.hasNext,
    };
  } catch (err) {
    console.error(`Lỗi tải thêm khóa học cho chương trình ${programId}:`, err);
  } finally {
    loadingMoreTreeProgramIds.value = { ...loadingMoreTreeProgramIds.value, [programId]: false };
  }
}

function isProgramCourseCountKnown(pNode: TreeProgramNode): boolean {
  if (loadingTreeProgramIds.value[pNode.program.id]) return true;
  if (treeProgramTotalCoursesMap.value[pNode.program.id] !== undefined) return true;
  return !pNode.directAllocation && pNode.courses.length > 0;
}

function getProgramTotalCourseCount(pNode: TreeProgramNode): number {
  const serverTotal = treeProgramTotalCoursesMap.value[pNode.program.id];
  if (typeof serverTotal === "number") {
    return Math.max(serverTotal, pNode.courses.length);
  }
  return pNode.courses.length;
}

const allocationTreeDomains = computed<TreeDomainNode[]>(() => {
  if (!selectedUnitId.value) return [];
  const filteredRows = filteredAssignedAllocations.value;
  if (!filteredRows.length) return [];

  const q = tableFilterQuery.value.trim().toLowerCase();

  const domainRowMap = new Map<string, AllocationRow>();
  const programRowMap = new Map<string, AllocationRow>();
  const coursesByProgramMap = new Map<string, AllocationRow[]>();

  for (const row of filteredRows) {
    if (row.type === "domain") {
      domainRowMap.set(row.id, row);
    } else if (row.type === "program") {
      programRowMap.set(row.id, row);
    } else if (row.type === "course") {
      const pId = row.rawObj?.programId || "unassigned_prog";
      const list = coursesByProgramMap.get(pId) || [];
      list.push(row);
      coursesByProgramMap.set(pId, list);
    }
  }

  const result: TreeDomainNode[] = [];

  for (const d of activeDomains.value) {
    const directDomainAlloc = domainRowMap.get(d.id);
    const domainProgs = activePrograms.value.filter((p) => p.domainId === d.id);

    const progNodes: TreeProgramNode[] = [];
    let domainTotalCourses = 0;

    for (const p of domainProgs) {
      const directProgAlloc = programRowMap.get(p.id);
      const directCourses = coursesByProgramMap.get(p.id) || [];

      // Chương trình được hiển thị nếu:
      // 1) Được gán trực tiếp, HOẶC 2) Có khóa học con được gán, HOẶC 3) Lĩnh vực cha được gán trọn gói
      if (directProgAlloc || directCourses.length > 0 || directDomainAlloc) {
        let mergedCourses: AllocationRow[] = [...directCourses];

        // Nếu cả Chương trình hoặc cả Lĩnh vực được phân bổ -> Hiển thị toàn bộ danh sách Khóa học thuộc Chương trình đó
        if (directProgAlloc || directDomainAlloc) {
          const inheritedGrant =
            directProgAlloc?.grantType || directDomainAlloc?.grantType || "MANDATORY";
          const cachedCourses = treeProgramCoursesMap.value[p.id] || [];
          const directCourseIdSet = new Set(directCourses.map((c) => c.id));

          for (const c of cachedCourses) {
            if (directCourseIdSet.has(c.id)) continue;
            // Nếu người dùng đang gõ từ khóa tìm kiếm, kiểm tra khớp tên/mã khóa hoặc tên chương trình/lĩnh vực
            const parentMatchesQuery =
              !q ||
              p.name.toLowerCase().includes(q) ||
              p.code.toLowerCase().includes(q) ||
              d.name.toLowerCase().includes(q) ||
              d.code.toLowerCase().includes(q);
            const courseMatchesQuery =
              !q ||
              c.name.toLowerCase().includes(q) ||
              (c.code && c.code.toLowerCase().includes(q)) ||
              (c.description && c.description.toLowerCase().includes(q));

            if (parentMatchesQuery || courseMatchesQuery) {
              mergedCourses.push({
                id: c.id,
                type: "course",
                typeLabel: "Khóa học",
                code: c.code || "",
                name: c.name,
                grantType: inheritedGrant,
                parentProgramName: p.name,
                isInherited: true,
                rawObj: c,
              });
            }
          }
        }

        progNodes.push({
          program: p,
          directAllocation: directProgAlloc,
          courses: mergedCourses,
        });
        domainTotalCourses += mergedCourses.length;
      }
    }

    if (directDomainAlloc || progNodes.length > 0) {
      result.push({
        domain: d,
        directAllocation: directDomainAlloc,
        programs: progNodes,
        totalCourses: domainTotalCourses,
      });
    }
  }

  return result;
});

// Tier-1 Pagination (Root Domain Branches)
const treeDomainPage = ref(1);
const treeDomainPageSize = ref(5);

const treeDomainTotalPages = computed(() =>
  Math.max(1, Math.ceil(allocationTreeDomains.value.length / treeDomainPageSize.value))
);

const pagedAllocationTreeDomains = computed(() => {
  const start = (treeDomainPage.value - 1) * treeDomainPageSize.value;
  return allocationTreeDomains.value.slice(start, start + treeDomainPageSize.value);
});

// Chỉ Lazy-Fetch danh sách Khóa học (page=0, size=6) cho các Chương trình nằm ở TRANG HIỆN TẠI và ĐANG ĐƯỢC MỞ RỘNG
function syncVisibleExpandedPrograms() {
  for (const dNode of pagedAllocationTreeDomains.value) {
    if (!expandedTreeNodeIds.value.has(dNode.domain.id)) continue;
    for (const pNode of dNode.programs) {
      if (
        expandedTreeNodeIds.value.has(pNode.program.id) &&
        (pNode.directAllocation || dNode.directAllocation)
      ) {
        ensureProgramCoursesLoaded(pNode.program.id);
      }
    }
  }
}

watch(
  allocationTreeDomains,
  (nodes) => {
    const nextSet = new Set<string>(expandedTreeNodeIds.value);
    const shouldAutoExpand = nextSet.size === 0;

    if (shouldAutoExpand && nodes.length > 0) {
      // Mặc định mở rộng Lĩnh vực đầu tiên và các Chương trình trong Lĩnh vực đầu tiên (Lazy-load đúng nhánh đang xem)
      const firstDomain = nodes[0];
      nextSet.add(firstDomain.domain.id);
      for (const pNode of firstDomain.programs) {
        nextSet.add(pNode.program.id);
      }
      expandedTreeNodeIds.value = nextSet;
      allTreeExpanded.value = false;
    }

    if (treeDomainPage.value > treeDomainTotalPages.value) {
      treeDomainPage.value = 1;
    }
    syncVisibleExpandedPrograms();
  },
  { immediate: true }
);

watch([pagedAllocationTreeDomains, treeDomainPage], () => {
  syncVisibleExpandedPrograms();
});

watch(selectedUnitId, () => {
  expandedTreeNodeIds.value = new Set();
});

function isTreeExpanded(id: string): boolean {
  return expandedTreeNodeIds.value.has(id);
}

function toggleTreeNode(id: string) {
  const next = new Set(expandedTreeNodeIds.value);
  if (next.has(id)) {
    next.delete(id);
  } else {
    next.add(id);
  }
  expandedTreeNodeIds.value = next;
  syncVisibleExpandedPrograms();
}

function toggleAllTreeNodes() {
  allTreeExpanded.value = !allTreeExpanded.value;
  if (!allTreeExpanded.value) {
    expandedTreeNodeIds.value = new Set();
  } else {
    const next = new Set<string>();
    for (const dNode of pagedAllocationTreeDomains.value) {
      next.add(dNode.domain.id);
      for (const pNode of dNode.programs) {
        next.add(pNode.program.id);
      }
    }
    expandedTreeNodeIds.value = next;
    syncVisibleExpandedPrograms();
  }
}

// Tier-2 Pagination (In-Branch Courses inside a Program — Server-Side Lazy Fetch)
function getCourseBranchLimit(programId: string): number {
  return courseBranchLimits.value[programId] || DEFAULT_COURSE_BRANCH_SIZE;
}

function getVisibleCoursesForProgram(programId: string, courses: AllocationRow[]): AllocationRow[] {
  const limit = getCourseBranchLimit(programId);
  return courses.slice(0, limit);
}

async function expandCourseBranchLimit(pNode: TreeProgramNode) {
  const programId = pNode.program.id;
  const total = getProgramTotalCourseCount(pNode);
  const current = getCourseBranchLimit(programId);
  const nextLimit = Math.min(total, current + DEFAULT_COURSE_BRANCH_SIZE);
  courseBranchLimits.value[programId] = nextLimit;

  const loadedCount = (treeProgramCoursesMap.value[programId] || []).length;
  if (nextLimit > loadedCount && treeProgramHasNextMap.value[programId]) {
    await fetchMoreProgramCoursesForTree(programId);
  }
}

function resetCourseBranchLimit(programId: string) {
  courseBranchLimits.value[programId] = DEFAULT_COURSE_BRANCH_SIZE;
}

// Flat Table Pagination
const flatTablePage = ref(1);
const flatTablePageSize = ref(10);

const flatTableTotalPages = computed(() =>
  Math.max(1, Math.ceil(filteredAssignedAllocations.value.length / flatTablePageSize.value))
);

const pagedFlatAllocations = computed(() => {
  const start = (flatTablePage.value - 1) * flatTablePageSize.value;
  return filteredAssignedAllocations.value.slice(start, start + flatTablePageSize.value);
});

watch([tableFilterQuery, grantFilter, selectedUnitId], () => {
  treeDomainPage.value = 1;
  flatTablePage.value = 1;
});

// ================= 3. INLINE GRANT TYPE QUICK SWITCH (BẮT BUỘC <-> TỰ CHỌN) =================
async function handleToggleGrantType(item: AllocationRow) {
  if (!selectedUnitId.value || togglingId.value) return;
  const uId = selectedUnitId.value;
  const nextGrantType = item.grantType === "MANDATORY" ? "OPTIONAL" : "MANDATORY";
  togglingId.value = item.id;

  try {
    const res = await axiosClient.patch("/api/admin/unit-allocations/grant-type", {
      unitId: uId,
      categoryType: item.type,
      targetId: item.id,
      grantType: nextGrantType,
    });

    const stats = res.data || {};
    const enrolledMsg =
      nextGrantType === "MANDATORY"
        ? ` (Đã tự động đăng ký cho ${stats.enrolledUsersCount ?? 0} nhân sự trong đơn vị)`
        : ` (Hiển thị tại mục Khám phá để nhân sự tự đăng ký học)`;

    showToast(
      `Đã chuyển [${item.name}] sang hình thức ${nextGrantType === "MANDATORY" ? "Bắt buộc" : "Tự chọn"}${enrolledMsg}!`,
      "success"
    );
    treeProgramCoursesMap.value = {};
    treeProgramTotalCoursesMap.value = {};
    await loadData();
    syncVisibleExpandedPrograms();
  } catch (err: any) {
    console.error("Lỗi khi đổi hình thức áp dụng:", err);
    showToast(err.response?.data?.message || "Không thể đổi hình thức áp dụng", "error");
  } finally {
    togglingId.value = "";
  }
}

// ================= 4. RICH HOVER DESCRIPTION TOOLTIP (LIKE CURRICULUM TREE STUDIO) =================
const hoverTooltip = ref<{
  visible: boolean;
  x: number;
  y: number;
  type: string;
  typeLabel: string;
  code: string;
  title: string;
  description: string;
  businessModule: string;
  level: string;
  deadline: string;
}>({
  visible: false,
  x: 0,
  y: 0,
  type: "domain",
  typeLabel: "Lĩnh vực",
  code: "",
  title: "",
  description: "",
  businessModule: "",
  level: "",
  deadline: "",
});

function formatLevelLabel(level?: string): string {
  switch (level) {
    case "BASIC":
      return "Cơ bản";
    case "ADVANCED":
      return "Nâng cao";
    case "EXPERT":
      return "Chuyên gia";
    case "COMPLIANCE":
      return "Định kỳ bắt buộc";
    default:
      return level || "";
  }
}

function showNodeTooltip(e: MouseEvent, type: "domain" | "program" | "course", rawObj: any) {
  if (!rawObj) return;
  const target = e.currentTarget as HTMLElement;
  const rect = target.getBoundingClientRect();

  const popoverWidth = 340;
  let left = rect.left;
  if (left + popoverWidth > window.innerWidth - 20) {
    left = Math.max(16, window.innerWidth - popoverWidth - 20);
  }
  let top = rect.bottom + 8;
  if (top + 170 > window.innerHeight) {
    top = Math.max(16, rect.top - 155);
  }

  const typeLabelMap: Record<string, string> = {
    domain: "Lĩnh vực đào tạo",
    program: "Chương trình đào tạo",
    course: "Khóa học nghiệp vụ",
  };

  hoverTooltip.value = {
    visible: true,
    x: left,
    y: top,
    type,
    typeLabel: typeLabelMap[type] || "Nội dung đào tạo",
    code: rawObj.code || "",
    title: rawObj.name || rawObj.title || "",
    description: rawObj.description || "",
    businessModule: rawObj.businessModule || "",
    level: rawObj.level || "",
    deadline: rawObj.completionDeadline || "",
  };
}

function hideNodeTooltip() {
  hoverTooltip.value.visible = false;
}

// Handle assign action (Unified API: /api/admin/unit-allocations/assign)
async function handleAssignAllocation() {
  if (!selectedUnitId.value) {
    showToast("Vui lòng chọn Đơn vị áp dụng!", "warning");
    return;
  }

  if (categoryType.value === "domain" && !targetDomainId.value) {
    showToast("Vui lòng chọn Lĩnh vực đào tạo!", "warning");
    return;
  }
  if ((categoryType.value === "program" || categoryType.value === "course") && !targetProgramId.value) {
    showToast("Vui lòng chọn Chương trình đào tạo!", "warning");
    return;
  }
  if (
    categoryType.value === "course" &&
    !selectAllCoursesInProgram.value &&
    !selectedSubCourseIds.value.length
  ) {
    showToast("Vui lòng chọn ít nhất 1 khóa học để phân bổ!", "warning");
    return;
  }

  saving.value = true;
  const uId = selectedUnitId.value;

  try {
    const res = await axiosClient.post("/api/admin/unit-allocations/assign", {
      unitId: uId,
      categoryType: categoryType.value,
      grantType: grantType.value,
      domainId: categoryType.value === "domain" ? targetDomainId.value : undefined,
      programId:
        categoryType.value === "program" || categoryType.value === "course"
          ? targetProgramId.value
          : undefined,
      allInProgram: categoryType.value === "course" ? selectAllCoursesInProgram.value : true,
      courseIds:
        categoryType.value === "course" && !selectAllCoursesInProgram.value
          ? selectedSubCourseIds.value
          : [],
    });

    const stats = res.data || {};
    const coursesCount = stats.allocatedCoursesCount ?? 0;
    const enrolledUsers = stats.enrolledUsersCount ?? 0;

    if (grantType.value === "MANDATORY") {
      showToast(
        `Đã phân bổ Bắt buộc (${coursesCount} khóa học) và tự động đăng ký cho ${enrolledUsers} nhân sự thuộc đơn vị!`,
        "success"
      );
    } else {
      showToast(
        `Đã phân bổ Tự chọn (${coursesCount} khóa học) cho đơn vị — hiển thị tại mục Khám phá để nhân sự tự đăng ký học!`,
        "success"
      );
    }

    treeProgramCoursesMap.value = {};
    treeProgramTotalCoursesMap.value = {};
    await loadData();
    syncVisibleExpandedPrograms();
  } catch (err: any) {
    console.error("Lỗi khi lưu phân bổ:", err);
    showToast(err.response?.data?.message || "Lỗi khi lưu phân bổ cho đơn vị", "error");
  } finally {
    saving.value = false;
  }
}

// Handle remove allocation (Unified API: DELETE /api/admin/unit-allocations)
async function handleRemoveAllocation(item: AllocationRow) {
  if (!confirm(`Bạn có chắc chắn muốn gỡ bỏ phân bổ [${item.name}] khỏi đơn vị này?`)) {
    return;
  }

  deletingId.value = item.id;
  const uId = selectedUnitId.value;

  try {
    await axiosClient.delete("/api/admin/unit-allocations", {
      params: {
        unitId: uId,
        categoryType: item.type,
        targetId: item.id,
      },
    });

    showToast(`Đã gỡ bỏ phân bổ [${item.name}] khỏi đơn vị!`, "success");
    treeProgramCoursesMap.value = {};
    treeProgramTotalCoursesMap.value = {};
    await loadData();
    syncVisibleExpandedPrograms();
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
   IMPECCABLE DESIGN SYSTEM: BIDV EMERALD & GOLD ENTERPRISE THEME
   Synchronized with global theme tokens (Light & Dark mode compatible)
   Concentric border-radiuses: 14px (Card) -> 12px (Sub-box) -> 10px (Control) -> 8px (Item) -> 9999px (Pill)
   ========================================================================== */

.unit-allocation-page {
  padding: 1.75rem 2rem 2.5rem;
  display: flex;
  flex-direction: column;
  gap: 1.35rem;
  max-width: 1600px;
  margin: 0 auto;
  width: 100%;
  color: var(--text-main);
}

/* BREADCRUMB BAR */
.breadcrumb-bar {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.76rem;
  font-weight: 500;
  color: var(--text-muted);
}

.crumb {
  color: var(--text-muted);
  transition: color 0.15s ease;
}

.crumb-separator {
  color: var(--text-muted);
  opacity: 0.55;
}

.crumb.active {
  color: var(--primary-ink);
  font-weight: 700;
}

/* PAGE HEADER */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  border-bottom: 1px solid var(--border-color);
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
  font-size: 1.5rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--text-main);
  margin: 0;
}

.subtitle {
  font-size: 0.85rem;
  color: var(--text-muted);
  margin: 0;
  line-height: 1.5;
}

/* TWO-COLUMN LAYOUT */
.allocation-layout {
  display: grid;
  grid-template-columns: 460px minmax(0, 1fr);
  gap: 1.5rem;
  align-items: stretch;
}

@media (max-width: 1100px) {
  .allocation-layout {
    grid-template-columns: 1fr;
  }
}

/* CARDS (Outer radius: 14px) */
.card {
  padding: 0;
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  overflow: hidden;
  box-shadow: var(--shadow-sm);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 1.35rem;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-main);
  gap: 0.75rem;
  flex-shrink: 0;
}

.panel-header-title {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.panel-header h3 {
  font-size: 0.96rem;
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--text-main);
  margin: 0;
}

.panel-icon {
  color: var(--primary-ink);
  flex-shrink: 0;
}

.step-guide-badge {
  font-size: 0.72rem;
  font-weight: 600;
  padding: 0.2rem 0.65rem;
  border-radius: 9999px;
  background: var(--primary-light);
  color: var(--primary-ink);
  border: 1px solid var(--success-border);
  font-variant-numeric: tabular-nums;
}

/* FORM PANEL */
.allocation-form {
  padding: 1.35rem;
  display: flex;
  flex-direction: column;
  gap: 1.3rem;
  flex: 1;
}

.form-actions {
  margin-top: auto;
  padding-top: 0.4rem;
}

.step-group {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

.step-header {
  display: flex;
  align-items: center;
  gap: 0.55rem;
}

.step-number {
  font-family: var(--font-family);
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--primary-ink);
  background: var(--primary-light);
  border: 1px solid var(--success-border);
  border-radius: 6px;
  padding: 0.1rem 0.42rem;
  letter-spacing: 0.04em;
}

.step-header label {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-main);
}

.sub-label {
  font-size: 0.79rem;
  font-weight: 600;
  color: var(--text-muted);
}

.sub-form-group {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.course-cascade-box {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

/* INPUTS & SELECTS (Radius: 10px) */
.form-control {
  width: 100%;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  color: var(--text-main);
  border-radius: 10px;
  padding: 0.68rem 0.9rem;
  font-size: 0.85rem;
  outline: none;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, background-color 0.16s ease;
}

.form-control:hover:not(:disabled) {
  border-color: var(--line-97c7b2, #97c7b2);
}

.form-control:focus {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px var(--primary-light);
}

/* CATEGORY SELECTOR (STEP 2 - Radius: 10px) */
.category-type-selector {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0.6rem;
}

.radio-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  gap: 0.4rem;
  background: var(--bg-main);
  border: 1.5px solid var(--border-color);
  border-radius: 10px;
  padding: 0.85rem 0.5rem;
  cursor: pointer;
  color: var(--text-muted);
  transition: all 0.16s cubic-bezier(0.22, 1, 0.36, 1);
  user-select: none;
}

.radio-card input {
  display: none;
}

.radio-card:hover {
  background: var(--primary-light);
  border-color: var(--line-97c7b2, #97c7b2);
  color: var(--primary-ink);
  transform: translateY(-1px);
}

.radio-card.active {
  background: var(--primary-light);
  border-color: var(--primary-color);
  color: var(--primary-ink);
  box-shadow: 0 2px 10px rgba(0, 91, 65, 0.12);
}

.radio-card-title {
  font-size: 0.84rem;
  font-weight: 700;
  color: inherit;
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
  background: var(--success-ink);
}

.optional-dot {
  background: var(--warning-ink);
}

/* BUTTONS (Radius: 10px) */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  padding: 0.62rem 1.05rem;
  font-size: 0.84rem;
  font-weight: 600;
  border-radius: 10px;
  border: 1px solid transparent;
  cursor: pointer;
  transition: all 0.16s cubic-bezier(0.22, 1, 0.36, 1);
  text-decoration: none;
}

.btn:active:not(:disabled) {
  transform: scale(0.98);
}

.btn-primary {
  background: var(--primary-color);
  color: #ffffff;
  border-color: var(--primary-color);
  box-shadow: 0 2px 8px rgba(0, 91, 65, 0.22);
}

.btn-primary:hover:not(:disabled) {
  background: var(--primary-hover);
  border-color: var(--primary-hover);
  box-shadow: 0 4px 14px rgba(0, 91, 65, 0.3);
}

.btn-secondary {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  color: var(--text-main);
}

.btn-secondary:hover:not(:disabled) {
  background: var(--primary-light);
  border-color: var(--primary-color);
  color: var(--primary-ink);
}

.btn-submit {
  width: 100%;
  padding: 0.78rem;
  font-size: 0.88rem;
  letter-spacing: 0.01em;
}

.btn-sm {
  padding: 0.42rem 0.8rem;
  font-size: 0.78rem;
  border-radius: 8px;
}

.btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  transform: none !important;
}

/* RIGHT PANEL: MATRIX & TREE HEADER */
.matrix-header {
  flex-direction: column;
  align-items: stretch;
  gap: 0.75rem;
  padding: 1rem 1.35rem 0.9rem;
}

.matrix-header-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.matrix-header-right {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
}

.current-unit-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.76rem;
  font-weight: 700;
  background: var(--primary-light);
  color: var(--primary-ink);
  border: 1px solid var(--success-border);
  padding: 0.28rem 0.55rem 0.28rem 0.75rem;
  border-radius: 9999px;
  max-width: 340px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chip-clear-unit {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 9999px;
  background: transparent;
  border: none;
  color: var(--primary-ink);
  cursor: pointer;
  opacity: 0.7;
  transition: opacity 0.15s ease, background-color 0.15s ease;
}

.chip-clear-unit:hover {
  opacity: 1;
  background: rgba(0, 0, 0, 0.08);
}

/* VIEW SWITCHER TABS (Cây phân cấp | Bảng phẳng) */
.matrix-view-Order-tabs {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.2rem;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 10px;
}

.matrix-view-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.32rem 0.65rem;
  border-radius: 7px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  font-size: 0.75rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.matrix-view-btn:hover {
  color: var(--text-main);
}

.matrix-view-btn.active {
  background: var(--primary-light);
  color: var(--primary-ink);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

/* KPI SUMMARY CHIPS (Interactive Filter Pills: 9999px) */
.kpi-chips-bar {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding-top: 0.65rem;
  border-top: 1px solid var(--border-color);
  flex-wrap: wrap;
}

.kpi-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.75rem;
  padding: 0.26rem 0.75rem;
  border-radius: 9999px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  cursor: pointer;
  transition: all 0.15s ease;
}

.kpi-chip:hover {
  transform: translateY(-1px);
}

.kpi-chip.is-active-filter {
  box-shadow: 0 0 0 2px var(--primary-color);
}

.kpi-label {
  color: var(--text-muted);
  font-weight: 500;
}

.kpi-val {
  font-family: var(--font-family);
  font-weight: 700;
  font-size: 0.79rem;
  color: var(--text-main);
  font-variant-numeric: tabular-nums;
}

.kpi-total {
  background: var(--primary-light);
  border-color: var(--success-border);
}

.kpi-total .kpi-val {
  color: var(--primary-ink);
}

.kpi-mandatory {
  background: var(--success-surface);
  border-color: var(--success-border);
}

.kpi-mandatory .kpi-val {
  color: var(--success-ink);
}

.kpi-optional {
  background: var(--warning-surface);
  border-color: var(--warning-border);
}

.kpi-optional .kpi-val {
  color: var(--warning-ink);
}

.kpi-hint-text {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.73rem;
  color: var(--text-muted);
}

/* QUICK FILTER BAR */
.matrix-filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.75rem 1.35rem;
  background: var(--bg-card);
  border-bottom: 1px solid var(--border-color);
  flex-wrap: wrap;
}

.quick-filter-input-wrap {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: var(--bg-main);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 0.45rem 0.75rem;
  flex: 1;
  max-width: 500px;
  transition: border-color 0.16s ease, box-shadow 0.16s ease;
}

.quick-filter-input-wrap:focus-within {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px var(--primary-light);
  background: var(--bg-card);
}

.quick-filter-icon {
  color: var(--text-muted);
  flex-shrink: 0;
}

.quick-filter-input {
  background: transparent;
  border: none;
  outline: none;
  color: var(--text-main);
  font-size: 0.82rem;
  width: 100%;
}

.quick-filter-input::placeholder {
  color: var(--text-muted);
}

.btn-clear-filter {
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  padding: 2px;
  border-radius: 4px;
}

.btn-clear-filter:hover {
  color: var(--text-main);
}

.matrix-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.filter-count-badge {
  font-size: 0.74rem;
  color: var(--text-muted);
  font-family: var(--font-family);
}

.btn-tree-expand-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.4rem 0.75rem;
  border-radius: 8px;
  border: 1px solid var(--border-color);
  background: var(--bg-main);
  color: var(--text-main);
  font-size: 0.76rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-tree-expand-toggle:hover {
  background: var(--primary-light);
  border-color: var(--primary-color);
  color: var(--primary-ink);
}

/* ==========================================================================
   STATE 1: INTERACTIVE UNIT DIRECTORY (ONBOARDING WHEN NO UNIT SELECTED)
   ========================================================================== */
.unit-directory-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.directory-intro {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.1rem 1.35rem;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-card);
  flex-wrap: wrap;
}

.directory-intro-left {
  display: flex;
  align-items: center;
  gap: 0.85rem;
}

.compact-ring {
  width: 44px !important;
  height: 44px !important;
  margin-bottom: 0 !important;
  flex-shrink: 0;
}

.directory-intro h4 {
  font-size: 0.94rem;
  font-weight: 700;
  color: var(--text-main);
  margin: 0 0 0.2rem;
}

.directory-intro p {
  font-size: 0.8rem;
  color: var(--text-muted);
  margin: 0;
}

.directory-search-box {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  background: var(--bg-main);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  padding: 0.45rem 0.75rem;
  min-width: 240px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.directory-search-box:focus-within {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px var(--primary-light);
  background: var(--bg-card);
}

.directory-search-box .search-icon {
  color: var(--text-muted);
  flex-shrink: 0;
}

.directory-search-input {
  border: none;
  background: transparent;
  outline: none;
  color: var(--text-main);
  font-size: 0.8rem;
  width: 100%;
}

.unit-cards-grid {
  padding: 1.25rem 1.35rem;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 0.85rem;
  align-content: start;
  flex: 1;
}

.unit-quick-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.55rem;
  padding: 0.95rem 1rem;
  border-radius: 12px;
  background: var(--bg-main);
  border: 1px solid var(--border-color);
  text-align: left;
  cursor: pointer;
  transition: all 0.16s cubic-bezier(0.22, 1, 0.36, 1);
}

.unit-quick-card:hover {
  background: var(--primary-light);
  border-color: var(--primary-color);
  transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
}

.unit-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.unit-card-icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  color: var(--primary-ink);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.unit-type-tag {
  font-size: 0.68rem;
  font-weight: 600;
  padding: 0.15rem 0.5rem;
  border-radius: 9999px;
  background: var(--bg-card);
  color: var(--text-muted);
  border: 1px solid var(--border-color);
}

.unit-card-name {
  font-size: 0.86rem;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.35;
}

.unit-card-metrics {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin-top: auto;
  padding-top: 0.25rem;
  flex-wrap: wrap;
}

.metric-pill {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--text-muted);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  padding: 0.14rem 0.48rem;
  border-radius: 6px;
  font-variant-numeric: tabular-nums;
}

/* ==========================================================================
   STATE 5A: 3-LEVEL CURRICULUM ALLOCATION TREE VIEW
   ========================================================================== */
.allocation-tree-wrapper {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 0;
}

.allocation-tree-scroll {
  flex: 1;
  overflow-y: auto;
  padding: 1rem 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  max-height: 620px;
}

.alloc-tree-branch {
  display: flex;
  flex-direction: column;
}

.alloc-tree-node {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.58rem 0.8rem;
  border-radius: 10px;
  cursor: pointer;
  user-select: none;
  transition: all 0.14s ease;
  border: 1px solid transparent;
}

.alloc-tree-node:hover {
  background: var(--primary-light);
  border-color: var(--line-97c7b2, #97c7b2);
}

.domain-node {
  background: var(--bg-main);
  border-color: var(--border-color);
  font-weight: 700;
}

.program-node {
  background: var(--bg-card);
  border-color: var(--border-color);
  font-weight: 600;
  margin-top: 0.35rem;
}

.course-node {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  font-weight: 500;
  padding: 0.48rem 0.75rem;
  border-radius: 8px;
  cursor: default;
}

.tree-chevron-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 6px;
  border: none;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  flex-shrink: 0;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.tree-chevron-btn:hover {
  background: var(--bg-card);
  color: var(--primary-ink);
}

.tree-chevron-btn svg {
  transition: transform 0.16s ease;
}

.tree-chevron-btn svg.rotated {
  transform: rotate(90deg);
}

.node-level-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.domain-icon {
  color: var(--info-ink);
}

.program-icon {
  color: var(--warning-ink);
}

.course-icon {
  color: var(--success-ink);
}

.course-tree-Dot {
  width: 6px;
  height: 6px;
  border-radius: 9999px;
  background: var(--border-color);
  margin-left: 0.25rem;
  flex-shrink: 0;
}

.node-main-info {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  padding: 0.15rem 0;
}

.node-code-badge {
  font-family: var(--font-family);
  font-size: 0.73rem;
  font-weight: 700;
  color: var(--primary-ink);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  padding: 0.1rem 0.42rem;
  border-radius: 6px;
  flex-shrink: 0;
}

.node-title-text {
  font-size: 0.85rem;
  color: var(--text-main);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.node-desc-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--info-ink);
  opacity: 0.65;
  flex-shrink: 0;
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.node-main-info:hover .node-desc-trigger {
  opacity: 1;
  transform: scale(1.12);
}

.node-meta-actions {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin-left: auto;
  flex-shrink: 0;
}

.module-pill {
  font-size: 0.69rem;
  font-weight: 700;
  padding: 0.14rem 0.48rem;
  border-radius: 9999px;
  background: var(--info-surface);
  color: var(--info-ink);
  border: 1px solid var(--info-border);
}

.child-count-tag {
  font-size: 0.71rem;
  font-weight: 600;
  padding: 0.14rem 0.5rem;
  border-radius: 9999px;
  background: var(--bg-card);
  color: var(--text-muted);
  border: 1px solid var(--border-color);
  font-variant-numeric: tabular-nums;
}

.partial-alloc-badge {
  font-size: 0.71rem;
  font-weight: 600;
  padding: 0.18rem 0.58rem;
  border-radius: 9999px;
  background: var(--bg-subtle);
  color: var(--text-muted);
  border: 1px dashed var(--border-color);
}

.inherited-alloc-badge {
  font-size: 0.71rem;
  font-weight: 600;
  padding: 0.18rem 0.58rem;
  border-radius: 9999px;
  background: var(--primary-light);
  color: var(--primary-ink);
  border: 1px dashed var(--success-border);
}

.course-status-tag {
  font-size: 0.69rem;
  font-weight: 600;
  padding: 0.14rem 0.48rem;
  border-radius: 9999px;
}

.course-status-tag.published {
  background: var(--success-surface);
  color: var(--success-ink);
  border: 1px solid var(--success-border);
}

.course-status-tag.draft {
  background: var(--bg-subtle);
  color: var(--text-muted);
  border: 1px solid var(--border-color);
}

/* Tree indentation & vertical guide lines (like CurriculumTreeStudio) */
.alloc-tree-children {
  margin-left: 1.35rem;
  padding-left: 0.85rem;
  border-left: 1px dashed var(--border-color);
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin-top: 0.3rem;
}

.course-children {
  margin-left: 1.5rem;
  padding-top: 0.2rem;
  padding-bottom: 0.2rem;
}

.tree-empty-leaf {
  font-size: 0.78rem;
  color: var(--text-muted);
  font-style: italic;
  padding: 0.35rem 0.5rem;
}

.tree-loading-leaf {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.78rem;
  color: var(--primary-ink);
  padding: 0.4rem 0.6rem;
}

.inherited-course-mode {
  border-style: dashed !important;
  opacity: 0.9;
}

/* Tier-2 In-Branch Course Pagination Row */
.in-branch-pagination-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.45rem 0.75rem;
  border-radius: 8px;
  background: var(--bg-main);
  border: 1px dashed var(--border-color);
  margin-top: 0.2rem;
  flex-wrap: wrap;
}

.in-branch-count {
  font-size: 0.74rem;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.in-branch-actions {
  display: flex;
  align-items: center;
  gap: 0.45rem;
}

.btn-in-branch-more,
.btn-in-branch-collapse {
  font-size: 0.74rem;
  font-weight: 600;
  padding: 0.24rem 0.65rem;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-in-branch-more {
  background: var(--primary-light);
  color: var(--primary-ink);
  border: 1px solid var(--success-border);
}

.btn-in-branch-more:hover {
  background: var(--primary-color);
  color: #ffffff;
}

.btn-in-branch-collapse {
  background: var(--bg-card);
  color: var(--text-muted);
  border: 1px solid var(--border-color);
}

.btn-in-branch-collapse:hover {
  color: var(--text-main);
}

/* ==========================================================================
   PAGINATION BAR (SHARED BY TREE & FLAT TABLE & DIRECTORY)
   ========================================================================== */
.matrix-pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.75rem 1.35rem;
  border-top: 1px solid var(--border-color);
  background: var(--bg-main);
  flex-wrap: wrap;
  margin-top: auto;
}

.pagination-summary {
  font-size: 0.78rem;
  color: var(--text-muted);
  font-variant-numeric: tabular-nums;
}

.pagination-summary strong {
  color: var(--text-main);
}

.pagination-controls {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  flex-wrap: wrap;
}

.page-size-label {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.76rem;
  color: var(--text-muted);
}

.page-size-select {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  color: var(--text-main);
  border-radius: 8px;
  padding: 0.25rem 0.55rem;
  font-size: 0.76rem;
  font-weight: 600;
  outline: none;
  cursor: pointer;
}

.page-size-select:focus {
  border-color: var(--primary-color);
}

.page-nav-group {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.page-btn {
  padding: 0.28rem 0.68rem;
  border-radius: 8px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-main);
  font-size: 0.76rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.14s ease;
}

.page-btn:hover:not(:disabled) {
  background: var(--primary-light);
  border-color: var(--primary-color);
  color: var(--primary-ink);
}

.page-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.page-indicator {
  font-size: 0.76rem;
  font-weight: 600;
  color: var(--text-main);
  padding: 0 0.25rem;
  font-variant-numeric: tabular-nums;
}

/* ==========================================================================
   STATE 5B: TABLE STYLING
   ========================================================================== */
.allocation-table-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.allocation-table-wrapper {
  flex: 1;
  overflow-x: auto;
  max-height: 600px;
}

.allocation-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 0.83rem;
  text-align: left;
}

.allocation-table thead th {
  position: sticky;
  top: 0;
  z-index: 10;
  background: var(--bg-main);
  color: var(--text-muted);
  font-size: 0.73rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  padding: 0.8rem 1.1rem;
  border-bottom: 1px solid var(--border-color);
  white-space: nowrap;
}

.allocation-table tbody tr {
  transition: background-color 0.12s ease;
}

.allocation-table tbody tr:hover {
  background-color: var(--primary-light);
}

.allocation-table td {
  padding: 0.85rem 1.1rem;
  border-bottom: 1px solid var(--border-color);
  color: var(--text-main);
  vertical-align: middle;
}

.table-name-with-info {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  cursor: help;
}

/* TYPE PILL (Cấp bậc đào tạo - Pill radius: 9999px) */
.type-pill {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  font-size: 0.72rem;
  font-weight: 700;
  padding: 0.22rem 0.65rem;
  border-radius: 9999px;
  white-space: nowrap;
}

.pill-dot {
  width: 5px;
  height: 5px;
  border-radius: 9999px;
}

.type-pill.domain {
  background: var(--info-surface);
  color: var(--info-ink);
  border: 1px solid var(--info-border);
}
.type-pill.domain .pill-dot { background: var(--info-ink); }

.type-pill.program {
  background: var(--purple-surface);
  color: var(--purple-ink);
  border: 1px solid var(--purple-border);
}
.type-pill.program .pill-dot { background: var(--purple-ink); }

.type-pill.course {
  background: var(--success-surface);
  color: var(--success-ink);
  border: 1px solid var(--success-border);
}
.type-pill.course .pill-dot { background: var(--success-ink); }

/* CODE & NAME COLS */
.code-col code {
  font-family: var(--font-family);
  font-size: 0.76rem;
  font-weight: 600;
  background: var(--bg-main);
  color: var(--primary-ink);
  padding: 0.22rem 0.5rem;
  border-radius: 6px;
  border: 1px solid var(--border-color);
}

.name-col strong {
  font-weight: 600;
  color: var(--text-main);
}

/* MODE BADGE (Bắt buộc / Tự chọn - Pill radius: 9999px) */
.mode-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.73rem;
  font-weight: 600;
  padding: 0.22rem 0.65rem;
  border-radius: 9999px;
  white-space: nowrap;
}

.interactive-mode-badge {
  cursor: pointer;
  transition: transform 0.14s ease, box-shadow 0.14s ease;
}

.interactive-mode-badge:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
}

.interactive-mode-badge:disabled {
  opacity: 0.6;
  cursor: wait;
}

.mode-badge.mandatory {
  background: var(--success-surface);
  color: var(--success-ink);
  border: 1px solid var(--success-border);
}

.mode-badge.optional {
  background: var(--warning-surface);
  color: var(--warning-ink);
  border: 1px solid var(--warning-border);
}

.parent-text {
  font-size: 0.8rem;
  color: var(--text-muted);
}

.muted-dash {
  color: var(--text-muted);
  opacity: 0.5;
}

/* ACTION BUTTON (Radius: 8px) */
.actions-col {
  text-align: right;
}

.btn-remove-allocation {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  background: var(--danger-surface);
  color: var(--danger-ink);
  border: 1px solid var(--danger-border);
  padding: 0.28rem 0.6rem;
  border-radius: 8px;
  font-size: 0.74rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}

.btn-remove-allocation:hover:not(:disabled) {
  background: #dc2626;
  color: #ffffff;
  border-color: #dc2626;
  box-shadow: 0 2px 8px rgba(220, 38, 38, 0.3);
}

.btn-remove-allocation:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* EMPTY STATES */
.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4.5rem 2rem;
  text-align: center;
  color: var(--text-muted);
  gap: 0.65rem;
}

.empty-icon-ring {
  width: 64px;
  height: 64px;
  border-radius: 9999px;
  background: var(--primary-light);
  border: 1px solid var(--success-border);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 0.4rem;
}

.empty-icon {
  color: var(--primary-ink);
}

.empty-state h4 {
  font-size: 1.02rem;
  font-weight: 700;
  color: var(--text-main);
  margin: 0;
}

.empty-state p {
  font-size: 0.84rem;
  color: var(--text-muted);
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
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  color: var(--text-muted);
  padding: 0.68rem 0.9rem;
  border-radius: 10px;
  font-size: 0.85rem;
  transition: all 0.16s ease;
}

.searchable-trigger.has-value {
  color: var(--text-main);
  font-weight: 500;
}

.searchable-trigger.is-open {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px var(--primary-light);
}

.dropdown-chevron {
  color: var(--text-muted);
  transition: transform 0.2s ease;
  flex-shrink: 0;
}

.dropdown-chevron.rotated {
  transform: rotate(180deg);
  color: var(--primary-ink);
}

.searchable-dropdown-menu {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  right: 0;
  z-index: 70;
  background: var(--bg-card);
  border: 1px solid var(--primary-color);
  border-radius: 12px;
  box-shadow: var(--shadow-lg);
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
  padding: 0.6rem 0.8rem;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-main);
}

.dropdown-search-input {
  background: transparent;
  border: none;
  outline: none;
  color: var(--text-main);
  font-size: 0.82rem;
  width: 100%;
}

.btn-clear-search {
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  padding: 2px;
}

.btn-clear-search:hover {
  color: var(--text-main);
}

.dropdown-items-list {
  max-height: 220px;
  overflow-y: auto;
  padding: 0.35rem;
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.55rem 0.75rem;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.82rem;
  color: var(--text-main);
  transition: background 0.12s;
}

.dropdown-item:hover {
  background: var(--primary-light);
  color: var(--primary-ink);
}

.dropdown-item.is-selected {
  background: var(--primary-light);
  color: var(--primary-ink);
  font-weight: 700;
}

.item-code {
  color: var(--primary-ink);
  font-family: var(--font-family);
  font-size: 0.76rem;
  font-weight: 600;
}

.item-name {
  flex: 1;
}

.item-checked {
  color: var(--primary-ink);
  flex-shrink: 0;
}

.dropdown-state-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  padding: 0.75rem;
  font-size: 0.78rem;
  color: var(--text-muted);
}

.dropdown-state-row.empty-state-row {
  color: var(--text-muted);
  padding: 1.1rem;
}

.dropdown-state-row.loading-more {
  color: var(--primary-ink);
  border-top: 1px dashed var(--border-color);
}

.dropdown-state-row.end-hint {
  color: var(--text-muted);
  border-top: 1px dashed var(--border-color);
  font-size: 0.73rem;
}

/* SUB-COURSES PICKER (STEP 3C - Concentric radius: 12px) */
.sub-courses-picker {
  margin-top: 0.35rem;
  background: var(--bg-main);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 0.9rem;
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
  font-size: 0.81rem;
  font-weight: 600;
  color: var(--text-main);
}

.course-count-badge {
  font-size: 0.73rem;
  font-weight: 600;
  font-family: var(--font-family);
  background: var(--primary-light);
  color: var(--primary-ink);
  padding: 0.18rem 0.6rem;
  border-radius: 9999px;
  border: 1px solid var(--success-border);
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
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 0.48rem 0.75rem;
  cursor: pointer;
  user-select: none;
  transition: border-color 0.15s ease;
}

.all-courses-checkbox:hover {
  border-color: var(--primary-color);
}

.all-courses-checkbox input[type="checkbox"],
.course-checkbox-item input[type="checkbox"] {
  accent-color: var(--primary-color);
  width: 15px;
  height: 15px;
}

.all-courses-checkbox .all-text {
  font-size: 0.76rem;
  font-weight: 700;
  color: var(--primary-ink);
  letter-spacing: 0.02em;
}

.course-search-box {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 0.48rem 0.7rem;
  flex: 1;
  transition: border-color 0.15s ease;
}

.course-search-box:focus-within {
  border-color: var(--primary-color);
}

.course-search-box .search-icon {
  color: var(--text-muted);
  flex-shrink: 0;
}

.course-search-input {
  background: transparent;
  border: none;
  color: var(--text-main);
  font-size: 0.8rem;
  width: 100%;
  outline: none;
}

.course-search-input::placeholder {
  color: var(--text-muted);
}

.courses-checkbox-list {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  max-height: 220px;
  overflow-y: auto;
  border-top: 1px dashed var(--border-color);
  padding-top: 0.55rem;
}

.course-checkbox-item {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  font-size: 0.81rem;
  color: var(--text-main);
  padding: 0.5rem 0.65rem;
  border-radius: 8px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  cursor: pointer;
  transition: all 0.12s ease;
}

.course-checkbox-item:hover {
  background: var(--primary-light);
  border-color: var(--primary-color);
  color: var(--primary-ink);
}

.course-code-tag {
  font-family: var(--font-family);
  color: var(--primary-ink);
  font-weight: 600;
  font-size: 0.75rem;
}

.empty-hint {
  font-size: 0.8rem;
  color: var(--text-muted);
  text-align: center;
  padding: 1rem 0.5rem;
}

.picker-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  padding: 1rem;
  color: var(--text-muted);
  font-size: 0.8rem;
}

.scroll-loading-more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  padding: 0.5rem;
  font-size: 0.76rem;
  color: var(--primary-ink);
  background: var(--primary-light);
  border-radius: 6px;
}

.scroll-end-hint {
  text-align: center;
  padding: 0.45rem;
  font-size: 0.73rem;
  color: var(--text-muted);
  border-top: 1px dashed var(--border-color);
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

<!-- GLOBAL STYLE FOR TELEPORTED HOVER DESCRIPTION POPOVER (Light & Dark Mode Synchronized) -->
<style>
.impeccable-desc-popover {
  position: fixed;
  z-index: 9999;
  width: 340px;
  background: var(--bg-card);
  color: var(--text-main);
  border: 1px solid var(--primary-color);
  border-radius: 12px;
  padding: 0.85rem 1rem;
  box-shadow: var(--shadow-lg);
  pointer-events: none;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.impeccable-desc-popover .popover-header {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  flex-wrap: wrap;
}

.impeccable-desc-popover .popover-type-badge {
  font-size: 0.68rem;
  font-weight: 700;
  padding: 0.14rem 0.5rem;
  border-radius: 9999px;
}

.impeccable-desc-popover .popover-type-badge.domain {
  background: var(--info-surface);
  color: var(--info-ink);
  border: 1px solid var(--info-border);
}

.impeccable-desc-popover .popover-type-badge.program {
  background: var(--purple-surface);
  color: var(--purple-ink);
  border: 1px solid var(--purple-border);
}

.impeccable-desc-popover .popover-type-badge.course {
  background: var(--success-surface);
  color: var(--success-ink);
  border: 1px solid var(--success-border);
}

.impeccable-desc-popover .popover-code {
  font-family: var(--font-family);
  font-size: 0.72rem;
  font-weight: 700;
  color: var(--primary-ink);
}

.impeccable-desc-popover .popover-module {
  margin-left: auto;
  font-size: 0.68rem;
  font-weight: 600;
  color: var(--text-muted);
  background: var(--bg-main);
  padding: 0.1rem 0.45rem;
  border-radius: 6px;
  border: 1px solid var(--border-color);
}

.impeccable-desc-popover .popover-title {
  font-size: 0.86rem;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.35;
}

.impeccable-desc-popover .popover-desc-box {
  background: var(--bg-main);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 0.55rem 0.7rem;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.impeccable-desc-popover .popover-desc-label {
  font-size: 0.7rem;
  font-weight: 700;
  color: var(--primary-ink);
  text-transform: uppercase;
  letter-spacing: 0.03em;
}

.impeccable-desc-popover .popover-desc-text {
  font-size: 0.79rem;
  color: var(--text-main);
  margin: 0;
  line-height: 1.45;
  white-space: pre-line;
}

.impeccable-desc-popover .popover-desc-text.is-empty {
  color: var(--text-muted);
  font-style: italic;
}

.impeccable-desc-popover .popover-footer-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  font-size: 0.72rem;
  color: var(--text-muted);
  padding-top: 0.2rem;
  border-top: 1px dashed var(--border-color);
}

.impeccable-desc-popover .popover-footer-meta strong {
  color: var(--text-main);
}

.tooltip-fade-enter-active,
.tooltip-fade-leave-active {
  transition: opacity 0.14s ease, transform 0.14s ease;
}

.tooltip-fade-enter-from,
.tooltip-fade-leave-to {
  opacity: 0;
  transform: translateY(4px);
}
</style>
