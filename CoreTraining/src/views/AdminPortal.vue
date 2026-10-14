<template>
  <div class="admin-portal-layout">
    <!-- Right Main Content Area -->
    <div class="admin-main-content">
      <header v-if="activeTab!=='overview'" class="admin-page-heading"><div><h1>{{ sectionHeading.title }}</h1><p>{{ sectionHeading.description }}</p></div></header>
      <!-- Loading State -->
      <div v-if="loading" class="loading-overlay">
        <div class="spinner"></div>
        <p>Đang tải dữ liệu cấu hình...</p>
      </div>

      <!-- Main Content Panel -->
      <div v-else class="admin-tab-content">
        <!-- 1. OVERVIEW / DASHBOARD TAB -->
        <div
          v-if="activeTab === 'overview'"
          class="tab-pane overview-pane animate-fade-in"
        >
          <!-- Statistics Cards Grid -->
          <div class="stats-grid">
            <div class="stat-card card">
              <div class="stat-icon-box users-bg">
                <Users class="stat-icon" />
              </div>
              <div class="stat-details">
                <span class="stat-label">Tổng số học viên</span>
                <h3>{{ users.length }}</h3>
              </div>
            </div>

            <div class="stat-card card">
              <div class="stat-icon-box online-bg">
                <div class="online-indicator-ping"></div>
                <Users class="stat-icon" />
              </div>
              <div class="stat-details">
                <span class="stat-label">Đang truy cập</span>
                <h3>
                  {{ activeSessions.length }}
                  <span class="active-badge-lbl">Trực tuyến</span>
                </h3>
              </div>
            </div>

            <div class="stat-card card">
              <div class="stat-icon-box lessons-bg">
                <BookOpen class="stat-icon" />
              </div>
              <div class="stat-details">
                <span class="stat-label">Tổng số bài học</span>
                <h3>{{ totalLessonsCount }}</h3>
              </div>
            </div>

            <div class="stat-card card">
              <div class="stat-icon-box questions-bg">
                <HelpCircle class="stat-icon" />
              </div>
              <div class="stat-details">
                <span class="stat-label">Tổng số câu hỏi</span>
                <h3>{{ totalQuestionsCount }}</h3>
              </div>
            </div>
          </div>

          <!-- Charts Row 1: Line Chart & Doughnut Chart -->
          <div class="charts-row">
            <!-- Line Chart: Daily Activity -->
            <div class="chart-card card line-chart-card">
              <h3 class="card-title">TẦN SUẤT HỌC TẬP THEO NGÀY</h3>
              <p class="card-subtitle">
                Số lượt bài học được hoàn thành trong 7 ngày gần nhất
              </p>
              <div class="chart-container">
                <Line
                  :data="activityChartData"
                  :options="activityChartOptions"
                />
              </div>
            </div>

            <!-- Doughnut Chart: Competency Level -->
            <div class="chart-card card doughnut-chart-card">
              <h3 class="card-title">XẾP LOẠI NĂNG LỰC HỌC VIÊN</h3>
              <p class="card-subtitle">
                Tỷ lệ học viên phân nhóm theo điểm tích lũy (PTS)
              </p>
              <div class="chart-container">
                <Doughnut
                  :data="competencyChartData"
                  :options="competencyChartOptions"
                />
              </div>
            </div>
          </div>

          <!-- Charts Row 2: Bar Chart & Active Sessions List -->
          <div class="charts-row split-grid">
            <!-- Bar Chart: Avg Score by Course -->
            <div class="chart-card card bar-chart-card">
              <h3 class="card-title">ĐIỂM THI THỬ TRUNG BÌNH THEO KHÓA HỌC</h3>
              <p class="card-subtitle">
                Kết quả kiểm tra trung bình (%) của các học viên
              </p>
              <div class="chart-container">
                <Bar
                  :data="courseScoreChartData"
                  :options="courseScoreChartOptions"
                />
              </div>
            </div>

            <!-- Active Sessions Card -->
            <div class="sessions-card card">
              <h3 class="card-title">HOẠT ĐỘNG HỌC TẬP THỜI GIAN THỰC</h3>
              <p class="card-subtitle">
                Giám sát các học viên đang học tập trên hệ thống
              </p>

              <div class="sessions-list">
                <div
                  v-for="session in activeSessions"
                  :key="session.username"
                  class="session-row"
                >
                  <div
                    class="session-user-avatar"
                    :style="{
                      backgroundColor: getAvatarColor(session.username),
                    }"
                  >
                    {{ session.username.charAt(0).toUpperCase() }}
                  </div>
                  <div class="session-info">
                    <span class="session-name">
                      <strong>{{ session.fullName }}</strong>
                      <span class="session-action">{{ session.action }}</span>
                    </span>
                    <span class="session-detail">{{ session.detail }}</span>
                  </div>
                  <div class="session-meta">
                    <span class="session-course-tag">{{ session.course }}</span>
                    <span class="session-time">{{ session.time }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Courses Table Summary -->
          <div class="courses-summary card">
            <div class="card-header-row">
              <div class="header-left">
                <h3 class="card-title">PHÂN BỔ BÀI HỌC VÀ CÂU HỎI</h3>
                <p class="card-subtitle">
                  Danh sách các khóa học đào tạo nghiệp vụ ngân hàng
                </p>
              </div>
              <button class="btn btn-primary btn-sm" @click="router.push('/admin/courses')">
                <Plus class="btn-icon-sm" /> THÊM KHÓA HỌC
              </button>
            </div>

            <table class="admin-table">
              <colgroup><col style="width:14%" /><col style="width:30%" /><col style="width:12%" /><col style="width:10%" /><col style="width:12%" /><col style="width:22%" /></colgroup>
              <thead>
                <tr>
                  <th>Mã khóa học</th>
                  <th>Tên khóa học</th>
                  <th>Icon hiển thị</th>
                  <th>Số bài học</th>
                  <th>Số câu hỏi thi</th>
                  <th class="actions-col">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="course in paginatedSummaryCourses" :key="course.id">
                  <td>
                    <span class="code-badge">{{ course.id }}</span>
                  </td>
                  <td>
                    <strong>{{ course.name }}</strong>
                  </td>
                  <td>
                    <span class="icon-label"
                      ><Settings class="inline-icon" />
                      {{ course.iconName }}</span
                    >
                  </td>
                  <td>{{ course.lessons.length }} bài học</td>
                  <td>{{ getQuestionCountForCourse(course.id) }} câu hỏi</td>
                  <td class="actions-col">
                    <button
                      class="action-btn edit"
                      title="Sửa khóa học"
                      v-if="course.status === 'DRAFT'"
                      @click="router.push(`/admin/courses/${course.id}`)"
                    >
                      <Edit />
                    </button>
                    <button
                      class="action-btn delete"
                      title="Xóa khóa học"
                      v-if="course.status === 'DRAFT'"
                      @click="handleDeleteCourse(course.id)"
                    >
                      <Trash2 />
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <TablePagination v-model:page="summaryPage" v-model:page-size="summaryPageSize" :total="courseStore.courses.length" :page-count="summaryPageCount" unit="khóa học" unit-label="Khóa học" />
          </div>
        </div>

        <!-- 2. USERS TAB -->
        <div v-if="activeTab === 'users'" class="tab-pane animate-fade-in">
          <div class="users-manager card">
            <div class="card-header-row">
              <div class="header-left">
                <h3 class="card-title">DANH SÁCH THÀNH VIÊN HỆ THỐNG</h3>
                <p class="card-subtitle">
                  Quản lý tài khoản đăng nhập, phân quyền, và điểm tích lũy học
                  tập.
                </p>
              </div>
              <div class="header-actions">
                <button class="btn btn-secondary" @click="handleDownloadTemplate">
                  <Download class="btn-icon" /> TẢI EXCEL MẪU
                </button>
                <button class="btn btn-secondary" @click="excelFileInput?.click()">
                  <Upload class="btn-icon" /> NHẬP TỪ EXCEL
                </button>
                <input ref="excelFileInput" type="file" hidden accept=".xlsx,.xls" @change="handleExcelUpload" />
                <button class="btn btn-primary" @click="openUserModal()">
                  <Plus class="btn-icon" /> THÊM HỌC VIÊN
                </button>
              </div>
            </div>

            <!-- Filter Search -->
            <div class="search-bar">
              <input
                v-model="userSearchQuery"
                type="text"
                placeholder="Tìm kiếm thành viên theo Tên đăng nhập hoặc Họ tên..."
                class="form-control search-input"
              />
            </div>

            <table class="admin-table">
              <colgroup><col style="width:17%" /><col style="width:13%" /><col style="width:10%" /><col style="width:18%" /><col style="width:13%" /><col style="width:9%" /><col style="width:8%" /><col style="width:12%" /></colgroup>
              <thead>
                <tr>
                  <th>Học viên</th>
                  <th>Tên đăng nhập</th>
                  <th>Mã NV</th>
                  <th>Email</th>
                  <th>Nhóm nghiệp vụ</th>
                  <th>Vai trò</th>
                  <th>Điểm số tích lũy</th>
                  <th class="actions-col">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="u in paginatedLegacyUsers" :key="u.username">
                  <td>
                    <div class="user-cell">
                      <div
                        class="user-avatar-circle"
                        :style="{ backgroundColor: u.avatarColor }"
                      >
                        {{ u.username.charAt(0).toUpperCase() }}
                      </div>
                      <div>
                        <span class="fullname">{{ u.fullName }}</span>
                      </div>
                    </div>
                  </td>
                  <td>
                    <span class="code-badge">{{ u.username }}</span>
                  </td>
                  <td>{{ u.employeeId || "-" }}</td>
                  <td>{{ u.email || "-" }}</td>
                  <td>{{ u.userGroupId || "-" }}</td>
                  <td>
                    <span
                      class="role-tag"
                      :class="u.role === 'ROLE_ADMIN' ? 'admin' : 'user'"
                    >
                      {{
                        u.role === "ROLE_ADMIN" ? "Quản trị viên" : "Học viên"
                      }}
                    </span>
                  </td>
                  <td>
                    <strong>{{ u.points }} PTS</strong>
                  </td>
                  <td class="actions-col">
                    <button
                      class="action-btn edit"
                      title="Sửa học viên"
                      @click="openUserModal(u)"
                    >
                      <Edit />
                    </button>
                    <button
                      class="action-btn delete"
                      title="Xóa học viên"
                      :disabled="u.username === 'admin'"
                      @click="handleDeleteUser(u)"
                    >
                      <Trash2 />
                    </button>
                  </td>
                </tr>
                <tr v-if="filteredUsers.length === 0">
                  <td colspan="8" class="empty-row">
                    Không tìm thấy thành viên phù hợp.
                  </td>
                </tr>
              </tbody>
            </table>
            <TablePagination v-model:page="legacyUserPage" v-model:page-size="legacyUserPageSize" :total="filteredUsers.length" :page-count="legacyUserPageCount" unit="tài khoản" unit-label="Tài khoản" />
          </div>
        </div>

        <div
          v-if="activeTab === 'enrollments'"
          class="tab-pane animate-fade-in"
        >
          <div class="lessons-manager card">
            <div class="card-header-row">
              <div class="header-left">
                <h3 class="card-title">Danh sách yêu cầu đăng ký</h3>
                <p class="card-subtitle">
                  Phê duyệt hoặc từ chối yêu cầu đăng ký khóa học tự chọn.
                </p>
              </div>
              <span class="pending-count"
                >{{ pendingEnrollmentCount }} yêu cầu chờ duyệt</span
              >
            </div>
            <div class="admin-table-toolbar"><div class="admin-search-box"><Search :size="18" /><input v-model="enrollmentQuery" type="search" placeholder="Tìm học viên hoặc khóa học…" aria-label="Tìm yêu cầu đăng ký" /></div><select v-model="enrollmentStatus" aria-label="Lọc trạng thái đăng ký"><option value="">Tất cả trạng thái</option><option value="PENDING">Chờ duyệt</option><option value="APPROVED">Đã duyệt</option><option value="REJECTED">Từ chối</option></select></div>
            <table class="admin-table enrollment-table">
              <colgroup><col style="width:23%" /><col style="width:32%" /><col style="width:16%" /><col style="width:12%" /><col style="width:17%" /></colgroup>
              <thead>
                <tr>
                  <th>Học viên</th>
                  <th>Khóa học</th>
                  <th>Thời gian gửi</th>
                  <th>Trạng thái</th>
                  <th>Thao tác</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="request in paginatedEnrollments" :key="request.id">
                  <td>
                    <strong>{{ request.fullName }}</strong
                    ><br /><small>{{ request.username }}</small>
                  </td>
                  <td>
                    <strong>{{ request.courseName }}</strong
                    ><br /><small>{{ request.courseId }}</small>
                  </td>
                  <td>{{ formatEnrollmentDate(request.requestedAt) }}</td>
                  <td>
                    <div class="enrollment-status-detail">
                      <span
                        class="enrollment-status"
                        :class="request.status.toLowerCase()"
                        >{{ enrollmentStatusLabel(request.status) }}</span
                      >
                      <small
                        v-if="request.rejectionReason"
                        class="rejection-reason"
                      >
                        <strong>Lý do:</strong> {{ request.rejectionReason }}
                      </small>
                    </div>
                  </td>
                  <td>
                    <div
                      v-if="request.status === 'PENDING' && accessUser.can('enrollments','update')"
                      class="enrollment-actions"
                    >
                      <button
                        class="approve-request"
                        @click="reviewEnrollment(request.id, true)"
                      >
                        <CheckCircle2 :size="16" /> Duyệt
                      </button>
                      <button
                        class="reject-request"
                        @click="openRejectEnrollmentModal(request)"
                      >
                        <XCircle :size="16" /> Từ chối
                      </button>
                    </div>
                    <template v-else>
                      <small>{{
                        request.reviewedBy
                          ? `Bởi ${request.reviewedBy}`
                          : "Đã xử lý"
                      }}</small>
                    </template>
                  </td>
                </tr>
                <tr v-if="!filteredEnrollments.length">
                  <td colspan="5" class="empty-row">
                    Không có yêu cầu đăng ký phù hợp.
                  </td>
                </tr>
              </tbody>
            </table>
            <TablePagination v-model:page="enrollmentPage" v-model:page-size="enrollmentPageSize" :total="filteredEnrollments.length" :page-count="enrollmentPageCount" unit="yêu cầu" unit-label="Yêu cầu" />
          </div>
        </div>

        <!-- 3. LESSONS TAB -->
        <div v-if="activeTab === 'lessons'" class="tab-pane animate-fade-in">
          <div class="lessons-manager card">
            <div class="card-header-row">
              <div class="header-left">
                <button
                  v-if="lessonCourse"
                  class="lesson-back-button"
                  @click="backToLessonCourses"
                >
                  <ArrowLeft :size="18" /> Danh sách khóa học
                </button>
                <h3 class="card-title">
                  {{
                    lessonCourse
                      ? `BÀI HỌC — ${lessonCourse.name}`
                      : "QUẢN LÝ BÀI HỌC THEO KHÓA HỌC"
                  }}
                </h3>
                <p class="card-subtitle">
                  {{
                    lessonCourse
                      ? "Kéo thả bài học để thay đổi thứ tự hiển thị trong khóa học."
                      : "Chọn một khóa học để thêm, cập nhật và sắp xếp bài học."
                  }}
                </p>
              </div>
              <button
                v-if="lessonCourse?.status === 'DRAFT'"
                class="btn btn-primary"
                @click="openLessonModal()"
              >
                <Plus class="btn-icon" /> THÊM BÀI HỌC
              </button>
            </div>

            <div v-if="!lessonCourse" class="lesson-course-grid">
              <button
                v-for="course in courseStore.courses"
                :key="course.id"
                class="lesson-course-card"
                @click="openCourseLessons(course.id)"
              >
                <div class="lesson-course-icon">
                  <BookOpen :size="22" />
                </div>
                <div class="lesson-course-copy">
                  <strong>{{ course.name }}</strong>
                  <small>{{ course.lessons.length }} bài học</small>
                </div>
                <ArrowRight :size="20" />
              </button>
            </div>

            <table
              v-else-if="currentCourseLessons.length > 0"
              class="admin-table lesson-order-table"
            >
              <colgroup><col style="width:8%" /><col style="width:12%" /><col style="width:24%" /><col style="width:20%" /><col style="width:10%" /><col style="width:12%" /><col style="width:14%" /></colgroup>
              <thead>
                <tr>
                  <th class="drag-column">Thứ tự</th>
                  <th>Mã bài học</th>
                  <th>Tiêu đề bài học</th>
                  <th>Gợi ý nghiệp vụ (Hint)</th>
                  <th>Video đính kèm</th>
                  <th>Tài liệu đính kèm</th>
                  <th class="actions-col">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(lesson, index) in paginatedLegacyLessons"
                  :key="lesson.id"
                  draggable="true"
                  :class="{
                    dragging: draggedLessonId === lesson.id,
                    'drag-over': dragOverLessonId === lesson.id,
                  }"
                  @dragstart="startLessonDrag(lesson.id, $event)"
                  @dragover.prevent="dragOverLessonId = lesson.id"
                  @dragleave="clearLessonDragOver(lesson.id)"
                  @drop.prevent="dropLessonAt(lesson.id)"
                  @dragend="endLessonDrag"
                >
                  <td class="drag-column">
                    <span class="drag-handle" title="Kéo để sắp xếp">
                      <GripVertical :size="18" />
                      <strong>{{ (lessonPage-1)*lessonPageSize + index+1 }}</strong>
                    </span>
                  </td>
                  <td>
                    <span class="code-badge">{{ lesson.id }}</span>
                  </td>
                  <td>
                    <strong>{{ lesson.title }}</strong>
                  </td>
                  <td>
                    <span class="hint-text-preview">{{ lesson.hint }}</span>
                  </td>
                  <td>
                    <span class="video-status" v-if="lesson.videoUrl">
                      Có video
                    </span>
                    <span class="video-status-none" v-else>Không</span>
                  </td>
                  <td>{{ lesson.documents?.length || 0 }} tài liệu</td>
                  <td class="actions-col">
                    <button
                      class="action-btn edit"
                      title="Sửa bài học"
                      v-if="lessonCourse?.status === 'DRAFT'"
                      @click="openLessonModal(lesson)"
                    >
                      <Edit />
                    </button>
                    <button
                      class="action-btn delete"
                      title="Xóa bài học"
                      v-if="lessonCourse?.status === 'DRAFT'"
                      @click="handleDeleteLesson(lesson.id)"
                    >
                      <Trash2 />
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div class="empty-state-box" v-else-if="lessonCourse">
              <p>
                Khóa học này chưa có bài học nào. Bấm nút "Thêm bài học" ở góc
                trên để bắt đầu cấu hình.
              </p>
            </div>

            <TablePagination v-model:page="lessonPage" v-model:page-size="lessonPageSize" :total="currentCourseLessons.length" :page-count="lessonPageCount" unit="bài học" unit-label="Bài học" />          </div>
        </div>

        <!-- 4. EXAMS TAB -->
        <div v-if="activeTab === 'exams'" class="tab-pane animate-fade-in">
          <div class="exams-manager card">
            <div class="card-header-row">
              <div class="header-left">
                <h3 class="card-title">Danh sách bài kiểm tra</h3>
                <p class="card-subtitle">
                  Chọn một bài kiểm tra để xem và quản lý câu hỏi bên trong.
                </p>
              </div>
              <div class="exam-header-actions">
                <button v-if="accessUser.can('exams','create')" class="btn btn-primary" @click="createQuiz">
                  <Plus class="btn-icon" /> Thêm bài kiểm tra
                </button>
              </div>
            </div>

            <!-- Select Course Filter -->
            <div class="course-selector-row">
              <label for="exam-course-select">Chọn khóa học cấu hình: </label>
              <select
                id="exam-course-select"
                v-model="selectedCourseId"
                class="form-control select-control"
              >
                <option :value="ALL_COURSES">Tất cả khóa học</option>
                <option
                  v-for="t in courseStore.courses"
                  :key="t.id"
                  :value="t.id"
                >
                  {{ t.name }}
                </option>
              </select>
            </div>

            <div class="quiz-list-toolbar">
              <Search :size="18" />
              <input
                v-model="quizSearchQuery"
                aria-label="Tìm bài kiểm tra"
                type="search"
                placeholder="Tìm theo tên bài kiểm tra hoặc bài học..."
              />
            </div>

            <div class="admin-table-scroll"><table class="admin-table"><colgroup><col style="width:55%" /><col style="width:15%" /><col style="width:15%" /><col style="width:15%" /></colgroup><thead><tr><th>Bài kiểm tra</th><th>Số câu hỏi</th><th>Ngưỡng đạt</th><th>Thao tác</th></tr></thead><tbody><tr v-for="quiz in paginatedQuizDefinitions" :key="quiz.key"><td><strong>{{ quiz.title }}</strong><small>{{ quiz.lessonId ? 'Kiểm tra bài học' : 'Kiểm tra khóa học' }}</small></td><td>{{ quiz.questionCount }} câu hỏi</td><td>{{ quiz.passingScore }}%</td><td><button class="admin-row-edit" @click="openQuizDefinition(quiz)">Chi tiết</button></td></tr><tr v-if="!filteredQuizDefinitions.length"><td colspan="4" class="admin-empty">Không có bài kiểm tra phù hợp.</td></tr></tbody></table></div>
            <TablePagination v-model:page="quizPage" v-model:page-size="quizPageSize" :total="filteredQuizDefinitions.length" :page-count="quizPageCount" unit="bài kiểm tra" unit-label="Bài kiểm tra" />
          </div>
        </div>

        <div v-if="activeTab === 'documents'" class="tab-pane animate-fade-in">
          <div class="courses-summary card rag-documents-manager">
            <div class="card-header-row">
              <div class="header-left">
                <h3 class="card-title">TÀI LIỆU TRI THỨC AI</h3>
                <p class="card-subtitle">
                  Tải PDF hoặc DOCX theo từng khóa học. Tài liệu sẽ được gửi tới
                  dịch vụ embedding và lưu vector trong Qdrant.
                </p>
              </div>
              <button class="btn btn-secondary" :disabled="documentListLoading" @click="loadDocuments()">
                <RefreshCw :size="16" :class="{ 'spin-icon': documentListLoading }" />
                LÀM MỚI
              </button>
            </div>

            <div class="rag-document-controls">
              <div class="form-group">
                <label class="form-label">Khóa học nhận tài liệu</label>
                <select v-model="selectedCourseId" class="form-input" @change="loadDocuments()">
                  <option v-for="course in courseStore.courses" :key="course.id" :value="course.id">
                    {{ course.id }} — {{ course.name }}
                  </option>
                </select>
              </div>
              <div class="rag-upload-box">
                <Database :size="28" />
                <div>
                  <strong>Upload và embedding tài liệu</strong>
                  <small>Định dạng PDF/DOCX, tối đa theo cấu hình máy chủ.</small>
                </div>
                <button v-if="allowedCourseAction('documents','create')" class="btn btn-primary" :disabled="documentUploading || !selectedCourseId || selectedCourseId === ALL_COURSES"
                  @click="ragFileInput?.click()">
                  <LoaderCircle v-if="documentUploading" :size="16" class="spin-icon" />
                  <Upload v-else :size="16" />
                  {{ documentUploading ? 'ĐANG TẢI LÊN' : 'CHỌN TỆP' }}
                </button>
                <input ref="ragFileInput" type="file" hidden
                  accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                  @change="uploadDocument" />
              </div>
            </div>

            <div class="admin-table-toolbar"><div class="admin-search-box"><Search :size="18" /><input v-model="documentQuery" type="search" placeholder="Tìm tên tài liệu…" aria-label="Tìm tài liệu AI" /></div></div>
            <div class="rag-document-table-scroll">
            <table class="admin-table rag-document-table">
              <colgroup><col style="width:24%" /><col style="width:10%" /><col style="width:12%" /><col style="width:7%" /><col style="width:16%" /><col style="width:21%" /><col style="width:10%" /></colgroup>
              <thead>
                <tr>
                  <th>Tệp</th>
                  <th>Dung lượng</th>
                  <th>Trạng thái</th>
                  <th>Chunks</th>
                  <th>Thời gian</th>
                  <th>Lỗi</th>
                  <th class="rag-document-actions">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                <tr v-if="documentListLoading && courseDocuments.length === 0">
                  <td colspan="7" class="rag-empty-cell">Đang tải danh sách tài liệu...</td>
                </tr>
                <tr v-for="document in paginatedDocuments" :key="document.id">
                  <td><strong>{{ document.fileName }}</strong></td>
                  <td>{{ formatFileSize(document.sizeBytes) }}</td>
                  <td><span class="rag-status" :class="`status-${document.status.toLowerCase()}`">
                    {{ documentStatusLabel(document.status) }}
                  </span></td>
                  <td>{{ document.indexedChunks ?? "-" }}</td>
                  <td>{{ formatDocumentDate(document.indexedAt || document.createdAt) }}</td>
                  <td class="rag-error-cell">
                    <span :title="document.errorMessage || ''">{{ document.errorMessage || "-" }}</span>
                  </td>
                  <td class="rag-document-actions">
                    <button v-if="allowedCourseAction('documents','delete')" type="button" class="btn btn-secondary btn-sm"
                      :disabled="document.status === 'PENDING' || document.status === 'INDEXING' || confirmationModal.submitting"
                      :title="document.status === 'PENDING' || document.status === 'INDEXING' ? 'Chờ embedding hoàn tất trước khi xóa' : 'Xóa tài liệu và dữ liệu embedding'"
                      @click="deleteCourseDocument(document)">
                      <Trash2 :size="16" /> Xóa
                    </button>
                  </td>
                </tr>
                <tr v-if="!documentListLoading && filteredDocuments.length === 0">
                  <td colspan="7" class="rag-empty-cell">Khóa học này chưa có tài liệu được embedding.</td>
                </tr>
              </tbody>
            </table>
            <TablePagination v-model:page="documentPage" v-model:page-size="documentPageSize" :total="filteredDocuments.length" :page-count="documentPageCount" unit="tài liệu" unit-label="Tài liệu" />
            </div>
          </div>
        </div>
      </div>
    </div>

    <AdminModal :open="rejectEnrollmentModal.show" title="Từ chối đăng ký khóa học" :busy="rejectEnrollmentModal.submitting" :width="640" submit-label="Gửi lý do từ chối" @close="closeRejectEnrollmentModal" @submit="confirmRejectEnrollment">
      <p class="admin-modal-help">Lý do sẽ được gửi tới học viên trong thông báo kết quả.</p>
        <div v-if="rejectEnrollmentModal.request" class="reject-request-summary">
          <div>
            <small>Học viên</small>
            <strong>{{ rejectEnrollmentModal.request.fullName }}</strong>
            <span>@{{ rejectEnrollmentModal.request.username }}</span>
          </div>
          <div>
            <small>Khóa học</small>
            <strong>{{ rejectEnrollmentModal.request.courseName }}</strong>
            <span>{{ rejectEnrollmentModal.request.courseId }}</span>
          </div>
        </div>

          <div class="form-group">
            <label for="enrollment-rejection-reason">
              Lý do từ chối <span class="required-mark">*</span>
            </label>
            <textarea
              id="enrollment-rejection-reason"
              v-model="rejectEnrollmentModal.reason"
              rows="4"
              maxlength="500"
              placeholder="Ví dụ: Khóa học chưa phù hợp với vị trí công tác hiện tại..."
              required
              autofocus
            ></textarea>
            <div class="reason-counter">
              {{ rejectEnrollmentModal.reason.length }}/500
            </div>
          </div>
    </AdminModal>

    <AdminModal :open="courseModal.show" :title="courseModal.isEdit ? 'Cập nhật khóa học' : 'Thêm khóa học'" :width="640" :busy="modalSaving" submit-label="Lưu" @close="courseModal.show=false" @submit="runModalSave(saveCourse)">

          <div class="form-group">
            <label
              >Mã khóa học (In hoa, không dấu, ví dụ: 'CIF', 'TIEN_GUI')</label
            >
            <input
              v-model="courseForm.id"
              type="text"
              class="form-control"
              required
              :disabled="courseModal.isEdit"
            />
          </div>
          <div class="form-group">
            <label>Tên khóa học hiển thị</label>
            <input
              v-model="courseForm.name"
              type="text"
              class="form-control"
              required
            />
          </div>
          <div class="form-group">
            <label
              >Tên biểu tượng Icon (Ví dụ: 'Coins', 'Users', 'Wallet',
              'PiggyBank')</label
            >
            <input
              v-model="courseForm.iconName"
              type="text"
              class="form-control"
              required
            />
          </div>

    </AdminModal>

    <AdminModal :open="userModal.show" :title="userModal.isEdit ? 'Cập nhật tài khoản' : 'Thêm tài khoản'" :width="720" :busy="modalSaving" submit-label="Lưu" @close="userModal.show=false" @submit="runModalSave(saveUser)">

          <div class="form-group">
            <label>Tên đăng nhập (Username)</label>
            <input
              v-model="userForm.username"
              type="text"
              class="form-control"
              required
              :disabled="userModal.isEdit"
            />
          </div>
          <div class="form-group">
            <label
              >Mật khẩu
              {{
                userModal.isEdit
                  ? "(Để trống nếu không đổi)"
                  : "(Để trống: tạo mật khẩu ngẫu nhiên; dùng quên mật khẩu qua email)"
              }}</label
            >
            <input
              v-model="userForm.password"
              type="password"
              class="form-control"
            />
          </div>
          <div class="form-group">
            <label>Họ và tên</label>
            <input
              v-model="userForm.fullName"
              type="text"
              class="form-control"
              required
            />
          </div>
          <div class="form-group">
            <label>Mã nhân viên</label>
            <input v-model="userForm.employeeId" type="text" class="form-control" required />
          </div>
          <div class="form-group">
            <label>Email công vụ</label>
            <input v-model="userForm.email" type="email" class="form-control" required />
          </div>
          <div class="form-group">
            <label>Nhóm nghiệp vụ</label>
            <select v-model="userForm.userGroupId" class="form-control">
              <option value="">Không phân nhóm</option>
              <option value="GDV">Giao dịch viên</option>
              <option value="QHKH">Quan hệ khách hàng</option>
              <option value="KSV">Kiểm soát viên</option>
            </select>
          </div>
          <div class="form-group">
            <label>Vai trò hệ thống</label>
            <select v-model="userForm.role" class="form-control">
              <option value="ROLE_USER">Học viên (ROLE_USER)</option>
              <option value="ROLE_ADMIN">Quản trị viên (ROLE_ADMIN)</option>
            </select>
          </div>

    </AdminModal>

    <AdminModal :open="lessonModal.show" :title="lessonModal.isEdit ? 'Cập nhật bài học' : 'Thêm bài học'" :width="960" :busy="modalSaving" submit-label="Lưu" @close="lessonModal.show=false" @submit="runModalSave(saveLesson)">

          <div ref="lessonModalBodyRef" class="legacy-lesson-fields">
            <div class="form-row">
              <div class="form-group col">
                <label>Khóa học trực thuộc</label>
                <select
                  v-model="lessonForm.courseId"
                  class="form-control"
                  disabled
                  required
                >
                  <option
                    v-for="t in courseStore.courses"
                    :key="t.id"
                    :value="t.id"
                  >
                    {{ t.name }}
                  </option>
                </select>
              </div>
              <div class="form-group col">
                <label>Mã bài học (Ví dụ: 'LN03', 'CIF05')</label>
                <input
                  v-model="lessonForm.id"
                  type="text"
                  class="form-control"
                  required
                  :disabled="lessonModal.isEdit"
                />
              </div>
            </div>

            <div class="form-group">
              <label>Tiêu đề bài học</label>
              <input
                v-model="lessonForm.title"
                type="text"
                class="form-control"
                required
              />
            </div>

            <div class="form-group">
              <label>Gợi ý nghiệp vụ nhanh (Hint)</label>
              <input
                v-model="lessonForm.hint"
                type="text"
                class="form-control"
                required
              />
            </div>

            <div class="form-group video-source-group">
              <label id="video-source-label">Nguồn video bài học</label>
              <div
                class="video-source-options"
                role="radiogroup"
                aria-labelledby="video-source-label"
              >
                <label
                  class="video-source-option"
                  :class="{ active: lessonForm.videoSource === 'UPLOAD' }"
                >
                  <input
                    v-model="lessonForm.videoSource"
                    type="radio"
                    value="UPLOAD"
                    name="video-source"
                  />
                  <Upload :size="19" />
                  <span>
                    <strong>Tải video lên</strong>
                    <small>Chọn file có sẵn trên máy</small>
                  </span>
                </label>
                <label
                  class="video-source-option"
                  :class="{ active: lessonForm.videoSource === 'URL' }"
                >
                  <input
                    v-model="lessonForm.videoSource"
                    type="radio"
                    value="URL"
                    name="video-source"
                  />
                  <Link2 :size="19" />
                  <span>
                    <strong>Dán đường link</strong>
                    <small>Dùng link YouTube hoặc video trực tiếp</small>
                  </span>
                </label>
              </div>

              <div
                v-if="lessonForm.videoSource === 'UPLOAD'"
                class="video-source-panel"
              >
                <div class="file-upload-control">
                  <label class="file-picker">
                    <Upload :size="18" />
                    <span>{{
                      selectedVideoFileName || "Chọn file video từ máy"
                    }}</span>
                    <input
                      type="file"
                      accept="video/mp4,video/webm,.m4v"
                      :disabled="loading"
                      @change="handleVideoFileChange"
                    />
                  </label>
                  <button
                    v-if="selectedVideoFileName"
                    type="button"
                    class="file-clear-btn"
                    title="Gỡ video"
                    aria-label="Gỡ video đã chọn"
                    @click="clearVideoFile"
                  >
                    <X :size="16" />
                  </button>
                </div>
                <small class="video-source-help">
                  Tối đa {{ Math.floor(VIDEO_UPLOAD_MAX_BYTES / 1024 / 1024) }} MiB. MP4/M4V: H.264/AAC; WebM: VP8/VP9, Opus/Vorbis.
                </small>
                <div v-if="videoUploadProgress !== null" role="status">
                  <progress :value="videoUploadProgress" max="100" style="width: 100%"></progress>
                  <small>{{ videoUploadProgress < 100 ? `Đang tải video: ${videoUploadProgress}%` : 'Đã gửi video, đang kiểm tra định dạng…' }}</small>
                </div>
                <small v-if="lessonForm.videoFile" class="selected-video-note">
                  Đã chọn: {{ lessonForm.videoFile.name }}
                </small>
              </div>

              <div v-else class="video-source-panel">
                <input
                  v-model="lessonForm.videoUrl"
                  type="url"
                  class="form-control"
                  style="width: 100%"
                  placeholder="Ví dụ: https://www.youtube.com/watch?v=..."
                />
                <small class="video-source-help">
                  Dán link YouTube hoặc đường dẫn trực tiếp đến video.
                </small>
              </div>
            </div>

            <!-- Lesson documents -->
            <div class="form-group">
              <div
                class="card-header-row"
                style="display: flex; align-items: center; gap: 10px"
              >
                <label class="section-sub-label"
                  >Tài liệu PDF/Word/PowerPoint đính kèm</label
                >
                <button
                  type="button"
                  class="btn btn-secondary btn-sm"
                  @click="addLessonDocument"
                  style="padding: 0.5rem 1rem !important"
                >
                  <Plus :size="15" /> Thêm tài liệu
                </button>
              </div>
              <div
                v-if="lessonForm.documents.length === 0"
                class="empty-state-box"
              >
                Bài học chưa có tài liệu đính kèm.
              </div>
              <div
                v-for="(document, index) in lessonForm.documents"
                :key="index"
                class="document-form-card"
              >
                <div class="document-form-index">
                  <FileText :size="19" /><span>Tài liệu {{ index + 1 }}</span>
                </div>
                <div class="document-form-fields">
                  <div class="form-group">
                    <label>Tên hiển thị</label>
                    <input
                      v-model="document.name"
                      class="form-control"
                      placeholder="Ví dụ: Quy trình nghiệp vụ"
                      required
                    />
                  </div>
                  <div class="form-group document-file-field">
                    <label>Chọn file PDF, Word hoặc PowerPoint</label>
                    <div class="file-upload-control">
                      <label class="file-picker">
                        <Upload :size="18" />
                        <span>{{
                          document.file?.name ||
                          document.originalFileName ||
                          "Chọn file từ máy"
                        }}</span>
                        <input
                          type="file"
                          accept=".pdf,.doc,.docx,.ppt,.pptx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,application/vnd.ms-powerpoint,application/vnd.openxmlformats-officedocument.presentationml.presentation"
                          :required="!document.fileUrl"
                          @change="handleDocumentFileChange($event, index)"
                        />
                      </label>
                      <button
                        v-if="document.file || document.fileUrl"
                        type="button"
                        class="file-clear-btn"
                        title="Gỡ file"
                        :aria-label="`Gỡ file của tài liệu ${index + 1}`"
                        @click="clearDocumentFile(index, $event)"
                      >
                        <X :size="16" />
                      </button>
                    </div>
                    <small
                      v-if="document.file || document.fileSize"
                      class="selected-file-meta"
                    >
                      {{ document.documentType }} ·
                      {{
                        formatFileSize(document.file?.size || document.fileSize)
                      }}
                    </small>
                  </div>
                </div>
                <button
                  type="button"
                  class="action-btn delete document-remove"
                  title="Xóa tài liệu"
                  @click="removeLessonDocument(index)"
                >
                  <Trash2 />
                </button>
              </div>
            </div>
          </div>

    </AdminModal>

    <AdminModal :open="questionModal.show" :title="questionModal.isEdit ? 'Cập nhật câu hỏi' : 'Thêm câu hỏi'" :width="860" :busy="modalSaving" submit-label="Lưu" @close="questionModal.show=false" @submit="runModalSave(saveQuestion)">

          <div class="form-row">
            <div class="form-group col">
              <label>1. Chọn khóa học</label>
              <select
                v-model="questionForm.courseId"
                class="form-control"
                required
              >
                <option
                  v-for="t in courseStore.courses"
                  :key="t.id"
                  :value="t.id"
                >
                  {{ t.name }}
                </option>
              </select>
            </div>
            <div class="form-group col">
              <label>2. Phạm vi bài kiểm tra</label>
              <select v-model="questionForm.lessonId" class="form-control">
                <option value="">Bài kiểm tra chung của khóa học</option>
                <option
                  v-for="lesson in questionCourseLessons"
                  :key="lesson.id"
                  :value="lesson.id"
                >
                  Bài học: {{ lesson.title }}
                </option>
              </select>
              <small class="field-help">
                Không bắt buộc chọn bài học. Nếu chọn, học viên phải đạt 80% để
                hoàn thành bài đó.
              </small>
            </div>
          </div>

          <div class="form-group">
            <label>3. Nhập nội dung câu hỏi</label>
            <textarea
              v-model="questionForm.text"
              rows="3"
              class="form-control"
              required
            ></textarea>
          </div>

          <div class="form-group-options">
            <label class="options-label"
              >4. Nhập đáp án và chọn đáp án đúng</label
            >
            <div
              v-for="idx in [0, 1, 2, 3]"
              :key="idx"
              class="option-input-row"
            >
              <input
                type="radio"
                :value="idx"
                v-model="questionForm.correctAnswer"
                name="correct-answer-radio"
                required
              />
              <span class="opt-label"
                >{{ String.fromCharCode(65 + idx) }}:</span
              >
              <input
                v-model="optionsInputs[idx]"
                type="text"
                class="form-control"
                placeholder="Nhập nội dung đáp án..."
                required
              />
            </div>
            <span class="helper-text-opt"
              >Chọn nút tròn bên trái đáp án chính xác. Cần nhập đủ 4 đáp
              án.</span
            >
          </div>


    </AdminModal>

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
import { showToast } from "../utils/toast";
import AdminModal from '../components/AdminModal.vue';
import TablePagination from '../components/TablePagination.vue';
import {useTablePagination} from '../composables/useTablePagination';
import { axiosClient } from "../api/axiosClient";
import { ref, computed, nextTick, onMounted, onBeforeUnmount, watch } from "vue";
import { useTheme } from "../composables/useTheme";
const { isDark } = useTheme();
import { useRoute, useRouter } from "vue-router";
import {useUserStore} from '../stores/userStore';
import { useCourseStore } from "../stores/courseStore";
import { useNavigationStore } from "../stores/navigationStore";
import { validateVideoUpload } from "../utils/videoAccess";
import { adminApi, VIDEO_UPLOAD_MAX_BYTES } from "../api/adminApi";
import type {
  AdminUser,
  CourseDocument,
  EnrollmentRequest,
} from "../api/adminApi";
import type { Course, Lesson, LessonDocument, Question } from "../types";
import ConfirmModal from "../components/ConfirmModal.vue";
import {
  Settings,
  Users,
  BookOpen,
  HelpCircle,
  Plus,
  Edit,
  Trash2,
  FileText,
  Upload,
  Download,
  Link2,
  X,
  Search,
  ArrowLeft,
  ArrowRight,
  GripVertical,
  CheckCircle2,
  XCircle,
  Database,
  LoaderCircle,
  RefreshCw,
} from "lucide-vue-next";

