// Runs before the app bundle so the first paint already uses the saved theme.
// Kept as a file rather than an inline script so the Content-Security-Policy needs no 'unsafe-inline'.
(() => {
  let theme;
  try { theme = localStorage.getItem('bidv-color-theme'); } catch {}
  const dark = theme === 'dark' || (theme !== 'light' && matchMedia('(prefers-color-scheme: dark)').matches);
  document.documentElement.dataset.theme = dark ? 'dark' : 'light';
  document.documentElement.style.colorScheme = dark ? 'dark' : 'light';
})();
