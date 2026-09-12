"use client";

import { useQuery } from "@tanstack/react-query";
import { api } from "./api-client";

export interface FeatureFlags {
  aiEnabled: boolean;
  billingEnabled: boolean;
}

/**
 * Which optional integrations are configured on the server. Used to show a
 * "coming soon" state rather than letting someone walk into a dead end. Treated
 * as off until the answer arrives, so nothing flashes as available and then
 * disappears.
 */
export function useFeatures() {
  const { data } = useQuery<FeatureFlags>({
    queryKey: ["features"],
    queryFn: () => api.get<FeatureFlags>("/api/features"),
    staleTime: 5 * 60 * 1000,
  });
  return data ?? { aiEnabled: false, billingEnabled: false };
}