// Chart.js imports & registrations
import { Line, Doughnut, Bar } from "vue-chartjs";
import {
  Chart as ChartJS,
  Title,
  Tooltip,
  Legend,
  PointElement,
  LineElement,
  BarElement,
  ArcElement,
  CategoryScale,
  LinearScale,
  Filler,
} from "chart.js";

ChartJS.register(
  Title,
  Tooltip,
  Legend,
  PointElement,
  LineElement,
  BarElement,
  ArcElement,
  CategoryScale,
  LinearScale,
  Filler,
);

// Stores
const courseStore = useCourseStore();const accessUser=useUserStore();
const allowedCourseAction=(page:string,action:string)=>courseStore.courses.find(c=>c.id===selectedCourseId.value)?.adminActions?.[page]?.includes(action)===true;
const navigationStore = useNavigationStore();
const router = useRouter();
const route = useRoute();

const allowedAdminTabs = computed(() =>
  navigationStore.adminItems
    .map((item) => item.id.replace(/^admin\./, ""))
    .filter((id) => !id.includes(".")),
);
const requestedAdminTab =
  route.name === "AdminCourseLessons"
    ? "lessons"
    : typeof route.query.tab === "string"
      ? route.query.tab
      : "overview";
const activeTab = ref(requestedAdminTab);

