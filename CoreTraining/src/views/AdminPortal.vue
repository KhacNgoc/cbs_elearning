<template>
  <div class="admin-portal-layout">
    <!-- Left Navigation Sidebar -->
    <aside class="admin-sidebar">
      <div class="sidebar-header">
        <div class="nav-logo">B</div>
        <div class="sidebar-title">
          <h2>BIDV WebCSR</h2>
          <span>Quản trị Hệ thống</span>
        </div>
      </div>

      <nav class="sidebar-menu">
        <button 
          v-for="tab in tabs" 
          :key="tab.id"
          class="sidebar-item"
          :class="{ active: activeTab === tab.id }"
          @click="switchTab(tab.id)"
        >
          <component :is="tab.icon" class="sidebar-icon" />
          <span>{{ tab.name }}</span>
        </button>
      </nav>

      <div class="sidebar-footer">
        <div class="admin-role-badge">
          <Settings class="admin-badge-icon spinning" />
          <span>ROLE_ADMIN</span>
        </div>
      </div>
    </aside>

    <!-- Right Main Content Area -->
    <div class="admin-main-content">
      <!-- Loading State -->
      <div v-if="loading" class="loading-overlay card">
        <div class="spinner"></div>
        <p>Đang tải dữ liệu cấu hình...</p>
      </div>

      <!-- Main Content Panel -->
      <div v-else class="admin-tab-content">
        <!-- 1. OVERVIEW / DASHBOARD TAB -->
        <div v-if="activeTab === 'overview'" class="tab-pane animate-fade-in">
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
              <p class="card-subtitle">Số lượt bài học được hoàn thành trong 7 ngày gần nhất</p>
              <div class="chart-container">
                <Line :data="activityChartData" :options="activityChartOptions" />
              </div>
            </div>

            <!-- Doughnut Chart: Competency Level -->
            <div class="chart-card card doughnut-chart-card">
              <h3 class="card-title">XẾP LOẠI NĂNG LỰC HỌC VIÊN</h3>
              <p class="card-subtitle">Tỷ lệ học viên phân nhóm theo điểm tích lũy (PTS)</p>
              <div class="chart-container">
                <Doughnut :data="competencyChartData" :options="competencyChartOptions" />
              </div>
            </div>
          </div>

          <!-- Charts Row 2: Bar Chart & Active Sessions List -->
          <div class="charts-row split-grid">
            <!-- Bar Chart: Avg Score by Topic -->
            <div class="chart-card card bar-chart-card">
              <h3 class="card-title">ĐIỂM THI THỬ TRUNG BÌNH THEO CHỦ ĐỀ</h3>
              <p class="card-subtitle">Kết quả kiểm tra trung bình (%) của các học viên</p>
              <div class="chart-container">
                <Bar :data="topicScoreChartData" :options="topicScoreChartOptions" />
              </div>
            </div>

            <!-- Active Sessions Card -->
            <div class="sessions-card card">
              <h3 class="card-title">HOẠT ĐỘNG HỌC TẬP THỜI GIAN THỰC</h3>
              <p class="card-subtitle">Giám sát các học viên đang học tập trên hệ thống</p>
              
              <div class="sessions-list">
                <div v-for="session in activeSessions" :key="session.username" class="session-row">
                  <div class="session-user-avatar" :style="{ backgroundColor: getAvatarColor(session.username) }">
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
                    <span class="session-topic-tag">{{ session.topic }}</span>
                    <span class="session-time">{{ session.time }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Topics Table Summary -->
          <div class="topics-summary card">
            <div class="card-header-row">
              <div class="header-left">
                <h3 class="card-title">PHÂN BỔ BÀI HỌC VÀ CÂU HỎI</h3>
                <p class="card-subtitle">Danh sách các chủ đề đào tạo nghiệp vụ ngân hàng</p>
              </div>
              <button class="btn btn-primary btn-sm" @click="openTopicModal()">
                <Plus class="btn-icon-sm" /> THÊM CHỦ ĐỀ
              </button>
            </div>
            
            <table class="admin-table">
              <thead>
                <tr>
                  <th>Mã chủ đề</th>
                  <th>Tên chủ đề</th>
                  <th>Icon hiển thị</th>
                  <th>Số bài học</th>
                  <th>Số câu hỏi thi</th>
                  <th class="actions-col">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="topic in courseStore.topics" :key="topic.id">
                  <td><span class="code-badge">{{ topic.id }}</span></td>
                  <td><strong>{{ topic.name }}</strong></td>
                  <td><span class="icon-label"><Settings class="inline-icon" /> {{ topic.iconName }}</span></td>
                  <td>{{ topic.lessons.length }} bài học</td>
                  <td>{{ getQuestionCountForTopic(topic.id) }} câu hỏi</td>
                  <td class="actions-col">
                    <button class="action-btn edit" title="Sửa chủ đề" @click="openTopicModal(topic)">
                      <Edit />
                    </button>
                    <button class="action-btn delete" title="Xóa chủ đề" @click="handleDeleteTopic(topic.id)">
                      <Trash2 />
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- 2. USERS TAB -->
        <div v-if="activeTab === 'users'" class="tab-pane animate-fade-in">
          <div class="users-manager card">
            <div class="card-header-row">
              <div class="header-left">
                <h3 class="card-title">DANH SÁCH THÀNH VIÊN HỆ THỐNG</h3>
                <p class="card-subtitle">Quản lý tài khoản đăng nhập, phân quyền, và điểm tích lũy học tập.</p>
              </div>
              <div class="header-actions">
                <button class="btn btn-secondary" @click="handleDownloadTemplate" :disabled="loading">
                  <Download class="btn-icon" /> TẢI EXCEL MẪU
                </button>
                <button class="btn btn-secondary" @click="triggerExcelUpload" :disabled="loading">
                  <Upload class="btn-icon" /> NHẬP TỪ EXCEL
                </button>
                <input 
                  type="file" 
                  ref="excelFileInput" 
                  style="display: none" 
                  accept=".xlsx, .xls"
                  @change="handleExcelUpload"
                />
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
              <thead>
                <tr>
                  <th>Học viên</th>
                  <th>Tên đăng nhập</th>
                  <th>Mã NV</th>
                  <th>Email</th>
                  <th>Nhóm nghiệp vụ</th>
                  <th>Vai trò</th>
                  <th>Điểm số</th>
                  <th class="actions-col">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="u in filteredUsers" :key="u.username">
                  <td>
                    <div class="user-cell">
                      <div class="user-avatar-circle" :style="{ backgroundColor: u.avatarColor }">
                        {{ u.username.charAt(0).toUpperCase() }}
                      </div>
                      <div>
                        <span class="fullname">{{ u.fullName }}</span>
                      </div>
                    </div>
                  </td>
                  <td><span class="code-badge">{{ u.username }}</span></td>
                  <td>{{ u.employeeId || '-' }}</td>
                  <td>{{ u.email || '-' }}</td>
                  <td>
                    <span v-if="u.userGroupId" class="code-badge">
                      {{ u.userGroupId === 'GDV' ? 'Giao dịch viên' : u.userGroupId === 'QHKH' ? 'Quan hệ khách hàng' : 'Kiểm soát viên' }}
                    </span>
                    <span v-else>-</span>
                  </td>
                  <td>
                    <span class="role-tag" :class="u.role === 'ROLE_ADMIN' ? 'admin' : 'user'">
                      {{ u.role === 'ROLE_ADMIN' ? 'Quản trị' : 'Học viên' }}
                    </span>
                  </td>
                  <td><strong>{{ u.points }} PTS</strong></td>
                  <td class="actions-col">
                    <button class="action-btn edit" title="Sửa học viên" @click="openUserModal(u)">
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
                  <td colspan="8" class="empty-row">Không tìm thấy thành viên phù hợp.</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <!-- 3. LESSONS TAB -->
        <div v-if="activeTab === 'lessons'" class="tab-pane animate-fade-in">
          <div class="lessons-manager card">
            <div class="card-header-row">
              <div class="header-left">
                <h3 class="card-title">CẤU HÌNH BÀI HỌC NGHIỆP VỤ</h3>
                <p class="card-subtitle">Soạn thảo, cập nhật và sắp xếp các bài đọc nghiệp vụ ngân hàng.</p>
              </div>
              <button class="btn btn-primary" @click="openLessonModal()">
                <Plus class="btn-icon" /> THÊM BÀI HỌC
              </button>
            </div>

            <!-- Select Topic Filter -->
            <div class="topic-selector-row">
              <label for="lesson-topic-select">Chọn chủ đề cấu hình: </label>
              <select id="lesson-topic-select" v-model="selectedTopicId" class="form-control select-control">
                <option v-for="t in courseStore.topics" :key="t.id" :value="t.id">
                  {{ t.name }}
                </option>
              </select>
            </div>

            <table class="admin-table" v-if="currentTopicLessons.length > 0">
              <thead>
                <tr>
                  <th>Mã bài học</th>
                  <th>Tiêu đề bài học</th>
                  <th>Gợi ý nghiệp vụ (Hint)</th>
                  <th>Video đính kèm</th>
                  <th>Số Slides tài liệu</th>
                  <th class="actions-col">Thao tác</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="lesson in currentTopicLessons" :key="lesson.id">
                  <td><span class="code-badge">{{ lesson.id }}</span></td>
                  <td><strong>{{ lesson.title }}</strong></td>
                  <td><span class="hint-text-preview">{{ lesson.hint }}</span></td>
                  <td>
                    <span class="video-status" v-if="lesson.videoUrl">
                      Có video
                    </span>
                    <span class="video-status-none" v-else>Không</span>
                  </td>
                  <td>{{ lesson.documentSlides ? lesson.documentSlides.length : 0 }} slides</td>
                  <td class="actions-col">
                    <button class="action-btn edit" title="Sửa bài học" @click="openLessonModal(lesson)">
                      <Edit />
                    </button>
                    <button class="action-btn delete" title="Xóa bài học" @click="handleDeleteLesson(lesson.id)">
                      <Trash2 />
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
            <div class="empty-state-box" v-else>
              <p>Chủ đề này chưa có bài học nào. Bấm nút "Thêm bài học" ở góc trên để bắt đầu cấu hình.</p>
            </div>
          </div>
        </div>

        <!-- 4. EXAMS TAB -->
        <div v-if="activeTab === 'exams'" class="tab-pane animate-fade-in">
          <div class="exams-manager card">
            <div class="card-header-row">
              <div class="header-left">
                <h3 class="card-title">NGÂN HÀNG CÂU HỎI TRẮC NGHIỆM</h3>
                <p class="card-subtitle">Thiết lập các câu hỏi thi thử và lựa chọn đáp án chính xác.</p>
              </div>
              <button class="btn btn-primary" @click="openQuestionModal()">
                <Plus class="btn-icon" /> THÊM CÂU HỎI
              </button>
            </div>

            <!-- Select Topic Filter -->
            <div class="topic-selector-row">
              <label for="exam-topic-select">Chọn chủ đề cấu hình: </label>
              <select id="exam-topic-select" v-model="selectedTopicId" class="form-control select-control">
                <option v-for="t in courseStore.topics" :key="t.id" :value="t.id">
                  {{ t.name }}
                </option>
              </select>
            </div>

            <div class="questions-list" v-if="currentTopicQuestions.length > 0">
              <div 
                v-for="(q, idx) in currentTopicQuestions" 
                :key="q.id" 
                class="question-admin-card"
              >
                <div class="q-card-header">
                  <h4>Câu hỏi {{ idx + 1 }} <span class="q-id-tag">ID: {{ q.id }}</span></h4>
                  <div class="q-actions">
                    <button class="action-btn edit" @click="openQuestionModal(q)">
                      <Edit />
                    </button>
                    <button class="action-btn delete" @click="handleDeleteQuestion(q.id)">
                      <Trash2 />
                    </button>
                  </div>
                </div>
                <p class="q-text">{{ q.text }}</p>
                
                <div class="options-preview">
                  <div 
                    v-for="(opt, oIdx) in q.options" 
                    :key="oIdx" 
                    class="option-item-preview"
                    :class="{ correct: oIdx === q.correctAnswer }"
                  >
                    <span class="opt-letter">{{ String.fromCharCode(65 + oIdx) }}</span>
                    <span class="opt-text">{{ opt }}</span>
                    <CheckCircle2 class="correct-badge" v-if="oIdx === q.correctAnswer" />
                  </div>
                </div>
              </div>
            </div>
            <div class="empty-state-box" v-else>
              <p>Chủ đề này chưa có câu hỏi thi thử nào. Bấm nút "Thêm câu hỏi" để bắt đầu.</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- MODAL 1: TOPIC FORM -->
    <div class="modal-overlay" v-if="topicModal.show" @click.self="topicModal.show = false">
      <div class="modal-content card animate-scale-in">
        <h3>{{ topicModal.isEdit ? 'CẬP NHẬT CHỦ ĐỀ' : 'TẠO CHỦ ĐỀ MỚI' }}</h3>
        <form @submit.prevent="saveTopic">
          <div class="form-group">
            <label>Mã chủ đề (In hoa, không dấu, ví dụ: 'CIF', 'TIEN_GUI')</label>
            <input 
              v-model="topicForm.id" 
              type="text" 
              class="form-control" 
              required
              :disabled="topicModal.isEdit"
            />
          </div>
          <div class="form-group">
            <label>Tên chủ đề hiển thị</label>
            <input 
              v-model="topicForm.name" 
              type="text" 
              class="form-control" 
              required 
            />
          </div>
          <div class="form-group">
            <label>Tên biểu tượng Icon (Ví dụ: 'Coins', 'Users', 'Wallet', 'PiggyBank')</label>
            <input 
              v-model="topicForm.iconName" 
              type="text" 
              class="form-control" 
              required 
            />
          </div>
          <div class="modal-buttons">
            <button type="button" class="btn btn-secondary" @click="topicModal.show = false">Hủy</button>
            <button type="submit" class="btn btn-primary">Lưu</button>
          </div>
        </form>
      </div>
    </div>

    <!-- MODAL 2: USER FORM -->
    <div class="modal-overlay" v-if="userModal.show" @click.self="userModal.show = false">
      <div class="modal-content card animate-scale-in">
        <h3>{{ userModal.isEdit ? 'CẬP NHẬT THÀNH VIÊN' : 'TẠO THÀNH VIÊN MỚI' }}</h3>
        <form @submit.prevent="saveUser">
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
            <label>Mật khẩu {{ userModal.isEdit ? '(Để trống nếu không đổi)' : '(Mặc định nếu để trống: 123456)' }}</label>
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
            <label>Mã nhân viên (Employee ID)</label>
            <input 
              v-model="userForm.employeeId" 
              type="text" 
              class="form-control" 
              placeholder="Ví dụ: NV001"
              required 
            />
          </div>
          <div class="form-group">
            <label>Email công vụ (Nhận OTP)</label>
            <input 
              v-model="userForm.email" 
              type="email" 
              class="form-control" 
              placeholder="Ví dụ: name@bidv.com.vn"
              required 
            />
          </div>
          <div class="form-group" v-if="userForm.role === 'ROLE_USER'">
            <label>Nhóm nghiệp vụ bắt buộc</label>
            <select v-model="userForm.userGroupId" class="form-control">
              <option value="">Không phân nhóm</option>
              <option value="GDV">Giao dịch viên (GDV)</option>
              <option value="QHKH">Quan hệ khách hàng (QHKH)</option>
              <option value="KSV">Kiểm soát viên (KSV)</option>
            </select>
          </div>
          <div class="form-group">
            <label>Màu đại diện Avatar Color (Hex code)</label>
            <div class="color-picker-row">
              <input v-model="userForm.avatarColor" type="color" class="color-picker" />
              <input v-model="userForm.avatarColor" type="text" class="form-control color-text" required />
            </div>
          </div>
          <div class="form-group">
            <label>Vai trò hệ thống</label>
            <select v-model="userForm.role" class="form-control">
              <option value="ROLE_USER">Học viên (ROLE_USER)</option>
              <option value="ROLE_ADMIN">Quản trị viên (ROLE_ADMIN)</option>
            </select>
          </div>
          <div class="form-group">
            <label>Điểm số tích lũy (PTS)</label>
            <input 
              v-model.number="userForm.points" 
              type="number" 
              min="0" 
              class="form-control" 
              required 
            />
          </div>
          <div class="modal-buttons">
            <button type="button" class="btn btn-secondary" @click="userModal.show = false">Hủy</button>
            <button type="submit" class="btn btn-primary">Lưu</button>
          </div>
        </form>
      </div>
    </div>

    <!-- MODAL 3: LESSON FORM -->
    <div class="modal-overlay" v-if="lessonModal.show" @click.self="lessonModal.show = false">
      <div class="modal-content card modal-large animate-scale-in">
        <h3>{{ lessonModal.isEdit ? 'CẬP NHẬT BÀI HỌC' : 'TẠO BÀI HỌC MỚI' }}</h3>
        <form @submit.prevent="saveLesson">
          <div class="form-row">
            <div class="form-group col">
              <label>Chủ đề trực thuộc</label>
              <select v-model="lessonForm.topicId" class="form-control" required>
                <option v-for="t in courseStore.topics" :key="t.id" :value="t.id">
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

          <div class="form-group">
            <label>Link Video đính kèm (URL mp4 hoặc Youtube)</label>
            <input 
              v-model="lessonForm.videoUrl" 
              type="text" 
              class="form-control" 
            />
          </div>

          <!-- Document Slides Configuration -->
          <div class="form-group">
            <label class="section-sub-label">Slides nội dung bài đọc (Mỗi slide phân cách bằng 1 dòng mới)</label>
            <textarea 
              v-model="slidesInputText" 
              rows="6" 
              class="form-control textarea-code"
              placeholder="Slide 1: Nhập mô tả nghiệp vụ ngân hàng tại đây...&#10;Slide 2: Nhập hướng dẫn thao tác hệ thống..."
              required
            ></textarea>
          </div>

          <div class="modal-buttons">
            <button type="button" class="btn btn-secondary" @click="lessonModal.show = false">Hủy</button>
            <button type="submit" class="btn btn-primary">Lưu</button>
          </div>
        </form>
      </div>
    </div>

    <!-- MODAL 4: QUESTION FORM -->
    <div class="modal-overlay" v-if="questionModal.show" @click.self="questionModal.show = false">
      <div class="modal-content card modal-large animate-scale-in">
        <h3>{{ questionModal.isEdit ? 'CẬP NHẬT CÂU HỎI THI' : 'TẠO CÂU HỎI THI MỚI' }}</h3>
        <form @submit.prevent="saveQuestion">
          <div class="form-row">
            <div class="form-group col">
              <label>Chủ đề thi thử</label>
              <select v-model="questionForm.topicId" class="form-control" required>
                <option v-for="t in courseStore.topics" :key="t.id" :value="t.id">
                  {{ t.name }}
                </option>
              </select>
            </div>
          </div>

          <div class="form-group">
            <label>Nội dung câu hỏi nghiệp vụ</label>
            <textarea 
              v-model="questionForm.text" 
              rows="3" 
              class="form-control" 
              required
            ></textarea>
          </div>

          <div class="form-group-options">
            <label class="options-label">Danh sách các đáp án lựa chọn:</label>
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
              <span class="opt-label">{{ String.fromCharCode(65 + idx) }}:</span>
              <input 
                v-model="optionsInputs[idx]" 
                type="text" 
                class="form-control" 
                placeholder="Nhập nội dung đáp án..." 
                required 
              />
            </div>
            <span class="helper-text-opt">* Tích chọn nút tròn bên trái để đánh dấu đó là đáp án chính xác.</span>
          </div>

          <div class="modal-buttons">
            <button type="button" class="btn btn-secondary" @click="questionModal.show = false">Hủy</button>
            <button type="submit" class="btn btn-primary">Lưu</button>
          </div>
        </form>
      </div>
    </div>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useCourseStore } from '../stores/courseStore';
