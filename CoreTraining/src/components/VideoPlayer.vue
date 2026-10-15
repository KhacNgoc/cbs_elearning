<template>
  <div class="video-container card">
    <div class="video-title-bar">
      <span class="video-filename">{{
        title || "CIF03_Quy trinh tao CIF KHTC.mp4"
      }}</span>
    </div>

    <div class="video-player-wrapper">
      <iframe
        v-if="youtubeEmbedUrl"
        :src="youtubeEmbedUrl"
        class="video-element youtube-player"
        :title="title || 'Video bài học'"
        allow="
          accelerometer;
          autoplay;
          clipboard-write;
          encrypted-media;
          gyroscope;
          picture-in-picture;
          web-share;
        "
        allowfullscreen
      ></iframe>
      <video
        v-else
        ref="videoElement"
        :src="resolvedVideoUrl || undefined"
        class="video-element"
        @timeupdate="onTimeUpdate"
        @loadedmetadata="onLoadedMetadata"
        @durationchange="syncDuration"
        @loadeddata="syncDuration"
        @ended="onVideoEnded"
        @error="onVideoError"
        @play="isPlaying = true"
        @pause="isPlaying = false"
        preload="metadata"
      ></video>

      <div
        v-if="!youtubeEmbedUrl && (videoError || accessLoading)"
        class="video-status"
        role="status"
      >
        <p>{{ videoError || "Đang tải video…" }}</p>
        <button v-if="videoError" type="button" @click="retryVideo">
          Thử lại
        </button>
      </div>
      <!-- Big Play Button Overlay -->
      <div
        v-if="
          !youtubeEmbedUrl &&
          resolvedVideoUrl &&
          !isPlaying &&
          !videoError &&
          !accessLoading
        "
        class="play-overlay cursor-pointer"
        @click="togglePlay"
      >
        <div class="big-play-btn">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            viewBox="0 0 24 24"
            fill="currentColor"
            class="play-svg"
          >
            <polygon points="5 3 19 12 5 21 5 3" />
          </svg>
        </div>
      </div>

      <!-- Custom Controls Bar -->
      <div v-if="!youtubeEmbedUrl" class="custom-controls">
        <div class="progress-container cursor-pointer" @click="seek">
          <div class="progress-bar">
            <div
              class="progress-current"
              :style="{ width: `${progressPercent}%` }"
            ></div>
          </div>
        </div>

        <div class="controls-row">
          <div class="left-ctrls">
            <button class="ctrl-btn" @click="togglePlay">
              <svg
                v-if="isPlaying"
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                fill="currentColor"
                class="icon-svg"
              >
                <rect x="6" y="4" width="4" height="16" />
                <rect x="14" y="4" width="4" height="16" />
              </svg>
              <svg
                v-else
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                fill="currentColor"
                class="icon-svg"
              >
                <polygon points="5 3 19 12 5 21 5 3" />
              </svg>
            </button>
            <span class="time-display"
              >{{ formatTime(currentTime) }} / {{ formatTime(duration) }}</span
            >
            <button
              data-testid="video-add-note"
              class="ctrl-btn note-control"
              title="Thêm ghi chú tại thời điểm hiện tại"
              aria-label="Thêm ghi chú"
              @click.stop="requestNote"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                class="icon-svg"
              >
                <path d="M12 20h9" />
                <path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z" />
              </svg>
              <span>Thêm ghi chú</span>
            </button>
          </div>

          <div class="right-ctrls">
            <div class="volume-control">
              <button
                class="ctrl-btn"
                :aria-label="isMuted ? 'Bật âm thanh' : 'Tắt âm thanh'"
                @click="toggleMute"
              >
                <svg
                  v-if="isMuted"
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 24 24"
                  fill="currentColor"
                  class="icon-svg"
                >
                  <path d="M11 5L6 9H2v6h4l5 4V5z" />
                  <line x1="23" y1="9" x2="17" y2="15" />
                  <line x1="17" y1="9" x2="23" y2="15" />
                </svg>
                <svg
                  v-else
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 24 24"
                  fill="currentColor"
                  class="icon-svg"
                >
                  <path d="M11 5L6 9H2v6h4l5 4V5z" />
                  <path
                    d="M19.07 4.93a10 10 0 0 1 0 14.14M15.54 8.46a5 5 0 0 1 0 7.07"
                  />
                </svg>
              </button>
              <input
                v-model.number="volume"
                class="volume-slider"
                type="range"
                min="0"
                max="1"
                step="0.05"
                aria-label="Âm lượng video"
                :style="{ '--volume-progress': `${volume * 100}%` }"
                @input="updateVolume"
              />
              <span class="volume-value">{{ Math.round(volume * 100) }}%</span>
            </div>
            <button class="ctrl-btn" @click="toggleFullscreen">
              <svg
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
                class="icon-svg"
              >
                <path
                  d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"
                />
              </svg>
            </button>
          </div>
        </div>
      </div>
    </div>

    <div class="video-footer-info">
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        class="vid-play-icon"
      >
        <polygon points="5 3 19 12 5 21 5 3" />
      </svg>
      <span>Video Hướng Dẫn</span>
      <span class="ext-btn cursor-pointer" @click="openExternal">Mở ngoài</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from "vue";