// Active sessions simulation
const activeSessions = ref([
  {
    username: "nguyenvana",
    fullName: "Nguyễn Văn An",
    action: "Đang học bài",
    detail: "LN02. Quy trình giải ngân thông thường",
    course: "TIỀN VAY",
    time: "Vừa xong",
  },
  {
    username: "tranb",
    fullName: "Trần Thị Bình",
    action: "Đang thi thử",
    detail: "Đề đánh giá năng lực CIF",
    course: "CIF",
    time: "2 phút trước",
  },
  {
    username: "lephuc",
    fullName: "Lê Hoàng Phúc",
    action: "Đang xem",
    detail: "Bảng xếp hạng vinh danh",
    course: "HỆ THỐNG",
    time: "5 phút trước",
  },
  {
    username: "phamduc",
    fullName: "Phạm Minh Đức",
    action: "Đang học bài",
    detail: "CT01. Quy trình chuyển tiền trong nước",
    course: "CHUYỂN TIỀN",
    time: "12 phút trước",
  },
]);

// Reactive states
const loading = ref(false);
const videoUploadProgress = ref<number | null>(null);
const users = ref<AdminUser[]>([]);
const enrollmentRequests = ref<EnrollmentRequest[]>([]);
const pendingEnrollmentCount = computed(
  () =>
    enrollmentRequests.value.filter((request) => request.status === "PENDING")
      .length,
);
const questions = ref<Question[]>([]);

