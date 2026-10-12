import { createApp } from 'vue';
import { createPinia } from 'pinia';
import router from './router';
import './assets/styles/main.css';
import './assets/styles/admin.css';
import App from './App.vue';
import { Chart } from 'chart.js';

// Canvas text does not inherit the page's CSS font.
Chart.defaults.font.family = 'Arial, sans-serif';

const app = createApp(App);
const pinia = createPinia();

app.config.errorHandler = (error, instance, info) => {
  console.error('Unhandled Vue error', {
    error,
    component: instance?.$options?.name || 'anonymous',
    info,
  });
};

window.addEventListener('error', (event) => {
  console.error('Unhandled browser error', {
    message: event.message,
    source: event.filename,
    line: event.lineno,
    column: event.colno,
    error: event.error,
  });
});

window.addEventListener('unhandledrejection', (event) => {
  console.error('Unhandled promise rejection', event.reason);
});

app.use(pinia);
app.use(router);
app.mount('#app');