import { VideoAccessSession } from "../utils/videoAccess";
import { axiosClient } from "../api/axiosClient";
import type { LessonProgressRequest } from "../types";

const emit = defineEmits<{
  "time-update": [seconds: number];
  "progress-update": [progress: LessonProgressRequest];
  ended: [progress: LessonProgressRequest];
  "add-note": [seconds: number];
}>();

const props = defineProps({
  lessonId: {
    type: String,
    required: true,
  },
  videoUrl: {
    type: String,
    required: true,
  },
  title: {
    type: String,
    default: "",
  },
  initialPositionSeconds: {
    type: Number,
    default: 0,
  },
  initialWatchedSeconds: {
    type: Number,
    default: 0,
  },
});

const videoElement = ref<HTMLVideoElement | null>(null);
const isPlaying = ref(false);
const currentTime = ref(0);
const duration = ref(0);
const watchedSeconds = ref(props.initialWatchedSeconds);
const lastPlaybackPosition = ref(props.initialPositionSeconds);
const isMuted = ref(false);
const volume = ref(1);
const authorizedStreamUrl = ref("");
const videoError = ref("");
const accessLoading = ref(false);
let accessSession: VideoAccessSession | undefined;
let pendingRestore = { position: props.initialPositionSeconds, playing: false };
let restoring = true;
const protectedVideo = computed(() =>
  props.videoUrl.startsWith("/uploads/videos/"),
);

const youtubeVideoId = computed(() => {
  const value = props.videoUrl.trim();
  if (!value) return "";
  try {
    const url = new URL(value);
    const host = url.hostname.replace(/^www\./, "").toLowerCase();
    if (host === "youtu.be")
      return url.pathname.split("/").filter(Boolean)[0] || "";
    if (
      host === "youtube.com" ||
      host === "m.youtube.com" ||
      host === "youtube-nocookie.com"
    ) {
      if (url.pathname === "/watch") return url.searchParams.get("v") || "";
      const parts = url.pathname.split("/").filter(Boolean);
      if (["embed", "shorts", "live"].includes(parts[0])) return parts[1] || "";
    }
  } catch {
    return "";
  }
  return "";
});

const youtubeEmbedUrl = computed(() =>
  youtubeVideoId.value
    ? `https://www.youtube-nocookie.com/embed/${encodeURIComponent(youtubeVideoId.value)}?rel=0`
    : "",
);

const resolvedVideoUrl = computed(() => {
  if (!props.videoUrl.trim()) return "";
  if (/^(https?:|blob:|data:)/i.test(props.videoUrl)) return props.videoUrl;
  if (props.videoUrl.startsWith("/uploads/videos/"))
    return authorizedStreamUrl.value;
  const apiBaseUrl = import.meta.env.VITE_API_URL || "";
  return `${apiBaseUrl}${props.videoUrl.startsWith("/") ? "" : "/"}${props.videoUrl}`;
});

const showVideoError = () => {
  accessLoading.value = false;
  videoError.value =
    "Không thể phát video. Quyền truy cập có thể đã hết hạn hoặc kết nối bị gián đoạn.";
};

const retryVideo = () => {
  videoError.value = "";
  accessLoading.value = true;
  if (accessSession) accessSession.retry();
  else {
    videoElement.value?.load();
    accessLoading.value = false;
  }
};

const onVideoError = () => {
  if (!resolvedVideoUrl.value || videoError.value) return;
  if (accessSession) accessSession.recover();
  else showVideoError();
};