const courseDocuments = ref<CourseDocument[]>([]);
const documentUploading = ref(false);
const documentListLoading = ref(false);
const ragFileInput = ref<HTMLInputElement | null>(null);
let documentPollingTimer: ReturnType<typeof setTimeout> | null = null;
const ALL_COURSES = "__ALL__";
const selectedCourseId = ref(
  typeof route.params.courseId === "string" ? route.params.courseId : ALL_COURSES,
);
const draggedLessonId = ref<string | null>(null);
const dragOverLessonId = ref<string | null>(null);
const userSearchQuery = ref("");
const quizSearchQuery = ref("");

// Forms & Modals states
const rejectEnrollmentModal = ref({
  show: false,
  request: null as EnrollmentRequest | null,
  reason: "",
  submitting: false,
});
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
const courseModal = ref({ show: false, isEdit: false });
const courseForm = ref({ id: "", name: "", iconName: "" });

const userModal = ref({
  show: false,
  isEdit: false,
  editId: null as number | null,
});
const userForm = ref({
  username: "",
  password: "",
  fullName: "",
  avatarColor: "#005B41",
  role: "ROLE_USER",
  points: 0,
  userGroupId: "",
  employeeId: "",
  email: "",
});

const lessonModal = ref({ show: false, isEdit: false });
type LessonDocumentForm = LessonDocument & { file?: File };
const lessonForm = ref({
  id: "",
  title: "",
  courseId: "",
  hint: "",
  videoSource: "UPLOAD" as "UPLOAD" | "URL",
  videoUrl: "",
  videoFile: undefined as File | undefined,
  documents: [] as LessonDocumentForm[],
});

const questionModal = ref({
  show: false,
  isEdit: false,
  editId: null as number | null,
});
const questionForm = ref({
  text: "",
  courseId: "",
  lessonId: "",
  correctAnswer: 0,
  options: [] as string[],
});
const optionsInputs = ref(["", "", "", ""]);

// Statistics Computeds
const totalLessonsCount = computed(() => {
  return courseStore.courses.reduce((acc, t) => acc + t.lessons.length, 0);
});

const totalQuestionsCount = computed(() => {
  return questions.value.length;
});

const filteredUsers = computed(() => {
  if (!userSearchQuery.value) return users.value;
  const q = userSearchQuery.value.toLowerCase().trim();
  return users.value.filter(
    (u) =>
      u.username.toLowerCase().includes(q) ||
      u.fullName.toLowerCase().includes(q),
  );
});

const currentCourseLessons = computed(() => {
  if (selectedCourseId.value === ALL_COURSES) {
    return courseStore.courses.flatMap((course) => course.lessons);
  }
  const course = courseStore.courses.find((t) => t.id === selectedCourseId.value);
  return course ? course.lessons : [];
});
const lessonCourse = computed(() =>
  typeof route.params.courseId === "string"
    ? courseStore.courses.find((course) => course.id === route.params.courseId)
    : undefined,
);

type QuizDefinition = {
  key: string;
  courseId: string;
  lessonId?: string;
  title: string;
  questionCount: number;
  passingScore: number;
};

const quizDefinitions = computed<QuizDefinition[]>(() => {
  const definitions: QuizDefinition[] = [];
  const courses =
    selectedCourseId.value === ALL_COURSES
      ? courseStore.courses
      : courseStore.courses.filter(
          (course) => course.id === selectedCourseId.value,
        );
  for (const course of courses) {
    const courseQuestions = questions.value.filter(
      (question) => question.courseId === course.id && !question.lessonId,
    );
    if (courseQuestions.length) {
      definitions.push({
        key: `course:${course.id}`,
        courseId: course.id,
        title: `Bài kiểm tra tổng hợp — ${course.name}`,
        questionCount: courseQuestions.length,
        passingScore: course.quizPassingScore ?? 80,
      });
    }
    for (const lesson of course.lessons) {
      const count = questions.value.filter(
        (question) => question.lessonId === lesson.id,
      ).length;
      if (count) {
        definitions.push({
          key: `lesson:${lesson.id}`,
          courseId: course.id,
          lessonId: lesson.id,
          title:
            selectedCourseId.value === ALL_COURSES
              ? `${lesson.title} — ${course.name}`
              : lesson.title,
          questionCount: count,
          passingScore: lesson.quizPassingScore ?? 80,
        });
      }
    }
  }
  return definitions;
});

const filteredQuizDefinitions = computed(() => {
  const query = quizSearchQuery.value.trim().toLocaleLowerCase("vi");
  if (!query) return quizDefinitions.value;
  return quizDefinitions.value.filter((quiz) =>
    `${quiz.title} ${quiz.lessonId || ""}`
      .toLocaleLowerCase("vi")
      .includes(query),
  );
});

const sectionHeading=computed(()=>({exams:{title:'Bài kiểm tra',description:'Quản lý bài kiểm tra và ngân hàng câu hỏi theo khóa, bài học.'},enrollments:{title:'Duyệt đăng ký',description:'Theo dõi và xử lý yêu cầu đăng ký khóa học của học viên.'},documents:{title:'Tài liệu AI',description:'Quản lý tài liệu và nguồn kiến thức của khóa học.'},lessons:{title:'Bài học',description:'Quản lý nội dung bài học theo khóa đào tạo.'},users:{title:'Tài khoản',description:'Quản lý người dùng trong phạm vi được cấp.'}}[activeTab.value] || {title:'Quản trị',description:''}));
const modalSaving=ref(false);
async function runModalSave(action:()=>Promise<void>){if(modalSaving.value)return;modalSaving.value=true;try{await action();}finally{modalSaving.value=false;}}
const enrollmentQuery=ref(''),enrollmentStatus=ref(''),documentQuery=ref('');
const filteredEnrollments=computed(()=>enrollmentRequests.value.filter(r=>`${r.fullName} ${r.username} ${r.courseName}`.toLocaleLowerCase('vi').includes(enrollmentQuery.value.trim().toLocaleLowerCase('vi')) && (!enrollmentStatus.value || r.status===enrollmentStatus.value)));
const filteredDocuments=computed(()=>courseDocuments.value.filter(d=>d.fileName.toLocaleLowerCase('vi').includes(documentQuery.value.trim().toLocaleLowerCase('vi'))));
const {page:summaryPage,pageSize:summaryPageSize,pageCount:summaryPageCount,paginated:paginatedSummaryCourses}=useTablePagination(computed(()=>courseStore.courses));
const {page:legacyUserPage,pageSize:legacyUserPageSize,pageCount:legacyUserPageCount,paginated:paginatedLegacyUsers,reset:resetLegacyUsers}=useTablePagination(filteredUsers);
const {page:enrollmentPage,pageSize:enrollmentPageSize,pageCount:enrollmentPageCount,paginated:paginatedEnrollments,reset:resetEnrollments}=useTablePagination(filteredEnrollments);
const {page:lessonPage,pageSize:lessonPageSize,pageCount:lessonPageCount,paginated:paginatedLegacyLessons,reset:resetLegacyLessons}=useTablePagination(currentCourseLessons);
const {page:documentPage,pageSize:documentPageSize,pageCount:documentPageCount,paginated:paginatedDocuments,reset:resetDocuments}=useTablePagination(filteredDocuments);
const {page:quizPage,pageSize:quizPageSize,pageCount:quizPageCount,paginated:paginatedQuizDefinitions,reset:resetQuizzes}=useTablePagination(filteredQuizDefinitions);
watch(userSearchQuery,resetLegacyUsers);watch([enrollmentQuery,enrollmentStatus],resetEnrollments);watch(documentQuery,resetDocuments);watch([quizSearchQuery,selectedCourseId],resetQuizzes);watch(selectedCourseId,()=>{resetLegacyLessons();resetDocuments();});

const navigationRoute = (id: string, params: Record<string, string> = {}) => {
  const item = navigationStore.items.find((entry) => entry.id === id);
  if (!item) throw new Error(`Navigation configuration not found: ${id}`);
  return Object.entries(params).reduce(
    (resolvedRoute, [key, value]) =>
      resolvedRoute.replace(`:${key}`, encodeURIComponent(value)),
    item.route,
  );
};

const openCourseLessons = (courseId: string) =>
  router.push(navigationRoute("admin.lesson.course", { courseId }));

const backToLessonCourses = () => router.push(navigationRoute("admin.lessons"));

const startLessonDrag = (lessonId: string, event: DragEvent) => {
  draggedLessonId.value = lessonId;
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = "move";
    event.dataTransfer.setData("text/plain", lessonId);
  }
};

const clearLessonDragOver = (lessonId: string) => {
  if (dragOverLessonId.value === lessonId) dragOverLessonId.value = null;
};

const endLessonDrag = () => {
  draggedLessonId.value = null;
  dragOverLessonId.value = null;
};

const dropLessonAt = async (targetLessonId: string) => {
  const sourceLessonId = draggedLessonId.value;
  const course = lessonCourse.value;
  endLessonDrag();
  if (!sourceLessonId || !course || sourceLessonId === targetLessonId) return;

  const sourceIndex = course.lessons.findIndex(
    (lesson) => lesson.id === sourceLessonId,
  );
  const targetIndex = course.lessons.findIndex(
    (lesson) => lesson.id === targetLessonId,
  );
  if (sourceIndex < 0 || targetIndex < 0) return;

  const previousLessons = [...course.lessons];
  const reorderedLessons = [...course.lessons];
  const [movedLesson] = reorderedLessons.splice(sourceIndex, 1);
  reorderedLessons.splice(targetIndex, 0, movedLesson);
  course.lessons = reorderedLessons.map((lesson, index) => ({
    ...lesson,
    lessonOrder: index,
  }));

  try {
    course.lessons = await adminApi.reorderLessons(
      course.id,
      course.lessons.map((lesson) => lesson.id),
    );
    showToast("Đã lưu thứ tự bài học.");
  } catch (error) {
    course.lessons = previousLessons;
    console.error("Failed to reorder lessons:", error);
    showToast("Không thể lưu thứ tự bài học. Vui lòng thử lại.", "error");
  }
};

const createQuiz = () => {
  const courseId =
    selectedCourseId.value === ALL_COURSES ? undefined : selectedCourseId.value;
  router.push({
    path: navigationRoute("admin.quiz.create"),
    query: courseId ? { courseId } : {},
  });
};

const openQuizDefinition = (quiz: QuizDefinition) =>
  router.push(
    navigationRoute(quiz.lessonId ? "admin.quiz.lesson" : "admin.quiz.course", {
      scopeId: quiz.lessonId || quiz.courseId,
    }),
  );

