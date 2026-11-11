const AUTH_STORAGE_KEYS = [
  "accessToken",
  "userRole",
  "username",
  "fullName",
  "mustChangePassword",
] as const;

export interface AuthSession {
  role: string;
  roles?: string[];
  roleNames?: Record<string, string>;
  systemAdmin?: boolean;
  permissionRevision?: number;
  permissions?: string[];
  unitId?: string;
  scopeUnitIds?: string[];
  status?: string;
  learnerType?: string;
  username: string;
  fullName: string;
  mustChangePassword: boolean;
}

let cachedSession: AuthSession | null = null;
// Set after a successful logout in this tab: the server session is known to be gone,
// so the route guard skips asking /api/auth/session (it would only answer 401).
let signedOut = false;

export const getCachedAuthSession = () => cachedSession;
export const isSignedOut = () => signedOut;
export const markSignedOut = () => {
  signedOut = true;
};

export const setCachedAuthSession = (session: AuthSession) => {
  cachedSession = session;
  signedOut = false;
};

export const updateCachedAuthSession = (updates: Partial<AuthSession>) => {
  if (cachedSession) cachedSession = { ...cachedSession, ...updates };
};

export const clearUserDraftsAndTemporaryState = () => {
  // 1. Clear all sessionStorage (modal drafts, tree states, etc.)
  try {
    sessionStorage.clear();
  } catch (e) {
    console.warn("Failed to clear sessionStorage on logout", e);
  }

  // 2. Clear modal drafts and temporary state stored in localStorage
  try {
    const keysToRemove: string[] = [];
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (
        key &&
        (key.toLowerCase().includes("draft") ||
          key.toLowerCase().includes("modal") ||
          key.startsWith("cbs_quick_curriculum") ||
          key.startsWith("curriculum_") ||
          key.includes("tree_state"))
      ) {
        keysToRemove.push(key);
      }
    }
    keysToRemove.forEach((k) => localStorage.removeItem(k));
  } catch (e) {
    console.warn("Failed to clear draft keys from localStorage on logout", e);
  }
};

export const clearAuthSession = () => {
  cachedSession = null;
  AUTH_STORAGE_KEYS.forEach((key) => localStorage.removeItem(key));
  clearUserDraftsAndTemporaryState();
};

export const redirectToLogin = () => {
  clearAuthSession();
  if (window.location.pathname !== "/login") {
    window.location.replace("/login");
  }
};
