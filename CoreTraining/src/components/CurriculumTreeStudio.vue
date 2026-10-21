<template>
  <div class="curriculum-tree-studio">
    <!-- Header Controls -->
    <div class="tree-header">
      <div class="header-left">
        <h3 class="tree-title">
          <Layers :size="18" class="title-icon" />
          <span>Cây cấu trúc đào tạo (7 Cấp)</span>
        </h3>
        <span class="tree-badge">Interactive Mode</span>
      </div>
      <div class="header-actions">
        <button
          type="button"
          class="btn-studio btn-primary"
          @click="openAddModal('domain')"
          title="Thêm Lĩnh vực đào tạo mới"
        >
          <Plus :size="15" />
          <span>Thêm Lĩnh vực</span>
        </button>
        <button
          type="button"
          class="btn-studio btn-ghost"
          @click="expandAll = !expandAll; toggleAllNodes(expandAll)"
          :title="expandAll ? 'Thu gọn tất cả' : 'Mở rộng tất cả'"
        >
          <component :is="expandAll ? Minimize2 : Maximize2" :size="15" />
          <span>{{ expandAll ? "Thu gọn" : "Mở rộng" }}</span>
        </button>
      </div>
    </div>

    <!-- Search box -->
    <div class="tree-search-wrapper">
      <Search :size="15" class="search-icon" />
      <input
        v-model="searchQuery"
        type="text"
        placeholder="Tìm kiếm lĩnh vực, chương trình, khóa học hoặc bài giảng..."
        class="tree-search-input"
      />
      <button
        v-if="searchQuery"
        type="button"
        class="clear-search-btn"
        @click="searchQuery = ''"
      >
        <X :size="13" />
      </button>
    </div>

    <!-- Tree Body -->
    <div class="tree-scroll-container">
      <div v-if="loading" class="tree-empty-state">
        <Loader2 :size="24" class="spin-icon" />
        <p>Đang tải cấu trúc chương trình...</p>
      </div>

      <div v-else-if="!filteredDomains.length" class="tree-empty-state">
        <FolderTree :size="36" class="empty-icon" />
        <p v-if="searchQuery">Không tìm thấy mục nào khớp với "{{ searchQuery }}".</p>
        <p v-else>Chưa có cấu trúc đào tạo nào. Hãy bấm "Thêm Lĩnh vực" để bắt đầu.</p>
      </div>

      <div v-else class="tree-root">
        <!-- LEVEL 1: DOMAIN (LĨNH VỰC) -->
        <div
          v-for="domain in filteredDomains"
          :key="domain.id"
          class="tree-branch domain-branch"
        >
          <div
            class="tree-node domain-node"
            :class="{ active: selectedId === domain.id, expanded: isExpanded(domain.id) }"
            @click="selectNode('domain', domain)"
          >
            <button
              type="button"
              class="toggle-chevron"
              @click.stop="toggleNode(domain.id)"
            >
              <ChevronRight
                :size="15"
                :class="{ rotated: isExpanded(domain.id) }"
              />
            </button>

            <span class="node-icon domain-icon">
              <Globe :size="16" />
            </span>

            <div class="node-info">
              <span class="node-title">{{ domain.name }}</span>
              <span class="node-code">{{ domain.code }}</span>
            </div>

            <div class="node-badge-group">
              <span class="count-tag">{{ getProgramsForDomain(domain.id).length }} CT</span>
            </div>

            <!-- Action Buttons -->
            <div class="node-hover-actions" @click.stop>
              <button
                type="button"
                class="btn-studio-node btn-primary-node"
                title="Thêm Chương trình đào tạo mới vào Lĩnh vực này"
                @click="openAddModal('program', domain)"
              >
                <Plus :size="13" />
                <span>Thêm Chương trình</span>
              </button>
              <button
                type="button"
                class="hover-action-btn menu-btn"
                title="Tùy chọn"
                @click="openMenu('domain', domain)"
              >
                <MoreHorizontal :size="14" />
              </button>
            </div>
          </div>

          <!-- LEVEL 2: PROGRAM (CHƯƠNG TRÌNH ĐÀO TẠO) -->
          <div v-if="isExpanded(domain.id)" class="tree-sub-tree">
            <div
              v-for="program in getProgramsForDomain(domain.id)"
              :key="program.id"
              class="tree-branch program-branch"
            >
              <div
                class="tree-node program-node"
                :class="{ active: selectedId === program.id, expanded: isExpanded(program.id) }"
                @click="selectNode('program', program)"
              >
                <button
                  type="button"
                  class="toggle-chevron"
                  @click.stop="toggleNode(program.id)"
                >
                  <ChevronRight
                    :size="14"
                    :class="{ rotated: isExpanded(program.id) }"
                  />
                </button>

                <span class="node-icon program-icon">
                  <Folder :size="15" />
                </span>

                <div class="node-info">
                  <span class="node-title">{{ program.name }}</span>
                  <span class="node-code">{{ program.code }}</span>
                </div>

                <div class="node-badge-group">
                  <span v-if="program.businessModule" class="meta-tag module-tag">
                    {{ program.businessModule }}
                  </span>
                  <span v-if="program.mandatoryUnitIds?.length" class="meta-tag mandatory-tag" title="Đơn vị bắt buộc hoàn thành">
                    <ShieldCheck :size="11" />
                    {{ program.mandatoryUnitIds.length }} ĐV Bắt buộc
                  </span>
                  <span class="count-tag">{{ getCoursesForProgram(program.id).length }} Khóa</span>
                </div>

                <!-- Hover Actions -->
                <div class="node-hover-actions" @click.stop>
                  <button
                    type="button"
                    class="btn-studio-node btn-primary-node"
                    title="Thêm Khóa học vào Chương trình này"
                    @click="openAddModal('course', program)"
                  >
                    <Plus :size="13" />
                    <span>Thêm Khóa</span>
                  </button>
                  <button
                    type="button"
                    class="hover-action-btn menu-btn"
                    title="Tùy chọn"
                    @click="openMenu('program', program)"
                  >
                    <MoreHorizontal :size="14" />
                  </button>
                </div>
              </div>

              <!-- LEVEL 3: COURSE (KHÓA HỌC) -->
              <div v-if="isExpanded(program.id)" class="tree-sub-tree">
                <div
                  v-for="course in getCoursesForProgram(program.id)"
                  :key="course.id"
                  class="tree-branch course-branch"
                >
                  <div
                    class="tree-node course-node"
                    :class="{ active: selectedId === course.id, expanded: isExpanded(course.id) }"
                    @click="selectNode('course', course)"
                  >
                    <button
                      type="button"
                      class="toggle-chevron"
                      @click.stop="toggleNode(course.id)"
                    >
                      <ChevronRight
                        :size="13"
                        :class="{ rotated: isExpanded(course.id) }"
                      />
                    </button>

                    <span class="node-icon course-icon">
                      <BookOpen :size="14" />
                    </span>

                    <div class="node-info">
                      <span class="node-title">{{ course.name }}</span>
                      <span class="node-status-dot" :class="course.status?.toLowerCase()"></span>
                    </div>

                    <div class="node-badge-group">
                      <span v-if="course.businessModule" class="meta-tag module-tag">{{ course.businessModule }}</span>
                      <span class="status-pill" :class="course.status?.toLowerCase()">
                        {{ course.status === 'PUBLISHED' ? 'Đã xuất bản' : 'Bản nháp' }}
                      </span>
                    </div>

                    <!-- Hover Actions -->
                    <div class="node-hover-actions" @click.stop>
                      <button
                        type="button"
                        class="hover-action-btn add-btn"
                        title="Thêm Chương vào Khóa học này"
                        @click="openAddModal('chapter', course)"
                      >
                        <Plus :size="14" />
                        <span class="btn-tooltip">Thêm Chương</span>
                      </button>
                      <button
                        type="button"
                        class="hover-action-btn menu-btn"
                        title="Tùy chọn"
                        @click="openMenu('course', course)"
                      >
                        <MoreHorizontal :size="14" />
                      </button>
                    </div>
                  </div>

                  <!-- LEVEL 4: CHAPTER (CHƯƠNG HỌC) -->
                  <div v-if="isExpanded(course.id)" class="tree-sub-tree">
                    <div
                      v-for="(chapter, cIdx) in getChaptersForCourse(course.id)"
                      :key="chapter.id"
                      class="tree-branch chapter-branch"
                    >
                      <div
                        class="tree-node chapter-node"
                        :class="{ active: selectedId === chapter.id, expanded: isExpanded(chapter.id) }"
                        @click="selectNode('chapter', chapter, course)"
                      >
                        <button
                          type="button"
                          class="toggle-chevron"
                          @click.stop="toggleNode(chapter.id)"
                        >
                          <ChevronRight
                            :size="12"
                            :class="{ rotated: isExpanded(chapter.id) }"
                          />
                        </button>

                        <span class="node-icon chapter-icon">
                          <Bookmark :size="13" />
                        </span>

                        <div class="node-info">
                          <span class="node-index">Chương {{ cIdx + 1 }}:</span>
                          <span class="node-title">{{ chapter.title }}</span>
                        </div>

                        <div class="node-badge-group">
                          <span class="count-tag">{{ chapter.lessons?.length || 0 }} bài</span>
                        </div>

                        <!-- Hover Actions -->
                        <div class="node-hover-actions" @click.stop>
                          <button
                            type="button"
                            class="btn-studio-node btn-primary-node"
                            title="Thêm Bài học vào Chương này"
                            @click="openAddModal('lesson', chapter, course)"
                          >
                            <Plus :size="12" />
                            <span>Thêm Bài</span>
                          </button>
                          <button
                            type="button"
                            class="hover-action-btn menu-btn"
                            title="Tùy chọn"
                            @click="openMenu('chapter', chapter, course)"
                          >
                            <MoreHorizontal :size="13" />
                          </button>
                        </div>
                      </div>

                      <!-- LEVEL 5: LESSON (BÀI HỌC) -->
                      <div v-if="isExpanded(chapter.id)" class="tree-sub-tree">
                        <div
                          v-for="(lesson, lIdx) in chapter.lessons || []"
                          :key="lesson.id"
                          class="tree-branch lesson-branch"
                        >
                          <div
                            class="tree-node lesson-node"
                            :class="{ active: selectedId === lesson.id, expanded: isExpanded(lesson.id) }"
                            @click="selectNode('lesson', lesson, chapter, course)"
                          >
                            <button
                              type="button"
                              class="toggle-chevron"
                              @click.stop="toggleNode(lesson.id)"
                            >
                              <ChevronRight
                                :size="11"
                                :class="{ rotated: isExpanded(lesson.id) }"
                              />
                            </button>

                            <span class="node-icon lesson-icon">
                              <FileText :size="13" />
                            </span>

                            <div class="node-info">
                              <span class="node-index">Bài {{ lIdx + 1 }}:</span>
                              <span class="node-title">{{ lesson.title }}</span>
                            </div>

                            <!-- Level 6 & 7 Indicators -->
                            <div class="node-badge-group mini-blocks">
                              <span
                                v-if="lesson.videoUrl"
                                class="mini-block-badge video"
                                title="Đã có Video bài giảng"
                              >
                                <Video :size="10" />
                              </span>
                              <span
                                v-if="lesson.documents && lesson.documents.length"
                                class="mini-block-badge docs"
                                :title="`${lesson.documents.length} tài liệu đính kèm`"
                              >
                                <FileUp :size="10" />
                              </span>
                              <span
                                v-if="lesson.procedureSteps && lesson.procedureSteps.length"
                                class="mini-block-badge proc"
                                :title="`${lesson.procedureSteps.length} bước quy trình CBS`"
                              >
                                <ListOrdered :size="10" />
                              </span>
                              <span
                                v-if="lesson.hasQuiz"
                                class="mini-block-badge quiz"
                                title="Đã gắn bài kiểm tra đánh giá"
                              >
                                <HelpCircle :size="10" />
                              </span>
                            </div>

                            <!-- Action -->
                            <div class="node-hover-actions" @click.stop>
                              <button
                                type="button"
                                class="hover-action-btn edit-btn"
                                title="Mở trang soạn bài học toàn diện"
                                @click="openLessonEditor(lesson.id)"
                              >
                                <ExternalLink :size="13" />
                                <span class="btn-tooltip">Soạn bài</span>
                              </button>
                            </div>
                          </div>

                          <!-- LEVEL 6 & 7: CONTENT BLOCKS & QUIZ -->
                          <div v-if="isExpanded(lesson.id)" class="tree-sub-tree content-blocks-tree">
                            <!-- Block 1: Video -->
                            <div
                              class="tree-node block-node video-block"
                              :class="{ active: !!lesson.videoUrl }"
                              @click="editLessonBlock(lesson, 'video')"
                            >
                              <Video :size="12" class="block-icon video-icon" />
                              <span class="block-title">
                                {{ lesson.videoUrl ? 'Video bài giảng (Đã thiết lập)' : '(Chưa có Video - Click để thêm)' }}
                              </span>
                              <span v-if="lesson.videoUrl" class="block-check">✓</span>
                            </div>

                            <!-- Block 2: Documents -->
                            <div
                              class="tree-node block-node docs-block"
                              :class="{ active: !!(lesson.documents && lesson.documents.length) }"
                              @click="editLessonBlock(lesson, 'documents')"
                            >
                              <FileUp :size="12" class="block-icon doc-icon" />
                              <span class="block-title">
                                {{ (lesson.documents && lesson.documents.length) ? `${lesson.documents.length} tài liệu học tập` : '(Chưa có tài liệu - Click để tải lên)' }}
                              </span>
                              <span v-if="lesson.documents && lesson.documents.length" class="block-check">✓</span>
                            </div>

                            <!-- Block 3: Procedure Steps -->
                            <div
                              class="tree-node block-node procedure-block"
                              :class="{ active: !!(lesson.procedureSteps && lesson.procedureSteps.length) }"
                              @click="editLessonBlock(lesson, 'procedure')"
                            >
                              <ListOrdered :size="12" class="block-icon proc-icon" />
                              <span class="block-title">
                                {{ (lesson.procedureSteps && lesson.procedureSteps.length) ? `${lesson.procedureSteps.length} bước quy trình CBS` : '(Chưa có quy trình - Click để thêm)' }}
                              </span>
                              <span v-if="lesson.procedureSteps && lesson.procedureSteps.length" class="block-check">✓</span>
                            </div>

                            <!-- Block 4: Assessment / Quiz (Level 7) -->
                            <div
                              class="tree-node block-node quiz-block"
                              :class="{ active: !!lesson.hasQuiz }"
                              @click="editLessonBlock(lesson, 'quiz')"
                            >
                              <HelpCircle :size="12" class="block-icon quiz-icon" />
                              <span class="block-title">
                                {{ lesson.hasQuiz ? 'Bài kiểm tra đánh giá năng lực' : '(Chưa gắn Quiz - Click để thêm)' }}
                              </span>
                              <span v-if="lesson.hasQuiz" class="block-check">✓</span>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ENRICHED INLINE ADD MODAL -->
    <div v-if="addModal.show" class="tree-modal-backdrop" @click.self="addModal.show = false">
      <div class="tree-quick-modal enriched-modal">
        <div class="modal-header">
          <div class="modal-title-box">
            <span class="modal-level-badge">{{ levelSubtitle }}</span>
            <h4>{{ modalTitle }}</h4>
          </div>
          <button type="button" class="close-btn" @click="addModal.show = false">
            <X :size="16" />
          </button>
        </div>

        <form @submit.prevent="submitAddModal" class="modal-form">
          <!-- SECTION 1: BASIC INFO -->
          <div class="form-section">
            <!-- Chọn Lĩnh vực khi thêm chương trình (Khóa cố định) -->
            <div class="form-group" v-if="addModal.type === 'program'">
              <label>Lĩnh vực đào tạo (*):</label>
              <select
                v-model="addModal.parentId"
                class="form-control select-control disabled-select"
                @change="handleDomainChange"
                disabled
                required
              >
                <option v-for="d in domains" :key="d.id" :value="d.id">
                  {{ d.name }} ({{ d.code }})
                </option>
              </select>
            </div>

            <!-- PHÂN HỆ CHO CẤP 1 (LĨNH VỰC) - CHO PHÉP CHỌN GỐC -->
            <div class="form-group" v-if="addModal.type === 'domain'">
              <div class="code-label-row">
                <label>Phân hệ nghiệp vụ (*):</label>
                <span class="badge-module-hint" v-if="addModal.businessModule">
                  Tiền tố mã: {{ getModulePrefix(addModal.type, addModal.businessModule) }}
                </span>
              </div>
              <select
                v-model="addModal.businessModule"
                class="form-control select-control module-select"
                @change="handleBusinessModuleChange"
                required
              >
                <option value="">-- Chọn phân hệ nghiệp vụ gốc --</option>
                <option
                  v-for="mod in BUSINESS_MODULES"
                  :key="mod.value"
                  :value="mod.value"
                >
                  {{ mod.label }}
                </option>
              </select>
            </div>

            <!-- PHÂN HỆ CHO CẤP 2 (CHƯƠNG TRÌNH) & CẤP 3 (KHÓA HỌC) - TỰ ĐỘNG CỐ ĐỊNH TỪ CHA -->
            <div
              class="form-group inherited-module-info"
              v-else-if="addModal.type === 'program' || addModal.type === 'course'"
            >
              <div class="code-label-row">
                <label>Phân hệ nghiệp vụ:</label>
                <span class="badge-module-hint" v-if="addModal.businessModule">
                  Tiền tố mã: {{ getModulePrefix(addModal.type, addModal.businessModule) }}
                </span>
              </div>
              <div class="inherited-module-display">
                <span class="module-chip-badge">{{ addModal.businessModule || "N/A" }}</span>
                <span class="module-title-text">{{ getModuleName(addModal.businessModule) }}</span>
              </div>
            </div>

            <!-- MÃ ĐỊNH DANH (CODE) VỚI OPTION TỰ ĐỘNG SINH / DISABLE / ẨN -->
            <div class="form-group code-group">
              <div class="code-label-row">
                <label>Mã định danh (*):</label>
                <div class="auto-code-toggle">
                  <input
                    type="checkbox"
                    id="auto-code-checkbox"
                    v-model="addModal.autoCode"
                    @change="handleAutoCodeToggle"
                  />
                  <label for="auto-code-checkbox" class="toggle-text">
                    <Sparkles :size="12" class="sparkle-icon" />
                    <span>Hệ thống tự sinh mã</span>
                  </label>
                </div>
              </div>

              <div class="code-input-wrapper">
                <input
                  v-model="addModal.code"
                  type="text"
                  :placeholder="addModal.autoCode ? '(Hệ thống đang tự sinh mã...)' : 'Ví dụ: RMCT1, RMLV1...'"
                  :disabled="addModal.autoCode"
                  :readonly="addModal.autoCode"
                  required
                  class="form-control code-input"
                  :class="{ 'is-auto': addModal.autoCode }"
                />
                <button
                  v-if="addModal.autoCode"
                  type="button"
                  class="re-generate-btn"
                  @click="reGenerateCode"
                  title="Tạo lại mã tự động"
                >
                  <Sparkles :size="13" />
                  <span>Sinh lại</span>
                </button>
                <button
                  type="button"
                  class="manual-edit-btn"
                  @click="addModal.autoCode = !addModal.autoCode"
                  :title="addModal.autoCode ? 'Chuyển sang tự nhập mã' : 'Chuyển sang tự động sinh mã'"
                >
                  <component :is="addModal.autoCode ? Lock : Unlock" :size="13" />
                  <span>{{ addModal.autoCode ? "Tự nhập" : "Khóa tự sinh" }}</span>
                </button>
              </div>
              <small class="form-hint" v-if="addModal.autoCode">
                Mã định danh tuần tự chuẩn nghiệp vụ (Ví dụ: RMLV1, RMCT1, RMKH1...).
              </small>
            </div>

            <div class="form-group">
              <label>Tên hiển thị (*):</label>
              <input
                v-model="addModal.name"
                type="text"
                :placeholder="namePlaceholder"
                required
                class="form-control name-input"
                @input="handleNameInput"
                ref="nameInputRef"
              />
            </div>
          </div>

          <!-- SECTION 2: BỔ SUNG & MÔ TẢ (CHỈ CÒN MỨC ĐỘ NẾU LÀ KHÓA HỌC, VÀ MÔ TẢ) -->
          <div v-if="addModal.type === 'domain' || addModal.type === 'program' || addModal.type === 'course'" class="form-section meta-section">
            <!-- Mức độ / Cấp độ: CHỈ HIỂN THỊ Ở CẤP KHÓA HỌC (COURSE) -->
            <div class="form-row-grid" v-if="addModal.type === 'course'">
              <div class="form-group">
                <label>Mức độ / Cấp độ:</label>
                <select v-model="addModal.level" class="form-control select-control">
                  <option
                    v-for="lvl in TRAINING_LEVELS"
                    :key="lvl.value"
                    :value="lvl.value"
                  >
                    {{ lvl.label }}
                  </option>
                </select>
              </div>

              <div class="form-group">
                <label>Hạn hoàn thành khuyến nghị:</label>
                <input
                  v-model="addModal.completionDeadline"
                  type="date"
                  class="form-control date-control"
                />
              </div>
            </div>

            <!-- Mục tiêu / Mô tả -->
            <div class="form-group">
              <label>Mục tiêu / Mô tả tóm tắt:</label>
              <textarea
                v-model="addModal.description"
                rows="2"
                placeholder="Nhập mục tiêu đào tạo, chuẩn đầu ra hoặc nội dung chính..."
                class="form-control textarea-control"
              ></textarea>
            </div>
          </div>

          <!-- SECTION 3: APPLIED UNITS & AUTO-ENROLLMENT (FOR DOMAIN & PROGRAM) -->
          <div v-if="addModal.type === 'domain' || addModal.type === 'program'" class="form-section units-section">
            <h5 class="section-sub-title">
              <Building2 :size="14" />
              <span>Phân bổ Đơn vị áp dụng</span>
            </h5>

            <!-- 1. ĐƠN VỊ BẮT BUỘC -->
            <div class="form-group unit-picker-box mandatory-box">
              <div class="unit-box-header">
                <label class="mandatory-label">
                  <ShieldCheck :size="14" />
                  <span>Đơn vị áp dụng BẮT BUỘC:</span>
                </label>
                <span class="badge-mandatory">Bắt buộc hoàn thành</span>
              </div>

              <select
                class="form-control unit-select"
                @change="handleSelectUnit($event, 'mandatory')"
              >
                <option value="">+ Chọn đơn vị bắt buộc...</option>
                <option
                  v-for="u in availableUnitsForMandatory"
                  :key="u.id"
                  :value="u.id"
                >
                  {{ u.name }} ({{ u.type === 'DEPARTMENT' ? 'Phòng ban' : 'Chi nhánh/Hội sở' }})
                </option>
              </select>

              <!-- Tags đơn vị bắt buộc đã chọn -->
              <div v-if="addModal.mandatoryUnitIds.length" class="unit-tags-container">
                <span
                  v-for="uId in addModal.mandatoryUnitIds"
                  :key="uId"
                  class="unit-chip mandatory-chip"
                >
                  <span>{{ getUnitName(uId) }}</span>
                  <button
                    type="button"
                    class="remove-chip-btn"
                    @click="removeUnit(uId, 'mandatory')"
                  >
                    ×
                  </button>
                </span>
              </div>


            </div>

            <!-- 2. ĐƠN VỊ TÙY CHỌN / KHÔNG BẮT BUỘC -->
            <div class="form-group unit-picker-box optional-box">
              <div class="unit-box-header">
                <label class="optional-label">
                  <Globe :size="14" />
                  <span>Đơn vị áp dụng TÙY CHỌN (Không bắt buộc):</span>
                </label>
                <span class="badge-optional">Tự nguyện học tập</span>
              </div>

              <select
                class="form-control unit-select"
                @change="handleSelectUnit($event, 'optional')"
              >
                <option value="">+ Chọn đơn vị tùy chọn...</option>
                <option
                  v-for="u in availableUnitsForOptional"
                  :key="u.id"
                  :value="u.id"
                >
                  {{ u.name }} ({{ u.type === 'DEPARTMENT' ? 'Phòng ban' : 'Chi nhánh/Hội sở' }})
                </option>
              </select>

              <!-- Tags đơn vị tùy chọn đã chọn -->
              <div v-if="addModal.optionalUnitIds.length" class="unit-tags-container">
                <span
                  v-for="uId in addModal.optionalUnitIds"
                  :key="uId"
                  class="unit-chip optional-chip"
                >
                  <span>{{ getUnitName(uId) }}</span>
                  <button
                    type="button"
                    class="remove-chip-btn"
                    @click="removeUnit(uId, 'optional')"
                  >
                    ×
                  </button>
                </span>
              </div>
            </div>
          </div>

          <!-- MODAL ACTIONS -->
          <div class="modal-actions">
            <button type="button" class="btn btn-secondary" @click="addModal.show = false">
              Hủy
            </button>
            <button type="submit" class="btn btn-primary" :disabled="submitting">
              <Loader2 v-if="submitting" :size="14" class="spin-icon" />
              <span>{{ submitting ? "Đang xử lý..." : "Tạo mới & Đồng bộ" }}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from "vue";