const questionCourseLessons = computed(
  () =>
    courseStore.courses.find((course) => course.id === questionForm.value.courseId)
      ?.lessons || [],
);

watch(
  () => questionForm.value.courseId,
  () => {
    if (
      questionForm.value.lessonId &&
      !questionCourseLessons.value.some(
        (lesson) => lesson.id === questionForm.value.lessonId,
      )
    ) {
      questionForm.value.lessonId = "";
    }
  },
);

// Watch course changes to adjust selectedCourseId if it gets empty
watch(
  () => courseStore.courses,
  (newCourses) => {
    if (newCourses.length > 0 && !selectedCourseId.value) {
      selectedCourseId.value = newCourses[0].id;
    }
  },
  { immediate: true },
);

const clearDocumentPolling = () => {
  if (documentPollingTimer) clearTimeout(documentPollingTimer);
  documentPollingTimer = null;
};

const loadDocuments = async (silent = false) => {
  clearDocumentPolling();
  if (!selectedCourseId.value || selectedCourseId.value === ALL_COURSES) {
    courseDocuments.value = [];
    return;
  }
  if (!silent) documentListLoading.value = true;
  try {
    courseDocuments.value = await adminApi.getCourseDocuments(
      selectedCourseId.value,
    );
    if (courseDocuments.value.some(document => ["PENDING", "INDEXING"].includes(document.status))) {
      documentPollingTimer = setTimeout(() => {
        if (activeTab.value === "documents") void loadDocuments(true);
      }, 2500);
    }
  } catch (error) {
    console.error("Failed to load course documents:", error);
    showToast("Không thể tải trạng thái tài liệu.", "error");
  } finally {
    documentListLoading.value = false;
  }
};

const uploadDocument = async (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file || !selectedCourseId.value || selectedCourseId.value === ALL_COURSES)
    return;
  const extension = file.name.split(".").pop()?.toLowerCase();
  if (!extension || !["pdf", "docx"].includes(extension)) {
    showToast("Chỉ hỗ trợ tệp PDF hoặc DOCX.", "warning");
    input.value = "";
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
    input.value = "";
  }
};

const documentStatusLabel = (status: CourseDocument["status"]) => ({
  PENDING: "Đang chờ",
  INDEXING: "Đang embedding",
  INDEXED: "Đã index",
  FAILED: "Thất bại",
}[status]);

const formatDocumentDate = (value?: string) => value
  ? new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short" }).format(new Date(value))
  : "-";

watch(
  () => [route.name, route.query.tab, route.params.courseId],
  async ([routeName, tab, courseId]) => {
    if (routeName === "AdminCourseLessons" && typeof courseId === "string") {
      activeTab.value = "lessons";
      selectedCourseId.value = courseId;
      return;
    }
    activeTab.value =
      typeof tab === "string" && allowedAdminTabs.value.includes(tab)
        ? tab
        : "overview";
    if (activeTab.value === "lessons") selectedCourseId.value = ALL_COURSES;
    if (activeTab.value === "enrollments") {
      try {
        enrollmentRequests.value = await adminApi.getEnrollmentRequests();
      } catch (error) {
        console.error("Failed to refresh enrollment requests:", error);
      }
    }
    if (activeTab.value === "users" && users.value.length === 0) {
      users.value = await adminApi.getAllUsers();
    }
    if (activeTab.value === "exams" && questions.value.length === 0) {
      await loadQuestions();
    }
    if (activeTab.value === "documents") {
      if (
        selectedCourseId.value === ALL_COURSES &&
        courseStore.courses.length > 0
      ) {
        selectedCourseId.value = courseStore.courses[0].id;
      }
      void loadDocuments();
    } else {
      clearDocumentPolling();
    }
  },
  { immediate: true },
);

// Helper for user avatar color fallback
const getAvatarColor = (username: string) => {
  const user = users.value.find((u) => u.username === username);
  return user ? user.avatarColor : "#64748b";
};

// Get questions count per course
const getQuestionCountForCourse = (courseId: string) => {
  return questions.value.filter((q) => q.courseId === courseId).length;
};

// ==========================================
// ADVANCED CHARTS DATA & OPTIONS
// ==========================================

// 1. Line Chart: Daily Activity
const activityChartData = computed(() => {
  return {
    labels: [
      "Thứ 3",
      "Thứ 4",
      "Thứ 5",
      "Thứ 6",
      "Thứ 7",
      "Chủ Nhật",
      "Hôm nay",
    ],
    datasets: [
      {
        label: "Bài học hoàn thành",
        data: [18, 24, 15, 29, 32, 12, 28],
        borderColor: isDark.value ? "#62dcb5" : "#005B41", // BIDV Emerald Green
        backgroundColor: "rgba(0, 91, 65, 0.1)",
        fill: true,
        tension: 0.3,
        borderWidth: 3,
        pointBackgroundColor: "#D4AF37", // Gold point
        pointBorderColor: "#ffffff",
        pointRadius: 5,
      },
    ],
  };
});

const activityChartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: { display: false },
    tooltip: { padding: 10 },
  },
  scales: {
    y: {
      grid: { color: isDark.value ? "#35465a" : "#F1F5F9" },
      ticks: { color: isDark.value ? "#a8b8ca" : "#64748B", font: { family: "Arial, sans-serif", size: 11 } },
    },
    x: {
      grid: { display: false },
      ticks: { color: isDark.value ? "#a8b8ca" : "#64748B", font: { family: "Arial, sans-serif", size: 11 } },
    },
  },
}));

// 2. Doughnut Chart: Competency Level
const competencyChartData = computed(() => {
  // Group users dynamically
  let excellent = 0; // >= 2500 PTS
  let good = 0; // 1800 - 2499 PTS
  let average = 0; // 1000 - 1799 PTS
  let weak = 0; // < 1000 PTS

  users.value.forEach((u) => {
    if (u.role === "ROLE_ADMIN") return; // Skip admin
    if (u.points >= 2500) excellent++;
    else if (u.points >= 1800) good++;
    else if (u.points >= 1000) average++;
    else weak++;
  });

  return {
    labels: [
      "Xuất sắc (>=2500)",
      "Khá (1800-2499)",
      "Trung bình (1000-1799)",
      "Yếu (<1000)",
    ],
    datasets: [
      {
        data: [excellent || 2, good || 3, average || 2, weak || 0],
        backgroundColor: [
          "#005B41", // Emerald
          "#00825D", // Light Emerald
          "#D4AF37", // Gold Metallic
          "#94A3B8", // Slate Muted
        ],
        borderWidth: 2,
        borderColor: "#ffffff",
      },
    ],
  };
});

const competencyChartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      position: "right" as const,
      labels: {
        color: isDark.value ? "#e6edf5" : "#1E293B",
        font: { family: "Arial, sans-serif", size: 11, weight: "bold" as const },
        boxWidth: 12,
      },
    },
  },
}));

// 3. Bar Chart: Average Score by Course
const courseScoreChartData = computed(() => {
  // Let's calculate actual averages based on simulated points or actual answers
  // We can calculate dynamic averages. If no scores exist, provide mock representative data.
  const scores: Record<string, { sum: number; count: number }> = {};

  users.value.forEach((u) => {
    // If the mock system has quiz scores in localstorage or user record
    // We can simulate averages based on user points to keep it dynamic!
    courseStore.courses.forEach((t) => {
      if (!scores[t.id]) scores[t.id] = { sum: 0, count: 0 };

      // Let's create a dynamic mock score calculation based on the user's points
      const basePercentage = Math.min(
        60 + Math.round((u.points / 3000) * 40),
        100,
      );
      // Add minor random variance per course
      const randomVariance = (u.username.charCodeAt(0) % 15) - 7;
      const finalScore = Math.max(
        50,
        Math.min(100, basePercentage + randomVariance),
      );

      scores[t.id].sum += finalScore;
      scores[t.id].count += 1;
    });
  });

  const labels = courseStore.courses.map((t) => t.name);
  const dataValues = courseStore.courses.map((t) => {
    const entry = scores[t.id];
    return entry && entry.count > 0 ? Math.round(entry.sum / entry.count) : 75;
  });

  return {
    labels,
    datasets: [
      {
        label: "Tỷ lệ điểm trung bình (%)",
        data: dataValues,
        backgroundColor: "#005B41",
        borderRadius: 4,
        maxBarThickness: 32,
      },
    ],
  };
});

const courseScoreChartOptions = computed(() => ({
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: { display: false },
  },
  scales: {
    y: {
      min: 0,
      max: 100,
      grid: { color: isDark.value ? "#35465a" : "#F1F5F9" },
      ticks: { color: isDark.value ? "#a8b8ca" : "#64748B", font: { family: "Arial, sans-serif", size: 11 } },
    },
    x: {
      grid: { display: false },
      ticks: {
        color: isDark.value ? "#a8b8ca" : "#64748B",
        font: { family: "Arial, sans-serif", size: 10, weight: "bold" as const },
      },
    },
  },
}));

const enrollmentStatusLabel = (status: EnrollmentRequest["status"]) =>
  ({
    PENDING: "Chờ duyệt",
    APPROVED: "Đã duyệt",
    REJECTED: "Đã từ chối",
  })[status];
const formatEnrollmentDate = (value: string) =>
  new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "short",
    timeStyle: "short",
  }).format(new Date(value));
const reviewEnrollment = async (
  id: number,
  approve: boolean,
  rejectionReason?: string,
): Promise<boolean> => {
  try {
    const updated = await adminApi.reviewEnrollmentRequest(
      id,
      approve,
      rejectionReason,
    );
    const index = enrollmentRequests.value.findIndex(
      (request) => request.id === id,
    );
    if (index >= 0) enrollmentRequests.value[index] = updated;
    return true;
  } catch (error) {
    console.error("Failed to review enrollment:", error);
    showToast("Không thể xử lý yêu cầu đăng ký. Vui lòng thử lại.", "error");
    return false;
  }
};

const openRejectEnrollmentModal = (request: EnrollmentRequest) => {
  rejectEnrollmentModal.value = {
    show: true,
    request,
    reason: "",
    submitting: false,
  };
};

const closeRejectEnrollmentModal = () => {
  if (rejectEnrollmentModal.value.submitting) return;
  rejectEnrollmentModal.value.show = false;
  rejectEnrollmentModal.value.request = null;
  rejectEnrollmentModal.value.reason = "";
};

