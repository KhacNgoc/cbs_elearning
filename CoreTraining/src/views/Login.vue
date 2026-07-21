<template>
  <div class="login-view-container animate-fade-in">
    <div class="login-card card">
      <div class="login-header">
        <div class="logo-box-large">B</div>
        <h2>BIDV Core Training</h2>
        <p>Hệ thống Đào tạo Nội bộ & Đánh giá Năng lực</p>
      </div>

      <!-- Tab Buttons -->
      <div class="login-tabs">
        <button 
          class="tab-btn" 
          :class="{ active: activeTab === 'login' }"
          @click="switchTab('login')"
        >
          ĐĂNG NHẬP
        </button>
        <button 
          class="tab-btn" 
          :class="{ active: activeTab === 'register' }"
          @click="switchTab('register')"
        >
          ĐĂNG KÝ
        </button>
      </div>

      <!-- Login Form -->
      <form v-if="activeTab === 'login'" @submit.prevent="handleLogin" class="login-form animate-fade-in">
        <div v-if="errorMessage" class="error-banner card">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="error-icon"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
          <span>{{ errorMessage }}</span>
        </div>

        <div v-if="successMessage" class="success-banner card">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="success-icon"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
          <span>{{ successMessage }}</span>
        </div>

        <div class="form-group">
          <label for="username">Tên tài khoản</label>
          <div class="input-wrapper">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="input-icon"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
            <input 
              type="text" 
              id="username" 
              v-model="username" 
              placeholder="Nhập tên đăng nhập (ví dụ: system)" 
              required
              :disabled="isLoading"
            />
          </div>
        </div>

        <div class="form-group">
          <label for="password">Mật khẩu</label>
          <div class="input-wrapper">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="input-icon"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
            <input 
              type="password" 
              id="password" 
              v-model="password" 
              placeholder="Nhập mật khẩu của bạn" 
              required
              :disabled="isLoading"
            />
          </div>
        </div>

        <button type="submit" class="btn btn-primary w-100 btn-submit" :disabled="isLoading">
          <span v-if="isLoading" class="spinner"></span>
          <span v-else>ĐĂNG NHẬP</span>
        </button>
      </form>

      <!-- Register Form -->
      <form v-else @submit.prevent="handleRegister" class="login-form animate-fade-in">
        <div v-if="errorMessage" class="error-banner card">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="error-icon"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
          <span>{{ errorMessage }}</span>
        </div>

        <div class="form-group">
          <label for="reg-username">Tên tài khoản (username)</label>
          <div class="input-wrapper">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="input-icon"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
            <input 
              type="text" 
              id="reg-username" 
              v-model="regUsername" 
              placeholder="Tên đăng nhập viết liền không dấu" 
              required
              :disabled="isLoading"
            />
          </div>
        </div>

        <div class="form-group">
          <label for="reg-fullname">Họ và tên học viên</label>
          <div class="input-wrapper">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="input-icon"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>
            <input 
              type="text" 
              id="reg-fullname" 
              v-model="regFullName" 
              placeholder="Nhập họ và tên đầy đủ" 
              required
              :disabled="isLoading"
            />
          </div>
        </div>

        <div class="form-group">
          <label for="reg-employee-id">Mã nhân viên (Employee ID)</label>
          <div class="input-wrapper">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="input-icon"><rect x="3" y="4" width="18" height="16" rx="2" ry="2"/><line x1="7" y1="8" x2="17" y2="8"/><line x1="7" y1="12" x2="17" y2="12"/><line x1="7" y1="16" x2="13" y2="16"/></svg>
            <input 
              type="text" 
              id="reg-employee-id" 
              v-model="regEmployeeId" 
              placeholder="Ví dụ: BIDV001 hoặc 123456" 
              required
              :disabled="isLoading"
            />
          </div>
        </div>

        <div class="form-group">
          <label for="reg-email">Email công vụ</label>
          <div class="input-wrapper">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="input-icon"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>
            <input 
              type="email" 
              id="reg-email" 
              v-model="regEmail" 
              placeholder="Ví dụ: user@bidv.com.vn" 
              required
              :disabled="isLoading"
            />
          </div>
        </div>

        <div class="form-group">
          <label for="reg-password">Mật khẩu</label>
          <div class="input-wrapper">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="input-icon"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
            <input 
              type="password" 
              id="reg-password" 
              v-model="regPassword" 
              placeholder="Tạo mật khẩu bảo mật" 
              required
              :disabled="isLoading"
            />
          </div>
        </div>

        <!-- User Group Selection -->
        <div class="form-group">
          <label for="reg-group">Nhóm nghiệp vụ bắt buộc</label>
          <div class="select-wrapper">
            <select id="reg-group" v-model="regGroupId" :disabled="isLoading">
              <option value="GDV">Giao dịch viên (GDV)</option>
              <option value="QHKH">Quan hệ khách hàng (QHKH)</option>
              <option value="KSV">Kiểm soát viên (KSV)</option>
            </select>
          </div>
        </div>

        <!-- Avatar Color Pickers -->
        <div class="form-group">
          <label>Màu sắc avatar đại diện</label>
          <div class="color-picker-row">
            <button 
              type="button"
              v-for="color in avatarColors" 
              :key="color" 
              class="color-btn"
              :style="{ backgroundColor: color }"
              :class="{ selected: regAvatarColor === color }"
              @click="regAvatarColor = color"
              :disabled="isLoading"
            ></button>
          </div>
        </div>

        <button type="submit" class="btn btn-primary w-100 btn-submit" :disabled="isLoading">
          <span v-if="isLoading" class="spinner"></span>
          <span v-else>ĐĂNG KÝ HỌC VIÊN</span>
        </button>
      </form>
      
      <div class="login-footer">
        <p>Phiên bản 2.0 • Bản quyền thuộc về BIDV</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useUserStore } from '../stores/userStore';
