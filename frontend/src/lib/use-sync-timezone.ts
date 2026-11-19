"use client";

import { useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { api } from "./api-client";
import type { User } from "./types";

/**
 * Keeps the account's timezone equal to the browser's.
 *
 * The server decides what "today" is - which workout is due, which day a
 * step count or meal belongs to, where the week starts - in the user's stored
 * timezone. Nothing ever set it, so every account stayed on the UTC default
 * and, for a client in New York, "today" rolled over at 7 pm. This sends the
 * browser's IANA zone whenever it differs from the stored one, and refreshes
 * every query afterwards because day-bucketed numbers may change.
 */
export function useSyncTimezone(user: User | undefined) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!user) return;
    let zone: string | undefined;
    try {
      zone = Intl.DateTimeFormat().resolvedOptions().timeZone;
    } catch {
      return;
    }
    if (!zone || zone === user.timezone) return;

    let cancelled = false;
    api
      .patch<User>("/api/users/me", { timezone: zone })
      .then((updated) => {
        if (cancelled) return;
        queryClient.setQueryData(["currentUser"], updated);
        queryClient.invalidateQueries({ predicate: (q) => q.queryKey[0] !== "currentUser" });
      })
      .catch(() => {
        // Not worth interrupting the user over: the next page load retries.
      });
    return () => {
      cancelled = true;
    };
  }, [user, queryClient]);
}
