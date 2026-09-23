"use client";

import { createContext, useCallback, useContext, useEffect, useState, ReactNode } from "react";
import { api, ApiError } from "./api";
import { Member } from "./types";

type AuthState = {
  member: Member | null;
  loading: boolean;
  login: (regno: string, password: string) => Promise<Member>;
  logout: () => Promise<void>;
  refresh: () => Promise<void>;
};

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [member, setMember] = useState<Member | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      const m = await api.get<Member>("/api/me");
      setMember(m);
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) setMember(null);
      else throw e;
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // Fetch-on-mount: checks for an existing session cookie against the backend.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    refresh();
  }, [refresh]);

  const login = useCallback(async (regno: string, password: string) => {
    const m = await api.post<Member>("/api/auth/login", { regno, password });
    setMember(m);
    return m;
  }, []);

  const logout = useCallback(async () => {
    await api.post("/api/auth/logout");
    setMember(null);
  }, []);

  return (
    <AuthContext.Provider value={{ member, loading, login, logout, refresh }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
