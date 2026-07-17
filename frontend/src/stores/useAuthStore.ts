import { create } from 'zustand'

export interface UserInfo {
  id: number; username: string; displayName: string; role: string; email: string
}

interface AuthState {
  accessToken: string | null; refreshToken: string | null; user: UserInfo | null
  setAuth: (access: string, refresh: string, user: UserInfo) => void
  setAccessToken: (token: string) => void
  logout: () => void
  isAuthenticated: () => boolean
  hasRole: (role: string) => boolean
}

const TOKENS_KEY = 'pl_auth'

function loadFromStorage(): Partial<AuthState> {
  try {
    const raw = localStorage.getItem(TOKENS_KEY)
    if (!raw) return {}
    const data = JSON.parse(raw)
    return { accessToken: data.accessToken, refreshToken: data.refreshToken, user: data.user }
  } catch { return {} }
}

function saveToStorage(state: Partial<AuthState>) {
  localStorage.setItem(TOKENS_KEY, JSON.stringify({
    accessToken: state.accessToken, refreshToken: state.refreshToken, user: state.user
  }))
}

const useAuthStore = create<AuthState>((set, get) => {
  const stored = loadFromStorage()
  return {
    accessToken: stored.accessToken ?? null,
    refreshToken: stored.refreshToken ?? null,
    user: stored.user ?? null,
    setAuth: (access, refresh, user) => {
      const s = { accessToken: access, refreshToken: refresh, user }
      saveToStorage(s); set(s)
    },
    setAccessToken: (token) => {
      set({ accessToken: token })
      const s = get()
      saveToStorage({ accessToken: s.accessToken, refreshToken: s.refreshToken, user: s.user })
    },
    logout: () => {
      localStorage.removeItem(TOKENS_KEY)
      set({ accessToken: null, refreshToken: null, user: null })
    },
    isAuthenticated: () => !!get().accessToken,
    hasRole: (role) => get().user?.role === role,
  }
})

export default useAuthStore