import { useRouter } from "vue-router";
import {
  Layers,
  Plus,
  Minimize2,
  Maximize2,
  Search,
  X,
  ChevronRight,
  Globe,
  Folder,
  BookOpen,
  Bookmark,
  FileText,
  Video,
  FileUp,
  ListOrdered,
  HelpCircle,
  MoreHorizontal,
  ExternalLink,
  FolderTree,
  Loader2,
  Sparkles,
  Lock,
  Unlock,
  Building2,
  ShieldCheck,
} from "lucide-vue-next";
import { trainingApi } from "../api/trainingApi";
import { adminApi, type AdminUser } from "../api/adminApi";
import { axiosClient } from "../api/axiosClient";
import { showToast } from "../utils/toast";
import type { TrainingDomain, TrainingProgram, Course, Chapter, Lesson } from "../types";
import type { OrganizationUnit } from "../types/organization";

const router = useRouter();
const emit = defineEmits<{
  (e: "select", item: { type: string; data: any; parent?: any }): void;
  (e: "refresh"): void;
}>();

// Business Modules & Training Levels
const BUSINESS_MODULES = [
  { value: "RM", label: "Quản trị rủi ro & Tuân thủ (RM)" },
  { value: "TD", label: "Tiền gửi & Huy động vốn (TD)" },
  { value: "LN", label: "Cho vay & Quản trị tín dụng (LN)" },
  { value: "CIF", label: "Thông tin khách hàng & Tài khoản (CIF)" },
  { value: "GL", label: "Kế toán tổng hợp & Giao dịch (GL)" },
  { value: "TF", label: "Thanh toán quốc tế & TT Thương mại (TF)" },
  { value: "DIGI", label: "Ngân hàng số & E-Banking (DIGI)" },
  { value: "CARD", label: "Dịch vụ thẻ ngân hàng (CARD)" },
  { value: "OPS", label: "Vận hành chi nhánh & DVKH (OPS)" },
];

