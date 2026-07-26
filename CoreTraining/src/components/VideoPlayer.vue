<template>
  <div class="video-container card">
    <div class="video-title-bar">
      <span class="video-filename">{{ title || 'CIF03_Quy trinh tao CIF KHTC.mp4' }}</span>
      <button class="btn-open-browser" @click="openInBrowser">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="btn-icon"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
        Open in browser
      </button>
    </div>
    
    <div class="video-player-wrapper">
      <video
        ref="videoElement"
        :src="videoUrl"
        class="video-element"
        @timeupdate="onTimeUpdate"
        @loadedmetadata="onLoadedMetadata"
        @ended="onVideoEnded"
        @play="isPlaying = true"
        @pause="isPlaying = false"
        preload="metadata"
      ></video>
      
      <!-- Big Play Button Overlay -->
      <div v-if="!isPlaying" class="play-overlay" @click="togglePlay">
        <div class="big-play-btn">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="play-svg"><polygon points="5 3 19 12 5 21 5 3"/></svg>
        </div>
      </div>
      
      <!-- Custom Controls Bar -->
      <div class="custom-controls">
        <div class="progress-container" @click="seek">
          <div class="progress-bar">
            <div class="progress-current" :style="{ width: `${progressPercent}%` }"></div>
          </div>
        </div>
        
        <div class="controls-row">
          <div class="left-ctrls">
            <button class="ctrl-btn" @click="togglePlay">
              <svg v-if="isPlaying" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="icon-svg"><rect x="6" y="4" width="4" height="16"/><rect x="14" y="4" width="4" height="16"/></svg>
              <svg v-else xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="icon-svg"><polygon points="5 3 19 12 5 21 5 3"/></svg>
            </button>
            <span class="time-display">{{ formatTime(currentTime) }} / {{ formatTime(duration) }}</span>
            <button data-testid="video-add-note" class="ctrl-btn note-control" title="Thêm ghi chú tại thời điểm hiện tại"
              aria-label="Thêm ghi chú" @click.stop="requestNote">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none"
                stroke="currentColor" stroke-width="2" stroke-linecap="round"
                stroke-linejoin="round" class="icon-svg">
                <path d="M12 20h9" />
                <path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z" />
              </svg>
              <span>Thêm ghi chú</span>
            </button>
          </div>
          
          <div class="right-ctrls">
            <button class="ctrl-btn" @click="toggleMute">
              <svg v-if="isMuted" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="icon-svg"><path d="M11 5L6 9H2v6h4l5 4V5z"/><line x1="23" y1="9" x2="17" y2="15"/><line x1="17" y1="9" x2="23" y2="15"/></svg>
              <svg v-else xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="icon-svg"><path d="M11 5L6 9H2v6h4l5 4V5z"/><path d="M19.07 4.93a10 10 0 0 1 0 14.14M15.54 8.46a5 5 0 0 1 0 7.07"/></svg>
            </button>
            <button class="ctrl-btn" @click="toggleFullscreen">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="icon-svg"><path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"/></svg>
            </button>
          </div>
        </div>
      </div>
    </div>
    
    <div class="video-footer-info">
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="vid-play-icon"><polygon points="5 3 19 12 5 21 5 3"/></svg>
      <span>Video Hướng Dẫn</span>
      <span class="ext-btn" @click="openExternal">Mở ngoài</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';

const emit = defineEmits<{
  'time-update': [seconds: number];
  'add-note': [seconds: number];
}>();

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

const videoElement = ref<HTMLVideoElement | null>(null);
const isPlaying = ref(false);
const currentTime = ref(0);
const duration = ref(0);
const isMuted = ref(false);

const progressPercent = computed(() => {
  if (duration.value === 0) return 0;
  return (currentTime.value / duration.value) * 100;
});

const togglePlay = () => {
  if (!videoElement.value) return;
  if (isPlaying.value) {
    videoElement.value.pause();
    isPlaying.value = false;
  } else {
    videoElement.value.play().then(() => {
      isPlaying.value = true;
    }).catch(err => {
      console.log('Video play failed or interrupted:', err);
    });
  }
};

