/** Small, bounded cache. Context is part of the key (account, permissions, progress). */
export function createAsyncResourceCache<T>(ttlMs = 30_000, capacity = 5) {
  const values = new Map<string, { value: T; expires: number }>();
  const pending = new Map<string, Promise<T>>();
  let generation = 0;
  return {
    async get(key: string, load: () => Promise<T>, force = false): Promise<T> {
      const cached = values.get(key);
      if (!force && cached && cached.expires > Date.now()) return cached.value;
      const running = pending.get(key);
      if (running) return running;
      const current = generation;
      const request = Promise.resolve().then(load).then(value => {
        if (current === generation) {
          values.delete(key);
          values.set(key, { value, expires: Date.now() + ttlMs });
          while (values.size > capacity) values.delete(values.keys().next().value!);
        }
        return value;
      }).finally(() => {
        if (pending.get(key) === request) pending.delete(key);
      });
      pending.set(key, request);
      return request;
    },
    clear() { generation++; values.clear(); pending.clear(); },
  };
}
