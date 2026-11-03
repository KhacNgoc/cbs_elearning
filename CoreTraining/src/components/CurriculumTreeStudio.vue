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

      <div v-else-if="!domains.length" class="tree-empty-state">
        <FolderTree :size="36" class="empty-icon" />
        <p v-if="searchQuery">Không tìm thấy mục nào khớp với "{{ searchQuery }}".</p>
        <p v-else>Chưa có cấu trúc đào tạo nào. Hãy bấm "Thêm Lĩnh vực" để bắt đầu.</p>
      </div>

      <div v-else class="tree-root">
        <!-- LEVEL 1: DOMAIN (LĨNH VỰC) -->
        <div
          v-for="domain in domains"
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

            <div
              class="node-info"
              @mouseenter="showNodeTooltip($event, 'domain', domain)"
              @mouseleave="hideNodeTooltip"
            >
              <span class="node-title">{{ domain.name }}</span>
            </div>

            <div class="node-badge-group">
              <span
                v-if="domain.mandatoryUnitIds?.length"
                class="meta-tag mandatory-tag"
                :title="`Đơn vị bắt buộc: ${domain.mandatoryUnitIds.map(getUnitName).join(', ')}`"
              >
                <ShieldCheck :size="11" />
                {{ domain.mandatoryUnitIds.length }} ĐV Bắt buộc
              </span>
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
                class="hover-action-btn edit-btn"
                title="Chỉnh sửa thông tin Lĩnh vực"
                @click.stop="openEditModal('domain', domain)"
              >
                <Edit3 :size="13" />
              </button>
              <button
                type="button"
                class="hover-action-btn delete-btn"
                title="Xóa Lĩnh vực này"
                @click.stop="promptDeleteNode('domain', domain)"
              >
                <Trash2 :size="13" />
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

                <div
                  class="node-info"
                  @mouseenter="showNodeTooltip($event, 'program', program)"
                  @mouseleave="hideNodeTooltip"
                >
                  <span class="node-title">{{ program.name }}</span>
                </div>

                <div class="node-badge-group">
                  <span
                    v-if="program.mandatoryUnitIds?.length"
                    class="meta-tag mandatory-tag"
                    :title="`Đơn vị bắt buộc: ${program.mandatoryUnitIds.map(getUnitName).join(', ')}`"
                  >
                    <ShieldCheck :size="11" />
                    {{ program.mandatoryUnitIds.length }} ĐV Bắt buộc
                  </span>
                  <span v-if="isProgramCourseCountLoaded(program.id)" class="count-tag">
                    <Loader2 v-if="loadingProgramCoursesMap[program.id]" :size="10" class="spin-icon" />
                    <span v-else>{{ getProgramTotalCoursesCount(program.id) }} Khóa</span>
                  </span>
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
                    class="hover-action-btn edit-btn"
                    title="Chỉnh sửa thông tin Chương trình"
                    @click.stop="openEditModal('program', program, domain)"
                  >
                    <Edit3 :size="13" />
                  </button>
                  <button
                    type="button"
                    class="hover-action-btn delete-btn"
                    title="Xóa Chương trình này"
                    @click.stop="promptDeleteNode('program', program, domain)"
                  >
                    <Trash2 :size="13" />
                  </button>
                </div>
              </div>

              <!-- LEVEL 3: COURSE (KHÓA HỌC - LAZY FETCHED) -->
              <div v-if="isExpanded(program.id)" class="tree-sub-tree">
                <div
                  v-if="loadingProgramCoursesMap[program.id] && !getCoursesForProgram(program.id).length"
                  class="tree-inline-loading"
                >
                  <Loader2 :size="13" class="spin-icon" />
                  <span>Đang tải danh sách khóa học...</span>
                </div>

                <div
                  v-for="course in getVisibleCoursesForProgram(program.id)"
                  :key="course.id"
                  class="tree-branch course-branch"
                >
                  <div
                    class="tree-node course-node"
                    :class="{ active: selectedId === course.id, expanded: isExpanded(course.id) }"
                    @click="selectNode('course', course, program)"
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

                    <div
                      class="node-info"
                      @mouseenter="showNodeTooltip($event, 'course', course)"
                      @mouseleave="hideNodeTooltip"
                    >
                      <span class="node-title">{{ course.name }}</span>
                      <span class="node-status-dot" :class="course.status?.toLowerCase()"></span>
                    </div>

                    <div class="node-badge-group">
                      <span
                        v-if="getCourseMandatoryUnits(course).length"
                        class="meta-tag mandatory-tag"
                        :title="`Đơn vị bắt buộc: ${getCourseMandatoryUnits(course).map(getUnitName).join(', ')}`"
                      >
                        <ShieldCheck :size="11" />
                        {{ getCourseMandatoryUnits(course).length }} ĐV Bắt buộc
                      </span>
                      <span v-if="course.status === 'PUBLISHED'" class="status-pill published">
                        Đã xuất bản
                      </span>
                      <span v-else class="status-pill draft">
                        Bản nháp
                      </span>
                    </div>

                    <!-- Hover Actions -->
                    <div class="node-hover-actions" @click.stop>
                      <button
                        type="button"
                        class="btn-studio-node btn-preview-node"
                        title="Xem trước & kiểm duyệt nội dung khóa học"
                        @click.stop="previewCourse(course.id)"
                      >
                        <Eye :size="13" />
                        <span>Xem trước</span>
                      </button>
                      <button
                        type="button"
                        class="btn-studio-node btn-primary-node"
                        title="Thêm Chương vào Khóa học này"
                        @click="openAddModal('chapter', course)"
                      >
                        <Plus :size="13" />
                        <span>Thêm Chương</span>
                      </button>
                      <button
                        type="button"
                        class="hover-action-btn edit-btn"
                        title="Chỉnh sửa thông tin Khóa học"
                        @click.stop="openEditModal('course', course, program)"
                      >
                        <Edit3 :size="13" />
                      </button>
                      <button
                        type="button"
                        class="hover-action-btn delete-btn"
                        title="Xóa Khóa học này"
                        @click.stop="promptDeleteNode('course', course, program)"
                      >
                        <Trash2 :size="13" />
                      </button>
                    </div>
                  </div>

                  <!-- LEVEL 4: CHAPTER (CHƯƠNG HỌC - LAZY FETCHED) -->
                  <div v-if="isExpanded(course.id)" class="tree-sub-tree">
                    <div
                      v-if="loadingCourseChaptersMap[course.id] && !getChaptersForCourse(course.id).length"
                      class="tree-inline-loading"
                    >
                      <Loader2 :size="12" class="spin-icon" />
                      <span>Đang tải danh sách chương...</span>
                    </div>
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

                        <div
                          class="node-info"
                          @mouseenter="showNodeTooltip($event, 'chapter', chapter, course)"
                          @mouseleave="hideNodeTooltip"
                        >
                          <span class="node-index">Chương {{ cIdx + 1 }}:</span>
                          <span class="node-title">{{ stripLeadingIndexPrefix(chapter.title, 'Chương') }}</span>
                        </div>

                        <div class="node-badge-group">
                          <span class="count-tag">{{ (chapter.lessons || []).filter(l => l.status !== 'DELETED').length }} bài</span>
                        </div>

                        <!-- Hover Actions -->
                        <div class="node-hover-actions" @click.stop>
                          <button
                            type="button"
                            class="btn-studio-node btn-primary-node"
                            title="Thêm Bài học vào Chương này"
                            @click="createLessonDirectly(chapter, course)"
                          >
                            <Plus :size="12" />
                            <span>Thêm Bài</span>
                          </button>
                          <button
                            type="button"
                            class="hover-action-btn edit-btn"
                            title="Chỉnh sửa thông tin Chương"
                            @click.stop="openEditModal('chapter', chapter, course)"
                          >
                            <Edit3 :size="13" />
                          </button>
                          <button
                            type="button"
                            class="hover-action-btn delete-btn"
                            title="Xóa Chương này"
                            @click.stop="promptDeleteNode('chapter', chapter, course)"
                          >
                            <Trash2 :size="13" />
                          </button>
                        </div>
                      </div>

                      <!-- LEVEL 5: LESSON (BÀI HỌC) -->
                      <div v-if="isExpanded(chapter.id)" class="tree-sub-tree">
                        <div
                          v-for="(lesson, lIdx) in (chapter.lessons || []).filter(l => l.status !== 'DELETED')"
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

                            <div
                              class="node-info"
                              @mouseenter="showNodeTooltip($event, 'lesson', lesson, course)"
                              @mouseleave="hideNodeTooltip"
                            >
                              <span class="node-index">Bài {{ lIdx + 1 }}:</span>
                              <span class="node-title">{{ stripLeadingIndexPrefix(lesson.title, 'Bài') }}</span>
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
                                class="btn-studio-node btn-editor-node"
                                title="Mở trang soạn bài học toàn diện"
                                @click="openLessonEditor(lesson.id)"
                              >
                                <ExternalLink :size="12" />
                                <span>Soạn bài</span>
                              </button>
                              <button
                                type="button"
                                class="hover-action-btn delete-btn"
                                title="Xóa Bài học này"
                                @click.stop="promptDeleteNode('lesson', lesson, chapter, course)"
                              >
                                <Trash2 :size="13" />
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

                <!-- TIER-2 IN-BRANCH COURSE PAGINATION (SERVER-SIDE LAZY FETCH) -->
                <div
                  v-if="getProgramTotalCoursesCount(program.id) > DEFAULT_COURSE_BRANCH_SIZE"
                  class="in-branch-pagination-row"
                >
                  <span class="in-branch-count">
                    Đang hiển thị {{ getVisibleCoursesForProgram(program.id).length }} / {{ getProgramTotalCoursesCount(program.id) }} khóa học trong chương trình
                  </span>
                  <div class="in-branch-actions">
                    <button
                      v-if="getVisibleCoursesForProgram(program.id).length < getProgramTotalCoursesCount(program.id)"
                      type="button"
                      class="btn-in-branch-more"
                      :disabled="loadingMoreProgramCoursesMap[program.id]"
                      @click.stop="expandCourseBranchLimit(program.id)"
                    >
                      <Loader2 v-if="loadingMoreProgramCoursesMap[program.id]" :size="11" class="spin-icon" />
                      <span>
                        + Xem thêm {{ Math.min(DEFAULT_COURSE_BRANCH_SIZE, getProgramTotalCoursesCount(program.id) - getVisibleCoursesForProgram(program.id).length) }} khóa học
                      </span>
                    </button>
                    <button
                      v-if="getCourseBranchLimit(program.id) > DEFAULT_COURSE_BRANCH_SIZE"
                      type="button"
                      class="btn-in-branch-collapse"
                      @click.stop="resetCourseBranchLimit(program.id)"
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
    </div>

    <!-- TIER-1 ROOT TREE PAGINATION FOOTER BAR -->
    <div v-if="!loading && domains.length > 0" class="tree-pagination-bar">
      <div class="pagination-summary">
        <span>
          Hiển thị <strong>{{ (treeDomainPage - 1) * treeDomainPageSize + 1 }}–{{ Math.min(treeDomainPage * treeDomainPageSize, treeDomainTotalElements) }}</strong>
          trên tổng số <strong>{{ treeDomainTotalElements }}</strong> lĩnh vực
          ({{ totalActiveProgramsCount }} chương trình)
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

    <!-- CONFIRM DELETE MODAL -->
    <div v-if="deleteConfirm.show" class="tree-modal-backdrop" @click.self="closeDeleteModal">
      <div class="tree-quick-modal delete-confirm-modal">
        <div class="modal-header delete-modal-header">
          <div class="modal-title-box">
            <h4>{{ deleteConfirm.title }}</h4>
          </div>
          <button type="button" class="close-btn" @click="closeDeleteModal">
            <X :size="16" />
          </button>
        </div>
        <div class="modal-form">
          <div class="delete-confirm-body">
            <p class="delete-main-question">{{ deleteConfirm.message }}</p>
            <div v-if="deleteConfirm.notes.length" class="delete-notes-group">
              <p
                v-for="(note, idx) in deleteConfirm.notes"
                :key="idx"
                class="delete-note-item"
              >
                {{ note }}
              </p>
            </div>
          </div>
          <div class="modal-actions delete-modal-actions">
            <button
              type="button"
              class="btn btn-secondary"
              :disabled="deleteConfirm.busy"
              @click="closeDeleteModal"
            >
              Hủy bỏ
            </button>
            <button
              type="button"
              class="btn btn-primary btn-delete-submit"
              :disabled="deleteConfirm.busy"
              @click="executeDeleteNode"
            >
              <Loader2 v-if="deleteConfirm.busy" class="spin-icon" :size="14" />
              <span>{{ deleteConfirm.busy ? 'Đang xóa...' : 'Xóa vĩnh viễn' }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>

    <!-- ENRICHED INLINE ADD MODAL -->
    <div v-if="addModal.show" class="tree-modal-backdrop">
      <div class="tree-quick-modal enriched-modal">
        <div class="modal-header">
          <div class="modal-title-box">
            <span class="modal-level-badge">{{ levelSubtitle }}</span>
            <h4>{{ modalTitle }}</h4>
          </div>
          <button type="button" class="close-btn" @click="closeAddModal">
            <X :size="16" />
          </button>
        </div>

        <!-- DRAFT RESTORED NOTICE BANNER (Bỏ banner) -->

        <form @submit.prevent="submitAddModal" class="modal-form">
          <!-- SECTION 1: BASIC INFO -->
          <div class="form-section">




            <!-- MÃ ĐỊNH DANH (CODE) VỚI OPTION TỰ ĐỘNG SINH / DISABLE / ẨN -->
            <!-- MÃ ĐỊNH DANH (CODE) - ĐÃ ẨN VÀ TỰ ĐỘNG DÙNG UUID -->
            <div class="form-group code-group" style="display: none;">
              <div class="code-label-row">
                <label>Mã định danh (*):</label>
              </div>

              <div class="code-input-wrapper">
                <input
                  v-model="addModal.code"
                  type="text"
                  :placeholder="addModal.autoCode ? '(Hệ thống đang tự sinh mã...)' : 'Nhập mã định danh...'"
                  :disabled="addModal.autoCode"
                  :readonly="addModal.autoCode"
                  required
                  class="form-control code-input"
                  :class="{ 'is-auto': addModal.autoCode }"
                />
                <button
                  type="button"
                  class="manual-edit-btn"
                  @click="toggleAutoCode"
                  :title="addModal.autoCode ? 'Chuyển sang tự nhập mã' : 'Chuyển sang tự động sinh mã'"
                >
                  <component :is="addModal.autoCode ? Lock : Unlock" :size="13" />
                  <span>{{ addModal.autoCode ? "Tự nhập" : "Khóa tự sinh" }}</span>
                </button>
              </div>
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

          <!-- SECTION 2: BỔ SUNG & MÔ TẢ (HIỂN THỊ ĐẦY ĐỦ CHO TẤT CẢ CÁC CẤP) -->
          <div class="form-section meta-section">
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

            <!-- Mục tiêu / Mô tả tóm tắt (Khóa học, Bài học, Lĩnh vực, Chương trình; Chương học không cần mô tả) -->
            <div v-if="addModal.type !== 'chapter'" class="form-group">
              <label>{{ descriptionLabel }}:</label>
              <textarea
                v-model="addModal.description"
                rows="2"
                :placeholder="descriptionPlaceholder"
                class="form-control textarea-control"
              ></textarea>
            </div>
          </div>

          <!-- SECTION 3: APPLIED UNITS & AUTO-ENROLLMENT -->
          <!-- 1. Cấp Lĩnh vực, Chương trình & Khóa học: Phân bổ Đơn vị áp dụng chọn trực tiếp -->
          <div v-if="addModal.type === 'domain' || addModal.type === 'program' || addModal.type === 'course'" class="form-section units-section">
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
            <button
              v-if="addModal.isEdit && addModal.type === 'lesson'"
              type="button"
              class="btn btn-outline-info"
              @click="openLessonEditor(addModal.editId!)"
            >
              <ExternalLink :size="14" />
              <span>Soạn chi tiết bài học</span>
            </button>
            <div class="actions-right">
              <button type="button" class="btn btn-secondary" @click="closeAddModal">
                Hủy
              </button>
              <button type="submit" class="btn btn-primary" :disabled="submitting">
                <Loader2 v-if="submitting" :size="14" class="spin-icon" />
                <span>{{ submitting ? "Đang xử lý..." : (addModal.isEdit ? "Cập nhật & Lưu" : "Tạo mới") }}</span>
              </button>
            </div>
          </div>
        </form>
      </div>
    </div>

    <!-- FLOATING RICH HOVER DESCRIPTION TOOLTIP (Synchronized with UnitAllocationAdmin) -->
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
          <div v-if="hoverTooltip.level || hoverTooltip.deadline || hoverTooltip.extraMeta" class="popover-footer-meta">
            <span v-if="hoverTooltip.level" class="meta-item">
              Cấp độ: <strong>{{ formatLevelLabel(hoverTooltip.level) }}</strong>
            </span>
            <span v-if="hoverTooltip.deadline" class="meta-item">
              Hạn khuyến nghị: <strong>{{ hoverTooltip.deadline }}</strong>
            </span>
            <span v-if="hoverTooltip.extraMeta" class="meta-item">
              Nội dung: <strong>{{ hoverTooltip.extraMeta }}</strong>
            </span>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- FULLSCREEN LOADING OVERLAY KHI ĐANG TẠO BÀI HỌC -->
    <Teleport to="body">
      <div v-if="isCreatingLessonDirectly" class="fullscreen-loading-overlay">
        <div class="loading-modal-card">
          <Loader2 :size="42" class="spin-icon loading-spinner" />
          <h4>Đang khởi tạo bài học mới...</h4>
          <p>Hệ thống đang chuẩn bị cấu trúc và chuyển hướng tới trang soạn thảo.</p>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, nextTick } from "vue";
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
  ExternalLink,
  FolderTree,
  Loader2,
  Lock,
  Unlock,
  Building2,
  ShieldCheck,
  Edit3,
  Trash2,
  Eye,
} from "lucide-vue-next";
import { courseApi } from "../api/courseApi";
import { lessonMetadataUpdate } from "../utils/lessonContent";
import { trainingApi } from "../api/trainingApi";
import { adminApi, type AdminUser } from "../api/adminApi";
import { axiosClient } from "../api/axiosClient";
import { showToast } from "../utils/toast";
import type { TrainingDomain, TrainingProgram, Course, Chapter, Lesson } from "../types";
import type { OrganizationUnit } from "../types/organization";

