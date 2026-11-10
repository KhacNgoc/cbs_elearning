<template>
  <div class="change-password-container animate-fade-in">
    <div class="login-card card">
      <div class="login-header">
        <img class="logo-box-large" :src="appLogo" alt="BIDV Core Training" width="72" height="72" />
        <h2>Yêu cầu đổi mật khẩu</h2>
        <p>
          Tài khoản của bạn được khởi tạo với mật khẩu mặc định. Vui lòng đổi
          mật khẩu mới để tiếp tục sử dụng hệ thống.
        </p>
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

      <form @submit.prevent="handleChangePassword" class="login-form">
        <div class="form-group">
          <label for="new-password">Mật khẩu mới</label>
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
              :type="showPassword ? 'text' : 'password'"
              id="new-password"
              v-model="newPassword"
              placeholder="Nhập mật khẩu mới của bạn"
              required
              :disabled="loading"
              class="password-input"
            />
            <button
              type="button"
              @click="showPassword = !showPassword"
              class="toggle-btn"
            >
              <span v-if="showPassword">Ẩn</span>
              <span v-else>Hiện</span>
            </button>
          </div>
        </div>

        <div class="form-group">
          <label for="confirm-password">Xác nhận mật khẩu mới</label>
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
              :type="showConfirmPassword ? 'text' : 'password'"
              id="confirm-password"
              v-model="confirmPassword"
              placeholder="Nhập lại mật khẩu mới"
              required
              :disabled="loading"
              class="password-input"
            />
            <button
              type="button"
              @click="showConfirmPassword = !showConfirmPassword"
              class="toggle-btn"
            >
              <span v-if="showConfirmPassword">Ẩn</span>
              <span v-else>Hiện</span>
            </button>
          </div>
        </div>

        <button
          type="submit"
          class="btn btn-primary w-100 btn-submit"
          :disabled="loading"
        >
          <span v-if="loading" class="spinner"></span>
          <span v-else>XÁC NHẬN ĐỔI MẬT KHẨU</span>
        </button>

        <div class="back-to-login">
          <a href="#" @click.prevent="handleLogout">Đăng xuất quay lại</a>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { useUserStore } from "../stores/userStore";
import { updateCachedAuthSession } from "../utils/authSession";
import { courseApi } from "../api/courseApi";
import router from "../router";
import appLogo from "../assets/app-logo.png";

const newPassword = ref("");
const confirmPassword = ref("");
const showPassword = ref(false);
const showConfirmPassword = ref(false);
const loading = ref(false);
const errorMessage = ref("");
const successMessage = ref("");

const userStore = useUserStore();

const handleChangePassword = async () => {
  errorMessage.value = "";
  successMessage.value = "";

  if (newPassword.value.length < 8) {
    errorMessage.value = "Mật khẩu phải dài tối thiểu 8 ký tự!";
    return;
  }

  if (newPassword.value !== confirmPassword.value) {
    errorMessage.value = "Mật khẩu xác nhận không khớp!";
    return;
  }

  loading.value = true;
  try {
    // Call userApi to change password
    await courseApi.changePassword(newPassword.value);

    // Update local storage and pinia store state
    localStorage.setItem("mustChangePassword", "false");
    userStore.mustChangePassword = false;
    updateCachedAuthSession({ mustChangePassword: false });

    successMessage.value =
      "Đổi mật khẩu thành công! Hệ thống đang chuyển hướng...";

    setTimeout(() => {
      router.push("/");
    }, 1500);
  } catch (error: any) {
    console.error("Failed to change password:", error);
    errorMessage.value =
      error.response?.data?.message || "Có lỗi xảy ra khi đổi mật khẩu!";
  } finally {
    loading.value = false;
  }
};

const handleLogout = () => {
  userStore.logout();
};
</script>

<style scoped>
.change-password-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: calc(100vh - 160px);
  background-color: var(--bg-main);
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
  width: 72px;
  height: 72px;
  object-fit: contain;
  display: block;
  margin-bottom: 0.5rem;
}

.login-header h2 {
  font-size: 1.6rem;
  font-weight: 700;
  color: var(--primary-ink);
}

.login-header p {
  font-size: 0.85rem;
  color: var(--text-muted);
  line-height: 1.5;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
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
  box-sizing: border-box;
}

.input-wrapper input:focus {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px var(--primary-light);
}

.toggle-btn {
  position: absolute;
  right: 1rem;
  background: none;
  border: none;
  color: var(--text-muted);
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  padding: 0;
  transition: var(--transition);
}

.toggle-btn:hover {
  color: var(--primary-ink);
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

.spinner {
  display: inline-block;
  width: 20px;
  height: 20px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-radius: 50%;
  border-top-color: var(--line-ffffff);
  animation: spin 1s ease-in-out infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
@media (max-width: 560px) {
  .change-password-container {
    padding: 0;
  }
  .login-card {
    padding: 1.25rem;
  }
}
</style>
