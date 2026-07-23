<template>
  <div class="login-view-container animate-fade-in">
    <div class="login-card card">
      <div class="login-header">
        <div class="logo-box-large">B</div>
        <h2>BIDV Core Training</h2>
        <p>Hệ thống Đào tạo Nội bộ & Đánh giá Năng lực</p>
      </div>

      <!-- Error and Success Banners -->
      <div v-if="errorMessage" class="error-banner card animate-fade-in">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="error-icon"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
        <span>{{ errorMessage }}</span>
      </div>

      <div v-if="successMessage" class="success-banner card animate-fade-in">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="success-icon"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>
        <span>{{ successMessage }}</span>
      </div>

      <!-- VIEW STATE 1: LOGIN FORM -->
      <form v-if="viewState === 'login'" @submit.prevent="handleLogin" class="login-form animate-fade-in">
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
          <div class="label-row">
            <label for="password">Mật khẩu</label>
            <a href="#" class="forgot-link" @click.prevent="setViewState('forgot')">Quên mật khẩu?</a>
          </div>
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

      <!-- VIEW STATE 2: FORGOT PASSWORD REQUEST -->
      <form v-else-if="viewState === 'forgot'" @submit.prevent="handleForgotPassword" class="login-form animate-fade-in">
        <div class="form-instruction">
          Nhập Tên tài khoản và Email công vụ của bạn. Hệ thống sẽ sinh mã xác thực OTP 6 số để bạn tiến hành đặt lại mật khẩu.
        </div>

        <div class="form-group">
          <label for="forgot-username">Tên tài khoản (Username)</label>
          <div class="input-wrapper">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="input-icon"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
            <input 
              type="text" 
              id="forgot-username" 
              v-model="forgotUsername" 
              placeholder="Ví dụ: system" 
              required
              :disabled="isLoading"
            />
          </div>
        </div>

        <div class="form-group">
          <label for="forgot-email">Email công vụ</label>
          <div class="input-wrapper">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="input-icon"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>
            <input 
              type="email" 
              id="forgot-email" 
              v-model="forgotEmail" 
              placeholder="Ví dụ: system@bidv.com.vn" 
              required
              :disabled="isLoading"
            />
          </div>
        </div>

        <button type="submit" class="btn btn-primary w-100 btn-submit" :disabled="isLoading">
          <span v-if="isLoading" class="spinner"></span>
          <span v-else>GỬI MÃ XÁC THỰC</span>
        </button>

        <div class="back-to-login">
          <a href="#" @click.prevent="setViewState('login')">Quay lại Đăng nhập</a>
        </div>
      </form>

      <!-- VIEW STATE 3: RESET PASSWORD WITH OTP -->
      <form v-else-if="viewState === 'reset-otp'" @submit.prevent="handleResetPassword" class="login-form animate-fade-in">
        <div class="form-instruction">
          Mã OTP đã được gửi đến email (hoặc logs). Vui lòng nhập mã và mật khẩu mới để cập nhật.
        </div>

        <div class="form-group">
          <label for="reset-username">Tên tài khoản</label>
          <div class="input-wrapper">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="input-icon"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
            <input 
              type="text" 
              id="reset-username" 
              v-model="username" 
              disabled
              class="disabled-input"
            />
          </div>
        </div>

        <div class="form-group">
          <label for="reset-otp">Mã xác thực OTP (6 chữ số)</label>
          <div class="input-wrapper">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="input-icon"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
            <input 
              type="text" 
              id="reset-otp" 
              v-model="otpCode" 
              placeholder="Nhập 6 chữ số (hoặc 999999)" 
              required
              maxlength="6"
              pattern="[0-9]*"
              :disabled="isLoading"
            />
          </div>
        </div>

        <div class="form-group">
          <label for="new-password">Mật khẩu mới</label>
          <div class="input-wrapper">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="input-icon"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
            <input 
              type="password" 
              id="new-password" 
              v-model="newPassword" 
              placeholder="Tối thiểu 6 ký tự" 
              required
              :disabled="isLoading"
            />
          </div>
        </div>

        <div class="form-group">
          <label for="confirm-password">Xác nhận mật khẩu mới</label>
          <div class="input-wrapper">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="input-icon"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
            <input 
              type="password" 
              id="confirm-password" 
              v-model="confirmPassword" 
              placeholder="Nhập lại mật khẩu mới" 
              required
              :disabled="isLoading"
            />
          </div>
        </div>

        <button type="submit" class="btn btn-primary w-100 btn-submit" :disabled="isLoading">
          <span v-if="isLoading" class="spinner"></span>
          <span v-else>ĐẶT LẠI MẬT KHẨU</span>
        </button>

        <div class="back-to-login">
          <a href="#" @click.prevent="setViewState('login')">Quay lại Đăng nhập</a>
        </div>
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