const router = useRouter();

const props = withDefaults(
  defineProps<{
    initialCourseId?: string;
  }>(),
  {
    initialCourseId: "",
  }
);

const emit = defineEmits<{
  (e: "select", item: { type: string; data: any; parent?: any }): void;
  (e: "refresh"): void;
}>();

// Training Levels

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
const expandAll = ref(false);
const selectedId = ref<string>("");
const expandedIds = ref<Set<string>>(new Set());

// Data storage
const domains = ref<TrainingDomain[]>([]);
const programs = ref<TrainingProgram[]>([]);
const courses = ref<Course[]>([]);
const chaptersMap = ref<Record<string, Chapter[]>>({});
const unitsList = ref<OrganizationUnit[]>([]);
const systemUsers = ref<AdminUser[]>([]);

// Delete confirmation state
const deleteConfirm = ref<{
  show: boolean;
  type: "domain" | "program" | "course" | "chapter" | "lesson";
  id: string;
  name: string;
  parentId?: string;
  grandParentId?: string;
  title: string;
  message: string;
  notes: string[];
  busy: boolean;
}>({
  show: false,
  type: "lesson",
  id: "",
  name: "",
  parentId: "",
  grandParentId: "",
  title: "",
  message: "",
  notes: [],
  busy: false,
});

// Inline Add / Edit Modal State
interface AddModalData {
  show: boolean;
  isEdit?: boolean;
  editId?: string;
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
  version?: number;
}