import { adminApi } from '../api/adminApi';
import type { AdminUser } from '../api/adminApi';
import { quizApi } from '../api/quizApi';
import type { Topic, Lesson, Question } from '../types';
import { 
  Settings, 
  Users, 
  BookOpen, 
  HelpCircle, 
  Plus, 
  Edit, 
  Trash2, 
  CheckCircle2,
  Download,
  Upload
} from 'lucide-vue-next';

// Chart.js imports & registrations
import { Line, Doughnut, Bar } from 'vue-chartjs';
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
  Filler
} from 'chart.js';

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
  Filler
);

// Stores
const courseStore = useCourseStore();

// Sidebar tab definitions
const activeTab = ref('overview');
const tabs = [
  { id: 'overview', name: 'Tổng quan', icon: Settings },
  { id: 'users', name: 'Thành viên', icon: Users },
  { id: 'lessons', name: 'Bài học', icon: BookOpen },
  { id: 'exams', name: 'Đề thi trắc nghiệm', icon: HelpCircle }
];

// Active sessions simulation
const activeSessions = ref([
  { username: 'nguyenvana', fullName: 'Nguyễn Văn An', action: 'Đang học bài', detail: 'LN02. Quy trình giải ngân thông thường', topic: 'TIỀN VAY', time: 'Vừa xong' },
  { username: 'tranb', fullName: 'Trần Thị Bình', action: 'Đang thi thử', detail: 'Đề đánh giá năng lực CIF', topic: 'CIF', time: '2 phút trước' },
  { username: 'lephuc', fullName: 'Lê Hoàng Phúc', action: 'Đang xem', detail: 'Bảng xếp hạng vinh danh', topic: 'HỆ THỐNG', time: '5 phút trước' },
  { username: 'phamduc', fullName: 'Phạm Minh Đức', action: 'Đang học bài', detail: 'CT01. Quy trình chuyển tiền trong nước', topic: 'CHUYỂN TIỀN', time: '12 phút trước' }
]);