const toggleMute = () => {
  if (!videoElement.value) return;
  videoElement.value.muted = !videoElement.value.muted;
  isMuted.value = videoElement.value.muted;
};

const toggleFullscreen = () => {
  if (!videoElement.value) return;
  if (videoElement.value.requestFullscreen) {
    videoElement.value.requestFullscreen();
  }
};

const onTimeUpdate = () => {
  if (videoElement.value) {
    currentTime.value = videoElement.value.currentTime;
    emit('time-update', currentTime.value);
  }
};

const onLoadedMetadata = () => {
  if (videoElement.value) {
    duration.value = videoElement.value.duration;
  }
};

const onVideoEnded = () => {
  isPlaying.value = false;
};

const seek = (event: MouseEvent) => {
  if (!videoElement.value || duration.value === 0) return;
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
  const clickX = event.clientX - rect.left;
  const percent = clickX / rect.width;
  videoElement.value.currentTime = percent * duration.value;
};

const seekTo = (seconds: number) => {
  if (!videoElement.value) return;
  const upperBound = Number.isFinite(videoElement.value.duration)
    ? videoElement.value.duration
    : seconds;
  videoElement.value.currentTime = Math.max(0, Math.min(seconds, upperBound));
  currentTime.value = videoElement.value.currentTime;
  emit('time-update', currentTime.value);
};

const requestNote = () => {
  if (!videoElement.value) return;
  videoElement.value.pause();
  currentTime.value = videoElement.value.currentTime;
  emit('time-update', currentTime.value);
  emit('add-note', Math.floor(currentTime.value));
};

defineExpose({ seekTo });

const formatTime = (timeInSeconds: number) => {
  if (isNaN(timeInSeconds)) return '00:00';
  const minutes = Math.floor(timeInSeconds / 60);
  const seconds = Math.floor(timeInSeconds % 60);
  return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
};

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

.play-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: rgba(0, 0, 0, 0.4);
  cursor: pointer;
  z-index: 10;
}

.big-play-btn {
  width: 72px;
  height: 72px;
  background-color: rgba(0, 91, 65, 0.95);
  border-radius: 50%;
  display: flex;
  justify-content: center;
  align-items: center;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.4);
  transition: var(--transition);
  border: 2px solid var(--accent-color);
}

.play-overlay:hover .big-play-btn {
  transform: scale(1.1);
  background-color: var(--primary-color);
}

.play-svg {
  width: 30px;
  height: 30px;
  color: #fff;
  margin-left: 4px; /* offset slightly to center visual play polygon */
}

.custom-controls {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  background: linear-gradient(to top, rgba(0,0,0,0.9) 0%, rgba(0,0,0,0.3) 70%, transparent 100%);
  padding: 1.5rem 1rem 0.5rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  z-index: 15;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.video-player-wrapper:hover .custom-controls {
  opacity: 1;
}

.progress-container {
  width: 100%;
  cursor: pointer;
  padding: 4px 0;
}

.progress-bar {
  width: 100%;
  height: 4px;
  background-color: rgba(255, 255, 255, 0.3);
  border-radius: 2px;
  overflow: hidden;
  transition: height 0.1s ease;
}

.progress-container:hover .progress-bar {
  height: 6px;
}

.progress-current {
  height: 100%;
  background-color: var(--primary-color);
  width: 0%;
}

.controls-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.left-ctrls, .right-ctrls {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.ctrl-btn {
  background: none;
  border: none;
  color: #fff;
  cursor: pointer;
  padding: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: var(--transition);
}

.ctrl-btn:hover {
  color: var(--primary-color);
}

.icon-svg {
  width: 20px;
  height: 20px;
}

.time-display {
  font-size: 0.8rem;
  color: #ccc;
  font-weight: 500;
}

.note-control {
  width: auto;
  gap: .4rem;
  padding: 5px 8px;
  border: 1px solid rgba(255,255,255,.42);
  border-radius: 4px;
  font-size: .75rem;
  font-weight: 600;
}

.note-control:hover {
  color: #fff;
  border-color: #fff;
  background: rgba(255,255,255,.12);
}

@media (max-width: 600px) {
  .note-control span { display: none; }
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