const addModal = ref<AddModalData>({
  show: false,
  isEdit: false,
  editId: "",
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

// ================= DRAFT PRESERVATION SYSTEM =================
// Lưu trữ dữ liệu đang nhập dở của modal thêm mới (theo phân cấp và cha)
function getDraftKey(type: string, parentId?: string) {
  return `${type}_${parentId || "root"}`;
}

const addModalDrafts = ref<Record<string, any>>((() => {
  try {
    const raw = sessionStorage.getItem("curriculum_add_modal_drafts");
    return raw ? JSON.parse(raw) : {};
  } catch (e) {
    return {};
  }
})());

const hasRestoredDraft = ref(false);

function saveCurrentDraft() {
  if (!addModal.value.show || addModal.value.isEdit) return;
  const {
    type,
    parentId,
    name,
    description,
    code,
    autoCode,
    businessModule,
    level,
    status,
    mandatoryUnitIds,
    optionalUnitIds,
    autoEnrollStaff,
    completionDeadline,
  } = addModal.value;

  const hasContent = !!(
    name.trim() ||
    description.trim() ||
    completionDeadline ||
    !autoCode
  );

  const key = getDraftKey(type, parentId);
  if (hasContent) {
    addModalDrafts.value[key] = {
      code,
      name,
      autoCode,
      businessModule,
      level,
      description,
      status,
      mandatoryUnitIds: Array.isArray(mandatoryUnitIds) ? [...mandatoryUnitIds] : [],
      optionalUnitIds: Array.isArray(optionalUnitIds) ? [...optionalUnitIds] : [],
      autoEnrollStaff,
      completionDeadline,
      updatedAt: Date.now(),
    };
    try {
      sessionStorage.setItem("curriculum_add_modal_drafts", JSON.stringify(addModalDrafts.value));
    } catch (e) {}
  }
}

function clearDraft(type: string, parentId?: string) {
  const key = getDraftKey(type, parentId);
  delete addModalDrafts.value[key];
  try {
    sessionStorage.setItem("curriculum_add_modal_drafts", JSON.stringify(addModalDrafts.value));
  } catch (e) {}
}

function closeAddModal() {
  saveCurrentDraft();
  addModal.value.show = false;
  hasRestoredDraft.value = false;
}

// Tự động lưu nháp khi có thay đổi trong modal tạo mới
watch(
  () => [
    addModal.value.name,
    addModal.value.description,
    addModal.value.code,
    addModal.value.autoCode,
    addModal.value.businessModule,
    addModal.value.level,
    addModal.value.status,
    addModal.value.completionDeadline,
    addModal.value.mandatoryUnitIds,
    addModal.value.optionalUnitIds,
  ],
  () => {
    if (addModal.value.show && !addModal.value.isEdit) {
      saveCurrentDraft();
    }
  },
  { deep: true }
);

// Modal title & subtitle helpers
const levelSubtitle = computed(() => {
  const prefix = addModal.value.isEdit ? "Cập nhật " : "";
  switch (addModal.value.type) {
    case "domain":
      return `${prefix}Cấp 1: Lĩnh vực đào tạo`;
    case "program":
      return `${prefix}Cấp 2: Chương trình đào tạo`;
    case "course":
      return `${prefix}Cấp 3: Khóa học nghiệp vụ`;
    case "chapter":
      return `${prefix}Cấp 4: Chương học`;
    case "lesson":
      return `${prefix}Cấp 5: Bài học`;
    default:
      return "Cấu trúc đào tạo";
  }
});

const modalTitle = computed(() => {
  if (addModal.value.isEdit) {
    switch (addModal.value.type) {
      case "domain":
        return `Cập nhật Lĩnh vực: ${addModal.value.name || addModal.value.code}`;
      case "program":
        return `Cập nhật Chương trình: ${addModal.value.name || addModal.value.code}`;
      case "course":
        return `Cập nhật Khóa học: ${addModal.value.name || addModal.value.code}`;
      case "chapter":
        return `Cập nhật Chương: ${addModal.value.name || addModal.value.code}`;
      case "lesson":
        return `Cập nhật Bài học: ${addModal.value.name || addModal.value.code}`;
      default:
        return "Cập nhật thông tin";
    }
  }
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
      return `${mod}CH`;
    case "lesson":
      return `${mod}BH`;
    default:
      return `${mod}_`;
  }
}

const descriptionLabel = computed(() => {
  switch (addModal.value.type) {
    case "domain":
      return "Mục tiêu / Mô tả lĩnh vực";
    case "program":
      return "Mục tiêu / Mô tả chương trình";
    case "course":
      return "Mục tiêu / Chuẩn đầu ra khóa học";
    case "chapter":
      return "Tóm tắt nội dung chương";
    case "lesson":
      return "Tóm tắt / Hướng dẫn bài học";
    default:
      return "Mục tiêu / Mô tả tóm tắt";
  }
});

const descriptionPlaceholder = computed(() => {
  switch (addModal.value.type) {
    case "domain":
      return "Nhập mục tiêu đào tạo, định hướng hoặc phạm vi nghiệp vụ lĩnh vực...";
    case "program":
      return "Nhập mục tiêu chương trình, chuẩn năng lực hoặc đối tượng áp dụng...";
    case "course":
      return "Nhập mục tiêu đào tạo, chuẩn đầu ra hoặc nội dung chính khóa học...";
    case "chapter":
      return "Nhập tóm tắt nội dung chính hoặc mục tiêu của chương học...";
    case "lesson":
      return "Nhập tóm tắt kiến thức trọng tâm, hướng dẫn thực hành của bài học...";
    default:
      return "Nhập mô tả tóm tắt...";
  }
});



// Đã bỏ phân hệ nghiệp vụ hoàn toàn
function extractModuleFromParent(_parent: any): string {
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
  } else if (type === "lesson") {
    const chapterId = addModal.value.parentId;
    const currentChapter = Object.values(chaptersMap.value).flat().find((ch) => ch.id === chapterId) || addModal.value.parentObj;
    list = (currentChapter?.lessons || []).map((l: any) => ({ code: l.id || l.code }));
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

function toggleAutoCode() {
  addModal.value.autoCode = !addModal.value.autoCode;
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

function getCourseMandatoryUnits(course: any): string[] {
  if (course?.mandatoryUnitIds && Array.isArray(course.mandatoryUnitIds) && course.mandatoryUnitIds.length > 0) {
    return course.mandatoryUnitIds;
  }
  if (course?.mandatoryUnitIds && typeof course.mandatoryUnitIds[Symbol.iterator] === "function") {
    const arr = Array.from(course.mandatoryUnitIds as string[]);
    if (arr.length > 0) return arr;
  }
  return [];
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

// ================= TRUE LAZY FETCH & SERVER-SIDE PAGINATION STATE =================
const DEFAULT_COURSE_BRANCH_SIZE = 6;
const programCoursesMap = ref<Record<string, Course[]>>({});
const programTotalCoursesMap = ref<Record<string, number>>({});
const programCoursePageMap = ref<Record<string, number>>({});
const programCourseHasNextMap = ref<Record<string, boolean>>({});
const loadingProgramCoursesMap = ref<Record<string, boolean>>({});
const loadingMoreProgramCoursesMap = ref<Record<string, boolean>>({});
const loadingCourseChaptersMap = ref<Record<string, boolean>>({});
const courseBranchLimits = ref<Record<string, number>>({});
const auxiliaryModalLoaded = ref(false);

function mergeCoursesIntoStore(newCourses: Course[]) {
  if (!newCourses?.length) return;
  const map = new Map<string, Course>();
  for (const c of courses.value) {
    map.set(c.id, c);
  }
  for (const c of newCourses) {
    map.set(c.id, c);
  }
  courses.value = Array.from(map.values());
}

async function ensureModalAuxiliaryDataLoaded() {
  if (auxiliaryModalLoaded.value) return;
  auxiliaryModalLoaded.value = true;
  try {
    const res = await axiosClient.get("/api/admin/options/catalog");
    if (res.data?.units) {
      unitsList.value = res.data.units;
    }
  } catch (e) {
    console.warn("Could not lazy-load auxiliary modal data:", e);
  }
}

async function ensureProgramCoursesLoaded(programId: string, forceReload = false) {
  if (
    !programId ||
    (!forceReload && programCoursesMap.value[programId] !== undefined) ||
    loadingProgramCoursesMap.value[programId]
  ) {
    return;
  }
  loadingProgramCoursesMap.value = { ...loadingProgramCoursesMap.value, [programId]: true };
  try {
    const fetchSize = Math.max(DEFAULT_COURSE_BRANCH_SIZE, getCourseBranchLimit(programId));
    const res = await axiosClient.get(`/api/admin/courses/by-program/${programId}`, {
      params: { page: 0, size: fetchSize },
    });
    const data = res.data || {};
    const items: Course[] = (data.items || (Array.isArray(data) ? data : [])).filter(
      (c: Course) => c.status !== "DELETED"
    );
    programCoursesMap.value = {
      ...programCoursesMap.value,
      [programId]: items,
    };
    programTotalCoursesMap.value = {
      ...programTotalCoursesMap.value,
      [programId]: typeof data.totalElements === "number" ? data.totalElements : items.length,
    };
    programCoursePageMap.value = {
      ...programCoursePageMap.value,
      [programId]: 0,
    };
    programCourseHasNextMap.value = {
      ...programCourseHasNextMap.value,
      [programId]: !!data.hasNext,
    };
    mergeCoursesIntoStore(items);
  } catch (err) {
    console.error(`Lỗi tải khóa học cho chương trình ${programId}:`, err);
    programCoursesMap.value = { ...programCoursesMap.value, [programId]: [] };
    programTotalCoursesMap.value = { ...programTotalCoursesMap.value, [programId]: 0 };
    programCourseHasNextMap.value = { ...programCourseHasNextMap.value, [programId]: false };
  } finally {
    loadingProgramCoursesMap.value = { ...loadingProgramCoursesMap.value, [programId]: false };
  }
}

async function fetchMoreCoursesForProgram(programId: string) {
  if (
    !programId ||
    loadingMoreProgramCoursesMap.value[programId] ||
    !programCourseHasNextMap.value[programId]
  ) {
    return;
  }
  const nextPage = (programCoursePageMap.value[programId] ?? 0) + 1;
  loadingMoreProgramCoursesMap.value = {
    ...loadingMoreProgramCoursesMap.value,
    [programId]: true,
  };
  try {
    const res = await axiosClient.get(`/api/admin/courses/by-program/${programId}`, {
      params: { page: nextPage, size: DEFAULT_COURSE_BRANCH_SIZE },
    });
    const data = res.data || {};
    const newItems: Course[] = (data.items || []).filter(
      (c: Course) => c.status !== "DELETED"
    );
    const existing = programCoursesMap.value[programId] || [];
    const existingIds = new Set(existing.map((c) => c.id));
    const merged = [...existing, ...newItems.filter((c) => !existingIds.has(c.id))];

    programCoursesMap.value = {
      ...programCoursesMap.value,
      [programId]: merged,
    };
    if (typeof data.totalElements === "number") {
      programTotalCoursesMap.value = {
        ...programTotalCoursesMap.value,
        [programId]: data.totalElements,
      };
    }
    programCoursePageMap.value = {
      ...programCoursePageMap.value,
      [programId]: nextPage,
    };
    programCourseHasNextMap.value = {
      ...programCourseHasNextMap.value,
      [programId]: !!data.hasNext,
    };
    mergeCoursesIntoStore(newItems);
  } catch (err) {
    console.error(`Lỗi tải thêm khóa học cho chương trình ${programId}:`, err);
  } finally {
    loadingMoreProgramCoursesMap.value = {
      ...loadingMoreProgramCoursesMap.value,
      [programId]: false,
    };
  }
}

function isProgramCourseCountLoaded(programId: string): boolean {
  return (
    !!loadingProgramCoursesMap.value[programId] ||
    programTotalCoursesMap.value[programId] !== undefined
  );
}

function getProgramTotalCoursesCount(programId: string): number {
  const serverTotal = programTotalCoursesMap.value[programId];
  if (typeof serverTotal === "number") {
    return Math.max(serverTotal, getCoursesForProgram(programId).length);
  }
  return getCoursesForProgram(programId).length;
}

// Expand only operating branch
function expandOperatingBranch(type: string, id?: string, parentId?: string) {
  if (type === "domain") {
    if (id) expandedIds.value.add(id);
  } else if (type === "program") {
    if (parentId) expandedIds.value.add(parentId);
    if (id) {
      expandedIds.value.add(id);
      ensureProgramCoursesLoaded(id, true);
    }
  } else if (type === "course") {
    const crs = courses.value.find((c) => c.id === id);
    const effectiveParentId = parentId || crs?.programId;
    if (effectiveParentId) {
      expandedIds.value.add(effectiveParentId);
      ensureProgramCoursesLoaded(effectiveParentId, true);
      const prog = programs.value.find((p) => p.id === effectiveParentId);
      if (prog?.domainId) expandedIds.value.add(prog.domainId);
    }
    if (id) {
      expandedIds.value.add(id);
      loadChaptersForCourse(id, true);
    }
  } else if (type === "chapter") {
    if (parentId) {
      expandedIds.value.add(parentId);
      loadChaptersForCourse(parentId, true);
      const crs = courses.value.find((c) => c.id === parentId);
      if (crs) {
        if (crs.programId) {
          expandedIds.value.add(crs.programId);
          ensureProgramCoursesLoaded(crs.programId);
          const prog = programs.value.find((p) => p.id === crs.programId);
          if (prog?.domainId) expandedIds.value.add(prog.domainId);
        }
      }
    }
    if (id) expandedIds.value.add(id);
  } else if (type === "lesson") {
    if (parentId) {
      expandedIds.value.add(parentId);
      let courseId = "";
      for (const [cId, chaps] of Object.entries(chaptersMap.value)) {
        if (chaps.some((ch) => ch.id === parentId)) {
          courseId = cId;
          break;
        }
      }
      if (courseId) {
        expandedIds.value.add(courseId);
        loadChaptersForCourse(courseId, true);
        const crs = courses.value.find((c) => c.id === courseId);
        if (crs?.programId) {
          expandedIds.value.add(crs.programId);
          ensureProgramCoursesLoaded(crs.programId);
          const prog = programs.value.find((p) => p.id === crs.programId);
          if (prog?.domainId) expandedIds.value.add(prog.domainId);
        }
      }
    }
    if (id) expandedIds.value.add(id);
  }
}

// DB-Level Paged Tree Catalog (Domains + Programs) — True Lazy Fetch for Courses & Chapters
async function loadTreeData(expandTarget?: { type: string; id?: string; parentId?: string }) {
  loading.value = true;
  try {
    const res = await trainingApi.domainsPaged({
      search: searchQuery.value.trim() || undefined,
      page: treeDomainPage.value - 1,
      size: treeDomainPageSize.value,
    });
    domains.value = (res.items || []).filter((d) => d.status !== "DELETED");
    treeDomainTotalPages.value = Math.max(1, res.totalPages || 1);
    treeDomainTotalElements.value = res.totalElements || 0;

    const domainIds = domains.value.map((d) => d.id);
    if (domainIds.length > 0) {
      const progResponses = await Promise.all(
        domainIds.map((dId) =>
          trainingApi.programsPaged({
            domainId: dId,
            search: searchQuery.value.trim() || undefined,
            size: 100,
          })
        )
      );
      programs.value = progResponses.flatMap((r) => (r.items || []).filter((p) => p.status !== "DELETED"));
    } else {
      programs.value = [];
    }

    if (searchQuery.value.trim()) {
      for (const d of domains.value) {
        expandedIds.value.add(d.id);
      }
      for (const p of programs.value) {
        expandedIds.value.add(p.id);
        ensureProgramCoursesLoaded(p.id);
      }
    }

    // Load units lightly so unit badges on courses/programs have names
    if (!unitsList.value.length) {
      axiosClient
        .get("/api/admin/options/catalog")
        .then((res) => {
          if (res.data?.units) unitsList.value = res.data.units;
        })
        .catch(() => {});
    }

    if (props.initialCourseId) {
      // Only fetch the specific course if needed
      if (!courses.value.some((c) => c.id === props.initialCourseId)) {
        try {
          const singleCourseRes = await axiosClient.get(`/api/courses/${props.initialCourseId}`);
          if (singleCourseRes.data) {
            mergeCoursesIntoStore([singleCourseRes.data]);
          }
        } catch (e) {}
      }
      expandedIds.value.clear();
      expandOperatingBranch("course", props.initialCourseId);
      selectedId.value = props.initialCourseId;
      await loadChaptersForCourse(props.initialCourseId, true);
      const courseChaps = chaptersMap.value[props.initialCourseId] || [];
      for (const ch of courseChaps) {
        expandedIds.value.add(ch.id);
      }
      saveTreeStateToStorage();
    } else if (expandTarget) {
      expandedIds.value.clear();
      expandOperatingBranch(expandTarget.type, expandTarget.id, expandTarget.parentId);
      saveTreeStateToStorage();
    } else if (expandedIds.value.size > 0) {
      // Khôi phục dữ liệu khóa học & chương cho các nhánh đang mở sẵn trước đó
      const expandedProgs = programs.value.filter((p) => expandedIds.value.has(p.id));
      for (const p of expandedProgs) {
        if (p.domainId) expandedIds.value.add(p.domainId);
      }
      if (expandedProgs.length > 0) {
        await Promise.all(expandedProgs.map((p) => ensureProgramCoursesLoaded(p.id, true)));
      }
      const expandedCourseIds: string[] = [];
      for (const p of expandedProgs) {
        const progCourses = getCoursesForProgram(p.id);
        for (const c of progCourses) {
          if (expandedIds.value.has(c.id)) {
            expandedCourseIds.push(c.id);
          }
        }
      }
      for (const c of courses.value) {
        if (expandedIds.value.has(c.id) && !expandedCourseIds.includes(c.id)) {
          expandedCourseIds.push(c.id);
        }
      }
      if (expandedCourseIds.length > 0) {
        await Promise.all(expandedCourseIds.map((cId) => loadChaptersForCourse(cId, true)));
      }
    }
  } catch (error) {
    console.error("Lỗi khi tải cấu trúc đào tạo:", error);
    showToast("Không thể tải cấu trúc cây đào tạo", "error");
  } finally {
    loading.value = false;
  }
}

async function loadChaptersForCourse(courseId: string, forceReload = false) {
  if (
    !courseId ||
    (!forceReload && chaptersMap.value[courseId] !== undefined) ||
    loadingCourseChaptersMap.value[courseId]
  ) {
    return;
  }
  loadingCourseChaptersMap.value = { ...loadingCourseChaptersMap.value, [courseId]: true };
  try {
    const chapters = await trainingApi.chapters(courseId);
    chaptersMap.value = {
      ...chaptersMap.value,
      [courseId]: chapters || [],
    };
  } catch (err) {
    console.error(`Lỗi tải chương cho khóa ${courseId}:`, err);
    chaptersMap.value = {
      ...chaptersMap.value,
      [courseId]: [],
    };
  } finally {
    loadingCourseChaptersMap.value = { ...loadingCourseChaptersMap.value, [courseId]: false };
  }
}

// Helpers for relationships
function getProgramsForDomain(domainId: string) {
  return programs.value.filter((p) => p.domainId === domainId && p.status !== "DELETED");
}

function getCoursesForProgram(programId: string) {
  if (programCoursesMap.value[programId] !== undefined && !searchQuery.value.trim()) {
    return programCoursesMap.value[programId].filter((c) => c.status !== "DELETED");
  }
  return courses.value.filter((c) => c.programId === programId && c.status !== "DELETED");
}

function getChaptersForCourse(courseId: string) {
  return (chaptersMap.value[courseId] || []).filter((ch) => ch.status !== "DELETED");
}

// Expand / Collapse (with On-Demand Lazy Fetch)
function isExpanded(id: string) {
  return expandedIds.value.has(id);
}

function toggleNode(id: string) {
  if (expandedIds.value.has(id)) {
    expandedIds.value.delete(id);
  } else {
    expandedIds.value.add(id);
    // Nếu mở rộng 1 Chương trình -> Lazy fetch trang đầu (6 khóa học) của Chương trình đó
    if (programs.value.some((p) => p.id === id)) {
      ensureProgramCoursesLoaded(id);
    }
    // Nếu mở rộng 1 Khóa học -> Lazy fetch danh sách Chương của đúng Khóa học đó
    else if (courses.value.some((c) => c.id === id)) {
      loadChaptersForCourse(id);
    }
  }
  saveTreeStateToStorage();
}

function toggleAllNodes(expand: boolean) {
  if (!expand) {
    expandedIds.value.clear();
  } else {
    // Chỉ mở rộng các Lĩnh vực & Chương trình thuộc TRANG HIỆN TẠI để tránh query ồ ạt
    for (const d of domains.value) {
      expandedIds.value.add(d.id);
      const progs = getProgramsForDomain(d.id);
      for (const p of progs) {
        expandedIds.value.add(p.id);
        ensureProgramCoursesLoaded(p.id);
      }
    }
  }
  saveTreeStateToStorage();
}

// Select node & toggle tree (expand/collapse)
function selectNode(type: string, data: any, parent?: any, grandparent?: any) {
  selectedId.value = data.id;
  emit("select", { type, data, parent: parent || grandparent });
  toggleNode(data.id);
}

// Open Inline Add Modal
function extractUnitsFromObject(obj: any): { mandatory: string[]; optional: string[] } {
  if (!obj) return { mandatory: [], optional: [] };
  const mandatory: string[] = [];
  const optional: string[] = [];
  if (obj.mandatoryUnitIds) {
    const list = Array.isArray(obj.mandatoryUnitIds) ? obj.mandatoryUnitIds : Array.from(obj.mandatoryUnitIds);
    for (const u of list) {
      if (u && !mandatory.includes(String(u))) mandatory.push(String(u));
    }
  }
  if (obj.optionalUnitIds) {
    const list = Array.isArray(obj.optionalUnitIds) ? obj.optionalUnitIds : Array.from(obj.optionalUnitIds);
    for (const u of list) {
      if (u && !optional.includes(String(u))) optional.push(String(u));
    }
  }
  if (obj.unitGrants && typeof obj.unitGrants === "object") {
    for (const [uId, grant] of Object.entries(obj.unitGrants)) {
      if (String(grant).toUpperCase() === "MANDATORY") {
        if (!mandatory.includes(uId)) mandatory.push(uId);
      } else {
        if (!optional.includes(uId)) optional.push(uId);
      }
    }
  }
  if (mandatory.length === 0 && optional.length === 0 && obj.unitIds) {
    const list = Array.isArray(obj.unitIds) ? obj.unitIds : Array.from(obj.unitIds);
    for (const u of list) {
      if (u && !optional.includes(String(u))) optional.push(String(u));
    }
  }
  return { mandatory, optional };
}



const isCreatingLessonDirectly = ref(false);

async function createLessonDirectly(chapter: any, course?: any) {
  try {
    const courseId =
      course?.id ||
      chapter?.courseId ||
      chapter?.course_id ||
      (courses.value as any[]).find((c: any) => c.chapters?.some((ch: any) => ch.id === chapter?.id))?.id;
    if (!chapter?.id) {
      showToast("Không tìm thấy thông tin chương học!", "error");
      return;
    }
    isCreatingLessonDirectly.value = true;
    const initialTitle = "Bài học mới";
    const initialId =
      typeof crypto !== "undefined" && crypto.randomUUID
        ? crypto.randomUUID()
        : Date.now().toString(36) + Math.random().toString(36).substring(2);

    const saved = await adminApi.createLesson({
      id: initialId,
      status: "DRAFT",
      title: initialTitle,
      hint: initialTitle,
      chapterId: chapter.id,
      courseId: courseId || "",
      videoUrl: "",
      videoSource: "URL",
      documents: [],
      procedureSteps: [],
    });

    // Lưu trạng thái mở của chương, khóa học, chương trình, lĩnh vực trước khi sang màn soạn bài
    expandedIds.value.add(chapter.id);
    if (courseId) {
      expandedIds.value.add(courseId);
      const crs = course || courses.value.find((c) => c.id === courseId);
      if (crs?.programId) {
        expandedIds.value.add(crs.programId);
        const prog = programs.value.find((p) => p.id === crs.programId);
        if (prog?.domainId) {
          expandedIds.value.add(prog.domainId);
        }
      }
    }
    selectedId.value = chapter.id;
    saveTreeStateToStorage();

    emit("refresh");
    await router.push(`/admin/lessons/${encodeURIComponent(saved.id)}/edit`);
  } catch (err: any) {
    isCreatingLessonDirectly.value = false;
    const msg = err.response?.data?.message || "Lỗi khi tạo bài học!";
    showToast(msg, "error");
  }
}

function openAddModal(
  type: "domain" | "program" | "course" | "chapter" | "lesson",
  parentObj?: any,
  grandParentObj?: any
) {
  if (type === "lesson") {
    void createLessonDirectly(parentObj, grandParentObj);
    return;
  }
  ensureModalAuxiliaryDataLoaded();
  const parent = parentObj || grandParentObj;
  const resolvedParent =
    (parent?.id && domains.value.find((d) => d.id === parent.id)) ||
    (parent?.id && programs.value.find((p) => p.id === parent.id)) ||
    (parent?.id && courses.value.find((c) => c.id === parent.id)) ||
    parent;

  // Tự động kế thừa phân hệ & phân bổ đơn vị từ cấp cha
  let defaultModule = "";
  if (resolvedParent) {
    defaultModule =
      extractModuleFromParent(resolvedParent) ||
      (grandParentObj ? extractModuleFromParent(grandParentObj) : "");
  }

  let inheritedMandatoryUnits: string[] = [];
  let inheritedOptionalUnits: string[] = [];

  // Tự động kế thừa phân bổ đơn vị cho cả Chương trình (từ Lĩnh vực) và Khóa học (từ Chương trình / Lĩnh vực)
  if ((type === "program" || type === "course") && resolvedParent) {
    const parentUnits = extractUnitsFromObject(resolvedParent);
    if (parentUnits.mandatory.length > 0 || parentUnits.optional.length > 0) {
      inheritedMandatoryUnits = [...parentUnits.mandatory];
      inheritedOptionalUnits = [...parentUnits.optional];
    } else if (grandParentObj) {
      const grandParentUnits = extractUnitsFromObject(grandParentObj);
      inheritedMandatoryUnits = [...grandParentUnits.mandatory];
      inheritedOptionalUnits = [...grandParentUnits.optional];
    }
  }

  const initialCode = (typeof crypto !== "undefined" && crypto.randomUUID) ? crypto.randomUUID() : (Date.now().toString(36) + Math.random().toString(36).substring(2));

  const draftKey = getDraftKey(type, resolvedParent?.id);
  const existingDraft = addModalDrafts.value[draftKey];

  if (
    existingDraft &&
    (existingDraft.name?.trim() ||
      existingDraft.description?.trim() ||
      existingDraft.completionDeadline ||
      !existingDraft.autoCode)
  ) {
    hasRestoredDraft.value = true;
    addModal.value = {
      show: true,
      isEdit: false,
      editId: "",
      type,
      parentId: resolvedParent?.id,
      parentObj: resolvedParent,
      code: existingDraft.code || initialCode,
      name: existingDraft.name || "",
      autoCode: existingDraft.autoCode ?? true,
      businessModule: existingDraft.businessModule || defaultModule,
      level: existingDraft.level || "BASIC",
      description: existingDraft.description || "",
      status: existingDraft.status || "ACTIVE",
      mandatoryUnitIds:
        type === "chapter"
          ? []
          : existingDraft.mandatoryUnitIds || inheritedMandatoryUnits,
      optionalUnitIds:
        type === "chapter"
          ? []
          : existingDraft.optionalUnitIds || inheritedOptionalUnits,
      autoEnrollStaff: existingDraft.autoEnrollStaff ?? true,
      completionDeadline: existingDraft.completionDeadline || "",
    };
  } else {
    hasRestoredDraft.value = false;
    addModal.value = {
      show: true,
      isEdit: false,
      editId: "",
      type,
      parentId: resolvedParent?.id,
      parentObj: resolvedParent,
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
  }

  nextTick(() => {
    nameInputRef.value?.focus();
  });
}

function openEditModal(
  type: "domain" | "program" | "course" | "chapter" | "lesson",
  item: any,
  parentObj?: any,
  grandParentObj?: any
) {
  if (type === "lesson") {
    openLessonEditor(item.id);
    return;
  }
  ensureModalAuxiliaryDataLoaded();
  hasRestoredDraft.value = false;
  selectedId.value = item.id;
  emit("select", { type, data: item, parent: parentObj || grandParentObj });

  const parent = parentObj || grandParentObj;
  let pId = "";
  if (type === "program") {
    pId = item.domainId || parent?.id || "";
  } else if (type === "course") {
    pId = item.programId || parent?.id || "";
  } else if (type === "chapter") {
    pId = item.courseId || parent?.id || "";
  }

  const resolvedParent =
    (pId && domains.value.find((d) => d.id === pId)) ||
    (pId && programs.value.find((p) => p.id === pId)) ||
    (pId && courses.value.find((c) => c.id === pId)) ||
    parent;

  let mandatoryUnits: string[] = [];
  let optionalUnits: string[] = [];

  if (item.mandatoryUnitIds && Array.isArray(item.mandatoryUnitIds)) {
    mandatoryUnits = [...item.mandatoryUnitIds];
  } else if (item.mandatoryUnitIds) {
    mandatoryUnits = Array.from(item.mandatoryUnitIds);
  }

  if (item.optionalUnitIds && Array.isArray(item.optionalUnitIds)) {
    optionalUnits = [...item.optionalUnitIds];
  } else if (item.optionalUnitIds) {
    optionalUnits = Array.from(item.optionalUnitIds);
  } else if (item.unitIds && Array.isArray(item.unitIds)) {
    optionalUnits = item.unitIds.filter((u: string) => !mandatoryUnits.includes(u));
  }

  // Kế thừa đơn vị: CHỈ cấp Khóa học mới kế thừa nếu chưa có đơn vị riêng
  if (type === "course" && mandatoryUnits.length === 0 && optionalUnits.length === 0 && resolvedParent) {
    if (resolvedParent?.mandatoryUnitIds) mandatoryUnits = [...resolvedParent.mandatoryUnitIds];
    if (resolvedParent?.optionalUnitIds) optionalUnits = [...resolvedParent.optionalUnitIds];
    else if (resolvedParent?.unitIds) optionalUnits = [...resolvedParent.unitIds];
  } else if (type === "chapter") {
    mandatoryUnits = [];
    optionalUnits = [];
  }

  const defaultModule =
    item.businessModule ||
    (resolvedParent ? extractModuleFromParent(resolvedParent) : "") ||
    (grandParentObj ? extractModuleFromParent(grandParentObj) : "");

  addModal.value = {
    show: true,
    isEdit: true,
    editId: item.id,
    type,
    parentId: pId || resolvedParent?.id,
    parentObj: resolvedParent,
    code: item.code || item.id || "",
    name: item.name || item.title || "",
    autoCode: false,
    businessModule: defaultModule,
    level: item.level || "BASIC",
    description: item.description || item.hint || "",
    status: item.status || "ACTIVE",
    mandatoryUnitIds: mandatoryUnits,
    optionalUnitIds: optionalUnits,
    autoEnrollStaff: false,
    completionDeadline: item.completionDeadline || "",
    version: item.version,
  };

  nextTick(() => {
    nameInputRef.value?.focus();
  });
}

// Submit Create or Edit Node
async function submitAddModal() {
  const { type, parentId, isEdit, editId } = addModal.value;
  const name = addModal.value.name;
  if (!name || !name.trim()) return;
  const code = (addModal.value.code && addModal.value.code.trim()) ? addModal.value.code.trim() : (typeof crypto !== "undefined" && crypto.randomUUID ? crypto.randomUUID() : (Date.now().toString(36) + Math.random().toString(36).substring(2)));

  submitting.value = true;
  let savedNodeId = isEdit ? editId : code.trim().toUpperCase();
  try {
    if (type === "domain") {
      const payload: any = {
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
        status: addModal.value.status || "ACTIVE",
      };
      if (isEdit) {
        payload.id = editId;
      }
      const res = await trainingApi.saveDomain(payload);
      if (res?.id) savedNodeId = res.id;
      showToast(isEdit ? "Cập nhật lĩnh vực đào tạo thành công!" : "Tạo lĩnh vực đào tạo thành công!", "success");
    } else if (type === "program") {
      const payload: any = {
        domainId: parentId,
        code: code.trim().toUpperCase(),
        name: name.trim(),
        description: addModal.value.description.trim() || undefined,
        businessModule: addModal.value.businessModule || undefined,
        level: "BASIC",
        status: addModal.value.status || "ACTIVE",
        mandatoryUnitIds: addModal.value.mandatoryUnitIds,
        optionalUnitIds: addModal.value.optionalUnitIds,
        unitIds: [
          ...addModal.value.mandatoryUnitIds,
          ...addModal.value.optionalUnitIds,
        ],
        autoEnrollStaff: addModal.value.autoEnrollStaff,
      };
      if (isEdit) {
        payload.id = editId;
      }
      const res = await trainingApi.saveProgram(payload);
      if (res?.id) savedNodeId = res.id;
      const staffMsg =
        !isEdit && addModal.value.mandatoryUnitIds.length && affectedStaff.value.length
          ? ` Đã tự động ghi danh ${affectedStaff.value.length} học viên!`
          : "";
      showToast(isEdit ? "Cập nhật chương trình đào tạo thành công!" : `Tạo chương trình đào tạo thành công!${staffMsg}`, "success");
    } else if (type === "course") {
      const payload: any = {
        programId: parentId,
        code: code.trim().toUpperCase(),
        name: name.trim(),
        description: addModal.value.description.trim() || undefined,
        businessModule: addModal.value.businessModule || undefined,
        level: addModal.value.level || "BASIC",
        completionDeadline: addModal.value.completionDeadline || null,
        iconName: "BookOpen",
        mandatoryUnitIds: addModal.value.mandatoryUnitIds,
        optionalUnitIds: addModal.value.optionalUnitIds,
        unitIds: [
          ...addModal.value.mandatoryUnitIds,
          ...addModal.value.optionalUnitIds,
        ],
      };
      if (isEdit) {
        payload.id = editId;
        payload.version = addModal.value.version;
      }
      const res = await trainingApi.saveCourse(payload);
      if (res?.id) savedNodeId = res.id;
      showToast(isEdit ? "Cập nhật khóa học thành công!" : "Tạo khóa học thành công!", "success");
    } else if (type === "chapter") {
      const payload: any = {
        code: code.trim().toUpperCase(),
        title: name.trim(),
        description: addModal.value.description.trim() || undefined,
      };
      if (isEdit) {
        payload.id = editId;
        payload.version = addModal.value.version;
      }
      const res = await trainingApi.saveChapter(parentId!, payload);
      if (res?.id) savedNodeId = res.id;
      showToast(isEdit ? "Cập nhật chương thành công!" : "Tạo chương thành công!", "success");
    } else if (type === "lesson") {
      let courseId =
        addModal.value.parentObj?.courseId ||
        addModal.value.parentObj?.course?.id ||
        "";
      if (!courseId && parentId) {
        for (const [cId, chaps] of Object.entries(chaptersMap.value)) {
          if (chaps.some((ch) => ch.id === parentId)) {
            courseId = cId;
            break;
          }
        }
      }
      if (isEdit) {
        const existing = await courseApi.getLessonById(editId!);
        await adminApi.updateLesson(editId!, lessonMetadataUpdate(existing, {
          title: name.trim(),
          hint: addModal.value.description.trim() || name.trim(),
          chapterId: parentId!,
          courseId,
        }, addModal.value.version));
        showToast("Cập nhật bài học thành công!", "success");
      } else {
        const saved = await adminApi.createLesson({
          id: code.trim().toUpperCase(),
          status: "DRAFT",
          title: name.trim(),
          hint: addModal.value.description.trim() || name.trim(),
          chapterId: parentId!,
          courseId: courseId,
          videoUrl: "",
          videoSource: "URL",
          documents: [],
          procedureSteps: [],
        });
        savedNodeId = saved.id;
        showToast("Tạo bài học thành công!", "success");
      }
    }

    clearDraft(type, parentId);
    hasRestoredDraft.value = false;
    addModal.value.show = false;
    await loadTreeData({ type, id: savedNodeId, parentId });
    emit("refresh");
    if (type === "lesson" && !isEdit && savedNodeId)
      await router.push(`/admin/lessons/${encodeURIComponent(savedNodeId)}/edit`);
  } catch (error: any) {
    const msg = error.response?.data?.message || (isEdit ? "Lỗi khi cập nhật!" : "Lỗi khi tạo mục mới!");
    showToast(msg, "error");
  } finally {
    submitting.value = false;
  }
}

// Delete handling
async function promptDeleteNode(
  type: "domain" | "program" | "course" | "chapter" | "lesson",
  item: any,
  parentObj?: any,
  grandParentObj?: any
) {
  const name = item.title || item.name || item.code || "";
  let typeLabel = "bài học";
  let notes: string[] = [];

  if (type === "domain") {
    typeLabel = "lĩnh vực";
    const progs = getProgramsForDomain(item.id);
    notes = [
      `Lĩnh vực này hiện có ${progs.length} chương trình đào tạo.`,
      `Xóa lĩnh vực sẽ đồng thời xóa toàn bộ chương trình và khóa học bên trong.`,
    ];
  } else if (type === "program") {
    typeLabel = "chương trình đào tạo";
    const totalCrss = getProgramTotalCoursesCount(item.id);
    notes = [
      `Chương trình này hiện có ${totalCrss} khóa học.`,
      `Xóa chương trình sẽ đồng thời xóa toàn bộ khóa học bên trong.`,
    ];
  } else if (type === "course") {
    typeLabel = "khóa học";
    if (chaptersMap.value[item.id] === undefined) {
      await loadChaptersForCourse(item.id);
    }
    const chaps = getChaptersForCourse(item.id);
    notes = [
      `Khóa học này hiện có ${chaps.length} chương.`,
      `Xóa khóa học sẽ đồng thời xóa toàn bộ chương và bài học bên trong.`,
    ];
  } else if (type === "chapter") {
    typeLabel = "chương";
    const activeLessons = (item.lessons || []).filter((l: any) => l.status !== "DELETED");
    notes = [
      `Chương này hiện có ${activeLessons.length} bài học.`,
      `Xóa chương sẽ đồng thời xóa toàn bộ bài học bên trong.`,
    ];
  } else if (type === "lesson") {
    typeLabel = "bài học";
    const docCount =
      (item.documents || []).length +
      (item.procedureSteps || []).length +
      (item.videoUrl ? 1 : 0);
    notes = [
      `Bài học này hiện có ${docCount} nội dung học liệu${item.hasQuiz ? " và 1 bài kiểm tra" : ""}.`,
      `Xóa bài học sẽ đồng thời xóa toàn bộ học liệu và bài kiểm tra bên trong.`,
    ];
  }

  deleteConfirm.value = {
    show: true,
    type,
    id: item.id,
    name,
    parentId: parentObj?.id || item.programId || item.courseId || item.chapterId || item.domainId || "",
    grandParentId: grandParentObj?.id || item.courseId || "",
    title: `Xác nhận xóa ${typeLabel}`,
    message: `Bạn có chắc chắn muốn xóa ${typeLabel} "${name}"?`,
    notes,
    busy: false,
  };
}

function closeDeleteModal() {
  if (deleteConfirm.value.busy) return;
  deleteConfirm.value.show = false;
}

async function executeDeleteNode() {
  const { type, id, name, parentId, grandParentId } = deleteConfirm.value;
  if (!id) return;

  deleteConfirm.value.busy = true;
  try {
    if (type === "domain") {
      await trainingApi.deleteDomain(id);
      const removedProgIds = new Set(getProgramsForDomain(id).map((p) => p.id));
      domains.value = domains.value.filter((d) => d.id !== id);
      programs.value = programs.value.filter((p) => p.domainId !== id);
      courses.value = courses.value.filter((c) => !c.programId || !removedProgIds.has(c.programId));
      expandedIds.value.delete(id);
      await loadTreeData();
      showToast(`Đã xóa lĩnh vực "${name}" thành công!`, "success");
    } else if (type === "program") {
      await trainingApi.deleteProgram(id);
      programs.value = programs.value.filter((p) => p.id !== id);
      courses.value = courses.value.filter((c) => c.programId !== id);
      const nextProgCourses = { ...programCoursesMap.value };
      delete nextProgCourses[id];
      programCoursesMap.value = nextProgCourses;
      const nextProgTotals = { ...programTotalCoursesMap.value };
      delete nextProgTotals[id];
      programTotalCoursesMap.value = nextProgTotals;
      expandedIds.value.delete(id);
      await loadTreeData();
      showToast(`Đã xóa chương trình "${name}" thành công!`, "success");
    } else if (type === "course") {
      const targetCourse = courses.value.find((c) => c.id === id);
      const progId = parentId || targetCourse?.programId || "";
      await adminApi.deleteCourse(id);
      courses.value = courses.value.filter((c) => c.id !== id);
      const nextChaps = { ...chaptersMap.value };
      delete nextChaps[id];
      chaptersMap.value = nextChaps;
      expandedIds.value.delete(id);
      if (progId) {
        if (programCoursesMap.value[progId]) {
          programCoursesMap.value = {
            ...programCoursesMap.value,
            [progId]: programCoursesMap.value[progId].filter((c) => c.id !== id),
          };
        }
        await ensureProgramCoursesLoaded(progId, true);
      }
      showToast(`Đã xóa khóa học "${name}" thành công!`, "success");
    } else if (type === "chapter") {
      await trainingApi.deleteChapter(id);
      let courseId = parentId || "";
      if (!courseId) {
        for (const [cId, chaps] of Object.entries(chaptersMap.value)) {
          if (chaps.some((ch) => ch.id === id)) {
            courseId = cId;
            break;
          }
        }
      }
      expandedIds.value.delete(id);
      if (courseId) {
        if (chaptersMap.value[courseId]) {
          chaptersMap.value = {
            ...chaptersMap.value,
            [courseId]: chaptersMap.value[courseId].filter((ch) => ch.id !== id),
          };
        }
        await loadChaptersForCourse(courseId, true);
      }
      showToast(`Đã xóa chương "${name}" thành công!`, "success");
    } else if (type === "lesson") {
      await trainingApi.deleteLesson(id);
      let courseId = grandParentId || "";
      if (!courseId) {
        for (const [cId, chaps] of Object.entries(chaptersMap.value)) {
          if (chaps.some((ch) => (ch.lessons || []).some((l) => l.id === id))) {
            courseId = cId;
            break;
          }
        }
      }
      expandedIds.value.delete(id);
      if (courseId) {
        if (chaptersMap.value[courseId]) {
          chaptersMap.value = {
            ...chaptersMap.value,
            [courseId]: chaptersMap.value[courseId].map((ch) => ({
              ...ch,
              lessons: (ch.lessons || []).filter((l) => l.id !== id),
            })),
          };
        }
        await loadChaptersForCourse(courseId, true);
      }
      showToast(`Đã xóa bài học "${name}" thành công!`, "success");
    }

    deleteConfirm.value.show = false;
    emit("refresh");
  } catch (error: any) {
    const msg = error.response?.data?.message || "Không thể xóa nội dung này. Vui lòng kiểm tra lại!";
    showToast(msg, "error");
  } finally {
    deleteConfirm.value.busy = false;
  }
}

// Actions
function previewCourse(courseId: string) {
  saveTreeStateToStorage();
  router.push(`/courses/${encodeURIComponent(courseId)}`);
}

function openLessonEditor(lessonId: string) {
  saveTreeStateToStorage();
  router.push(`/admin/lessons/${encodeURIComponent(lessonId)}/edit`);
}

function editLessonBlock(lesson: Lesson, blockType: string) {
  saveTreeStateToStorage();
  router.push({
    path: `/admin/lessons/${encodeURIComponent(lesson.id)}/edit`,
    query: { tab: blockType },
  });
}

// 2-Tier Tree Pagination with DB-level Domains
const treeDomainPage = ref(1);
const treeDomainPageSize = ref(5);
const treeDomainTotalPages = ref(1);
const treeDomainTotalElements = ref(0);

const totalActiveProgramsCount = computed(() =>
  programs.value.filter((p) => p.status !== "DELETED").length
);

const TREE_STATE_STORAGE_KEY = "cbs_curriculum_tree_state_v1";
let isRestoringTreeState = false;

function saveTreeStateToStorage() {
  if (isRestoringTreeState) return;
  try {
    sessionStorage.setItem(
      TREE_STATE_STORAGE_KEY,
      JSON.stringify({
        expandedIds: Array.from(expandedIds.value),
        selectedId: selectedId.value,
        treeDomainPage: treeDomainPage.value,
        treeDomainPageSize: treeDomainPageSize.value,
      })
    );
  } catch {
    // ignore storage errors
  }
}

function loadTreeStateFromStorage(): boolean {
  try {
    const raw = sessionStorage.getItem(TREE_STATE_STORAGE_KEY);
    if (!raw) return false;
    const parsed = JSON.parse(raw);
    isRestoringTreeState = true;
    if (Array.isArray(parsed.expandedIds)) {
      expandedIds.value = new Set(parsed.expandedIds);
    }
    if (parsed.selectedId) {
      selectedId.value = parsed.selectedId;
    }
    if (typeof parsed.treeDomainPageSize === "number" && parsed.treeDomainPageSize > 0) {
      treeDomainPageSize.value = parsed.treeDomainPageSize;
    }
    if (typeof parsed.treeDomainPage === "number" && parsed.treeDomainPage > 0) {
      treeDomainPage.value = parsed.treeDomainPage;
    }
    isRestoringTreeState = false;
    return true;
  } catch {
    isRestoringTreeState = false;
    return false;
  }
}

watch(treeDomainPageSize, () => {
  if (isRestoringTreeState) return;
  treeDomainPage.value = 1;
  saveTreeStateToStorage();
  loadTreeData();
});

watch(treeDomainPage, () => {
  if (isRestoringTreeState) return;
  saveTreeStateToStorage();
  loadTreeData();
});

// Tier-2 In-Branch Course Pagination inside a Program (Server-Side Lazy Fetch)
function getCourseBranchLimit(programId: string): number {
  return courseBranchLimits.value[programId] || DEFAULT_COURSE_BRANCH_SIZE;
}

function getVisibleCoursesForProgram(programId: string): Course[] {
  const all = getCoursesForProgram(programId);
  if (searchQuery.value.trim()) {
    return all;
  }
  return all.slice(0, getCourseBranchLimit(programId));
}

async function expandCourseBranchLimit(programId: string) {
  const total = getProgramTotalCoursesCount(programId);
  const current = getCourseBranchLimit(programId);
  const nextLimit = Math.min(total, current + DEFAULT_COURSE_BRANCH_SIZE);
  courseBranchLimits.value[programId] = nextLimit;

  const loadedCount = (programCoursesMap.value[programId] || []).length;
  if (nextLimit > loadedCount && programCourseHasNextMap.value[programId]) {
    await fetchMoreCoursesForProgram(programId);
  }
}

function resetCourseBranchLimit(programId: string) {
  courseBranchLimits.value[programId] = DEFAULT_COURSE_BRANCH_SIZE;
}

// ================= RICH HOVER DESCRIPTION TOOLTIP =================
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
  extraMeta: string;
}>({
  visible: false,
  x: 0,
  y: 0,
  type: "domain",
  typeLabel: "Lĩnh vực đào tạo",
  code: "",
  title: "",
  description: "",
  businessModule: "",
  level: "",
  deadline: "",
  extraMeta: "",
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

function stripLeadingIndexPrefix(text: string | undefined, prefixWord: string): string {
  if (!text) return "";
  const regex = new RegExp(`^${prefixWord}\\s*\\d+\\s*[:.-]\\s*`, "i");
  return text.replace(regex, "").trim() || text;
}

let nodeTooltipTimer: ReturnType<typeof setTimeout> | null = null;

function showNodeTooltip(
  e: MouseEvent,
  type: "domain" | "program" | "course" | "chapter" | "lesson",
  rawObj: any,
  parentObj?: any
) {
  if (!rawObj) return;
  if (nodeTooltipTimer) clearTimeout(nodeTooltipTimer);

  const target = (e.currentTarget || e.target) as HTMLElement;
  const rect = target.getBoundingClientRect();

  nodeTooltipTimer = setTimeout(() => {
    const popoverWidth = 340;
    let left = rect.left;
    if (left + popoverWidth > window.innerWidth - 20) {
      left = Math.max(16, window.innerWidth - popoverWidth - 20);
    }
    let top = rect.bottom + 8;
    if (top + 190 > window.innerHeight) {
      top = Math.max(16, rect.top - 180);
    }

    const typeLabelMap: Record<string, string> = {
      domain: "Lĩnh vực đào tạo",
      program: "Chương trình đào tạo",
      course: "Khóa học nghiệp vụ",
      chapter: "Chương học",
      lesson: "Bài giảng",
    };

    let description = rawObj.description || rawObj.hint || "";
    let extraMeta = "";
    if (type === "chapter") {
      const lessonCount = (rawObj.lessons || []).filter((l: any) => l.status !== "DELETED").length;
      if (!description) {
        description = `Chương học thuộc khóa "${parentObj?.name || "Khóa học hiện tại"}", bao gồm ${lessonCount} bài giảng nghiệp vụ.`;
      }
      extraMeta = `${lessonCount} bài giảng`;
    } else if (type === "lesson") {
      const parts: string[] = [];
      if (rawObj.videoUrl) parts.push("Video");
      if (rawObj.documents?.length) parts.push(`${rawObj.documents.length} Tài liệu`);
      if (rawObj.procedureSteps?.length) parts.push(`${rawObj.procedureSteps.length} Bước quy trình`);
      if (rawObj.hasQuiz) parts.push("Quiz đánh giá");
      extraMeta = parts.length ? parts.join(" • ") : "Chưa đính kèm học liệu";
    }

    hoverTooltip.value = {
      visible: true,
      x: left,
      y: top,
      type,
      typeLabel: typeLabelMap[type] || "Nội dung đào tạo",
      code: rawObj.code || "",
      title: rawObj.name || rawObj.title || "",
      description,
      businessModule: rawObj.businessModule || parentObj?.businessModule || "",
      level: type === "course" ? rawObj.level || "" : "",
      deadline: type === "course" ? rawObj.completionDeadline || "" : "",
      extraMeta,
    };
  }, 220);
}

function hideNodeTooltip() {
  if (nodeTooltipTimer) {
    clearTimeout(nodeTooltipTimer);
    nodeTooltipTimer = null;
  }
  hoverTooltip.value.visible = false;
}

// On-Demand Search: debounced DB query across domains and programs
let treeSearchDebounceTimer: any = null;
watch(searchQuery, () => {
  if (treeSearchDebounceTimer) clearTimeout(treeSearchDebounceTimer);
  treeSearchDebounceTimer = setTimeout(() => {
    treeDomainPage.value = 1;
    loadTreeData();
  }, 300);
});

onMounted(() => {
  loadTreeStateFromStorage();
  loadTreeData();
});

watch(
  () => props.initialCourseId,
  async (newCourseId) => {
    if (newCourseId && courses.value.length > 0) {
      expandedIds.value.clear();
      expandOperatingBranch("course", newCourseId);
      selectedId.value = newCourseId;
      await loadChaptersForCourse(newCourseId);
      const courseChaps = chaptersMap.value[newCourseId] || [];
      for (const ch of courseChaps) {
        expandedIds.value.add(ch.id);
      }
    }
  }
);
</script>

<style scoped>
/* ==========================================================================
   IMPECCABLE DESIGN SYSTEM: CURRICULUM TREE STUDIO (7 LEVELS)
   Synchronized with global Light & Dark Mode tokens + BIDV Emerald Brand
   ========================================================================== */

.curriculum-tree-studio {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--bg-card);
  border-radius: 14px;
  border: 1px solid var(--border-color);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
  font-family: inherit;
  color: var(--text-main);
}

/* Header */
.tree-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.95rem 1.25rem;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-main);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 0.65rem;
}

.tree-title {
  margin: 0;
  font-size: 0.96rem;
  font-weight: 700;
  color: var(--text-main);
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.title-icon {
  color: var(--primary-ink);
}

.tree-badge {
  font-size: 0.72rem;
  padding: 0.18rem 0.6rem;
  background: var(--primary-light);
  color: var(--primary-ink);
  border: 1px solid var(--success-border);
  border-radius: 9999px;
  font-weight: 600;
}

.header-actions {
  display: flex;
  gap: 0.5rem;
}

.btn-studio {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.48rem 0.9rem;
  border-radius: 10px;
  font-size: 0.82rem;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.16s cubic-bezier(0.22, 1, 0.36, 1);
}

.btn-studio.btn-primary {
  background: var(--primary-color);
  color: #ffffff;
  border-color: var(--primary-color);
  box-shadow: 0 2px 6px rgba(0, 91, 65, 0.2);
}

.btn-studio.btn-primary:hover {
  background: var(--primary-hover);
  border-color: var(--primary-hover);
}

/* Node action button styled like header add button */
.btn-studio-node {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.32rem;
  padding: 0 0.65rem;
  height: 26px;
  line-height: 1;
  border-radius: 6px;
  font-size: 0.74rem;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.15s ease;
  white-space: nowrap;
  box-sizing: border-box;
}

.btn-studio-node.btn-primary-node {
  background: var(--primary-color);
  color: #ffffff;
}

.btn-studio-node.btn-primary-node:hover {
  background: var(--primary-hover);
}

.btn-studio.btn-ghost {
  background: var(--bg-card);
  color: var(--text-main);
  border: 1px solid var(--border-color);
}

.btn-studio.btn-ghost:hover {
  background: var(--primary-light);
  border-color: var(--primary-color);
  color: var(--primary-ink);
}

/* Search */
.tree-search-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  padding: 0.7rem 1.25rem;
  border-bottom: 1px solid var(--border-color);
  background: var(--bg-card);
}

.search-icon {
  position: absolute;
  left: 1.95rem;
  color: var(--text-muted);
  pointer-events: none;
}

.tree-search-input {
  width: 100%;
  padding: 0.52rem 2.2rem 0.52rem 2.3rem;
  background: var(--bg-main);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  color: var(--text-main);
  font-size: 0.84rem;
  outline: none;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, background-color 0.16s ease;
}

.tree-search-input:focus {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px var(--primary-light);
  background: var(--bg-card);
}

.clear-search-btn {
  position: absolute;
  right: 1.8rem;
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0.2rem;
}

.clear-search-btn:hover {
  color: var(--text-main);
}

/* Tree Scroll Container */
.tree-scroll-container {
  flex: 1;
  overflow-y: auto;
  padding: 0.85rem 0.9rem;
}

.tree-empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 3.5rem 1rem;
  color: var(--text-muted);
  text-align: center;
  gap: 0.75rem;
}

.spin-icon {
  animation: spin 1s linear infinite;
  color: var(--primary-ink);
}

@keyframes spin {
  100% {
    transform: rotate(360deg);
  }
}

.empty-icon {
  color: var(--primary-ink);
  opacity: 0.75;
}

/* Tree Structure */
.tree-root {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.tree-branch {
  position: relative;
  display: flex;
  flex-direction: column;
}

.tree-sub-tree {
  margin-left: 1.35rem;
  padding-left: 0.65rem;
  border-left: 1.5px dashed var(--border-color);
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-top: 0.2rem;
  margin-bottom: 0.3rem;
}

/* Node Styling */
.tree-node {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.55rem;
  padding: 0.35rem 0.75rem;
  border-radius: 10px;
  background: transparent;
  border: 1px solid transparent;
  cursor: pointer;
  transition: background-color 0.14s ease, border-color 0.14s ease;
  user-select: none;
  min-height: 40px;
  box-sizing: border-box;
}

.tree-node:hover {
  background: var(--primary-light);
  border-color: var(--success-border);
}

.tree-node.active {
  background: var(--primary-light);
  border: 1px solid var(--primary-color);
  color: var(--primary-ink);
}

/* Level specifics */
.domain-node {
  font-weight: 700;
  color: var(--text-main);
  background: var(--bg-main);
  border: 1px solid var(--border-color);
  margin-bottom: 0.15rem;
}

.domain-icon {
  color: var(--info-ink);
}

.program-node {
  font-weight: 600;
  color: var(--text-main);
}

.program-icon {
  color: var(--warning-ink);
}

.course-node {
  color: var(--text-main);
  font-weight: 500;
}

.course-icon {
  color: var(--success-ink);
}

.chapter-node {
  color: var(--text-muted);
  font-size: 0.85rem;
}

.chapter-icon {
  color: var(--purple-ink);
}

.lesson-node {
  color: var(--text-main);
  font-size: 0.84rem;
}

.lesson-icon {
  color: var(--danger-ink);
}

/* Toggle Chevron */
.toggle-chevron {
  display: flex;
  align-items: center;
  justify-content: center;
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 0;
  width: 22px;
  height: 22px;
  border-radius: 4px;
  flex-shrink: 0;
}

.toggle-chevron:hover {
  color: var(--primary-ink);
  background: var(--primary-light);
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
  gap: 0.5rem;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.node-title {
  overflow: hidden;
  text-overflow: ellipsis;
}

.node-code {
  font-size: 0.73rem;
  font-weight: 600;
  color: var(--primary-ink);
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  padding: 0.12rem 0.42rem;
  border-radius: 6px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.node-desc-tooltip-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--info-ink);
  opacity: 0.7;
  cursor: help;
  transition: opacity 0.15s ease, transform 0.15s ease;
  flex-shrink: 0;
}

.node-desc-tooltip-trigger:hover {
  opacity: 1;
  transform: scale(1.15);
}

.node-index {
  font-size: 0.78rem;
  color: var(--text-muted);
  font-weight: 600;
}

/* Badges */
.node-badge-group {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  margin-left: auto;
  flex-shrink: 0;
}

.count-tag {
  font-size: 0.72rem;
  font-weight: 600;
  padding: 0 0.55rem;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-subtle);
  color: var(--text-muted);
  border-radius: 9999px;
  border: 1px solid var(--border-color);
  box-sizing: border-box;
  line-height: 1;
}

.meta-tag {
  font-size: 0.7rem;
  padding: 0 0.55rem;
  height: 24px;
  border-radius: 9999px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  box-sizing: border-box;
  line-height: 1;
}

.module-tag {
  background: var(--info-surface);
  color: var(--info-ink);
  border: 1px solid var(--info-border);
}

.mandatory-tag {
  background: var(--success-surface);
  color: var(--success-ink);
  border: 1px solid var(--success-border);
  display: none;
}

.tree-node:hover .mandatory-tag {
  display: inline-flex;
}

.status-pill {
  font-size: 0.72rem;
  font-weight: 600;
  padding: 0 0.55rem;
  height: 24px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 9999px;
  background: var(--bg-subtle);
  color: var(--text-muted);
  border: 1px solid var(--border-color);
  box-sizing: border-box;
  line-height: 1;
}

.status-pill.published {
  background: var(--success-surface);
  color: var(--success-ink);
  border: 1px solid var(--success-border);
}

.status-pill.draft {
  background: var(--bg-subtle);
  color: var(--text-muted);
  border: 1px solid var(--border-color);
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
  width: 20px;
  height: 20px;
  border-radius: 5px;
  background: var(--bg-main);
  border: 1px solid var(--border-color);
}

.mini-block-badge.video {
  color: var(--info-ink);
}
.mini-block-badge.docs {
  color: var(--warning-ink);
}
.mini-block-badge.proc {
  color: var(--purple-ink);
}
.mini-block-badge.quiz {
  color: var(--success-ink);
}

/* Level 6 & 7: Blocks */
.content-blocks-tree {
  margin-left: 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.block-node {
  font-size: 0.79rem;
  padding: 0.35rem 0.6rem;
  color: var(--text-muted);
  border: 1px solid transparent;
  min-height: 30px;
}

.block-node:hover {
  background: var(--primary-light);
  color: var(--primary-ink);
}

.block-node.active {
  color: var(--primary-ink);
  font-weight: 600;
}

.block-icon {
  margin-right: 0.2rem;
}

.block-title {
  flex: 1;
}

.block-check {
  font-size: 0.74rem;
  color: var(--success-ink);
  font-weight: bold;
}

/* Hover Action Buttons */
.node-hover-actions {
  display: none;
  align-items: center;
  gap: 0.3rem;
  margin-left: 0.4rem;
  flex-shrink: 0;
  height: 26px;
  box-sizing: border-box;
}

.tree-node:hover .node-hover-actions {
  display: flex;
}

.hover-action-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 6px;
  color: var(--text-muted);
  cursor: pointer;
  transition: all 0.14s ease;
  box-sizing: border-box;
  padding: 0;
  flex-shrink: 0;
}

.hover-action-btn:hover {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: #ffffff;
}

.hover-action-btn.add-btn:hover {
  background: var(--primary-color);
  border-color: var(--primary-color);
}

.hover-action-btn.edit-btn:hover {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: #ffffff;
}

.hover-action-btn.delete-btn:hover {
  background: #dc2626;
  border-color: #dc2626;
  color: #ffffff;
}

.btn-studio-node.btn-editor-node {
  background: var(--info-surface);
  color: var(--info-ink);
  border: 1px solid var(--info-border);
}

.btn-studio-node.btn-editor-node:hover {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: #ffffff;
}

.btn-studio-node.btn-preview-node {
  background: var(--bg-card);
  color: var(--text-main);
  border: 1px solid var(--border-color);
}

.btn-studio-node.btn-preview-node:hover {
  border-color: var(--primary-color);
  color: var(--primary-ink);
  background: var(--primary-light);
}

.btn-studio-node.btn-publish-node {
  background: rgba(16, 185, 129, 0.14);
  color: #10b981;
  border: 1px solid rgba(16, 185, 129, 0.35);
}

.btn-studio-node.btn-publish-node:hover:not(:disabled) {
  background: #10b981;
  border-color: #10b981;
  color: #ffffff;
}

.btn-studio-node.btn-unpublish-node {
  background: rgba(245, 158, 11, 0.14);
  color: #f59e0b;
  border: 1px solid rgba(245, 158, 11, 0.35);
}

.btn-studio-node.btn-unpublish-node:hover:not(:disabled) {
  background: #f59e0b;
  border-color: #f59e0b;
  color: #ffffff;
}

.btn-tooltip {
  position: absolute;
  top: -26px;
  right: 0;
  background: var(--bg-card);
  color: var(--text-main);
  font-size: 0.7rem;
  padding: 0.18rem 0.45rem;
  border-radius: 6px;
  white-space: nowrap;
  pointer-events: none;
  opacity: 0;
  transition: opacity 0.15s ease;
  border: 1px solid var(--border-color);
  box-shadow: var(--shadow-sm);
  z-index: 10;
}

.hover-action-btn:hover .btn-tooltip {
  opacity: 1;
}

/* MODALS (Add / Edit / Delete) */
.delete-confirm-modal {
  width: 100%;
  max-width: 460px;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  padding: 1.25rem 1.5rem;
  box-shadow: var(--shadow-lg);
  overflow: hidden;
  color: var(--text-main);
}

.delete-modal-header {
  position: relative;
  justify-content: center !important;
  align-items: center !important;
  margin-bottom: 0.85rem !important;
  padding-bottom: 0.75rem !important;
}

.delete-modal-header .modal-title-box {
  text-align: center;
  width: 100%;
  padding: 0 1.75rem;
}

.delete-modal-header .modal-title-box h4 {
  margin: 0 !important;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--text-main);
}

.delete-modal-header .close-btn {
  position: absolute;
  right: 0;
  top: 0;
}

.delete-confirm-body {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  padding: 0.15rem 0 0.35rem;
}

.delete-main-question {
  margin: 0;
  font-size: 0.92rem;
  font-weight: 400;
  line-height: 1.5;
  color: var(--text-main);
}

.delete-notes-group {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.delete-note-item {
  margin: 0;
  font-size: 0.8rem;
  font-weight: 400;
  font-style: italic;
  line-height: 1.45;
  color: var(--text-muted);
}

.delete-modal-actions {
  justify-content: center !important;
  gap: 0.75rem;
}

.btn-delete-submit {
  background: #dc2626 !important;
  color: #ffffff !important;
  border: 1px solid #dc2626 !important;
}

.btn-delete-submit:hover:not(:disabled) {
  background: #b91c1c !important;
}

/* QUICK MODAL ENHANCED */
.tree-modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.55);
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
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 14px;
  padding: 1.35rem 1.5rem;
  box-shadow: var(--shadow-lg);
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  color: var(--text-main);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 1rem;
  padding-bottom: 0.7rem;
  border-bottom: 1px solid var(--border-color);
}

.modal-title-box h4 {
  margin: 0.25rem 0 0;
  font-size: 1.08rem;
  font-weight: 700;
  color: var(--text-main);
}

.modal-level-badge {
  font-size: 0.72rem;
  color: var(--primary-ink);
  background: var(--primary-light);
  border: 1px solid var(--success-border);
  padding: 0.14rem 0.55rem;
  border-radius: 9999px;
  font-weight: 600;
  display: inline-block;
}

.close-btn {
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 0.25rem;
  border-radius: 6px;
}

.close-btn:hover {
  color: var(--text-main);
  background: var(--bg-main);
}

.modal-draft-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.6rem 0.9rem;
  background: var(--warning-surface);
  border: 1px solid var(--warning-border);
  border-radius: 10px;
  margin-bottom: 0.85rem;
  font-size: 0.8rem;
}