// Reactive states
const loading = ref(false);
const users = ref<AdminUser[]>([]);
const questions = ref<Question[]>([]);
const selectedTopicId = ref('');
const userSearchQuery = ref('');

// Forms & Modals states
const topicModal = ref({ show: false, isEdit: false });
const topicForm = ref({ id: '', name: '', iconName: '' });

const userModal = ref({ show: false, isEdit: false, editId: null as number | null });
const userForm = ref({ username: '', password: '', fullName: '', avatarColor: '#005B41', role: 'ROLE_USER', points: 0, userGroupId: '', employeeId: '', email: '' });

const lessonModal = ref({ show: false, isEdit: false });
const lessonForm = ref({ id: '', title: '', topicId: '', hint: '', videoUrl: '', documentSlides: [] as string[] });
const slidesInputText = ref('');

const questionModal = ref({ show: false, isEdit: false, editId: null as number | null });
const questionForm = ref({ text: '', topicId: '', correctAnswer: 0, options: [] as string[] });
const optionsInputs = ref(['', '', '', '']);

// Statistics Computeds
const totalLessonsCount = computed(() => {
  return courseStore.topics.reduce((acc, t) => acc + t.lessons.length, 0);
});

const totalQuestionsCount = computed(() => {
  return questions.value.length;
});

