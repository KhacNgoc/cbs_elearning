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
        <svg
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          class="error-icon"
        >
          <circle cx="12" cy="12" r="10" />
          <line x1="12" y1="8" x2="12" y2="12" />
          <line x1="12" y1="16" x2="12.01" y2="16" />
        </svg>
        <span>{{ errorMessage }}</span>
      </div>

      <div v-if="successMessage" class="success-banner card animate-fade-in">
        <svg
          xmlns="http://www.w3.org/2000/svg"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          class="success-icon"
        >
          <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
          <polyline points="22 4 12 14.01 9 11.01" />
        </svg>
        <span>{{ successMessage }}</span>
      </div>

      <!-- VIEW STATE 1: LOGIN FORM -->
      <form
        v-if="viewState === 'login'"
        @submit.prevent="handleLogin"
        class="login-form animate-fade-in"
      >
        <div class="form-group">
          <label for="username">Tên tài khoản</label>
          <div class="input-wrapper">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              class="input-icon"
            >
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
              <circle cx="12" cy="7" r="4" />
            </svg>
            <input
              type="text"
              id="username"
              v-model="username"
              placeholder="Nhập tên đăng nhập"
              required
              :disabled="isLoading"
            />
          </div>
        </div>

        <div class="form-group">
          <div class="label-row">
            <label for="password">Mật khẩu</label>
            <a
              href="#"
              class="forgot-link"
              @click.prevent="setViewState('forgot')"
              >Quên mật khẩu?</a
            >
          </div>
          <div class="input-wrapper">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              class="input-icon"
            >
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
              <path d="M7 11V7a5 5 0 0 1 10 0v4" />
            </svg>
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

        <button
          type="submit"
          class="btn btn-primary w-100 btn-submit"
          :disabled="isLoading"
        >
          <span v-if="isLoading" class="spinner"></span>
          <span v-else>ĐĂNG NHẬP</span>
        </button>
      </form>

      <!-- VIEW STATE 2: FORGOT PASSWORD REQUEST -->
      <form
        v-else-if="viewState === 'forgot'"
        @submit.prevent="handleForgotPassword"
        class="login-form animate-fade-in"
      >
        <div class="form-instruction">
          Nhập thông tin định danh chính xác (Tên tài khoản, Email công vụ và Mã
          nhân viên) để thiết lập lại mật khẩu.
        </div>

        <div class="form-group">
          <label for="forgot-username">Tên tài khoản (Username)</label>
          <div class="input-wrapper">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              class="input-icon"
            >
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
              <circle cx="12" cy="7" r="4" />
            </svg>
            <input
              type="text"
              id="forgot-username"
              v-model="forgotUsername"
              placeholder="Ví dụ: quantrh"
              required
              :disabled="isLoading"
            />
          </div>
        </div>

        <div class="form-group">
          <label for="forgot-email">Email công vụ</label>
          <div class="input-wrapper">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              class="input-icon"
            >
              <path
                d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"
              />
              <polyline points="22,6 12,13 2,6" />
            </svg>
            <input
              type="email"
              id="forgot-email"
              v-model="forgotEmail"
              placeholder="Ví dụ: quantrh@bidv.com.vn"
              required
              :disabled="isLoading"
            />
          </div>
        </div>

        <div class="form-group">
          <button
            type="button"
            class="btn btn-secondary"
            :disabled="isLoading || !forgotUsername || !forgotEmail"
            @click="sendOtp"
          >
            Gửi mã xác minh
          </button>
          <label for="forgot-employee-id">Mã xác minh gửi qua email</label>
          <div class="input-wrapper">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              class="input-icon"
            >
              <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
              <line x1="16" y1="2" x2="16" y2="6" />
              <line x1="8" y1="2" x2="8" y2="6" />
              <line x1="3" y1="10" x2="21" y2="10" />
            </svg>
            <input
              type="text"
              id="forgot-employee-id"
              v-model="resetOtp"
              placeholder="Nhập mã gồm 6 chữ số"
              required
              :disabled="isLoading"
            />
          </div>
        </div>

        <div class="form-group">
          <label for="forgot-new-password">Mật khẩu mới</label>
          <div class="input-wrapper">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              class="input-icon"
            >
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
              <path d="M7 11V7a5 5 0 0 1 10 0v4" />
            </svg>
            <input
              type="password"
              id="forgot-new-password"
              v-model="newPassword"
              placeholder="Tối thiểu 8 ký tự"
              required
              :disabled="isLoading"
            />
          </div>
        </div>

        <div class="form-group">
          <label for="forgot-confirm-password">Xác nhận mật khẩu mới</label>
          <div class="input-wrapper">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
              class="input-icon"
            >
              <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
              <path d="M7 11V7a5 5 0 0 1 10 0v4" />
            </svg>
            <input
              type="password"
              id="forgot-confirm-password"
              v-model="confirmPassword"
              placeholder="Nhập lại mật khẩu mới"
              required
              :disabled="isLoading"
            />
          </div>
        </div>

        <button
          type="submit"
          class="btn btn-primary w-100 btn-submit"
          :disabled="isLoading"
        >
          <span v-if="isLoading" class="spinner"></span>
          <span v-else>ĐẶT LẠI MẬT KHẨU</span>
        </button>

        <div class="back-to-login">
          <a href="#" @click.prevent="setViewState('login')"
            >Quay lại Đăng nhập</a
          >
        </div>
      </form>

      <div class="login-footer">
        <p>Phiên bản 2.0 • Bản quyền thuộc về BIDV</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useUserStore } from "../stores/userStore";
