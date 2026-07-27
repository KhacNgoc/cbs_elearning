const AUTH_STORAGE_KEYS = ['accessToken', 'userRole', 'username', 'fullName', 'mustChangePassword'] as const;

export const clearAuthSession = () => {
  AUTH_STORAGE_KEYS.forEach((key) => localStorage.removeItem(key));
};

export const redirectToLogin = () => {
  clearAuthSession();
  if (window.location.pathname !== '/login') {
    window.location.replace('/login');
  }
};