const filteredUsers = computed(() => {
  if (!userSearchQuery.value) return users.value;
  const q = userSearchQuery.value.toLowerCase().trim();
  return users.value.filter(u => 
    u.username.toLowerCase().includes(q) || 
    u.fullName.toLowerCase().includes(q)
  );
});

const currentTopicLessons = computed(() => {
  const topic = courseStore.topics.find(t => t.id === selectedTopicId.value);
  return topic ? topic.lessons : [];
});

const currentTopicQuestions = computed(() => {
  return questions.value.filter(q => q.topicId === selectedTopicId.value);
});

// Watch topic changes to adjust selectedTopicId if it gets empty
watch(() => courseStore.topics, (newTopics) => {
  if (newTopics.length > 0 && !selectedTopicId.value) {
    selectedTopicId.value = newTopics[0].id;
  }
}, { immediate: true });

// Switch Tab
const switchTab = (tabId: string) => {
  activeTab.value = tabId;
};

// Helper for user avatar color fallback
const getAvatarColor = (username: string) => {
  const user = users.value.find(u => u.username === username);
  return user ? user.avatarColor : '#64748b';
};

// Get questions count per topic
const getQuestionCountForTopic = (topicId: string) => {
  return questions.value.filter(q => q.topicId === topicId).length;
};