.draft-banner-info {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  color: var(--warning-ink);
  font-weight: 600;
}

.draft-sparkle-icon {
  color: var(--warning-ink);
  flex-shrink: 0;
}

.btn-clear-draft {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  background: var(--bg-card);
  border: 1px solid var(--warning-border);
  color: var(--warning-ink);
  padding: 0.28rem 0.65rem;
  border-radius: 6px;
  font-size: 0.75rem;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s ease;
}

.btn-clear-draft:hover {
  background: var(--danger-surface);
  border-color: var(--danger-border);
  color: var(--danger-ink);
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
  font-size: 0.82rem;
  color: var(--text-main);
  font-weight: 600;
}

.form-control {
  padding: 0.6rem 0.8rem;
  font-size: 0.85rem;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 10px;
  color: var(--text-main);
  outline: none;
  transition: border-color 0.16s ease, box-shadow 0.16s ease;
}

.form-control:focus {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px var(--primary-light);
}

.form-control:disabled,
.form-control:read-only {
  background: var(--bg-subtle);
  color: var(--text-muted);
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
  background-color: var(--bg-subtle) !important;
  color: var(--text-muted) !important;
  cursor: not-allowed !important;
  border-color: var(--border-color) !important;
  opacity: 0.85;
}

.inherited-parent-display {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.6rem 0.8rem;
  background: var(--bg-main);
  border: 1px solid var(--border-color);
  border-radius: 10px;
}