const TRAINING_LEVELS = [
  { value: "BASIC", label: "Cơ bản (Nền tảng nghiệp vụ)" },
  { value: "ADVANCED", label: "Nâng cao (Xử lý tình huống & Tác nghiệp sâu)" },
  { value: "EXPERT", label: "Chuyên gia (Thẩm định & Phê duyệt)" },
  { value: "COMPLIANCE", label: "Định kỳ bắt buộc (Tuân thủ & Quy định mới)" },
];

// State
const loading = ref(false);
const submitting = ref(false);
const searchQuery = ref("");
const expandAll = ref(true);
const selectedId = ref<string>("");
const expandedIds = ref<Set<string>>(new Set());

// Data storage
const domains = ref<TrainingDomain[]>([]);
const programs = ref<TrainingProgram[]>([]);
const courses = ref<Course[]>([]);
const chaptersMap = ref<Record<string, Chapter[]>>({});
const unitsList = ref<OrganizationUnit[]>([]);
const systemUsers = ref<AdminUser[]>([]);

// Inline Add Modal State
interface AddModalData {
  show: boolean;
  type: "domain" | "program" | "course" | "chapter" | "lesson";
  parentId?: string;
  parentObj?: any;
  code: string;
  name: string;
  autoCode: boolean;
  businessModule: string;
  level: string;
  description: string;
  status: string;
  mandatoryUnitIds: string[];
  optionalUnitIds: string[];
  autoEnrollStaff: boolean;
  completionDeadline?: string;
}