import { authApi } from '../api/authApi';

const userStore = useUserStore();

// Navigation states
const activeTab = ref<'login' | 'register'>('login');
const errorMessage = ref('');
const successMessage = ref('');
const isLoading = ref(false);

// Login fields
const username = ref('system'); // pre-fill for convenience
const password = ref('');

// Register fields
const regUsername = ref('');
const regFullName = ref('');
const regEmployeeId = ref('');
const regEmail = ref('');
const regPassword = ref('');
const regGroupId = ref('GDV');
const regAvatarColor = ref('#005B41');

const avatarColors = [
  '#005B41', // BIDV Dark Green
  '#4F46E5', // Indigo
  '#10B981', // Emerald
  '#F59E0B', // Amber
  '#EF4444'  // Rose
];

const switchTab = (tab: 'login' | 'register') => {
  activeTab.value = tab;
  errorMessage.value = '';
  successMessage.value = '';
};

const handleLogin = async () => {
  isLoading.value = true;
  errorMessage.value = '';
  successMessage.value = '';
  try {
    await userStore.login({
      username: username.value,
      password: password.value
    });
  } catch (err: any) {
    errorMessage.value = 'Đăng nhập không thành công. Vui lòng kiểm tra lại tài khoản hoặc mật khẩu!';
    console.error(err);
  } finally {
    isLoading.value = false;
  }
};

const handleRegister = async () => {
  isLoading.value = true;
  errorMessage.value = '';
  successMessage.value = '';
  try {
    await authApi.register({
      username: regUsername.value,
      password: regPassword.value,
      fullName: regFullName.value,
      employeeId: regEmployeeId.value,
      email: regEmail.value,
      avatarColor: regAvatarColor.value,
      userGroupId: regGroupId.value
    });
    
    // Register success, switch to login tab
    successMessage.value = `Tài khoản '${regUsername.value}' đã được đăng ký thành công! Hãy nhập mật khẩu để đăng nhập.`;
    username.value = regUsername.value;
    activeTab.value = 'login';
    
    // Clear registration fields
    regUsername.value = '';
    regFullName.value = '';
    regEmployeeId.value = '';
    regEmail.value = '';
    regPassword.value = '';
    regGroupId.value = 'GDV';
    regAvatarColor.value = '#005B41';
  } catch (err: any) {
    if (err.response && err.response.data && err.response.data.message) {
      errorMessage.value = `Đăng ký thất bại: ${err.response.data.message}`;
    } else {
      errorMessage.value = 'Đăng ký tài khoản thất bại. Vui lòng thử tên đăng nhập khác!';
    }
    console.error(err);
  } finally {
    isLoading.value = false;
  }
};
</script>