.parent-chip-badge {
  font-weight: 700;
  color: var(--primary-ink);
  font-size: 0.84rem;
}

.inherited-units-box {
  background: var(--bg-main);
  border: 1px dashed var(--border-color);
  border-radius: 10px;
  padding: 0.75rem 0.85rem;
}

.inherited-units-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-top: 0.4rem;
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
  color: var(--primary-ink);
  background: var(--primary-light);
  padding: 0.15rem 0.55rem;
  border-radius: 9999px;
  border: 1px solid var(--success-border);
  letter-spacing: 0.3px;
}

.inherited-module-info {
  background: var(--bg-main);
  padding: 0.65rem 0.85rem;
  border-radius: 10px;
  border: 1px solid var(--border-color);
}

.inherited-module-display {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.35rem;
  flex-wrap: wrap;
}

.module-chip-badge {
  background: var(--primary-color);
  color: #ffffff;
  font-weight: 700;
  font-size: 0.72rem;
  padding: 0.15rem 0.5rem;
  border-radius: 6px;
  letter-spacing: 0.5px;
}

.module-title-text {
  font-size: 0.84rem;
  font-weight: 600;
  color: var(--text-main);
}

.inherited-tag {
  font-size: 0.72rem;
  color: var(--info-ink);
  background: var(--info-surface);
  border: 1px solid var(--info-border);
  padding: 0.1rem 0.45rem;
  border-radius: 6px;
  font-style: italic;
}

