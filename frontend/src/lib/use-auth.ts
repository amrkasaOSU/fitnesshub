"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { api, ApiError } from "./api-client";
import type { User } from "./types";

export function useCurrentUser() {
  return useQuery<User, ApiError>({
    queryKey: ["currentUser"],
    queryFn: () => api.get<User>("/api/users/me"),
    retry: false,
  });
}

interface LoginPayload {
  email: string;
  password: string;
}

export function useLogin() {
  const queryClient = useQueryClient();
  const router = useRouter();
  return useMutation<User, ApiError, LoginPayload>({
    mutationFn: (payload) => api.post<User>("/api/auth/login", payload),
    onSuccess: (user) => {
      queryClient.setQueryData(["currentUser"], user);
      router.push(user.role === "COACH" ? "/coach/dashboard" : "/dashboard");
    },
  });
}

interface RegisterPayload {
  email: string;
  password: string;
  firstName: string;
  lastName: string;
  role: "COACH" | "CLIENT";
  coachId?: string;
}

export function useRegister() {
  const queryClient = useQueryClient();
  const router = useRouter();
  return useMutation<User, ApiError, RegisterPayload>({
    mutationFn: (payload) => api.post<User>("/api/auth/register", payload),
    onSuccess: (user) => {
      queryClient.setQueryData(["currentUser"], user);
      router.push(user.role === "COACH" ? "/coach/dashboard" : "/dashboard");
    },
  });
}

export function useLogout() {
  const queryClient = useQueryClient();
  const router = useRouter();
  return useMutation({
    mutationFn: () => api.post<void>("/api/auth/logout"),
    onSuccess: () => {
      queryClient.clear();
      router.push("/login");
    },
  });
}