const addModal = ref<AddModalData>({
  show: false,
  type: "domain",
  code: "",
  name: "",
  autoCode: true,
  businessModule: "",
  level: "BASIC",
  description: "",
  status: "ACTIVE",
  mandatoryUnitIds: [],
  optionalUnitIds: [],
  autoEnrollStaff: true,
  completionDeadline: "",
});

const nameInputRef = ref<HTMLInputElement | null>(null);

// Modal title & subtitle helpers
const levelSubtitle = computed(() => {
  switch (addModal.value.type) {
    case "domain":
      return "Cấp 1: Lĩnh vực đào tạo";
    case "program":
      return "Cấp 2: Chương trình đào tạo";
    case "course":
      return "Cấp 3: Khóa học nghiệp vụ";
    case "chapter":
      return "Cấp 4: Chương học";
    case "lesson":
      return "Cấp 5: Bài học";
    default:
      return "Cấu trúc đào tạo";
  }
});

const modalTitle = computed(() => {
  switch (addModal.value.type) {
    case "domain":
      return "Thêm Lĩnh vực đào tạo mới";
    case "program":
      return `Thêm Chương trình vào [${addModal.value.parentObj?.name || addModal.value.parentObj?.code}]`;
    case "course":
      return `Thêm Khóa học vào [${addModal.value.parentObj?.name || addModal.value.parentObj?.code}]`;
    case "chapter":
      return `Thêm Chương vào [${addModal.value.parentObj?.name || addModal.value.parentObj?.code}]`;
    case "lesson":
      return `Thêm Bài học vào [${addModal.value.parentObj?.title || addModal.value.parentObj?.name}]`;
    default:
      return "Thêm mới";
  }
});

const namePlaceholder = computed(() => {
  switch (addModal.value.type) {
    case "domain":
      return "Ví dụ: Quản trị rủi ro & Tuân thủ, Khách hàng cá nhân...";
    case "program":
      return "Ví dụ: Nghiệp vụ Quản lý rủi ro thanh khoản, Giám sát tín dụng...";
    case "course":
      return "Ví dụ: Khóa học Quy trình đánh giá rủi ro CBS...";
    case "chapter":
      return "Ví dụ: Tổng quan quy chuẩn rủi ro CBS...";
    case "lesson":
      return "Ví dụ: Các thao tác thẩm định trên phân hệ WebCSR...";
    default:
      return "Nhập tên tiêu đề...";
  }
});

// Helper: generate standard code based on parent and name
function generateStandardCode(type: string, name: string, parentCode = ""): string {
  const cleanParent = (parentCode || "").trim().toUpperCase().replace(/[^A-Z0-9]/g, "");
  if (!name.trim()) {
    const seq = Math.floor(10 + Math.random() * 89);
    const prefix = cleanParent ? `${cleanParent}_` : "";
    switch (type) {
      case "domain":
        return `LV_${seq}`;
      case "program":
        return `${prefix}PRG_${seq}`;
      case "course":
        return `${prefix}CRS_${seq}`;
      case "chapter":
        return `CH_${seq}`;
      case "lesson":
        return `LS_${seq}`;
      default:
        return `CODE_${seq}`;
    }
  }

  // Convert Vietnamese accented letters to Latin
  const normalized = name
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d")
    .replace(/Đ/g, "D")
    .replace(/[^a-zA-Z0-9\s]/g, "")
    .trim();

  const words = normalized.split(/\s+/).filter(Boolean);
  let acronym = "";
  if (words.length <= 2) {
    acronym = words.map((w) => w.toUpperCase()).join("_");
  } else {
    acronym = words.map((w) => w[0]?.toUpperCase() || "").join("");
  }

  const prefix = cleanParent ? `${cleanParent}_` : "";
  const code = `${prefix}${acronym}`;
  return code.slice(0, 30);
}