.auto-code-toggle {
  display: flex;
  align-items: center;
  gap: 0.35rem;
}

.auto-code-toggle input[type="checkbox"] {
  accent-color: var(--primary-color);
  cursor: pointer;
}

.toggle-text {
  font-size: 0.74rem !important;
  color: var(--primary-ink) !important;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 0.25rem;
  font-weight: 600;
}

.code-input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.code-input {
  flex: 1;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  letter-spacing: 0.5px;
}

.code-input.is-auto {
  border-color: var(--success-border);
}

.re-generate-btn,
.manual-edit-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.5rem 0.7rem;
  border-radius: 8px;
  font-size: 0.76rem;
  font-weight: 600;
  background: var(--bg-main);
  border: 1px solid var(--border-color);
  color: var(--text-main);
  cursor: pointer;
  white-space: nowrap;
}

.re-generate-btn:hover,
.manual-edit-btn:hover {
  background: var(--primary-light);
  border-color: var(--primary-color);
  color: var(--primary-ink);
}

.form-hint {
  font-size: 0.73rem;
  color: var(--text-muted);
}

/* Section Sub Title */
.section-sub-title {
  margin: 0;
  font-size: 0.86rem;
  font-weight: 700;
  color: var(--text-main);
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding-bottom: 0.4rem;
  border-bottom: 1px dashed var(--border-color);
}