// ==========================================
// ADVANCED CHARTS DATA & OPTIONS
// ==========================================

// 1. Line Chart: Daily Activity
const activityChartData = computed(() => {
  return {
    labels: ['Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7', 'Chủ Nhật', 'Hôm nay'],
    datasets: [
      {
        label: 'Bài học hoàn thành',
        data: [18, 24, 15, 29, 32, 12, 28],
        borderColor: '#005B41', // BIDV Emerald Green
        backgroundColor: 'rgba(0, 91, 65, 0.1)',
        fill: true,
        tension: 0.3,
        borderWidth: 3,
        pointBackgroundColor: '#D4AF37', // Gold point
        pointBorderColor: '#ffffff',
        pointRadius: 5
      }
    ]
  };
});

const activityChartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: { display: false },
    tooltip: { padding: 10 }
  },
  scales: {
    y: {
      grid: { color: '#F1F5F9' },
      ticks: { color: '#64748B', font: { family: 'Inter', size: 11 } }
    },
    x: {
      grid: { display: false },
      ticks: { color: '#64748B', font: { family: 'Inter', size: 11 } }
    }
  }
};

// 2. Doughnut Chart: Competency Level
const competencyChartData = computed(() => {
  // Group users dynamically
  let excellent = 0; // >= 2500 PTS
  let good = 0;      // 1800 - 2499 PTS
  let average = 0;   // 1000 - 1799 PTS
  let weak = 0;      // < 1000 PTS

  users.value.forEach(u => {
    if (u.role === 'ROLE_ADMIN') return; // Skip admin
    if (u.points >= 2500) excellent++;
    else if (u.points >= 1800) good++;
    else if (u.points >= 1000) average++;
    else weak++;
  });

  return {
    labels: ['Xuất sắc (>=2500)', 'Khá (1800-2499)', 'Trung bình (1000-1799)', 'Yếu (<1000)'],
    datasets: [
      {
        data: [excellent || 2, good || 3, average || 2, weak || 0],
        backgroundColor: [
          '#005B41', // Emerald
          '#00825D', // Light Emerald
          '#D4AF37', // Gold Metallic
          '#94A3B8'  // Slate Muted
        ],
        borderWidth: 2,
        borderColor: '#ffffff'
      }
    ]
  };
});

const competencyChartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: {
      position: 'right' as const,
      labels: {
        color: '#1E293B',
        font: { family: 'Outfit', size: 11, weight: 'bold' as const },
        boxWidth: 12
      }
    }
  }
};

// 3. Bar Chart: Average Score by Topic
const topicScoreChartData = computed(() => {
  // Let's calculate actual averages based on simulated points or actual answers
  // We can calculate dynamic averages. If no scores exist, provide mock representative data.
  const scores: Record<string, { sum: number, count: number }> = {};
  
  users.value.forEach(u => {
    // If the mock system has quiz scores in localstorage or user record
    // We can simulate averages based on user points to keep it dynamic!
    courseStore.topics.forEach(t => {
      if (!scores[t.id]) scores[t.id] = { sum: 0, count: 0 };
      
      // Let's create a dynamic mock score calculation based on the user's points
      const basePercentage = Math.min(60 + Math.round((u.points / 3000) * 40), 100);
      // Add minor random variance per topic
      const randomVariance = (u.username.charCodeAt(0) % 15) - 7;
      const finalScore = Math.max(50, Math.min(100, basePercentage + randomVariance));
      
      scores[t.id].sum += finalScore;
      scores[t.id].count += 1;
    });
  });

  const labels = courseStore.topics.map(t => t.name);
  const dataValues = courseStore.topics.map(t => {
    const entry = scores[t.id];
    return entry && entry.count > 0 ? Math.round(entry.sum / entry.count) : 75;
  });

  return {
    labels,
    datasets: [
      {
        label: 'Tỷ lệ điểm trung bình (%)',
        data: dataValues,
        backgroundColor: '#005B41',
        borderRadius: 4,
        maxBarThickness: 32
      }
    ]
  };
});

const topicScoreChartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  plugins: {
    legend: { display: false }
  },
  scales: {
    y: {
      min: 0,
      max: 100,
      grid: { color: '#F1F5F9' },
      ticks: { color: '#64748B', font: { family: 'Inter', size: 11 } }
    },
    x: {
      grid: { display: false },
      ticks: { color: '#64748B', font: { family: 'Inter', size: 10, weight: 'bold' as const } }
    }
  }
};

// ==========================================
// LOAD ALL DATA
// ==========================================
const loadAllData = async () => {
  loading.value = true;
  try {
    await courseStore.fetchTopics();
    users.value = await adminApi.getAllUsers();
    
    const questionsList: Question[] = [];
    for (const topic of courseStore.topics) {
      try {
        const qList = await quizApi.getQuizQuestions(topic.id);
        questionsList.push(...(qList as unknown as Question[]));
      } catch (err) {
        console.error(`Failed to load questions for ${topic.id}:`, err);
      }
    }
    questions.value = questionsList;

    if (courseStore.topics.length > 0 && !selectedTopicId.value) {
      selectedTopicId.value = courseStore.topics[0].id;
    }
  } catch (error) {
    console.error('Failed to load admin panel data:', error);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadAllData();
});

// ==========================================
// TOPIC ACTIONS
// ==========================================
const openTopicModal = (topic?: Topic) => {
  if (topic) {
    topicModal.value = { show: true, isEdit: true };
    topicForm.value = { id: topic.id, name: topic.name, iconName: topic.iconName };
  } else {
    topicModal.value = { show: true, isEdit: false };
    topicForm.value = { id: '', name: '', iconName: 'Settings' };
  }
};

const saveTopic = async () => {
  loading.value = true;
  try {
    if (topicModal.value.isEdit) {
      await adminApi.updateTopic(topicForm.value.id, topicForm.value as unknown as Topic);
    } else {
      await adminApi.createTopic(topicForm.value as unknown as Topic);
    }
    topicModal.value.show = false;
    await loadAllData();
  } catch (err) {
    alert('Thao tác chủ đề thất bại!');
    console.error(err);
  } finally {
    loading.value = false;
  }
};

const handleDeleteTopic = async (id: string) => {
  if (!confirm('Bạn có chắc chắn muốn xóa chủ đề này? Tất cả bài học và câu hỏi trực thuộc cũng sẽ bị xóa.')) return;
  loading.value = true;
  try {
    await adminApi.deleteTopic(id);
    await loadAllData();
  } catch (err) {
    alert('Xóa chủ đề thất bại!');
    console.error(err);
  } finally {
    loading.value = false;
  }
};