// Prefix helper theo chuẩn phân hệ và cấp độ
function getModulePrefix(type: string, moduleCode: string): string {
  const mod = (moduleCode || "").trim().toUpperCase();
  if (!mod) return "";
  switch (type) {
    case "domain":
      return `${mod}LV`;
    case "program":
      return `${mod}CT`;
    case "course":
      return `${mod}KH`;
    case "chapter":
      return "CH";
    case "lesson":
      return "BH";
    default:
      return `${mod}_`;
  }
}

function getModuleName(code?: string): string {
  if (!code) return "Chưa xác định";
  const found = BUSINESS_MODULES.find((m) => m.value === code);
  return found ? found.label : code;
}

// Trích xuất mã phân hệ từ cấp cha một cách thông minh (kể cả khi code là RMLV1, RMCT1...)
function extractModuleFromParent(parent: any): string {
  if (!parent) return "";
  if (parent.businessModule) return parent.businessModule;
  const code = (parent.code || "").toUpperCase();
  for (const mod of BUSINESS_MODULES) {
    if (code.startsWith(mod.value)) {
      return mod.value;
    }
  }
  return "";
}

// Thuật toán In-Memory tính toán số Seq tiếp theo tức thì (0ms latency, zero DB scan)
function computeNextSequenceCode(type: string, moduleCode: string): string {
  if (!moduleCode) {
    return generateStandardCode(type, addModal.value.name, addModal.value.parentObj?.code || "");
  }

  const prefix = getModulePrefix(type, moduleCode);
  let list: Array<{ code?: string }> = [];

  if (type === "domain") {
    list = domains.value || [];
  } else if (type === "program") {
    list = programs.value || [];
  } else if (type === "course") {
    list = courses.value || [];
  } else if (type === "chapter") {
    const courseId = addModal.value.parentId;
    list = (courseId && chaptersMap.value[courseId]) || [];
  } else {
    list = [];
  }

  // Regex trích xuất số đuôi: ví dụ RMLV1 -> 1, RMCT2 -> 2
  const regex = new RegExp(`^${prefix}(\\d+)$`, "i");
  let maxSeq = 0;
  for (const item of list) {
    if (item.code) {
      const match = item.code.trim().match(regex);
      if (match) {
        const seq = parseInt(match[1], 10);
        if (seq > maxSeq) {
          maxSeq = seq;
        }
      }
    }
  }

  return `${prefix}${maxSeq + 1}`;
}

async function handleBusinessModuleChange() {
  if (addModal.value.autoCode) {
    // 1. Cập nhật tức thời (0ms UI latency) từ In-Memory Store
    addModal.value.code = computeNextSequenceCode(
      addModal.value.type,
      addModal.value.businessModule
    );

    // 2. Kiểm tra xác thực nhẹ nhàng với server backend (non-blocking)
    if (addModal.value.businessModule) {
      try {
        const res = await axiosClient.get("/api/admin/training/next-code", {
          params: {
            type: addModal.value.type,
            module: addModal.value.businessModule,
          },
        });
        if (res.data?.code && addModal.value.autoCode) {
          addModal.value.code = res.data.code;
        }
      } catch (e) {
        console.debug("Background next-code check:", e);
      }
    }
  }
}

function handleNameInput() {
  if (addModal.value.autoCode) {
    if (addModal.value.businessModule) {
      if (!addModal.value.code) {
        handleBusinessModuleChange();
      }
    } else {
      const parentCode = addModal.value.parentObj?.code || "";
      addModal.value.code = generateStandardCode(
        addModal.value.type,
        addModal.value.name,
        parentCode
      );
    }
  }
}

function handleAutoCodeToggle() {
  if (addModal.value.autoCode) {
    reGenerateCode();
  }
}

function reGenerateCode() {
  if (addModal.value.businessModule) {
    handleBusinessModuleChange();
  } else {
    const parentCode = addModal.value.parentObj?.code || "";
    addModal.value.code = generateStandardCode(
      addModal.value.type,
      addModal.value.name,
      parentCode
    );
  }
}

// Units selection helpers
const availableUnitsForMandatory = computed(() => {
  const selectedOptional = new Set(addModal.value.optionalUnitIds);
  const selectedMandatory = new Set(addModal.value.mandatoryUnitIds);
  return unitsList.value.filter(
    (u) => !selectedOptional.has(u.id) && !selectedMandatory.has(u.id)
  );
});

const availableUnitsForOptional = computed(() => {
  const selectedOptional = new Set(addModal.value.optionalUnitIds);
  const selectedMandatory = new Set(addModal.value.mandatoryUnitIds);
  return unitsList.value.filter(
    (u) => !selectedOptional.has(u.id) && !selectedMandatory.has(u.id)
  );
});

function getUnitName(unitId: string): string {
  const found = unitsList.value.find((u) => u.id === unitId);
  return found?.name || unitId;
}

function handleSelectUnit(event: Event, type: "mandatory" | "optional") {
  const select = event.target as HTMLSelectElement;
  const unitId = select.value;
  if (!unitId) return;

  if (type === "mandatory") {
    if (!addModal.value.mandatoryUnitIds.includes(unitId)) {
      addModal.value.mandatoryUnitIds.push(unitId);
    }
  } else {
    if (!addModal.value.optionalUnitIds.includes(unitId)) {
      addModal.value.optionalUnitIds.push(unitId);
    }
  }
  select.value = "";
}

function removeUnit(unitId: string, type: "mandatory" | "optional") {
  if (type === "mandatory") {
    addModal.value.mandatoryUnitIds = addModal.value.mandatoryUnitIds.filter(
      (id) => id !== unitId
    );
  } else {
    addModal.value.optionalUnitIds = addModal.value.optionalUnitIds.filter(
      (id) => id !== unitId
    );
  }
}

// Compute staff affected by mandatory units
const affectedStaff = computed(() => {
  if (!addModal.value.mandatoryUnitIds.length) return [];
  const targetUnitIds = new Set(addModal.value.mandatoryUnitIds);

  // Add child units if any
  unitsList.value.forEach((u) => {
    if (u.parentId && targetUnitIds.has(u.parentId)) {
      targetUnitIds.add(u.id);
    }
  });

  return systemUsers.value.filter(
    (u) => u.unitId && targetUnitIds.has(u.unitId)
  );
});

// Load full tree catalog & auxiliary options
async function loadTreeData() {
  loading.value = true;
  try {
    const catalog = await trainingApi.catalog();
    domains.value = catalog.domains || [];
    programs.value = catalog.programs || [];

    // Load courses
    const allCourses = await trainingApi.courses();
    courses.value = allCourses || [];

    // Load units from options
    try {
      const optionsRes = await axiosClient.get("/api/admin/options/catalog");
      if (optionsRes.data?.units) {
        unitsList.value = optionsRes.data.units;
      }
    } catch (e) {
      console.warn("Could not load units from options:", e);
    }

    // Load users for staff calculation
    try {
      systemUsers.value = await adminApi.getAllUsers();
    } catch (e) {
      console.warn("Could not load users for enroll preview:", e);
    }

    // Expand domains & programs
    domains.value.forEach((d) => expandedIds.value.add(d.id));
    programs.value.forEach((p) => expandedIds.value.add(p.id));

    // Load chapters for courses
    for (const c of courses.value) {
      expandedIds.value.add(c.id);
      loadChaptersForCourse(c.id);
    }
  } catch (error) {
    console.error("Lỗi khi tải cấu trúc đào tạo:", error);
    showToast("Không thể tải cấu trúc cây đào tạo", "error");
  } finally {
    loading.value = false;
  }
}

async function loadChaptersForCourse(courseId: string) {
  try {
    const chapters = await trainingApi.chapters(courseId);
    chaptersMap.value[courseId] = chapters || [];
    chapters?.forEach((ch) => expandedIds.value.add(ch.id));
  } catch (err) {
    console.error(`Lỗi tải chương cho khóa ${courseId}:`, err);
  }
}

// Helpers for relationships
function getProgramsForDomain(domainId: string) {
  return programs.value.filter((p) => p.domainId === domainId);
}