/* Units Selection Section */
.units-section {
  background: var(--bg-main);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 0.9rem;
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

.mandatory-label,
.optional-label {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  color: var(--text-main) !important;
  font-weight: 600;
}

.badge-mandatory {
  font-size: 0.7rem;
  font-weight: 700;
  background: rgba(245, 158, 11, 0.16);
  color: #f59e0b;
  border: 1px solid rgba(245, 158, 11, 0.45);
  padding: 0.12rem 0.55rem;
  border-radius: 9999px;
  letter-spacing: 0.02em;
}

.badge-optional {
  font-size: 0.7rem;
  font-weight: 600;
  background: var(--primary-light);
  color: var(--primary-ink);
  border: 1px solid var(--success-border);
  padding: 0.12rem 0.5rem;
  border-radius: 9999px;
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
  padding: 0.22rem 0.55rem;
  border-radius: 6px;
  font-size: 0.74rem;
  font-weight: 600;
}

.unit-chip.mandatory-chip {
  background: rgba(245, 158, 11, 0.14);
  border: 1px solid rgba(245, 158, 11, 0.4);
  color: #f59e0b;
}

.unit-chip.optional-chip {
  background: var(--primary-light);
  border: 1px solid var(--success-border);
  color: var(--primary-ink);
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
  background: var(--danger-surface);
  border: 1px solid var(--danger-border);
  border-radius: 10px;
  padding: 0.7rem 0.9rem;
}

.auto-enroll-title {
  font-weight: 700;
  color: var(--danger-ink);
  font-size: 0.82rem;
}

.auto-enroll-alert.pending-units {
  background: var(--bg-main);
  color: var(--text-muted);
  border: 1px dashed var(--border-color);
}

.auto-enroll-wrapper {
  margin-top: 0.4rem;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  background: var(--danger-surface);
  border: 1px solid var(--danger-border);
  border-radius: 10px;
  padding: 0.6rem 0.8rem;
}

.auto-enroll-check-label {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  font-size: 0.78rem;
  color: var(--danger-ink);
  cursor: pointer;
  font-weight: 600;
}

.auto-enroll-check-label input[type="checkbox"] {
  accent-color: #dc2626;
}

.auto-enroll-alert {
  display: flex;
  align-items: flex-start;
  gap: 0.4rem;
  font-size: 0.75rem;
  color: var(--warning-ink);
  background: var(--warning-surface);
  border: 1px solid var(--warning-border);
  border-radius: 8px;
  padding: 0.45rem 0.65rem;
}

.auto-enroll-alert.empty-staff {
  color: var(--text-muted);
  background: var(--bg-main);
  border-color: var(--border-color);
}

.alert-icon {
  color: var(--warning-ink);
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
  color: var(--warning-ink);
}

/* Modal Actions */
.modal-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.6rem;
  margin-top: 0.5rem;
  padding-top: 0.85rem;
  border-top: 1px solid var(--border-color);
}