watch(
  () => [props.lessonId, props.videoUrl],
  () => {
    accessSession?.stop();
    accessSession = undefined;
    authorizedStreamUrl.value = "";
    videoError.value = "";
    accessLoading.value = false;
    isPlaying.value = false;
    duration.value = 0;
    currentTime.value = props.initialPositionSeconds;
    watchedSeconds.value = props.initialWatchedSeconds;
    lastPlaybackPosition.value = props.initialPositionSeconds;
    pendingRestore = { position: props.initialPositionSeconds, playing: false };
    restoring = true;
    if (!protectedVideo.value) return;
    const lessonId = props.lessonId;
    accessLoading.value = true;
    accessSession = new VideoAccessSession(
      async (signal) => {
        const response = await axiosClient.get<{
          streamUrl: string;
          expiresAt: number;
        }>(`/api/videos/access/${encodeURIComponent(lessonId)}`, { signal });
        return response.data;
      },
      (access) => {
        if (authorizedStreamUrl.value && !restoring) {
          const video = videoElement.value;
          pendingRestore = {
            position: video?.currentTime ?? currentTime.value,
            playing: video ? !video.paused && !video.ended : isPlaying.value,
          };
        }
        restoring = true;
        const apiBaseUrl = (import.meta.env.VITE_API_URL || "").replace(
          /\/$/,
          "",
        );
        authorizedStreamUrl.value = `${apiBaseUrl}${access.streamUrl}`;
        videoError.value = "";
        accessLoading.value = false;
      },
      showVideoError,
    );
    void accessSession.load();
  },
  { immediate: true },
);

onBeforeUnmount(() => accessSession?.stop());

const progressPercent = computed(() => {
  if (duration.value <= 0) return 0;
  return Math.min(100, (currentTime.value / duration.value) * 100);
});

const syncDuration = () => {
  const video = videoElement.value;
  if (!video) return;

  const candidates = [video.duration];
  if (video.seekable.length > 0) {
    candidates.push(video.seekable.end(video.seekable.length - 1));
  }
  const detectedDuration = Math.max(
    ...candidates.filter((value) => Number.isFinite(value) && value > 0),
    0,
  );
  if (detectedDuration > 0) duration.value = detectedDuration;
};

const togglePlay = () => {
  if (!videoElement.value || !resolvedVideoUrl.value || videoError.value)
    return;
  if (isPlaying.value) {
    videoElement.value.pause();
    isPlaying.value = false;
  } else {
    videoElement.value
      .play()
      .then(() => {
        isPlaying.value = true;
      })
      .catch((err) => {
        console.log("Video play failed or interrupted:", err);
      });
  }
};

const toggleMute = () => {
  if (!videoElement.value) return;
  const shouldUnmute = videoElement.value.muted;
  if (shouldUnmute && videoElement.value.volume === 0) {
    videoElement.value.volume = 0.5;
    volume.value = 0.5;
  }
  videoElement.value.muted = !shouldUnmute;
  isMuted.value = videoElement.value.muted;
};

const updateVolume = () => {
  if (!videoElement.value) return;
  videoElement.value.volume = volume.value;
  videoElement.value.muted = volume.value === 0;
  isMuted.value = videoElement.value.muted;
};

const toggleFullscreen = () => {
  if (!videoElement.value) return;
  if (videoElement.value.requestFullscreen) {
    videoElement.value.requestFullscreen();
  }
};

const onTimeUpdate = () => {
  if (restoring) return;
  if (videoElement.value) {
    const nextPosition = videoElement.value.currentTime;
    const elapsed = nextPosition - lastPlaybackPosition.value;
    if (!restoring && isPlaying.value && elapsed > 0 && elapsed <= 2)
      watchedSeconds.value += elapsed;
    currentTime.value = nextPosition;
    lastPlaybackPosition.value = nextPosition;
    syncDuration();
    emit("time-update", currentTime.value);
    emit("progress-update", progressSnapshot());
  }
};

const onLoadedMetadata = () => {
  const video = videoElement.value;
  if (!video) return;
  video.volume = volume.value;
  video.muted = isMuted.value;
  syncDuration();
  const position = pendingRestore.position;
  video.currentTime = Math.max(
    0,
    Math.min(position, duration.value || position),
  );
  currentTime.value = video.currentTime;
  lastPlaybackPosition.value = currentTime.value;
  restoring = false;
  if (pendingRestore.playing) {
    void video.play().catch(() => {
      isPlaying.value = false;
    });
  }
};

const onVideoEnded = () => {
  isPlaying.value = false;
  currentTime.value = duration.value;
  watchedSeconds.value = Math.max(watchedSeconds.value, duration.value);
  const progress = progressSnapshot();
  emit("progress-update", progress);
  emit("ended", progress);
};

const progressSnapshot = (): LessonProgressRequest => ({
  lastPositionSeconds: currentTime.value,
  watchedSeconds: watchedSeconds.value,
  durationSeconds: duration.value,
});

const seek = (event: MouseEvent) => {
  if (!videoElement.value || duration.value === 0) return;
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
  const clickX = event.clientX - rect.left;
  const percent = clickX / rect.width;
  videoElement.value.currentTime =
    Math.max(0, Math.min(1, percent)) * duration.value;
  lastPlaybackPosition.value = videoElement.value.currentTime;
};