function getCoursesForProgram(programId: string) {
  return courses.value.filter((c) => c.programId === programId);
}

function getChaptersForCourse(courseId: string) {
  return chaptersMap.value[courseId] || [];
}

// Expand / Collapse
function isExpanded(id: string) {
  return expandedIds.value.has(id);
}

function toggleNode(id: string) {
  if (expandedIds.value.has(id)) {
    expandedIds.value.delete(id);
  } else {
    expandedIds.value.add(id);
  }
}

function toggleAllNodes(expand: boolean) {
  if (!expand) {
    expandedIds.value.clear();
  } else {
    domains.value.forEach((d) => expandedIds.value.add(d.id));
    programs.value.forEach((p) => expandedIds.value.add(p.id));
    courses.value.forEach((c) => expandedIds.value.add(c.id));
    Object.values(chaptersMap.value)
      .flat()
      .forEach((ch) => expandedIds.value.add(ch.id));
  }
}

// Select node
function selectNode(type: string, data: any, parent?: any, grandparent?: any) {
  selectedId.value = data.id;
  emit("select", { type, data, parent: parent || grandparent });
}

// Open Inline Add Modal
function handleDomainChange() {
  const chosen = domains.value.find((d) => d.id === addModal.value.parentId);
  if (chosen) {
    addModal.value.parentObj = chosen;
    addModal.value.businessModule = extractModuleFromParent(chosen);
    if (addModal.value.autoCode) {
      handleBusinessModuleChange();
    }
  }
}

function openAddModal(
  type: "domain" | "program" | "course" | "chapter" | "lesson",
  parentObj?: any,
  grandParentObj?: any
) {
  const parent = parentObj || grandParentObj;
  const parentCode = parent?.code || "";

  // Tự động kế thừa phân hệ & phân bổ đơn vị từ cấp cha
  let defaultModule = "";
  let inheritedMandatoryUnits: string[] = [];
  let inheritedOptionalUnits: string[] = [];

  if (parent) {
    if (type === "program" || type === "course") {
      defaultModule = extractModuleFromParent(parent);
    }
    if (parent.mandatoryUnitIds && Array.isArray(parent.mandatoryUnitIds)) {
      inheritedMandatoryUnits = [...parent.mandatoryUnitIds];
    }
    if (parent.optionalUnitIds && Array.isArray(parent.optionalUnitIds)) {
      inheritedOptionalUnits = [...parent.optionalUnitIds];
    }
  }

  const initialCode = defaultModule
    ? computeNextSequenceCode(type, defaultModule)
    : generateStandardCode(type, "", parentCode);

  addModal.value = {
    show: true,
    type,
    parentId: parent?.id,
    parentObj: parent,
    code: initialCode,
    name: "",
    autoCode: true,
    businessModule: defaultModule,
    level: "BASIC",
    description: "",
    status: "ACTIVE",
    mandatoryUnitIds: inheritedMandatoryUnits,
    optionalUnitIds: inheritedOptionalUnits,
    autoEnrollStaff: true,
    completionDeadline: "",
  };

  if (defaultModule) {
    handleBusinessModuleChange();
  }

  nextTick(() => {
    nameInputRef.value?.focus();
  });
}

// Submit Create Node
async function submitAddModal() {
  const { type, parentId, code, name } = addModal.value;
  if (!code.trim() || !name.trim()) return;

  submitting.value = true;
  try {
    if (type === "domain") {
      await trainingApi.saveDomain({
        code: code.trim().toUpperCase(),
        name: name.trim(),
        description: addModal.value.description.trim() || undefined,
        businessModule: addModal.value.businessModule || undefined,
        mandatoryUnitIds: addModal.value.mandatoryUnitIds,
        optionalUnitIds: addModal.value.optionalUnitIds,
        unitIds: [
          ...addModal.value.mandatoryUnitIds,
          ...addModal.value.optionalUnitIds,
        ],
        status: "ACTIVE",
      });
      showToast("Tạo lĩnh vực đào tạo thành công!", "success");
    } else if (type === "program") {
      await trainingApi.saveProgram({
        domainId: parentId,
        code: code.trim().toUpperCase(),
        name: name.trim(),
        description: addModal.value.description.trim() || undefined,
        businessModule: addModal.value.businessModule || undefined,
        level: "BASIC",
        status: "ACTIVE",
        mandatoryUnitIds: addModal.value.mandatoryUnitIds,
        optionalUnitIds: addModal.value.optionalUnitIds,
        unitIds: [
          ...addModal.value.mandatoryUnitIds,
          ...addModal.value.optionalUnitIds,
        ],
        autoEnrollStaff: addModal.value.autoEnrollStaff,
      });
      const staffMsg =
        addModal.value.mandatoryUnitIds.length && affectedStaff.value.length
          ? ` Đã tự động ghi danh ${affectedStaff.value.length} học viên!`
          : "";
      showToast(`Tạo chương trình đào tạo thành công!${staffMsg}`, "success");
    } else if (type === "course") {
      await trainingApi.saveCourse({
        programId: parentId,
        code: code.trim().toUpperCase(),
        name: name.trim(),
        description: addModal.value.description.trim() || undefined,
        businessModule: addModal.value.businessModule || undefined,
        level: addModal.value.level || "BASIC",
        completionDeadline: addModal.value.completionDeadline || null,
        iconName: "BookOpen",
      });
      showToast("Tạo khóa học thành công!", "success");
    } else if (type === "chapter") {
      await trainingApi.saveChapter(parentId!, {
        code: code.trim().toUpperCase(),
        title: name.trim(),
        description: addModal.value.description.trim() || undefined,
      });
      showToast("Tạo chương thành công!", "success");
    } else if (type === "lesson") {
      const courseId =
        addModal.value.parentObj?.courseId ||
        addModal.value.parentObj?.course?.id ||
        "";
      await adminApi.createLesson({
        id: code.trim().toUpperCase(),
        title: name.trim(),
        hint: name.trim(),
        chapterId: parentId!,
        courseId: courseId,
        videoUrl: "",
        videoSource: "URL",
        documents: [],
        procedureSteps: [],
      });
      showToast("Tạo bài học thành công!", "success");
    }

    addModal.value.show = false;
    await loadTreeData();
    emit("refresh");
  } catch (error: any) {
    const msg = error.response?.data?.message || "Lỗi khi tạo mục mới!";
    showToast(msg, "error");
  } finally {
    submitting.value = false;
  }
}

// Actions
function openMenu(type: string, item: any, parent?: any) {
  selectNode(type, item, parent);
}

function openLessonEditor(lessonId: string) {
  router.push(`/admin/lessons/${encodeURIComponent(lessonId)}/edit`);
}

function editLessonBlock(lesson: Lesson, blockType: string) {
  router.push({
    path: `/admin/lessons/${encodeURIComponent(lesson.id)}/edit`,
    query: { tab: blockType },
  });
}

// Search Filter
const filteredDomains = computed(() => {
  if (!searchQuery.value.trim()) return domains.value;
  const q = searchQuery.value.toLowerCase().trim();
  return domains.value.filter(
    (d) =>
      d.name.toLowerCase().includes(q) ||
      d.code.toLowerCase().includes(q) ||
      getProgramsForDomain(d.id).some(
        (p) =>
          p.name.toLowerCase().includes(q) ||
          p.code.toLowerCase().includes(q) ||
          getCoursesForProgram(p.id).some((c) => c.name.toLowerCase().includes(q))
      )
  );
});

onMounted(() => {
  loadTreeData();
});
</script>