const confirmRejectEnrollment = async () => {
  const request = rejectEnrollmentModal.value.request;
  const reason = rejectEnrollmentModal.value.reason.trim();
  if (!request || !reason || rejectEnrollmentModal.value.submitting) return;

  rejectEnrollmentModal.value.submitting = true;
  const succeeded = await reviewEnrollment(request.id, false, reason);
  rejectEnrollmentModal.value.submitting = false;
  if (succeeded) closeRejectEnrollmentModal();
};

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
  const courseName = courseStore.courses.find(course => course.id === courseId)?.name || courseId;
  openConfirmationModal(
    "Xóa tài liệu tri thức AI",
    `Bạn có thực sự muốn xóa tài liệu “${document.fileName}” khỏi khóa “${courseName}”? Tệp, dữ liệu embedding và liên kết nguồn của bài học sẽ bị xóa. Hành động này không thể hoàn tác.`,
    async () => {
      try {
        await adminApi.deleteCourseDocument(courseId, document.id);
        if (selectedCourseId.value === courseId) {
          courseDocuments.value = courseDocuments.value.filter(item => item.id !== document.id);
        }
        showToast("Đã xóa tài liệu và dữ liệu embedding.");
      } catch (error: any) {
        showToast(error?.response?.data?.message || "Không thể xóa tài liệu. Vui lòng thử lại.", "error");
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

// ==========================================
// LOAD ALL DATA
// ==========================================
const loadQuestions = async () => {
  const requests = courseStore.courses.flatMap((course) => [
    adminApi.getQuizQuestions(course.id),
    ...course.lessons.map((lesson) => adminApi.getQuizQuestions(course.id, lesson.id)),
  ]);
  const results = await Promise.allSettled(requests);
  questions.value = results.flatMap((result) =>
    result.status === "fulfilled" ? (result.value as unknown as Question[]) : [],
  );
};

const loadAllData = async () => {
  loading.value = true;
  try {
    courseStore.courses = ["exams","documents"].includes(activeTab.value)?(await axiosClient.get("/api/admin/courses",{params:{page:activeTab.value}})).data:accessUser.can('courses')?(await axiosClient.get("/api/admin/courses")).data:[];
    const tasks: Promise<unknown>[] = [];
    if (["overview", "users"].includes(activeTab.value) && accessUser.can("users")) {
      tasks.push(adminApi.getAllUsers().then((value) => (users.value = value)));
    }
    if (["overview", "enrollments"].includes(activeTab.value) && accessUser.can("enrollments")) {
      tasks.push(adminApi.getEnrollmentRequests().then((value) => (enrollmentRequests.value = value)));
    }
    if (["overview", "exams"].includes(activeTab.value) && accessUser.can("exams")) tasks.push(loadQuestions());
    await Promise.all(tasks);

    if (courseStore.courses.length > 0 && !selectedCourseId.value) {
      selectedCourseId.value = courseStore.courses[0].id;
    }
    if (activeTab.value === "documents" && courseStore.courses.length > 0) {
      if (selectedCourseId.value === ALL_COURSES) {
        selectedCourseId.value = courseStore.courses[0].id;
      }
      await loadDocuments();
    }
  } catch (error) {
    console.error("Failed to load admin panel data:", error);
    showToast("Không thể tải dữ liệu quản trị. Vui lòng thử lại.", "error");
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadAllData();
});

onBeforeUnmount(clearDocumentPolling);

// ==========================================
// COURSE ACTIONS
// ==========================================
const saveCourse = async () => {
  loading.value = true;
  try {
    if (courseModal.value.isEdit) {
      await adminApi.updateCourse(
        courseForm.value.id,
        courseForm.value as unknown as Course,
      );
    } else {
      await adminApi.createCourse(courseForm.value as unknown as Course);
    }
    courseModal.value.show = false;
    await courseStore.fetchCourses(true);
    showToast(courseModal.value.isEdit ? "Cập nhật khóa học thành công." : "Tạo khóa học thành công.");
  } catch (err) {
    showToast("Không thể lưu khóa học. Vui lòng thử lại.", "error");
    console.error(err);
  } finally {
    loading.value = false;
  }
};

const handleDeleteCourse = (id: string) => {
  openConfirmationModal(
    "Xóa khóa học?",
    "Tất cả bài học và câu hỏi trực thuộc khóa học này cũng sẽ bị xóa. Thao tác không thể hoàn tác.",
    async () => {
      loading.value = true;
      try {
        await adminApi.deleteCourse(id);
        await loadAllData();
      } catch (err) {
        showToast("Xóa khóa học thất bại!", "error");
        console.error(err);
        throw err;
      } finally {
        loading.value = false;
      }
    },
  );
};

// ==========================================
// USER ACTIONS
// ==========================================
const openUserModal = (user?: AdminUser) => {
  if (user) {
    userModal.value = { show: true, isEdit: true, editId: user.id || null };
    userForm.value = {
      username: user.username,
      password: "",
      fullName: user.fullName,
      avatarColor: user.avatarColor || "#005B41",
      role: user.role,
      points: user.points,
      userGroupId: user.userGroupId || "",
      employeeId: user.employeeId || "",
      email: user.email || "",
    };
  } else {
    userModal.value = { show: true, isEdit: false, editId: null };
    userForm.value = {
      username: "",
      password: "",
      fullName: "",
      avatarColor: "#005B41",
      role: "ROLE_USER",
      points: 0,
      userGroupId: "",
      employeeId: "",
      email: "",
    };
  }
};

const excelFileInput = ref<HTMLInputElement | null>(null);

const handleDownloadTemplate = async () => {
  try {
    const blob = await adminApi.downloadUserTemplate();
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "mau-import-hoc-vien.xlsx";
    link.click();
    URL.revokeObjectURL(url);
  } catch (error) {
    console.error(error);
    showToast("Không thể tải file Excel mẫu.", "error");
  }
};

const handleExcelUpload = async (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  loading.value = true;
  try {
    await adminApi.importUsersExcel(file);
    await loadAllData();
    showToast("Nhập danh sách học viên thành công.", "success");
  } catch (error) {
    console.error(error);
    showToast("Không thể nhập danh sách học viên.", "error");
  } finally {
    loading.value = false;
    input.value = "";
  }
};

const saveUser = async () => {
  loading.value = true;
  try {
    if (userModal.value.isEdit && userModal.value.editId !== null) {
      await adminApi.updateUser(
        userModal.value.editId,
        userForm.value as AdminUser,
      );
    } else {
      await adminApi.createUser(userForm.value as AdminUser);
    }
    userModal.value.show = false;
    users.value = await adminApi.getAllUsers();
    showToast(userModal.value.isEdit ? "Cập nhật học viên thành công." : "Tạo học viên thành công.");
  } catch (err) {
    showToast("Không thể lưu học viên. Vui lòng thử lại.", "error");
    console.error(err);
  } finally {
    loading.value = false;
  }
};

const handleDeleteUser = (user: AdminUser) => {
  if (!user.id) return;
  openConfirmationModal(
    "Xóa học viên?",
    `Học viên ${user.fullName} (${user.username}) sẽ bị xóa khỏi hệ thống.`,
    async () => {
      loading.value = true;
      try {
        await adminApi.deleteUser(user.id!);
        await loadAllData();
      } catch (err) {
        showToast("Xóa học viên thất bại!", "error");
        console.error(err);
        throw err;
      } finally {
        loading.value = false;
      }
    },
  );
};

// ==========================================
// LESSON ACTIONS
// ==========================================
const openLessonModal = (lesson?: Lesson) => {
  if (lesson) {
    lessonModal.value = { show: true, isEdit: true };
    lessonForm.value = {
      id: lesson.id,
      title: lesson.title,
      courseId: lesson.courseId,
      hint: lesson.hint,
      videoSource:
        lesson.videoSource ||
        (lesson.videoUrl?.includes("/uploads/") ? "UPLOAD" : "URL"),
      videoUrl: lesson.videoUrl || "",
      videoFile: undefined,
      documents: (lesson.documents || []).map((document) => ({ ...document })),
    };
  } else {
    lessonModal.value = { show: true, isEdit: false };
    lessonForm.value = {
      id: "",
      title: "",
      courseId:
        selectedCourseId.value === ALL_COURSES
          ? courseStore.courses[0]?.id || ""
          : selectedCourseId.value,
      hint: "",
      videoSource: "UPLOAD",
      videoUrl: "",
      videoFile: undefined,
      documents: [],
    };
  }
};

const lessonModalBodyRef = ref<HTMLElement | null>(null);

const selectedVideoFileName = computed(() => {
  if (lessonForm.value.videoFile) return lessonForm.value.videoFile.name;
  if (lessonForm.value.videoSource === "UPLOAD" && lessonForm.value.videoUrl) {
    return decodeURIComponent(
      lessonForm.value.videoUrl.split("/").pop() || "Video đã tải lên",
    );
  }
  return "";
});

const handleVideoFileChange = (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;

  const error = validateVideoUpload(file, VIDEO_UPLOAD_MAX_BYTES);
  if (error) {
    showToast(error, "error");
    input.value = "";
    return;
  }
  lessonForm.value.videoFile = file;
};

const clearVideoFile = (event: MouseEvent) => {
  lessonForm.value.videoFile = undefined;
  lessonForm.value.videoUrl = "";
  const control = (event.currentTarget as HTMLElement).closest(
    ".file-upload-control",
  );
  const input = control?.querySelector<HTMLInputElement>('input[type="file"]');
  if (input) input.value = "";
};

const addLessonDocument = async () => {
  lessonForm.value.documents.push({
    name: "",
    documentType: "PDF",
    fileUrl: "",
    originalFileName: "",
  });

  await nextTick();
  const cards = lessonModalBodyRef.value?.querySelectorAll<HTMLElement>(
    ".document-form-card",
  );
  const newCard = cards?.[cards.length - 1];
  if (!newCard) return;

  newCard.scrollIntoView({ behavior: "smooth", block: "nearest" });
  window.setTimeout(() => {
    newCard.querySelector<HTMLInputElement>("input.form-control")?.focus({
      preventScroll: true,
    });
  }, 250);
};

const handleDocumentFileChange = (event: Event, index: number) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;

  const extension = file.name.split(".").pop()?.toLowerCase();
  if (!["pdf", "doc", "docx", "ppt", "pptx"].includes(extension || "")) {
    showToast("Chỉ hỗ trợ file PDF, DOC, DOCX, PPT hoặc PPTX.", "warning");
    input.value = "";
    return;
  }

  const document = lessonForm.value.documents[index];
  document.file = file;
  document.originalFileName = file.name;
  document.fileSize = file.size;
  document.documentType =
    extension === "pdf"
      ? "PDF"
      : ["ppt", "pptx"].includes(extension || "")
        ? "POWERPOINT"
        : "WORD";
  if (!document.name.trim()) document.name = file.name.replace(/\.[^.]+$/, "");
};

const formatFileSize = (size?: number) => {
  if (!size) return "";
  return size < 1024 * 1024
    ? `${Math.ceil(size / 1024)} KB`
    : `${(size / 1024 / 1024).toFixed(1)} MB`;
};

const clearDocumentFile = (index: number, event: MouseEvent) => {
  const document = lessonForm.value.documents[index];
  document.file = undefined;
  document.fileUrl = "";
  document.originalFileName = "";
  document.fileSize = undefined;
  const control = (event.currentTarget as HTMLElement).closest(
    ".file-upload-control",
  );
  const input = control?.querySelector<HTMLInputElement>('input[type="file"]');
  if (input) input.value = "";
};

const removeLessonDocument = (index: number) => {
  lessonForm.value.documents.splice(index, 1);
};

const saveLesson = async () => {
  if (loading.value) return;
  loading.value = true;
  videoUploadProgress.value = null;
  try {
    const videoUrl =
      lessonForm.value.videoSource === "UPLOAD" && lessonForm.value.videoFile
        ? await adminApi.uploadVideo(lessonForm.value.videoFile, percent => { videoUploadProgress.value = percent; })
        : lessonForm.value.videoUrl.trim();
    videoUploadProgress.value = null;
    const documents = await Promise.all(
      lessonForm.value.documents.map(async (document, index) => {
        const uploaded = document.file
          ? await adminApi.uploadDocument(document.file)
          : document;
        return {
          id: uploaded.id,
          name: document.name || uploaded.name,
          documentType: uploaded.documentType,
          fileUrl: uploaded.fileUrl,
          originalFileName: uploaded.originalFileName,
          fileSize: uploaded.fileSize,
          documentOrder: index,
        } satisfies LessonDocument;
      }),
    );

    const payload = {
      ...lessonForm.value,
      videoUrl,
      documents,
      procedureSteps: [],
    };
    delete (payload as Partial<typeof lessonForm.value>).videoFile;

    if (lessonModal.value.isEdit) {
      await adminApi.updateLesson(lessonForm.value.id, payload);
    } else {
      await adminApi.createLesson(payload);
    }

    lessonModal.value.show = false;
    await courseStore.fetchCourses(true);
    showToast(lessonModal.value.isEdit ? "Cập nhật bài học thành công." : "Tạo bài học thành công.");
  } catch (err) {
    const serverMessage = (err as { response?: { data?: { message?: string } } }).response?.data?.message;
    showToast(serverMessage || (err instanceof Error ? err.message : "Không thể lưu bài học. Vui lòng kiểm tra dữ liệu và thử lại."), "error");
    console.error(err);
  } finally {
    videoUploadProgress.value = null;
    loading.value = false;
  }
};

const handleDeleteLesson = (id: string) => {
  openConfirmationModal(
    "Xóa bài học?",
    "Bài học này sẽ bị xóa khỏi khóa học. Thao tác không thể hoàn tác.",
    async () => {
      loading.value = true;
      try {
        await adminApi.deleteLesson(id);
        await loadAllData();
      } catch (err) {
        showToast("Xóa bài học thất bại!", "error");
        console.error(err);
        throw err;
      } finally {
        loading.value = false;
      }
    },
  );
};

// ==========================================
// QUESTION ACTIONS
// ==========================================
const openQuestionModal = (question?: Question) => {
  if (question) {
    questionModal.value = { show: true, isEdit: true, editId: question.id };
    questionForm.value = {
      text: question.text,
      courseId: question.courseId,
      lessonId: question.lessonId || "",
      correctAnswer: question.correctAnswer,
      options: [],
    };
    optionsInputs.value = [
      question.options[0] || "",
      question.options[1] || "",
      question.options[2] || "",
      question.options[3] || "",
    ];
  } else {
    questionModal.value = { show: true, isEdit: false, editId: null };
    questionForm.value = {
      text: "",
      courseId:
        selectedCourseId.value === ALL_COURSES
          ? courseStore.courses[0]?.id || ""
          : selectedCourseId.value,
      lessonId: "",
      correctAnswer: 0,
      options: [],
    };
    optionsInputs.value = ["", "", "", ""];
  }
};

const saveQuestion = async () => {
  loading.value = true;
  try {
    const opts = optionsInputs.value
      .map((o) => o.trim())
      .filter((o) => o.length > 0);
    if (opts.length !== 4) {
      showToast("Vui lòng nhập đầy đủ 4 đáp án trước khi lưu.", "warning");
      loading.value = false;
      return;
    }

    const payload = {
      ...questionForm.value,
      options: opts,
    };

    if (questionModal.value.isEdit && questionModal.value.editId !== null) {
      const fullPayload = {
        ...payload,
        id: questionModal.value.editId,
      } as Question;
      await adminApi.updateQuestion(questionModal.value.editId, fullPayload);
    } else {
      await adminApi.createQuestion(payload);
    }

    questionModal.value.show = false;
    await loadQuestions();
    showToast(questionModal.value.isEdit ? "Cập nhật câu hỏi thành công." : "Tạo câu hỏi thành công.");
  } catch (err) {
    showToast("Không thể lưu câu hỏi trắc nghiệm.", "error");
    console.error(err);
  } finally {
    loading.value = false;
  }
};

const escapeCsv = (value: string) => `"${value.replace(/"/g, '""')}"`;

const downloadQuestionTemplate = () => {
  const courseId =
    selectedCourseId.value === ALL_COURSES
      ? courseStore.courses[0]?.id || "CIF"
      : selectedCourseId.value || courseStore.courses[0]?.id || "CIF";
  const lessonId =
    courseStore.courses.find((course) => course.id === courseId)?.lessons[0]?.id ||
    "";
  const rows = [
    [
      "courseId",
      "lessonId",
      "question",
      "optionA",
      "optionB",
      "optionC",
      "optionD",
      "correctAnswer",
    ],
    [
      courseId,
      "",
      "Câu hỏi mẫu cho khóa học?",
      "Đáp án A",
      "Đáp án B",
      "Đáp án C",
      "Đáp án D",
      "A",
    ],
    [
      courseId,
      lessonId,
      "Câu hỏi mẫu cho bài học?",
      "Đáp án A",
      "Đáp án B",
      "Đáp án C",
      "Đáp án D",
      "B",
    ],
  ];
  const content =
    "\uFEFF" + rows.map((row) => row.map(escapeCsv).join(",")).join("\r\n");
  const url = URL.createObjectURL(
    new Blob([content], { type: "text/csv;charset=utf-8" }),
  );
  const link = window.document.createElement("a");
  link.href = url;
  link.download = "mau-import-cau-hoi.csv";
  link.click();
  URL.revokeObjectURL(url);
};

const parseCsv = (content: string): string[][] => {
  const rows: string[][] = [];
  let row: string[] = [];
  let value = "";
  let quoted = false;
  for (let index = 0; index < content.length; index++) {
    const char = content[index];
    if (char === '"') {
      if (quoted && content[index + 1] === '"') {
        value += '"';
        index++;
      } else quoted = !quoted;
    } else if (char === "," && !quoted) {
      row.push(value.trim());
      value = "";
    } else if ((char === "\n" || char === "\r") && !quoted) {
      if (char === "\r" && content[index + 1] === "\n") index++;
      row.push(value.trim());
      if (row.some(Boolean)) rows.push(row);
      row = [];
      value = "";
    } else value += char;
  }
  row.push(value.trim());
  if (row.some(Boolean)) rows.push(row);
  return rows;
};

const importQuestionsFromCsv = async (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  try {
    const rows = parseCsv((await file.text()).replace(/^\uFEFF/, ""));
    const expected = [
      "courseId",
      "lessonId",
      "question",
      "optionA",
      "optionB",
      "optionC",
      "optionD",
      "correctAnswer",
    ];
    if (
      !rows.length ||
      expected.some((name, index) => rows[0]?.[index] !== name)
    ) {
      throw new Error(`Dòng tiêu đề phải đúng thứ tự: ${expected.join(", ")}`);
    }
    const errors: string[] = [];
    const payloads = rows.slice(1).map((row, index) => {
      const line = index + 2;
      const [courseId, lessonId, text, ...rest] = row;
      const options = rest.slice(0, 4);
      const answerText = (rest[4] || "").toUpperCase();
      const correctAnswer = ["A", "B", "C", "D"].indexOf(answerText);
      const course = courseStore.courses.find((item) => item.id === courseId);
      if (!course)
        errors.push(`Dòng ${line}: courseId "${courseId}" không tồn tại.`);
      if (
        lessonId &&
        !course?.lessons.some((lesson) => lesson.id === lessonId)
      ) {
        errors.push(`Dòng ${line}: lessonId "${lessonId}" không thuộc khóa học.`);
      }
      if (!text?.trim()) errors.push(`Dòng ${line}: thiếu nội dung câu hỏi.`);
      if (options.length !== 4 || options.some((option) => !option?.trim())) {
        errors.push(`Dòng ${line}: phải có đủ 4 đáp án A, B, C, D.`);
      }
      if (correctAnswer < 0)
        errors.push(`Dòng ${line}: correctAnswer phải là A, B, C hoặc D.`);
      return {
        courseId,
        lessonId: lessonId || undefined,
        text,
        options,
        correctAnswer,
      };
    });
    if (!payloads.length) errors.push("File không có dòng dữ liệu.");
    if (errors.length) {
      showToast(`Không thể import vì file chưa đúng định dạng:\n\n${errors.slice(0, 12).join("\n")}`, "warning");
      return;
    }
    loading.value = true;
    await Promise.all(
      payloads.map((payload) => adminApi.createQuestion(payload)),
    );
    await loadAllData();
    showToast(`Đã import thành công ${payloads.length} câu hỏi.`, "success");
  } catch (error) {
    showToast(error instanceof Error ? error.message : "Không thể đọc file CSV.", "error");
  } finally {
    loading.value = false;
    input.value = "";
  }
};

const handleDeleteQuestion = (id: number) => {
  openConfirmationModal(
    "Xóa câu hỏi?",
    "Câu hỏi này sẽ bị xóa vĩnh viễn. Thao tác không thể hoàn tác.",
    async () => {
      loading.value = true;
      try {
        await adminApi.deleteQuestion(id);
        await loadAllData();
      } catch (err) {
        showToast("Xóa câu hỏi thất bại!", "error");
        console.error(err);
        throw err;
      } finally {
        loading.value = false;
      }
    },
  );
};

// Kept temporarily for compatibility with the existing question modal markup;
// the active management flow now lives on AdminQuizDetail.
void openQuestionModal;
void downloadQuestionTemplate;
void importQuestionsFromCsv;
void handleDeleteQuestion;
</script>

<style scoped>
.admin-table{font-size:.875rem}.admin-table th{text-transform:none!important;font-size:.75rem;white-space:nowrap}.card-title{text-transform:none!important}.admin-page-heading{margin-bottom:1.6rem}

.legacy-lesson-fields{overflow:visible;min-height:0}.admin-table-scroll{width:100%;overflow-x:auto}.admin-table small{display:block;margin-top:.35rem;color:var(--text-muted);font-size:.75rem}

/* Redesigned Sidebar Layout */
.admin-portal-layout {
  display: flex;
  min-height: 100%;
  background-color: var(--surface-ffffff);
  font-family: var(--font-family);
}

/* Sidebar styling */
.admin-sidebar {
  display: none;
  width: 280px;
  background-color: var(--primary-color); /* Emerald Green */
  color: white;
  flex-direction: column;
  border-right: 1px solid var(--border-color);
  flex-shrink: 0;
  box-shadow: 2px 0 10px rgba(0, 0, 0, 0.05);
}

.sidebar-menu {
  padding: 2rem 1rem 1.5rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  flex: 1;
}

.sidebar-item {
  background: none;
  border: none;
  text-align: left;
  color: rgba(255, 255, 255, 0.75);
  font-size: 0.95rem;
  font-weight: 600;
  padding: 0.85rem 1.25rem;
  border-radius: var(--radius-md);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  transition: var(--transition);
}

.sidebar-item:hover {
  color: white;
  background-color: rgba(255, 255, 255, 0.08);
}

.sidebar-item.active {
  color: var(--primary-ink);
  background-color: var(--surface-ffffff); /* White tab bg for clean contrast */
  box-shadow: var(--shadow-sm);
}

.sidebar-icon {
  width: 18px;
  height: 18px;
}

.sidebar-footer {
  padding: 1.5rem;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
}

.admin-role-badge {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background-color: rgba(255, 255, 255, 0.1);
  color: white;
  padding: 0.6rem 1rem;
  border-radius: var(--radius-full);
  font-size: 0.8rem;
  font-weight: 700;
}

.admin-badge-icon {
  width: 14px;
  height: 14px;
}

.spinning {
  animation: spin 8s linear infinite;
}

@keyframes spin {
  100% {
    transform: rotate(360deg);
  }
}

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

.admin-top-header {
  padding: 1.5rem 2rem;
  border-left: 5px solid var(--primary-color);
  background: var(--surface-ffffff);
  box-shadow: var(--shadow-sm);
}

.banner-tag {
  font-size: 0.7rem;
  font-weight: 700;
  color: var(--accent-secondary);
  letter-spacing: 1.2px;
}

.admin-top-header h1 {
  font-size: 1.6rem;
  color: var(--primary-ink);
  font-weight: 700;
  margin-top: 0.1rem;
}

.header-desc {
  font-size: 0.85rem;
  color: var(--text-muted);
}

/* Stats Cards Grid */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 1.25rem;
  margin-bottom: 1.5rem;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 1.25rem;
  padding: 1.5rem;
  background: var(--surface-ffffff);
}

.stat-icon-box {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  position: relative;
}

.stat-icon {
  width: 22px;
  height: 22px;
}

.users-bg {
  background-color: #3b82f6;
}
.online-bg {
  background-color: #10b981;
}
.lessons-bg {
  background-color: var(--primary-color);
}
.questions-bg {
  background-color: var(--surface-8b5cf6);
}

.online-indicator-ping {
  position: absolute;
  top: -3px;
  right: -3px;
  width: 10px;
  height: 10px;
  background-color: var(--surface-ef4444);
  border: 2px solid var(--line-ffffff);
  border-radius: 50%;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0% {
    transform: scale(0.9);
    opacity: 1;
  }
  50% {
    transform: scale(1.3);
    opacity: 0.5;
  }
  100% {
    transform: scale(0.9);
    opacity: 1;
  }
}

.stat-details h3 {
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text-main);
  display: flex;
  align-items: center;
  gap: 0.4rem;
}