// Navigation states: 'login' | 'forgot' | 'reset-otp'
const viewState = ref<'login' | 'forgot' | 'reset-otp'>('login');
const errorMessage = ref('');
const successMessage = ref('');
const isLoading = ref(false);

// Login fields
const username = ref('system'); // pre-fill for convenience
const password = ref('');

// Forgot password fields
const forgotUsername = ref('');
const forgotEmail = ref('');

// Reset fields
const otpCode = ref('');
const newPassword = ref('');
const confirmPassword = ref('');

const setViewState = (state: 'login' | 'forgot' | 'reset-otp') => {
  viewState.value = state;
  errorMessage.value = '';
  successMessage.value = '';
  password.value = '';
  otpCode.value = '';
  newPassword.value = '';
  confirmPassword.value = '';
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

const handleForgotPassword = async () => {
  isLoading.value = true;
  errorMessage.value = '';
  successMessage.value = '';
  try {
    await authApi.sendResetOtp(forgotUsername.value, forgotEmail.value);
    successMessage.value = 'Mã xác thực OTP đã được gửi thành công! Hãy kiểm tra Email (hoặc Docker logs).';
    username.value = forgotUsername.value; // prefill username for the next screen
    viewState.value = 'reset-otp';
  } catch (err: any) {
    if (err.response && err.response.data && err.response.data.message) {
      errorMessage.value = err.response.data.message;
    } else {
      errorMessage.value = 'Tài khoản hoặc email không khớp trên hệ thống!';
    }
    console.error(err);
  } finally {
    isLoading.value = false;
  }
};

const handleResetPassword = async () => {
  if (newPassword.value !== confirmPassword.value) {
    errorMessage.value = 'Mật khẩu xác nhận không khớp!';
    return;
  }
  if (newPassword.value.length < 6) {
    errorMessage.value = 'Mật khẩu mới phải có tối thiểu 6 ký tự!';
    return;
  }
  
  isLoading.value = true;
  errorMessage.value = '';
  successMessage.value = '';
  try {
    await authApi.resetPasswordWithOtp(username.value, otpCode.value, newPassword.value);
    successMessage.value = 'Đổi mật khẩu thành công! Hãy dùng mật khẩu mới để đăng nhập.';
    setViewState('login');
  } catch (err: any) {
    if (err.response && err.response.data && err.response.data.message) {
      errorMessage.value = err.response.data.message;
    } else {
      errorMessage.value = 'Đặt lại mật khẩu thất bại. Vui lòng thử lại!';
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
  margin-bottom: 2rem;
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

.login-form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.form-instruction {
  font-size: 0.85rem;
  color: var(--text-muted);
  line-height: 1.5;
  background-color: rgba(0, 91, 65, 0.04);
  padding: 0.75rem;
  border-left: 3px solid var(--primary-color);
  border-radius: var(--radius-sm);
  margin-bottom: 0.25rem;
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
  margin-bottom: 1.25rem;
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
  margin-bottom: 1.25rem;
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

.label-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.forgot-link {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--primary-color);
  text-decoration: none;
  transition: var(--transition);
}

.forgot-link:hover {
  text-decoration: underline;
  color: var(--accent-secondary);
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

.disabled-input {
  background-color: var(--bg-body);
  color: var(--text-muted);
  cursor: not-allowed;
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

.back-to-login {
  text-align: center;
  margin-top: 0.5rem;
}

.back-to-login a {
  font-size: 0.9rem;
  color: var(--text-muted);
  text-decoration: none;
  font-weight: 600;
  transition: var(--transition);
}

.back-to-login a:hover {
  color: var(--primary-color);
  text-decoration: underline;
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
  margin: 0 auto;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
