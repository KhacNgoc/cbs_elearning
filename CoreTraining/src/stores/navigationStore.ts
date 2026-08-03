import { defineStore } from 'pinia';
import { navigationApi } from '../api/navigationApi';
import type { NavigationItem } from '../api/navigationApi';

let navigationRequest: Promise<void> | null = null;

export const useNavigationStore = defineStore('navigation', {
  state: () => ({
    items: [] as NavigationItem[],
    loaded: false
  }),
  getters: {
    mainItems: state => state.items.filter(item => item.area === 'MAIN' && item.visible),
    adminItems: state => state.items.filter(item => item.area === 'ADMIN' && item.visible),
    allowedPageIds: state => new Set(state.items.map(item => item.id))
  },
  actions: {
    async load(force = false) {
      if (this.loaded && !force) return;
      if (navigationRequest && !force) return navigationRequest;
      navigationRequest = (async () => {
        this.items = await navigationApi.getMyNavigation();
        this.loaded = true;
      })();
      try {
        await navigationRequest;
      } finally {
        navigationRequest = null;
      }
    },
    clear() {
      this.items = [];
      this.loaded = false;
    }
  }
});