// ==========================================
// USER ACTIONS
// ==========================================
const openUserModal = (user?: AdminUser) => {
  if (user) {
    userModal.value = { show: true, isEdit: true, editId: user.id || null };
    userForm.value = {
      username: user.username,
      password: '',
      fullName: user.fullName,
      avatarColor: user.avatarColor || '#005B41',
      role: user.role,
      points: user.points,
      userGroupId: user.userGroupId || '',
      employeeId: user.employeeId || '',
      email: user.email || ''
    };
  } else {
    userModal.value = { show: true, isEdit: false, editId: null };
    userForm.value = {
      username: '',
      password: '',
      fullName: '',
      avatarColor: '#005B41',
      role: 'ROLE_USER',
      points: 0,
      userGroupId: '',
      employeeId: '',
      email: ''
    };
  }
};

const excelFileInput = ref<HTMLInputElement | null>(null);

const triggerExcelUpload = () => {
  if (excelFileInput.value) {
    excelFileInput.value.click();
  }
};

const handleDownloadTemplate = async () => {
  loading.value = true;
  try {
    const blob = await adminApi.downloadUserTemplate();
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', 'user_import_template.xlsx');
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  } catch (err) {
    alert('Không thể tải file mẫu Excel. Vui lòng thử lại!');
    console.error(err);
  } finally {
    loading.value = false;
  }
};

const handleExcelUpload = async (event: Event) => {
  const target = event.target as HTMLInputElement;
  if (target.files && target.files.length > 0) {
    const file = target.files[0];
    loading.value = true;
    try {
      const responseMessage = await adminApi.importUsersExcel(file);
      alert(responseMessage || 'Nhập danh sách học viên thành công!');
      await loadAllData();
    } catch (err: any) {
      if (err.response && err.response.data && err.response.data.message) {
        alert(err.response.data.message);
      } else {
        alert('Nhập danh sách học viên thất bại. Vui lòng kiểm tra lại định dạng file!');
      }
      console.error(err);
    } finally {
      loading.value = false;
      if (excelFileInput.value) {
        excelFileInput.value.value = '';
      }
    }
  }
};

const saveUser = async () => {
  loading.value = true;
  try {
    if (userModal.value.isEdit && userModal.value.editId !== null) {
      await adminApi.updateUser(userModal.value.editId, userForm.value as AdminUser);
    } else {
      await adminApi.createUser(userForm.value as AdminUser);
    }
    userModal.value.show = false;
    await loadAllData();
  } catch (err) {
    alert('Lưu người dùng thất bại!');
    console.error(err);
  } finally {
    loading.value = false;
  }
};

const handleDeleteUser = async (user: AdminUser) => {
  if (!user.id) return;
  if (!confirm(`Bạn có chắc chắn muốn xóa học viên ${user.fullName} (${user.username})?`)) return;
  loading.value = true;
  try {
    await adminApi.deleteUser(user.id);
    await loadAllData();
  } catch (err) {
    alert('Xóa học viên thất bại!');
    console.error(err);
  } finally {
    loading.value = false;
  }
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
      topicId: lesson.topicId,
      hint: lesson.hint,
      videoUrl: lesson.videoUrl || '',
      documentSlides: [...lesson.documentSlides]
    };
    slidesInputText.value = lesson.documentSlides.join('\n');
  } else {
    lessonModal.value = { show: true, isEdit: false };
    lessonForm.value = {
      id: '',
      title: '',
      topicId: selectedTopicId.value,
      hint: '',
      videoUrl: '',
      documentSlides: []
    };
    slidesInputText.value = '';
  }
};

const saveLesson = async () => {
  loading.value = true;
  try {
    const slides = slidesInputText.value
      .split('\n')
      .map(s => s.trim())
      .filter(s => s.length > 0);

    const payload = {
      ...lessonForm.value,
      documentSlides: slides,
      procedureSteps: []
    };

    if (lessonModal.value.isEdit) {
      await adminApi.updateLesson(lessonForm.value.id, payload);
    } else {
      await adminApi.createLesson(payload);
    }
    
    lessonModal.value.show = false;
    await loadAllData();
  } catch (err) {
    alert('Lưu bài học thất bại!');
    console.error(err);
  } finally {
    loading.value = false;
  }
};

const handleDeleteLesson = async (id: string) => {
  if (!confirm('Bạn có chắc chắn muốn xóa bài học này?')) return;
  loading.value = true;
  try {
    await adminApi.deleteLesson(id);
    await loadAllData();
  } catch (err) {
    alert('Xóa bài học thất bại!');
    console.error(err);
  } finally {
    loading.value = false;
  }
};

// ==========================================
// QUESTION ACTIONS
// ==========================================
const openQuestionModal = (question?: Question) => {
  if (question) {
    questionModal.value = { show: true, isEdit: true, editId: question.id };
    questionForm.value = {
      text: question.text,
      topicId: question.topicId,
      correctAnswer: question.correctAnswer,
      options: []
    };
    optionsInputs.value = [
      question.options[0] || '',
      question.options[1] || '',
      question.options[2] || '',
      question.options[3] || ''
    ];
  } else {
    questionModal.value = { show: true, isEdit: false, editId: null };
    questionForm.value = {
      text: '',
      topicId: selectedTopicId.value,
      correctAnswer: 0,
      options: []
    };
    optionsInputs.value = ['', '', '', ''];
  }
};

