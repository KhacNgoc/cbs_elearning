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

export const getCachedAuthSession = () => cachedSession;

export const setCachedAuthSession = (session: AuthSession) => {
  cachedSession = session;
};

export const updateCachedAuthSession = (updates: Partial<AuthSession>) => {
  if (cachedSession) cachedSession = { ...cachedSession, ...updates };
};

export const clearAuthSession = () => {
  cachedSession = null;
  AUTH_STORAGE_KEYS.forEach((key) => localStorage.removeItem(key));
};

export const redirectToLogin = () => {
  clearAuthSession();
  if (window.location.pathname !== "/login") {
    window.location.replace("/login");
  }
};
