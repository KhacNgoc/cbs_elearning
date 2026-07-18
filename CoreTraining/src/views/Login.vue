<template>
  <div class="login-view-container animate-fade-in">
    <div class="login-card card">
      <div class="login-header">
        <div class="logo-box-large">B</div>
        <h2>BIDV Core Training</h2>
        <p>Hệ thống Đào tạo Nội bộ & Đánh giá Năng lực</p>
      </div>

      <form @submit.prevent="handleLogin" class="login-form">
        <div v-if="errorMessage" class="error-banner card">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="error-icon"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
          <span>{{ errorMessage }}</span>
        </div>

        <div class="form-group">
          <label for="username">Tên tài khoản</label>
          <div class="input-wrapper">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="input-icon"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
            <input 
              type="text" 
              id="username" 
              v-model="username" 
              placeholder="Nhập tên đăng nhập (ví dụ: quantrh)" 
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
      
      <div class="login-footer">
        <p>Phiên bản 2.0 • Bản quyền thuộc về BIDV</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useUserStore } from '../stores/userStore';

const userStore = useUserStore();

const username = ref('quantrh'); // pre-fill for onboarding convenience
const password = ref('');
const errorMessage = ref('');
const isLoading = ref(false);

const handleLogin = async () => {
  isLoading.value = true;
  errorMessage.value = '';
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