<style scoped>
.login-view-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 160px);
  padding: 1rem;
}

.login-card {
  width: 100%;
  max-width: 450px;
  padding: 2.5rem;
  background-color: var(--bg-card);
  box-shadow: var(--shadow-lg);
  border: 1.5px solid var(--border-color);
  border-radius: var(--radius-lg);
}

.login-header {
  text-align: center;
  margin-bottom: 1.5rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}

.logo-box-large {
  width: 60px;
  height: 60px;
  background: radial-gradient(circle, var(--accent-secondary) 20%, var(--primary-color) 80%);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-weight: 800;
  font-size: 2rem;
  margin-bottom: 0.5rem;
  box-shadow: 0 4px 15px rgba(0, 91, 65, 0.25);
}

.login-header h2 {
  font-size: 1.6rem;
  font-weight: 700;
  color: var(--primary-color);
}

.login-header p {
  font-size: 0.85rem;
  color: var(--text-muted);
}

/* Tabs */
.login-tabs {
  display: flex;
  border-bottom: 1.5px solid var(--border-color);
  margin-bottom: 1.5rem;
}

.login-tabs .tab-btn {
  flex: 1;
  padding: 0.75rem;
  background: none;
  border: none;
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--text-muted);
  cursor: pointer;
  position: relative;
  transition: var(--transition);
}

.login-tabs .tab-btn:hover {
  color: var(--primary-color);
}

.login-tabs .tab-btn.active {
  color: var(--primary-color);
}

.login-tabs .tab-btn.active::after {
  content: '';
  position: absolute;
  bottom: -1.5px;
  left: 0;
  right: 0;
  height: 2.5px;
  background-color: var(--primary-color);
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.error-banner {
  background-color: #fee2e2;
  color: #b91c1c;
  padding: 0.75rem 1rem;
  border-radius: var(--radius-sm);
  font-size: 0.85rem;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  border: 1px solid #fca5a5;
}

.error-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}

.success-banner {
  background-color: #ecfdf5;
  color: #047857;
  padding: 0.75rem 1rem;
  border-radius: var(--radius-sm);
  font-size: 0.85rem;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  border: 1px solid #a7f3d0;
}

.success-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.form-group label {
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--text-main);
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-icon {
  position: absolute;
  left: 1rem;
  width: 18px;
  height: 18px;
  color: var(--text-muted);
  pointer-events: none;
}

.input-wrapper input {
  width: 100%;
  padding: 0.8rem 1rem 0.8rem 2.5rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
  font-size: 0.95rem;
  outline: none;
  transition: var(--transition);
}

.input-wrapper input:focus {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px var(--primary-light);
}

/* Select wrapper styling */
.select-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.select-wrapper select {
  width: 100%;
  padding: 0.8rem 1.5rem 0.8rem 1rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
  font-size: 0.95rem;
  outline: none;
  background-color: white;
  appearance: none;
  cursor: pointer;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%2364748b' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpolyline points='6 9 12 15 18 9'%3E%3C/polyline%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 1rem center;
  background-size: 16px;
  transition: var(--transition);
}

.select-wrapper select:focus {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px var(--primary-light);
}

/* Color pickers */
.color-picker-row {
  display: flex;
  gap: 0.75rem;
}

.color-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 2px solid transparent;
  cursor: pointer;
  transition: var(--transition);
}

.color-btn:hover {
  transform: scale(1.1);
}

.color-btn.selected {
  border-color: var(--text-main);
  box-shadow: 0 0 0 2px white inset, var(--shadow-sm);
  transform: scale(1.1);
}

.btn-submit {
  padding: 0.9rem;
  font-size: 1rem;
  letter-spacing: 0.5px;
  margin-top: 0.5rem;
}

.w-100 {
  width: 100%;
}

.login-footer {
  text-align: center;
  margin-top: 2rem;
  font-size: 0.75rem;
  color: var(--text-muted);
}

/* Loading spinner */
.spinner {
  width: 20px;
  height: 20px;
  border: 3px solid rgba(255,255,255,0.3);
  border-radius: 50%;
  border-top-color: white;
  animation: spin 1s ease-in-out infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