import { authApi } from "../api/authApi";

const userStore = useUserStore();

// Navigation states: 'login' | 'forgot'
const viewState = ref<"login" | "forgot">("login");
const errorMessage = ref("");
const successMessage = ref("");
const isLoading = ref(false);

// Login fields
const username = ref("");
const password = ref("");

// Forgot password fields
const forgotUsername = ref("");
const forgotEmail = ref("");
const resetOtp = ref("");

// Reset fields
const newPassword = ref("");
const confirmPassword = ref("");

const setViewState = (state: "login" | "forgot") => {
  viewState.value = state;
  errorMessage.value = "";
  successMessage.value = "";
  password.value = "";
  newPassword.value = "";
  confirmPassword.value = "";
  resetOtp.value = "";
};

const handleLogin = async () => {
  isLoading.value = true;
  errorMessage.value = "";
  successMessage.value = "";
  try {
    await userStore.login({
      username: username.value.trim(),
      password: password.value,
    });
  } catch (err: any) {
    errorMessage.value =
      "Đăng nhập không thành công. Vui lòng kiểm tra lại tài khoản hoặc mật khẩu!";
    console.error(err);
  } finally {
    isLoading.value = false;
  }
};

const sendOtp = async () => {
  isLoading.value = true;
  errorMessage.value = "";
  successMessage.value = "";
  try {
    successMessage.value = await authApi.sendResetOtp(
      forgotUsername.value,
      forgotEmail.value,
    );
  } catch (err: any) {
    errorMessage.value =
      err.response?.data?.message || "Không gửi được mã xác minh";
  } finally {
    isLoading.value = false;
  }
};
const handleForgotPassword = async () => {
  if (newPassword.value !== confirmPassword.value) {
    errorMessage.value = "Mật khẩu xác nhận không khớp!";
    return;
  }
  if (newPassword.value.length < 8) {
    errorMessage.value = "Mật khẩu mới phải có tối thiểu 8 ký tự!";
    return;
  }

  isLoading.value = true;
  errorMessage.value = "";
  successMessage.value = "";
  try {
    await authApi.resetPasswordWithOtp(
      forgotUsername.value,
      resetOtp.value,
      newPassword.value,
    );
    successMessage.value =
      "Đặt lại mật khẩu thành công! Hãy dùng mật khẩu mới để đăng nhập.";
    setTimeout(() => {
      setViewState("login");
    }, 1500);
  } catch (err: any) {
    if (err.response && err.response.data && err.response.data.message) {
      errorMessage.value = err.response.data.message;
    } else {
      errorMessage.value = "Thông tin xác thực không khớp trên hệ thống!";
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
  background: radial-gradient(
    circle,
    var(--accent-secondary) 20%,
    var(--primary-color) 80%
  );
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
  color: var(--primary-ink);
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
  background-color: var(--surface-fee2e2);
  color: var(--ink-b91c1c);
  padding: 0.75rem 1rem;
  border-radius: var(--radius-sm);
  font-size: 0.85rem;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  border: 1px solid var(--line-fca5a5);
  margin-bottom: 1.25rem;
}

.error-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}

.success-banner {
  background-color: var(--surface-ecfdf5);
  color: var(--ink-047857);
  padding: 0.75rem 1rem;
  border-radius: var(--radius-sm);
  font-size: 0.85rem;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  border: 1px solid var(--line-a7f3d0);
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
  color: var(--primary-ink);
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
  color: var(--primary-ink);
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
  border: 3px solid rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  border-top-color: var(--line-ffffff);
  animation: spin 1s ease-in-out infinite;
  margin: 0 auto;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
@media (max-width: 560px) {
  .login-view-container {
    padding: 0;
  }
  .login-card {
    padding: 1.25rem;
  }
}
</style>
