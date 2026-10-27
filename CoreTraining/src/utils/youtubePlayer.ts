export interface YouTubePlayer {
  getCurrentTime(): number;
  getDuration(): number;
  cueVideoById(options: { videoId: string; startSeconds: number }): void;
  seekTo(seconds: number, allowSeekAhead: boolean): void;
  pauseVideo(): void;
  destroy(): void;
}

export interface YouTubeApi {
  Player: new (element: HTMLIFrameElement, options: {
    events: {
      onReady(event: { target: YouTubePlayer }): void;
      onStateChange(event: { target: YouTubePlayer; data: number }): void;
      onError(): void;
    };
  }) => YouTubePlayer;
}

type YouTubeWindow = Window & {
  YT?: YouTubeApi;
  onYouTubeIframeAPIReady?: () => void;
};
let loading: Promise<YouTubeApi> | undefined;

export function loadYouTubeApi(): Promise<YouTubeApi> {
  const host = window as YouTubeWindow;
  if (host.YT?.Player) return Promise.resolve(host.YT);
  if (loading) return loading;
  loading = new Promise<YouTubeApi>((resolve, reject) => {
    const previous = host.onYouTubeIframeAPIReady;
    const script = document.createElement('script');
    let timer: ReturnType<typeof setTimeout>;
    const cleanup = () => {
      clearTimeout(timer);
      if (host.onYouTubeIframeAPIReady === ready) host.onYouTubeIframeAPIReady = previous;
      script.onerror = null;
    };
    const fail = () => {
      cleanup();
      script.remove();
      loading = undefined;
      reject(new Error('Không thể tải YouTube Player API'));
    };
    const ready = () => {
      if (!host.YT?.Player) { fail(); return; }
      cleanup();
      resolve(host.YT);
      previous?.();
    };
    host.onYouTubeIframeAPIReady = ready;
    script.src = 'https://www.youtube.com/iframe_api';
    script.async = true;
    script.onerror = fail;
    timer = setTimeout(fail, 15000);
    document.head.appendChild(script);
  });
  return loading;
}