const seekTo = (seconds: number) => {
  if (!videoElement.value) return;
  const upperBound = Number.isFinite(videoElement.value.duration)
    ? videoElement.value.duration
    : seconds;
  videoElement.value.currentTime = Math.max(0, Math.min(seconds, upperBound));
  currentTime.value = videoElement.value.currentTime;
  lastPlaybackPosition.value = currentTime.value;
  emit("time-update", currentTime.value);
};

const resetProgress = () => {
  if (videoElement.value) {
    videoElement.value.pause();
    videoElement.value.currentTime = 0;
  }
  isPlaying.value = false;
  currentTime.value = 0;
  watchedSeconds.value = 0;
  lastPlaybackPosition.value = 0;
  emit("time-update", 0);
};

const requestNote = () => {
  if (!videoElement.value) return;
  videoElement.value.pause();
  currentTime.value = videoElement.value.currentTime;
  emit("time-update", currentTime.value);
  emit("add-note", Math.floor(currentTime.value));
};

defineExpose({ seekTo, resetProgress });

const formatTime = (timeInSeconds: number) => {
  if (isNaN(timeInSeconds)) return "00:00";
  const minutes = Math.floor(timeInSeconds / 60);
  const seconds = Math.floor(timeInSeconds % 60);
  return `${minutes.toString().padStart(2, "0")}:${seconds.toString().padStart(2, "0")}`;
};

const openExternal = () => {
  if (resolvedVideoUrl.value)
    window.open(resolvedVideoUrl.value, "_blank", "noopener,noreferrer");
};
</script>

<style scoped>
.video-status {
  position: absolute;
  inset: 0;
  z-index: 20;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  background: #111d;
  text-align: center;
}
.video-status button {
  padding: 0.5rem 1rem;
  cursor: pointer;
}
.video-container {
  display: flex;
  flex-direction: column;
  background-color: #1e1e1e;
  border-color: var(--line-333333);
  padding: 1rem;
  color: #fff;
  min-height: 480px;
}

.video-title-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
  border-bottom: 1px solid var(--line-333333);
  padding-bottom: 0.5rem;
}

.video-filename {
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--ink-cccccc);
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

.youtube-player {
  border: 0;
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
  background: linear-gradient(
    to top,
    rgba(0, 0, 0, 0.9) 0%,
    rgba(0, 0, 0, 0.3) 70%,
    transparent 100%
  );
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

.left-ctrls,
.right-ctrls {
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
  color: var(--primary-ink);
}

.icon-svg {
  width: 20px;
  height: 20px;
}

.volume-control {
  display: flex;
  align-items: center;
  gap: 6px;
}

.volume-slider {
  width: 82px;
  height: 4px;
  border-radius: 999px;
  outline: none;
  appearance: none;
  cursor: pointer;
  background: linear-gradient(
    to right,
    var(--surface-ffffff) var(--volume-progress),
    rgba(255, 255, 255, 0.32) var(--volume-progress)
  );
}

.volume-slider::-webkit-slider-thumb {
  width: 12px;
  height: 12px;
  border: 0;
  border-radius: 50%;
  appearance: none;
  background: var(--surface-ffffff);
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.35);
}

.volume-slider::-moz-range-thumb {
  width: 12px;
  height: 12px;
  border: 0;
  border-radius: 50%;
  background: var(--surface-ffffff);
}

.volume-value {
  min-width: 32px;
  color: var(--ink-d7dee8);
  font-size: 0.68rem;
  font-variant-numeric: tabular-nums;
}

.time-display {
  font-size: 0.8rem;
  color: var(--ink-cccccc);
  font-weight: 500;
}

.note-control {
  width: auto;
  gap: 0.4rem;
  padding: 5px 8px;
  border: 1px solid rgba(255, 255, 255, 0.42);
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 600;
}

.note-control:hover {
  color: #fff;
  border-color: var(--line-ffffff);
  background: rgba(255, 255, 255, 0.12);
}

@media (max-width: 600px) {
  .note-control span {
    display: none;
  }
  .volume-value {
    display: none;
  }
  .volume-slider {
    width: 58px;
  }
}

.video-footer-info {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-top: 1rem;
  padding-top: 0.5rem;
  border-top: 1px solid var(--line-333333);
  font-size: 0.9rem;
  color: var(--ink-888888);
}

.vid-play-icon {
  width: 16px;
  height: 16px;
  color: var(--primary-ink);
}

.ext-btn {
  margin-left: auto;
  font-size: 0.8rem;
  color: var(--ink-aaaaaa);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 0.25rem;
}

.ext-btn:hover {
  color: #fff;
}
</style>