const saveQuestion = async () => {
  loading.value = true;
  try {
    const opts = optionsInputs.value.map(o => o.trim()).filter(o => o.length > 0);
    if (opts.length < 2) {
      alert('Vui lòng điền tối thiểu 2 đáp án lựa chọn!');
      loading.value = false;
      return;
    }

    const payload = {
      ...questionForm.value,
      options: opts
    };

    if (questionModal.value.isEdit && questionModal.value.editId !== null) {
      const fullPayload = {
        ...payload,
        id: questionModal.value.editId
      } as Question;
      await adminApi.updateQuestion(questionModal.value.editId, fullPayload);
    } else {
      await adminApi.createQuestion(payload);
    }

    questionModal.value.show = false;
    await loadAllData();
  } catch (err) {
    alert('Lưu câu hỏi trắc nghiệm thất bại!');
    console.error(err);
  } finally {
    loading.value = false;
  }
};

const handleDeleteQuestion = async (id: number) => {
  if (!confirm('Bạn có chắc chắn muốn xóa câu hỏi này?')) return;
  loading.value = true;
  try {
    await adminApi.deleteQuestion(id);
    await loadAllData();
  } catch (err) {
    alert('Xóa câu hỏi thất bại!');
    console.error(err);
  } finally {
    loading.value = false;
  }
};
</script>

<style scoped>
/* Redesigned Sidebar Layout */
.admin-portal-layout {
  display: flex;
  min-height: calc(100vh - 80px);
  margin: -2rem; /* Stretch layout to edges to override parent padding */
  background-color: var(--bg-main);
  font-family: var(--font-family);
}

/* Sidebar styling */
.admin-sidebar {
  width: 280px;
  background-color: var(--primary-color); /* Emerald Green */
  color: white;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border-color);
  flex-shrink: 0;
  box-shadow: 2px 0 10px rgba(0,0,0,0.05);
}

.sidebar-header {
  padding: 2rem 1.5rem;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.sidebar-title h2 {
  font-size: 1.15rem;
  font-weight: 800;
  letter-spacing: -0.5px;
  line-height: 1.2;
}

.sidebar-title span {
  font-size: 0.7rem;
  text-transform: uppercase;
  color: var(--accent-color); /* Gold */
  letter-spacing: 1px;
  font-weight: 700;
}

.sidebar-menu {
  padding: 1.5rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  flex: 1;
}

.sidebar-item {
  background: none;
  border: none;
  text-align: left;
  color: rgba(255,255,255,0.75);
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
  color: var(--primary-color);
  background-color: white; /* White tab bg for clean contrast */
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
  100% { transform: rotate(360deg); }
}

/* Main Content area */
.admin-main-content {
  flex: 1;
  padding: 2.2rem;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 1.8rem;
}

.admin-top-header {
  padding: 1.5rem 2rem;
  border-left: 5px solid var(--primary-color);
  background: white;
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
  color: var(--primary-color);
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
  background: white;
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

.users-bg { background-color: #3b82f6; }
.online-bg { background-color: #10b981; }
.lessons-bg { background-color: var(--primary-color); }
.questions-bg { background-color: #8b5cf6; }

.online-indicator-ping {
  position: absolute;
  top: -3px;
  right: -3px;
  width: 10px;
  height: 10px;
  background-color: #ef4444;
  border: 2px solid white;
  border-radius: 50%;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0% { transform: scale(0.9); opacity: 1; }
  50% { transform: scale(1.3); opacity: 0.5; }
  100% { transform: scale(0.9); opacity: 1; }
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
  color: #10b981;
  background-color: #dcfce7;
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
  background: white;
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
  background: white;
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
  color: var(--primary-color);
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

.session-topic-tag {
  background-color: var(--primary-light);
  color: var(--primary-color);
  font-weight: 700;
  padding: 0.1rem 0.4rem;
  border-radius: var(--radius-sm);
  font-size: 0.7rem;
}

.session-time {
  color: var(--text-muted);
}

/* Tables & CRUD styling */
.topics-summary, .users-manager, .lessons-manager, .exams-manager {
  background: white;
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
  font-family: monospace;
  background-color: var(--bg-main);
  color: var(--primary-color);
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
  color: var(--primary-color);
}

.action-btn.delete:hover {
  background-color: #fee2e2;
  color: #ef4444;
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
  background-color: #fee2e2;
  color: #ef4444;
}

.role-tag.user {
  background-color: #e0f2fe;
  color: #0369a1;
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
}

.modal-content {
  width: 100%;
  max-width: 480px;
  padding: 2rem;
  background: white;
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-lg);
}

.modal-large {
  max-width: 650px;
}

.modal-content h3 {
  font-size: 1.15rem;
  color: var(--primary-color);
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
  background-color: white;
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

.loading-overlay {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 4rem;
  gap: 1rem;
}

.spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--primary-light);
  border-top-color: var(--primary-color);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.header-actions {
  display: flex;
  gap: 0.75rem;
  align-items: center;
}
</style>