<style scoped>
.curriculum-tree-studio {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-surface, #1e293b);
  border-radius: 8px;
  border: 1px solid var(--border-color, #334155);
  overflow: hidden;
  font-family: inherit;
}

/* Header */
.tree-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.85rem 1rem;
  border-bottom: 1px solid var(--border-color, #334155);
  background: rgba(15, 23, 42, 0.4);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.tree-title {
  margin: 0;
  font-size: 0.95rem;
  font-weight: 600;
  color: var(--text-primary, #f8fafc);
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.title-icon {
  color: var(--brand-color, #10b981);
}

.tree-badge {
  font-size: 0.7rem;
  padding: 0.15rem 0.45rem;
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
  border-radius: 4px;
  font-weight: 500;
}

.header-actions {
  display: flex;
  gap: 0.4rem;
}

.btn-studio {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.4rem 0.75rem;
  border-radius: 6px;
  font-size: 0.82rem;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: all 0.15s ease;
}

.btn-studio.btn-primary {
  background: #10b981;
  color: #ffffff;
}

.btn-studio.btn-primary:hover {
  background: #059669;
}

/* Node action button styled like header add button */
.btn-studio-node {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.28rem 0.65rem;
  border-radius: 6px;
  font-size: 0.76rem;
  font-weight: 500;
  cursor: pointer;
  border: none;
  transition: all 0.15s ease;
  white-space: nowrap;
}

.btn-studio-node.btn-primary-node {
  background: #10b981;
  color: #ffffff;
}

.btn-studio-node.btn-primary-node:hover {
  background: #059669;
}

.btn-studio.btn-ghost {
  background: rgba(51, 65, 85, 0.5);
  color: #cbd5e1;
  border: 1px solid #334155;
}

.btn-studio.btn-ghost:hover {
  background: #334155;
  color: #ffffff;
}

/* Search */
.tree-search-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  padding: 0.6rem 1rem;
  border-bottom: 1px solid var(--border-color, #334155);
  background: rgba(15, 23, 42, 0.25);
}

.search-icon {
  position: absolute;
  left: 1.6rem;
  color: #64748b;
  pointer-events: none;
}

.tree-search-input {
  width: 100%;
  padding: 0.45rem 2rem 0.45rem 2.2rem;
  background: #0f172a;
  border: 1px solid #334155;
  border-radius: 6px;
  color: #f8fafc;
  font-size: 0.82rem;
  outline: none;
  transition: border-color 0.15s;
}

.tree-search-input:focus {
  border-color: #10b981;
}

.clear-search-btn {
  position: absolute;
  right: 1.5rem;
  background: none;
  border: none;
  color: #64748b;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0.2rem;
}

.clear-search-btn:hover {
  color: #f8fafc;
}

/* Tree Scroll Container */
.tree-scroll-container {
  flex: 1;
  overflow-y: auto;
  padding: 0.75rem 0.5rem;
}

.tree-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3rem 1rem;
  color: #64748b;
  text-align: center;
  gap: 0.75rem;
}

.spin-icon {
  animation: spin 1s linear infinite;
  color: #10b981;
}

@keyframes spin {
  100% {
    transform: rotate(360deg);
  }
}

.empty-icon {
  color: #475569;
}

/* Tree Structure */
.tree-root {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.tree-branch {
  position: relative;
  display: flex;
  flex-direction: column;
}

.tree-sub-tree {
  margin-left: 1.25rem;
  padding-left: 0.5rem;
  border-left: 1px dashed rgba(100, 116, 139, 0.35);
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  margin-top: 0.15rem;
  margin-bottom: 0.25rem;
}

/* Node Styling */
.tree-node {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.45rem 0.6rem;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  transition: all 0.12s ease;
  user-select: none;
  min-height: 36px;
}

.tree-node:hover {
  background: rgba(51, 65, 85, 0.45);
}

.tree-node.active {
  background: rgba(16, 185, 129, 0.12);
  border: 1px solid rgba(16, 185, 129, 0.3);
}

/* Level specifics */
.domain-node {
  font-weight: 600;
  color: #f1f5f9;
  background: rgba(30, 41, 59, 0.7);
  border: 1px solid rgba(51, 65, 85, 0.5);
  margin-bottom: 0.15rem;
}

.domain-icon {
  color: #38bdf8;
}

.program-node {
  font-weight: 500;
  color: #e2e8f0;
}

.program-icon {
  color: #fbbf24;
}

.course-node {
  color: #cbd5e1;
}

.course-icon {
  color: #34d399;
}

.chapter-node {
  color: #94a3b8;
  font-size: 0.85rem;
}

.chapter-icon {
  color: #a78bfa;
}

.lesson-node {
  color: #cbd5e1;
  font-size: 0.83rem;
}

.lesson-icon {
  color: #f472b6;
}

/* Toggle Chevron */
.toggle-chevron {
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  color: #64748b;
  cursor: pointer;
  padding: 0.1rem;
  border-radius: 3px;
}

.toggle-chevron:hover {
  color: #f8fafc;
  background: rgba(255, 255, 255, 0.08);
}

.toggle-chevron svg {
  transition: transform 0.15s ease;
}

.toggle-chevron svg.rotated {
  transform: rotate(90deg);
}

/* Node info */
.node-info {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.node-title {
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-code {
  font-size: 0.72rem;
  color: #64748b;
  background: rgba(15, 23, 42, 0.6);
  padding: 0.1rem 0.35rem;
  border-radius: 4px;
  font-family: monospace;
}

.node-index {
  font-size: 0.78rem;
  color: #94a3b8;
  font-weight: 600;
}

/* Badges */
.node-badge-group {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin-left: auto;
}

.count-tag {
  font-size: 0.7rem;
  padding: 0.1rem 0.4rem;
  background: rgba(15, 23, 42, 0.6);
  color: #94a3b8;
  border-radius: 4px;
  border: 1px solid rgba(51, 65, 85, 0.4);
}

.meta-tag {
  font-size: 0.68rem;
  padding: 0.1rem 0.38rem;
  border-radius: 4px;
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  gap: 0.2rem;
}

.module-tag {
  background: rgba(56, 189, 248, 0.15);
  color: #38bdf8;
  border: 1px solid rgba(56, 189, 248, 0.3);
}

.mandatory-tag {
  background: rgba(239, 68, 68, 0.15);
  color: #f87171;
  border: 1px solid rgba(239, 68, 68, 0.3);
}

.status-pill {
  font-size: 0.68rem;
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
}

.status-pill.published {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
}

.status-pill.draft {
  background: rgba(148, 163, 184, 0.15);
  color: #94a3b8;
}

/* Mini block indicators */
.mini-blocks {
  display: flex;
  gap: 0.25rem;
}

.mini-block-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 3px;
  background: rgba(15, 23, 42, 0.8);
}

.mini-block-badge.video {
  color: #38bdf8;
}
.mini-block-badge.docs {
  color: #fbbf24;
}
.mini-block-badge.proc {
  color: #a78bfa;
}
.mini-block-badge.quiz {
  color: #10b981;
}

/* Level 6 & 7: Blocks */
.content-blocks-tree {
  margin-left: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.block-node {
  font-size: 0.78rem;
  padding: 0.35rem 0.55rem;
  color: #94a3b8;
  border: 1px solid transparent;
  min-height: 28px;
}

.block-node:hover {
  background: rgba(30, 41, 59, 0.6);
  color: #f1f5f9;
}

.block-node.active {
  color: #cbd5e1;
}

.block-icon {
  margin-right: 0.2rem;
}

.block-title {
  flex: 1;
}

.block-check {
  font-size: 0.72rem;
  color: #10b981;
  font-weight: bold;
}

/* Hover Action Buttons */
.node-hover-actions {
  display: none;
  align-items: center;
  gap: 0.25rem;
  margin-left: 0.4rem;
}

.tree-node:hover .node-hover-actions {
  display: flex;
}

.hover-action-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  background: #334155;
  border: 1px solid #475569;
  border-radius: 4px;
  color: #cbd5e1;
  cursor: pointer;
  transition: all 0.12s ease;
}

.hover-action-btn:hover {
  background: #10b981;
  border-color: #10b981;
  color: #ffffff;
}

.hover-action-btn.add-btn:hover {
  background: #10b981;
  border-color: #10b981;
}

.btn-tooltip {
  position: absolute;
  top: -24px;
  right: 0;
  background: #09101d;
  color: #f8fafc;
  font-size: 0.68rem;
  padding: 0.15rem 0.4rem;
  border-radius: 3px;
  white-space: nowrap;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.15s ease;
  border: 1px solid #334155;
  z-index: 10;
}

.hover-action-btn:hover .btn-tooltip {
  opacity: 1;
}

/* QUICK MODAL ENHANCED */
.tree-modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.75);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 9999;
  backdrop-filter: blur(3px);
  padding: 1rem;
}

.tree-quick-modal.enriched-modal {
  width: 100%;
  max-width: 600px;
  max-height: 90vh;
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 10px;
  padding: 1.25rem 1.4rem;
  box-shadow: 0 20px 35px -10px rgba(0, 0, 0, 0.6);
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1rem;
  padding-bottom: 0.6rem;
  border-bottom: 1px solid rgba(51, 65, 85, 0.6);
}

.modal-title-box h4 {
  margin: 0.2rem 0 0;
  font-size: 1.05rem;
  font-weight: 600;
  color: #f8fafc;
}

.modal-level-badge {
  font-size: 0.7rem;
  color: #10b981;
  background: rgba(16, 185, 129, 0.15);
  padding: 0.1rem 0.45rem;
  border-radius: 4px;
  font-weight: 500;
  display: inline-block;
}

.close-btn {
  background: none;
  border: none;
  color: #94a3b8;
  cursor: pointer;
  padding: 0.25rem;
  border-radius: 4px;
}

.close-btn:hover {
  color: #f8fafc;
  background: rgba(255, 255, 255, 0.08);
}

.modal-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.form-group label {
  font-size: 0.8rem;
  color: #cbd5e1;
  font-weight: 500;
}

.form-control {
  padding: 0.55rem 0.75rem;
  font-size: 0.85rem;
  background: #0f172a;
  border: 1px solid #334155;
  border-radius: 6px;
  color: #f8fafc;
  outline: none;
  transition: border-color 0.15s;
}

.form-control:focus {
  border-color: #10b981;
}

.form-control:disabled,
.form-control:read-only {
  background: #09101d;
  color: #94a3b8;
  cursor: not-allowed;
}

.form-row-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0.75rem;
}

.textarea-control {
  resize: vertical;
  min-height: 55px;
}

.select-control {
  cursor: pointer;
}

.disabled-select {
  background-color: rgba(30, 41, 59, 0.75) !important;
  color: #94a3b8 !important;
  cursor: not-allowed !important;
  border-color: #334155 !important;
  opacity: 0.85;
}

/* Code Group with Auto-Generator */
.code-label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.badge-module-hint {
  font-size: 0.72rem;
  font-weight: 600;
  color: #0d9488;
  background: #ccfbf1;
  padding: 0.15rem 0.5rem;
  border-radius: 9999px;
  border: 1px solid #99f6e4;
  letter-spacing: 0.3px;
}

.inherited-module-info {
  background: rgba(15, 23, 42, 0.65);
  padding: 0.65rem 0.85rem;
  border-radius: 8px;
  border: 1px solid rgba(51, 65, 85, 0.6);
}

.inherited-module-display {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.35rem;
  flex-wrap: wrap;
}

.module-chip-badge {
  background: #0284c7;
  color: #ffffff;
  font-weight: 700;
  font-size: 0.72rem;
  padding: 0.15rem 0.5rem;
  border-radius: 4px;
  letter-spacing: 0.5px;
}

.module-title-text {
  font-size: 0.84rem;
  font-weight: 500;
  color: #e2e8f0;
}

.inherited-tag {
  font-size: 0.72rem;
  color: #38bdf8;
  background: rgba(56, 189, 248, 0.1);
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
  font-style: italic;
}

.auto-code-toggle {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.auto-code-toggle input[type="checkbox"] {
  accent-color: #10b981;
  cursor: pointer;
}

.toggle-text {
  font-size: 0.74rem !important;
  color: #10b981 !important;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-weight: 500;
}

.code-input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.code-input {
  flex: 1;
  font-family: monospace;
  letter-spacing: 0.5px;
}

.code-input.is-auto {
  border-color: rgba(16, 185, 129, 0.4);
}

.re-generate-btn,
.manual-edit-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.45rem 0.6rem;
  border-radius: 6px;
  font-size: 0.76rem;
  background: #334155;
  border: 1px solid #475569;
  color: #cbd5e1;
  cursor: pointer;
  white-space: nowrap;
}

.re-generate-btn:hover,
.manual-edit-btn:hover {
  background: #475569;
  color: #ffffff;
}

.form-hint {
  font-size: 0.72rem;
  color: #64748b;
}

/* Section Sub Title */
.section-sub-title {
  margin: 0;
  font-size: 0.85rem;
  font-weight: 600;
  color: #f1f5f9;
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding-bottom: 0.35rem;
  border-bottom: 1px dashed rgba(51, 65, 85, 0.5);
}

/* Units Selection Section */
.units-section {
  background: rgba(15, 23, 42, 0.45);
  border: 1px solid rgba(51, 65, 85, 0.5);
  border-radius: 8px;
  padding: 0.85rem;
  gap: 0.85rem;
}

.unit-picker-box {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.unit-box-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mandatory-label {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  color: #f87171 !important;
}

.badge-mandatory {
  font-size: 0.68rem;
  background: rgba(239, 68, 68, 0.15);
  color: #f87171;
  border: 1px solid rgba(239, 68, 68, 0.3);
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
}

.optional-label {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  color: #38bdf8 !important;
}

.badge-optional {
  font-size: 0.68rem;
  background: rgba(56, 189, 248, 0.15);
  color: #38bdf8;
  border: 1px solid rgba(56, 189, 248, 0.3);
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
}

.unit-tags-container {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem;
  margin-top: 0.2rem;
}

.unit-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.2rem 0.45rem;
  border-radius: 4px;
  font-size: 0.74rem;
  font-weight: 500;
}

.unit-chip.mandatory-chip {
  background: rgba(239, 68, 68, 0.2);
  border: 1px solid rgba(239, 68, 68, 0.4);
  color: #fca5a5;
}

.unit-chip.optional-chip {
  background: rgba(56, 189, 248, 0.2);
  border: 1px solid rgba(56, 189, 248, 0.4);
  color: #bae6fd;
}

.remove-chip-btn {
  background: none;
  border: none;
  color: inherit;
  font-size: 0.95rem;
  cursor: pointer;
  padding: 0;
  display: flex;
  align-items: center;
  line-height: 1;
}

.remove-chip-btn:hover {
  opacity: 0.8;
}

/* Auto-Enroll Box */
.auto-enroll-master-card {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.25);
  border-radius: 6px;
  padding: 0.65rem 0.85rem;
}

.auto-enroll-title {
  font-weight: 600;
  color: #fca5a5;
  font-size: 0.82rem;
}

.auto-enroll-alert.pending-units {
  background: rgba(51, 65, 85, 0.3);
  color: #94a3b8;
  border: 1px dashed rgba(100, 116, 139, 0.35);
}

.auto-enroll-wrapper {
  margin-top: 0.4rem;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.25);
  border-radius: 6px;
  padding: 0.55rem 0.75rem;
}

.auto-enroll-check-label {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.78rem;
  color: #fca5a5;
  cursor: pointer;
  font-weight: 500;
}

.auto-enroll-check-label input[type="checkbox"] {
  accent-color: #ef4444;
}

.auto-enroll-alert {
  display: flex;
  align-items: flex-start;
  gap: 0.4rem;
  font-size: 0.74rem;
  color: #fed7aa;
  background: rgba(249, 115, 22, 0.12);
  border-radius: 4px;
  padding: 0.4rem 0.55rem;
}

.auto-enroll-alert.empty-staff {
  color: #94a3b8;
  background: rgba(51, 65, 85, 0.3);
}

.alert-icon {
  color: #f97316;
  flex-shrink: 0;
  margin-top: 0.1rem;
}

.alert-content {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.staff-list {
  font-style: italic;
  color: #fdba74;
}

/* Modal Actions */
.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.6rem;
  margin-top: 0.5rem;
  padding-top: 0.75rem;
  border-top: 1px solid rgba(51, 65, 85, 0.6);
}

.btn {
  padding: 0.5rem 1rem;
  font-size: 0.84rem;
  border-radius: 6px;
  border: none;
  cursor: pointer;
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  transition: all 0.15s ease;
}

.btn-primary {
  background: #10b981;
  color: #ffffff;
}

.btn-primary:hover:not(:disabled) {
  background: #059669;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary {
  background: #334155;
  color: #cbd5e1;
}

.btn-secondary:hover {
  background: #475569;
  color: #ffffff;
}
</style>
