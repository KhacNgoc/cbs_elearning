<template>
  <div class="slide-viewer card">
    <!-- <div class="viewer-header">
      <div class="file-info">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="file-icon"><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2z"/><polyline points="14 2 14 8 20 8"/></svg>
        <span class="file-name">Tài liệu hướng dẫn chuyên đề</span>
      </div>
      <button class="btn-external" @click="openExternal">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="ext-icon"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
        Mở ngoài
      </button>
    </div> -->
    
    <div class="slide-content">
      <div class="slide-card animate-fade-in" :key="currentSlideIndex">
        <div class="bidv-banner">
          <div class="logo-box">BIDV</div>
          <div class="banner-sub">BAN QLDA TRIỂN KHAI DỰ ÁN CHUYỂN ĐỔI HỆ THỐNG COREBANKING</div>
        </div>
        
        <div class="slide-body">
          <h3>{{ topicName }}</h3>
          <p class="slide-text">{{ slides[currentSlideIndex] }}</p>
        </div>
        
        <div class="slide-footer">
          <span>Hà Nội, 2026</span>
          <span class="page-num">TRANG {{ currentSlideIndex + 1 }} / {{ slides.length }}</span>
        </div>
      </div>
    </div>
    
    <div class="viewer-controls">
      <button class="btn-ctrl" @click="prevSlide" :disabled="currentSlideIndex === 0">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>
        Trước
      </button>
      <span class="slide-counter">SLIDE {{ currentSlideIndex + 1 }} OF {{ slides.length }}</span>
      <button class="btn-ctrl" @click="nextSlide" :disabled="currentSlideIndex === slides.length - 1">
        Sau
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue';

const props = defineProps({
  slides: {
    type: Array as () => string[],
    required: true,
    default: () => []
  },
  topicName: {
    type: String,
    default: ''
  }
});

const currentSlideIndex = ref(0);

const prevSlide = () => {
  if (currentSlideIndex.value > 0) {
    currentSlideIndex.value--;
  }
};

const nextSlide = () => {
  if (currentSlideIndex.value < props.slides.length - 1) {
    currentSlideIndex.value++;
  }
};

const openExternal = () => {
  alert('Đang mở tài liệu PDF gốc trong tab mới...');
};
</script>

<style scoped>
.slide-viewer {
  display: flex;
  flex-direction: column;
  background-color: #1e1e1e;
  border-color: #333;
  padding: 1rem;
  color: #fff;
  min-height: 480px;
}

.viewer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  border-bottom: 1px solid #333;
  padding-bottom: 0.5rem;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.file-icon {
  width: 18px;
  height: 18px;
  color: #ef4444; /* PDF Red */
}

.file-name {
  font-size: 0.9rem;
  font-weight: 500;
  color: #ccc;
}

.btn-external {
  background: none;
  border: 1px solid #444;
  color: #aaa;
  padding: 0.25rem 0.75rem;
  border-radius: var(--radius-sm);
  font-size: 0.8rem;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 0.25rem;
  transition: var(--transition);
}

.btn-external:hover {
  background-color: #333;
  color: #fff;
  border-color: #666;
}

.ext-icon {
  width: 12px;
  height: 12px;
}

.slide-content {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: #0f172a;
  border-radius: var(--radius-md);
  padding: 1.5rem;
  position: relative;
  overflow: hidden;
  border: 1px solid #334155;
  min-height: 350px;
}

.slide-card {
  width: 100%;
  max-width: 680px;
  aspect-ratio: 16/9;
  background-color: #005B41; /* BIDV Brand Green background for presentation slides */
  border-radius: var(--radius-sm);
  box-shadow: 0 10px 25px rgba(0,0,0,0.5);
  display: flex;
  flex-direction: column;
  padding: 1.5rem;
  justify-content: space-between;
  border: 1.5px solid var(--accent-color);
  background-image: radial-gradient(circle at 90% 10%, rgba(212, 175, 55, 0.08) 0%, transparent 60%);
}

.bidv-banner {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  border-bottom: 1px solid rgba(255,255,255,0.2);
  padding-bottom: 0.5rem;
}

.logo-box {
  background-color: #fff;
  color: #005B41;
  font-weight: 800;
  padding: 0.15rem 0.5rem;
  border-radius: 2px;
  font-size: 0.8rem;
}

.banner-sub {
  font-size: 0.6rem;
  font-weight: 600;
  letter-spacing: 0.5px;
  color: rgba(255,255,255,0.8);
}

.slide-body {
  margin: 1.5rem 0;
  text-align: center;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 1rem;
}

.slide-body h3 {
  font-size: 1.25rem;
  color: var(--accent-color);
  font-weight: 700;
  text-transform: uppercase;
}

.slide-text {
  font-size: 1.05rem;
  line-height: 1.6;
  color: #fff;
  padding: 0 1rem;
  font-weight: 400;
}

.slide-footer {
  display: flex;
  justify-content: space-between;
  font-size: 0.65rem;
  color: rgba(255,255,255,0.6);
  border-top: 1px solid rgba(255,255,255,0.1);
  padding-top: 0.5rem;
}

.page-num {
  font-weight: 600;
  color: var(--accent-color);
}

.viewer-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px
}

.btn-ctrl {
  background-color: #333;
  color: #fff;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: var(--radius-sm);
  cursor: pointer;
  font-size: 0.85rem;
  display: flex;
  align-items: center;
  gap: 0.25rem;
  transition: var(--transition);
}

.btn-ctrl:hover:not(:disabled) {
  background-color: var(--primary-color);
}

.btn-ctrl:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.slide-counter {
  font-size: 0.85rem;
  color: #888;
  font-weight: 500;
}
</style>
