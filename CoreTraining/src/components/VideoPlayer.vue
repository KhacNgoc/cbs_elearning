<template>
  <div class="video-container card">
    <div class="video-title-bar">
      <span class="video-filename">{{ title || 'CIF03_Quy trinh tao CIF KHTC.mp4' }}</span>
      <button class="btn-open-browser" @click="openInBrowser">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="btn-icon"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
        Mở trong trình duyệt
      </button>
    </div>
    
    <div class="video-player-wrapper">
      <video
        ref="videoElement"
        :src="videoUrl"
        class="video-element"
        controls
        preload="metadata"
      ></video>
    </div>
    
    <div class="video-footer-info">
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="vid-play-icon"><polygon points="5 3 19 12 5 21 5 3"/></svg>
      <span>Video Hướng Dẫn</span>
      <span class="ext-btn" @click="openExternal">Mở ngoài</span>
    </div>
  </div>
</template>

<script setup lang="ts">


const props = defineProps({
  videoUrl: {
    type: String,
    required: true
  },
  title: {
    type: String,
    default: ''
  }
});



const openInBrowser = () => {
  window.open(props.videoUrl, '_blank');
};

const openExternal = () => {
  window.open(props.videoUrl, '_blank');
};
</script>

<style scoped>
.video-container {
  display: flex;
  flex-direction: column;
  background-color: #1e1e1e;
  border-color: #333;
  padding: 1rem;
  color: #fff;
  min-height: 480px;
}

.video-title-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  border-bottom: 1px solid #333;
  padding-bottom: 0.5rem;
}

.video-filename {
  font-size: 0.9rem;
  font-weight: 500;
  color: #ccc;
}

.btn-open-browser {
  background-color: #333;
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

.btn-open-browser:hover {
  background-color: #444;
  color: #fff;
  border-color: #666;
}

.btn-icon {
  width: 12px;
  height: 12px;
}

.video-player-wrapper {
  flex: 1;
  position: relative;
  background-color: #000;
  border-radius: var(--radius-md);
  overflow: hidden;
  display: flex;
  justify-content: center;
  align-items: center;
  aspect-ratio: 16/9;
  max-height: 400px;
}

.video-element {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.video-footer-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 1rem;
  padding-top: 0.5rem;
  border-top: 1px solid #333;
  font-size: 0.9rem;
  color: #888;
}

.vid-play-icon {
  width: 16px;
  height: 16px;
  color: var(--primary-color);
}

.ext-btn {
  margin-left: auto;
  font-size: 0.8rem;
  color: #aaa;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.ext-btn:hover {
  color: #fff;
}
</style>