.actions-right {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-left: auto;
}

.btn-outline-info {
  background: var(--info-surface);
  border: 1px solid var(--info-border);
  color: var(--info-ink);
}

.btn-outline-info:hover {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: #ffffff;
}

.btn {
  padding: 0.55rem 1.05rem;
  font-size: 0.84rem;
  border-radius: 10px;
  border: 1px solid transparent;
  cursor: pointer;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  transition: all 0.15s ease;
}

.btn-primary {
  background: var(--primary-color);
  color: #ffffff;
}

.btn-primary:hover:not(:disabled) {
  background: var(--primary-hover);
}

.btn-primary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-secondary {
  background: var(--bg-main);
  border: 1px solid var(--border-color);
  color: var(--text-main);
}

.btn-secondary:hover {
  background: var(--primary-light);
  border-color: var(--primary-color);
  color: var(--primary-ink);
}

/* ==========================================================================
   2-TIER TREE PAGINATION (IN-BRANCH & ROOT FOOTER BAR)
   ========================================================================== */
.in-branch-pagination-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.45rem 0.75rem;
  border-radius: 8px;
  background: var(--bg-main);
  border: 1px dashed var(--border-color);
  margin-top: 0.25rem;
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

.tree-pagination-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.75rem 1.25rem;
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

.tree-inline-loading {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.48rem 0.85rem;
  margin-left: 1.5rem;
  font-size: 0.78rem;
  color: var(--text-muted);
  font-style: italic;
}
</style>

<!-- GLOBAL STYLE FOR TELEPORTED HOVER DESCRIPTION POPOVER (Light & Dark Mode Synchronized) -->
<style>
/* Tooltip Transition */
.tooltip-fade-enter-active,
.tooltip-fade-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.tooltip-fade-enter-from,
.tooltip-fade-leave-to {
  opacity: 0;
  transform: translateY(4px);
}

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

.impeccable-desc-popover .popover-type-badge.chapter {
  background: var(--warning-surface);
  color: var(--warning-ink);
  border: 1px solid var(--warning-border);
}

.impeccable-desc-popover .popover-type-badge.lesson {
  background: var(--danger-surface);
  color: var(--danger-ink);
  border: 1px solid var(--danger-border);
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
  flex-wrap: wrap;
}

.impeccable-desc-popover .popover-footer-meta strong {
  color: var(--text-main);
}

.fullscreen-loading-overlay {
  position: fixed;
  inset: 0;
  z-index: 999999;
  background: rgba(10, 16, 26, 0.78);
  backdrop-filter: blur(5px);
  display: flex;
  align-items: center;
  justify-content: center;
}
.loading-modal-card {
  background: var(--bg-surface, #1e293b);
  border: 1px solid var(--border-color, #334155);
  border-radius: 16px;
  padding: 2.2rem 3rem;
  text-align: center;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.65);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.9rem;
}
.loading-modal-card h4 {
  margin: 0;
  font-size: 1.25rem;
  font-weight: 700;
  color: #fff;
}
.loading-modal-card p {
  margin: 0;
  font-size: 0.9rem;
  color: #94a3b8;
}
.loading-spinner {
  color: #10b981;
}
</style>
