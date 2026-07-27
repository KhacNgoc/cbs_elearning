<template>
  <div class="change-password-container">
    <div class="glass-card">
      <div class="logo-section">
        <div class="logo-badge">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="lock-icon"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path></svg>
        </div>
        <h2>Yêu cầu đổi mật khẩu</h2>
        <p class="subtitle">Tài khoản của bạn được khởi tạo với mật khẩu mặc định. Vui lòng đổi mật khẩu mới để tiếp tục sử dụng hệ thống.</p>
      </div>

      <form @submit.prevent="handleChangePassword" class="form-section">
        <div class="form-group">
          <label for="new-password">Mật khẩu mới</label>
          <div class="input-wrapper">
            <input 
              :type="showPassword ? 'text' : 'password'" 
              id="new-password" 
              v-model="newPassword" 
              placeholder="Nhập mật khẩu mới của bạn" 
              required
              class="form-control"
            />
            <button type="button" @click="showPassword = !showPassword" class="toggle-btn">
              <span v-if="showPassword">Ẩn</span>
              <span v-else>Hiện</span>
            </button>
          </div>
        </div>

        <div class="form-group">
          <label for="confirm-password">Xác nhận mật khẩu mới</label>
          <div class="input-wrapper">
            <input 
              :type="showConfirmPassword ? 'text' : 'password'" 
              id="confirm-password" 
              v-model="confirmPassword" 
              placeholder="Nhập lại mật khẩu mới" 
              required
              class="form-control"
            />
            <button type="button" @click="showConfirmPassword = !showConfirmPassword" class="toggle-btn">
              <span v-if="showConfirmPassword">Ẩn</span>
              <span v-else>Hiện</span>
            </button>
          </div>
        </div>

        <div v-if="errorMessage" class="error-alert">
          {{ errorMessage }}
        </div>

        <div v-if="successMessage" class="success-alert">
          {{ successMessage }}
        </div>

        <button type="submit" class="submit-btn" :disabled="loading">
          <span v-if="loading">Đang cập nhật...</span>
          <span v-else>Xác nhận Đổi mật khẩu</span>
        </button>

        <button type="button" @click="handleLogout" class="logout-btn">
          Đăng xuất quay lại
        </button>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useUserStore } from '../stores/userStore';
import { courseApi } from '../api/courseApi';
import router from '../router';

const newPassword = ref('');
const confirmPassword = ref('');
const showPassword = ref(false);
const showConfirmPassword = ref(false);
const loading = ref(false);
const errorMessage = ref('');
const successMessage = ref('');

const userStore = useUserStore();

const handleChangePassword = async () => {
  errorMessage.value = '';
  successMessage.value = '';

  if (newPassword.value.length < 6) {
    errorMessage.value = 'Mật khẩu phải dài tối thiểu 6 ký tự!';
    return;
  }

  if (newPassword.value !== confirmPassword.value) {
    errorMessage.value = 'Mật khẩu xác nhận không khớp!';
    return;
  }

  loading.value = true;
  try {
    // Call userApi to change password
    await courseApi.changePassword(newPassword.value);
    
    // Update local storage and pinia store state
    localStorage.setItem('mustChangePassword', 'false');
    userStore.mustChangePassword = false;

    successMessage.value = 'Đổi mật khẩu thành công! Hệ thống đang chuyển hướng...';
    
    setTimeout(() => {
      router.push('/');
    }, 1500);
  } catch (error: any) {
    console.error('Failed to change password:', error);
    errorMessage.value = error.response?.data?.message || 'Có lỗi xảy ra khi đổi mật khẩu!';
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
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #003B2B 0%, #005B41 50%, #008F68 100%);
  padding: 2rem 1rem;
  font-family: 'Inter', system-ui, -apple-system, sans-serif;
}

.glass-card {
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 20px;
  width: 100%;
  max-width: 460px;
  padding: 3rem 2.5rem;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.3);
  box-sizing: border-box;
}

.logo-section {
  text-align: center;
  margin-bottom: 2.5rem;
}

.logo-badge {
  width: 60px;
  height: 60px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 1.5rem;
}

.lock-icon {
  width: 28px;
  height: 28px;
  color: #10B981;
}

.logo-section h2 {
  color: #ffffff;
  font-size: 1.6rem;
  margin: 0 0 0.75rem;
  font-weight: 700;
  letter-spacing: -0.025em;
}

.subtitle {
  color: rgba(255, 255, 255, 0.65);
  font-size: 0.9rem;
  line-height: 1.5;
  margin: 0;
}

.form-section {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.form-group label {
  color: rgba(255, 255, 255, 0.85);
  font-size: 0.85rem;
  font-weight: 500;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.form-control {
  width: 100%;
  padding: 0.85rem 1rem;
  padding-right: 3.5rem;
  background: rgba(0, 0, 0, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  color: #ffffff;
  font-size: 0.95rem;
  transition: all 0.3s ease;
  box-sizing: border-box;
}

.form-control:focus {
  outline: none;
  border-color: #10B981;
  background: rgba(0, 0, 0, 0.35);
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.15);
}

.toggle-btn {
  position: absolute;
  right: 1rem;
  background: none;
  border: none;
  color: rgba(255, 255, 255, 0.5);
  font-size: 0.8rem;
  cursor: pointer;
  padding: 0;
  transition: color 0.3s ease;
}

.toggle-btn:hover {
  color: #ffffff;
}

.error-alert {
  background: rgba(239, 68, 68, 0.15);
  border: 1px solid rgba(239, 68, 68, 0.25);
  border-radius: 8px;
  padding: 0.75rem 1rem;
  color: #FCA5A5;
  font-size: 0.85rem;
}

.success-alert {
  background: rgba(16, 185, 129, 0.15);
  border: 1px solid rgba(16, 185, 129, 0.25);
  border-radius: 8px;
  padding: 0.75rem 1rem;
  color: #A7F3D0;
  font-size: 0.85rem;
}

.submit-btn {
  background: #10B981;
  border: none;
  border-radius: 10px;
  color: #ffffff;
  padding: 0.9rem;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-top: 0.5rem;
}

.submit-btn:hover:not(:disabled) {
  background: #059669;
  transform: translateY(-1px);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.logout-btn {
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 10px;
  color: rgba(255, 255, 255, 0.7);
  padding: 0.85rem;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.logout-btn:hover {
  background: rgba(255, 255, 255, 0.05);
  color: #ffffff;
}
</style>