.active-badge-lbl {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--ink-10b981);
  background-color: var(--surface-dcfce7);
  padding: 0.1rem 0.4rem;
  border-radius: var(--radius-sm);
}

.stat-label {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

/* Charts Rows styling */
.charts-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: 1.5rem;
  margin-bottom: 1.5rem;
}

.charts-row.split-grid {
  grid-template-columns: 3fr 2fr;
}

@media (max-width: 1024px) {
  .charts-row.split-grid {
    grid-template-columns: 1fr;
  }
}

.chart-card {
  padding: 1.5rem;
  background: var(--surface-ffffff);
}

.chart-container {
  position: relative;
  height: 250px;
  width: 100%;
  margin-top: 1rem;
}

/* Real-time Sessions list Card */
.sessions-card {
  padding: 1.5rem;
  background: var(--surface-ffffff);
  display: flex;
  flex-direction: column;
}

.sessions-list {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  margin-top: 1.25rem;
  max-height: 250px;
  overflow-y: auto;
  padding-right: 0.25rem;
}

.session-row {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  padding: 0.75rem;
  background-color: var(--bg-main);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
  transition: var(--transition);
}

.session-row:hover {
  transform: translateX(3px);
  border-color: var(--primary-light);
}

.session-user-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  color: white;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.85rem;
  box-shadow: var(--shadow-sm);
}

.session-info {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
}

.session-name {
  font-size: 0.85rem;
  color: var(--text-main);
}

.session-action {
  color: var(--text-muted);
  font-size: 0.8rem;
  margin-left: 0.35rem;
}

.session-detail {
  font-size: 0.8rem;
  color: var(--primary-ink);
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.session-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 0.2rem;
  font-size: 0.75rem;
}

.session-course-tag {
  background-color: var(--primary-light);
  color: var(--primary-ink);
  font-weight: 700;
  padding: 0.1rem 0.4rem;
  border-radius: var(--radius-sm);
  font-size: 0.7rem;
}

.session-time {
  color: var(--text-muted);
}

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

.card-header-row .card-subtitle {
  margin: 0.4rem 0 0;
  line-height: 1.5;
  color: var(--text-muted);
}

.card-header-row > .btn {
  flex: 0 0 auto;
  margin-left: auto;
}

.course-selector-row {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  margin-bottom: 1rem;
}

.course-selector-row label {
  flex: 0 0 auto;
  font-weight: 500;
}

.course-selector-row .select-control {
  width: 170px;
}

.courses-summary,
.users-manager,
.lessons-manager,
.exams-manager {
  background: var(--surface-ffffff);
  padding: 1.8rem;
  box-shadow: var(--shadow-sm);
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

.code-badge {
  font-family: var(--font-family);
  background-color: var(--bg-main);
  color: var(--primary-ink);
  padding: 0.25rem 0.5rem;
  border-radius: var(--radius-sm);
  font-weight: 700;
  font-size: 0.8rem;
  border: 1px solid var(--border-color);
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
.user-cell {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.user-avatar-circle {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: white;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}

.role-tag {
  font-size: 0.75rem;
  font-weight: 700;
  padding: 0.2rem 0.5rem;
  border-radius: var(--radius-full);
}

.role-tag.admin {
  background-color: var(--surface-fee2e2);
  color: var(--ink-ef4444);
}

.role-tag.user {
  background-color: var(--surface-e0f2fe);
  color: var(--ink-0369a1);
}

/* Modal details */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(15, 23, 42, 0.4);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 1.25rem;
  overflow-y: auto;
}

.modal-content {
  width: 100%;
  max-width: 480px;
  max-height: calc(100vh - 2.5rem);
  overflow-y: auto;
  padding: 2rem;
  background: var(--surface-ffffff);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
}

.modal-large {
  max-width: 650px;
}

.lesson-modal-content {
  width: min(960px, 100%);
  max-width: 960px;
  max-height: calc(100dvh - 2.5rem);
  padding: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.lesson-modal-header {
  flex: 0 0 auto;
  padding: 1.35rem 1.75rem 0;
  background: var(--surface-ffffff);
}

.lesson-modal-header h3 {
  margin-bottom: 0;
  padding-bottom: 0.75rem;
}

.lesson-modal-form {
  min-height: 0;
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
}

.lesson-modal-body {
  min-height: 0;
  flex: 1 1 auto;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
  padding: 1.25rem 1.75rem 0.5rem;
}

.lesson-modal-footer {
  flex: 0 0 auto;
  margin-top: 0;
  padding: 1rem 1.75rem;
  border-top: 1px solid var(--border-color);
  background: var(--surface-ffffff);
  box-shadow: 0 -8px 20px rgba(15, 35, 29, 0.05);
}

.modal-content h3 {
  font-size: 1.15rem;
  color: var(--primary-ink);
  margin-bottom: 1.25rem;
  border-bottom: 2px solid var(--primary-light);
  padding-bottom: 0.5rem;
  font-weight: 800;
}

.form-group {
  margin-bottom: 1.15rem;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.form-group label {
  font-size: 0.75rem;
  font-weight: 700;
  color: var(--text-muted);
  text-transform: uppercase;
}

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

.modal-buttons {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 1.5rem;
}

.document-form-card {
  position: relative;
  margin-top: 0.75rem;
  padding: 0.9rem 3.25rem 0.9rem 0.9rem;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-main);
}

.document-form-index {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  margin-bottom: 0.7rem;
  color: var(--primary-ink);
  font-size: 0.8rem;
  font-weight: 700;
}

.document-form-fields {
  display: grid;
  grid-template-columns: minmax(180px, 0.8fr) minmax(260px, 1.2fr);
  gap: 0.8rem;
}

.document-form-fields .form-group {
  margin-bottom: 0;
}

.document-form-fields label {
  display: block;
  margin-bottom: 0.35rem;
  font-size: 0.72rem;
}

.file-picker {
  flex: 1;
  min-width: 0;
  min-height: 42px;
  padding: 0.55rem 0.75rem;
  display: flex !important;
  align-items: center;
  gap: 0.55rem;
  margin: 0 !important;
  border: 1px dashed var(--line-9ab9ae);
  border-radius: var(--radius-sm);
  background: var(--surface-ffffff);
  color: var(--primary-ink);
  cursor: pointer;
}

.exam-header-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.55rem;
}

.import-questions-btn {
  margin: 0 !important;
  cursor: pointer;
}

.import-questions-btn input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.q-scope-tag {
  display: inline-flex;
  margin-left: 0.4rem;
  padding: 0.2rem 0.45rem;
  border-radius: 999px;
  background: var(--primary-light);
  color: var(--primary-ink);
  font-size: 0.66rem;
  font-weight: 700;
}

.field-help {
  display: block;
  margin-top: 0.4rem;
  color: var(--text-muted);
  font-size: 0.72rem;
  line-height: 1.45;
}

.quiz-list-toolbar {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  max-width: 520px;
  margin: 1rem 0;
  padding: 0 0.8rem;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--surface-ffffff);
  color: var(--text-muted);
}

.quiz-list-toolbar input {
  width: 100%;
  height: 42px;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text-color);
}

