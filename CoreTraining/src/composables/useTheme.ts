import { readonly, ref } from 'vue';

export type ThemePreference = 'light' | 'dark' | 'system';
export const THEME_STORAGE_KEY = 'bidv-color-theme';
const preference = ref<ThemePreference>('system');
const isDark = ref(false);
let initialized = false;

export function initializeTheme() {
  if (initialized) return;
  initialized = true;
  const media = window.matchMedia('(prefers-color-scheme: dark)');
  const readPreference = (): ThemePreference => {
    try {
      const value = localStorage.getItem(THEME_STORAGE_KEY);
      return value === 'light' || value === 'dark' ? value : 'system';
    } catch { return 'system'; }
  };
  const apply = () => {
    isDark.value = preference.value === 'dark' || (preference.value === 'system' && media.matches);
    document.documentElement.dataset.theme = isDark.value ? 'dark' : 'light';
    document.documentElement.style.colorScheme = isDark.value ? 'dark' : 'light';
  };
  preference.value = readPreference();
  apply();
  media.addEventListener('change', apply);
  window.addEventListener('storage', event => {
    if (event.key === THEME_STORAGE_KEY || event.key === null) {
      preference.value = readPreference();
      apply();
    }
  });
  updateTheme = (value: ThemePreference) => {
    preference.value = value;
    try {
      if (value === 'system') localStorage.removeItem(THEME_STORAGE_KEY);
      else localStorage.setItem(THEME_STORAGE_KEY, value);
    } catch { /* The selection still works for this session. */ }
    apply();
  };
}

let updateTheme: (value: ThemePreference) => void = () => {};
export function useTheme() {
  initializeTheme();
  return { isDark: readonly(isDark), preference: readonly(preference), setTheme: (value: ThemePreference) => updateTheme(value) };
}
