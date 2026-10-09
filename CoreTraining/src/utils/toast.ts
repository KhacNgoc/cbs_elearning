export type ToastVariant = 'info' | 'success' | 'warning' | 'error';

export function showToast(message: string, variant: ToastVariant = 'success') {
  window.dispatchEvent(new CustomEvent('app-toast', { detail: { message, variant } }));
}