.quiz-definition-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 0.8rem;
}

.quiz-definition-card {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  padding: 1rem;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--surface-ffffff);
  color: var(--text-color);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.2s,
    box-shadow 0.2s,
    transform 0.2s;
}

.quiz-definition-card:hover {
  border-color: var(--primary-color);
  box-shadow: var(--shadow-sm);
  transform: translateY(-1px);
}

.quiz-definition-icon {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  flex: 0 0 44px;
  border-radius: 12px;
  background: var(--primary-light);
  color: var(--primary-ink);
}

.quiz-definition-copy {
  min-width: 0;
  flex: 1;
}

.quiz-definition-copy h4 {
  margin: 0.35rem 0 0.2rem;
  font-size: 0.9rem;
}

.quiz-definition-copy p {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.75rem;
}

.pending-count {
  padding: 0.45rem 0.7rem;
  border-radius: var(--radius-full);
  background: var(--surface-fff7d6);
  color: var(--ink-806710);
  font-weight: 700;
  font-size: 0.78rem;
}
.lesson-back-button {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 0.75rem;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--primary-ink);
  font-weight: 700;
  cursor: pointer;
}
.lesson-back-button:hover {
  text-decoration: underline;
}
.lesson-course-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 1rem;
}
.lesson-course-card {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  width: 100%;
  padding: 1rem;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--surface-ffffff);
  color: var(--text-color);
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}
.lesson-course-card:hover {
  border-color: var(--primary-color);
  box-shadow: 0 6px 16px rgb(0 103 79 / 12%);
  transform: translateY(-2px);
}
.lesson-course-icon {
  display: grid;
  place-items: center;
  flex: 0 0 44px;
  height: 44px;
  border-radius: 10px;
  background: var(--primary-light);
  color: var(--primary-ink);
}
.lesson-course-copy {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.25rem;
}
.lesson-course-copy small {
  color: var(--text-muted);
}
.lesson-order-table tbody tr {
  transition:
    background-color 0.15s ease,
    opacity 0.15s ease;
}
.lesson-order-table tbody tr.dragging {
  opacity: 0.45;
}
.lesson-order-table tbody tr.drag-over td {
  border-top: 2px solid var(--primary-color);
  background: var(--primary-light);
}
.drag-column {
  width: 88px;
  text-align: center !important;
}
.drag-handle {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--text-muted);
  cursor: grab;
  user-select: none;
}
.drag-handle:active {
  cursor: grabbing;
}
.enrollment-status {
  display: inline-flex;
  padding: 0.3rem 0.55rem;
  border-radius: var(--radius-full);
  font-size: 0.72rem;
  font-weight: 700;
}
.enrollment-status.pending {
  background: var(--surface-fff7d6);
  color: var(--ink-806710);
}
.enrollment-status.approved {
  background: var(--surface-e8f5ef);
  color: var(--primary-ink);
}
.enrollment-status.rejected {
  background: var(--surface-feecec);
  color: var(--ink-b42318);
}
.enrollment-status-detail {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.45rem;
}
.enrollment-actions {
  display: flex;
  gap: 0.4rem;
}
.enrollment-table th,
.enrollment-table td {
  text-align: center;
  vertical-align: middle;
}
.enrollment-table .enrollment-actions {
  justify-content: center;
}
.enrollment-actions button {
  padding: 0.45rem 0.6rem;
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  border-radius: 6px;
  cursor: pointer;
  font-weight: 700;
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}
.approve-request {
  border: 1px solid var(--primary-color);
  background: var(--primary-color);
  color: white;
}
.approve-request:hover {
  background: #005a46;
  border-color: #005a46;
  box-shadow: 0 4px 10px rgb(0 103 79 / 22%);
  transform: translateY(-1px);
}
.reject-request {
  border: 1px solid #ef4444;
  background: var(--surface-ffffff);
  color: var(--ink-b42318);
}
.reject-request:hover {
  background: #dc2626;
  border-color: #dc2626;
  color: white;
  box-shadow: 0 4px 10px rgb(220 38 38 / 20%);
  transform: translateY(-1px);
}
.enrollment-actions button:active {
  box-shadow: none;
  transform: translateY(0);
}

.rejection-reason {
  display: inline-flex;
  max-width: 280px;
  gap: 0.25rem;
  color: var(--ink-b42318);
  line-height: 1.35;
  text-align: left;
}

.reject-enrollment-modal {
  width: min(520px, calc(100vw - 2rem));
  padding: 1.75rem;
}

.reject-modal-icon {
  width: 52px;
  height: 52px;
  margin-bottom: 1rem;
  display: grid;
  place-items: center;
  border-radius: 14px;
  background: var(--surface-feecec);
  color: var(--ink-b42318);
}

.reject-modal-heading > span {
  color: var(--ink-b42318);
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.reject-modal-heading h3 {
  margin: 0.3rem 0 0.45rem;
  color: var(--text-main);
  font-size: 1.25rem;
}

.reject-modal-heading p {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.86rem;
}

.reject-request-summary {
  margin: 1.25rem 0;
  padding: 0.9rem 1rem;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--surface-f8fafc);
}

.reject-request-summary > div {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.reject-request-summary small,
.reject-request-summary span {
  color: var(--text-muted);
  font-size: 0.72rem;
}

.reject-request-summary strong {
  color: var(--text-main);
  font-size: 0.88rem;
}

.reject-enrollment-modal textarea {
  width: 100%;
  padding: 0.75rem 0.85rem;
  resize: vertical;
  border: 1px solid var(--line-cbd5e1);
  border-radius: 9px;
  outline: none;
  color: var(--text-main);
  line-height: 1.5;
}

.reject-enrollment-modal textarea:focus {
  border-color: #dc2626;
  box-shadow: 0 0 0 3px rgb(220 38 38 / 10%);
}

.required-mark {
  color: var(--ink-dc2626);
}

.reason-counter {
  margin-top: 0.25rem;
  color: var(--text-muted);
  font-size: 0.7rem;
  text-align: right;
}

.reject-confirm-button {
  background: #dc2626;
  color: white;
}

.reject-confirm-button:hover:not(:disabled) {
  background: #b91c1c;
}

.reject-confirm-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.file-upload-control {
  display: flex;
  align-items: stretch;
  gap: 0.45rem;
}

.file-clear-btn {
  width: 38px;
  flex: 0 0 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--line-f0c5c5);
  border-radius: var(--radius-sm);
  background: var(--surface-fff7f7);
  color: var(--ink-b42318);
  cursor: pointer;
  transition:
    border-color 0.2s,
    background 0.2s;
}

.file-clear-btn:hover {
  border-color: #d92d20;
  background: var(--surface-fee4e2);
}

.file-clear-btn:focus-visible {
  outline: 2px solid #d92d20;
  outline-offset: 2px;
}

.file-picker:hover {
  border-color: var(--primary-color);
  background: var(--primary-light);
}

.file-picker span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 0.82rem;
  font-weight: 600;
}

.file-picker input {
  position: absolute;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
}

.video-source-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.65rem;
}

.video-source-option {
  display: flex !important;
  align-items: center;
  gap: 0.65rem;
  min-height: 64px;
  margin: 0 !important;
  padding: 0.7rem 0.8rem;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--surface-ffffff);
  color: var(--text-muted);
  cursor: pointer;
  transition:
    border-color 0.2s,
    background 0.2s,
    color 0.2s;
}

.video-source-option:hover {
  border-color: var(--line-9ab9ae);
}

.video-source-option.active {
  border-color: var(--primary-color);
  background: var(--primary-light);
  color: var(--primary-ink);
  box-shadow: 0 0 0 1px var(--primary-color);
}

.video-source-option input {
  width: 16px;
  height: 16px;
  margin: 0;
  accent-color: var(--primary-color);
}

.video-source-option > span {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.video-source-option strong {
  font-size: 0.82rem;
}

.video-source-option small {
  color: var(--text-muted);
  font-size: 0.7rem;
  font-weight: 400;
}

.video-source-panel {
  margin-top: 0.75rem;
}

.video-source-help {
  display: block;
  margin-top: 0.4rem;
  color: var(--text-muted);
  font-size: 0.72rem;
}

.selected-video-note {
  display: block;
  margin-top: 0.5rem;
  color: var(--primary-ink);
}

.selected-file-meta {
  display: block;
  margin-top: 0.3rem;
  color: var(--text-muted);
  font-size: 0.7rem;
}

.document-remove {
  position: absolute;
  top: 50%;
  right: 0.8rem;
  transform: translateY(-50%);
}

@media (max-width: 700px) {
  .reject-request-summary {
    grid-template-columns: 1fr;
  }

  .video-source-options {
    grid-template-columns: 1fr;
  }

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

  .course-selector-row {
    align-items: stretch;
    flex-direction: column;
  }

  .course-selector-row .select-control {
    width: 100%;
  }

  .modal-overlay {
    padding: 0.5rem;
  }

  .lesson-modal-content {
    max-height: calc(100dvh - 1rem);
  }

  .lesson-modal-header {
    padding: 1rem 1rem 0;
  }

  .lesson-modal-body {
    padding: 1rem 1rem 0.25rem;
    scrollbar-gutter: auto;
  }

  .lesson-modal-footer {
    padding: 0.8rem 1rem;
  }

  .document-form-fields {
    grid-template-columns: 1fr;
  }
}

.loading-overlay {
  width: 100%;
  min-height: 100%;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  color: var(--text-muted);
  text-align: center;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--primary-light);
  border-top-color: var(--primary-color);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

select:disabled {
  background-color: var(--surface-f3f4f6);
  color: var(--ink-6b7280);
  border: 1px solid var(--line-d1d5db);
  cursor: not-allowed;
  opacity: 1;
}

.rag-documents-manager {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.rag-document-controls {
  display: grid;
  grid-template-columns: minmax(240px, 0.8fr) minmax(420px, 2fr);
  gap: 1rem;
  align-items: end;
}

.rag-upload-box {
  min-height: 82px;
  display: flex;
  align-items: center;
  gap: 0.85rem;
  padding: 1rem;
  border: 1px dashed var(--line-9db9ae);
  border-radius: var(--radius-md);
  background: var(--surface-f4f8f6);
  color: var(--primary-ink);
}

.rag-upload-box > div {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.rag-upload-box small {
  color: var(--text-muted);
}

.rag-upload-message {
  margin: -0.45rem 0 0;
  color: var(--primary-ink);
  font-weight: 600;
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

.rag-error-cell {
  min-width: 0;
  color: var(--ink-b91c1c);
  white-space: normal;
  word-break: break-word;
  overflow-wrap: anywhere;
}

.rag-error-cell span {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
  line-clamp: 3;
}

.rag-document-table-scroll {
  min-width: 0;
  max-width: 100%;
  overflow-x: auto;
}

.rag-document-table {
  table-layout: fixed;
  min-width: 760px;
}

.rag-document-table th,
.rag-document-table td {
  box-sizing: border-box;
  padding: 0.9rem 0.55rem;
  overflow-wrap: anywhere;
}

.rag-document-table th:nth-child(1) { width: 24%; }
.rag-document-table th:nth-child(2) { width: 10%; }
.rag-document-table th:nth-child(3) { width: 12%; }
.rag-document-table th:nth-child(4) { width: 7%; }
.rag-document-table th:nth-child(5) { width: 16%; }
.rag-document-table th:nth-child(6) { width: 21%; }
.rag-document-table th:nth-child(7) { width: 10%; }

.rag-document-table .rag-document-actions {
  position: sticky;
  right: 0;
  background: var(--surface-ffffff);
  text-align: right;
}

.rag-document-table .rag-document-actions .btn {
  padding: 0.5rem;
  white-space: nowrap;
}

.rag-empty-cell {
  padding: 2rem !important;
  color: var(--text-muted);
  text-align: center;
}

.spin-icon {
  animation: spin 0.9s linear infinite;
}

@media (max-width: 900px) {
  .rag-document-controls {
    grid-template-columns: 1fr;
  }

  .rag-upload-box {
    align-items: flex-start;
    flex-wrap: wrap;
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
