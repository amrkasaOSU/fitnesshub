"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "./api-client";
import type { WorkoutExerciseDto } from "./types";

interface PendingSet {
  id: string;
  exercise: WorkoutExerciseDto;
  weight: number;
  reps: number;
  rpe?: number;
}

/**
 * Section 151/152: if a set fails to log because the network is down, keep it
 * locally (per workout session) and retry when connectivity returns, instead
 * of silently losing the client's data. Conflicts (the set was already logged
 * some other way) are surfaced, never silently overwritten.
 */
export function usePendingSetQueue(sessionId: string | undefined) {
  const storageKey = sessionId ? `fitnesshub:pending-sets:${sessionId}` : null;
  const [pending, setPending] = useState<PendingSet[]>([]);

  useEffect(() => {
    if (!storageKey) return;
    // Reading an external store (localStorage) on mount/key-change is exactly
    // what useEffect is for; this isn't state mirrored from props.
    try {
      const raw = localStorage.getItem(storageKey);
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setPending(raw ? JSON.parse(raw) : []);
    } catch {
      setPending([]);
    }
  }, [storageKey]);

  const persist = useCallback(
    (next: PendingSet[]) => {
      setPending(next);
      if (storageKey) {
        try {
          localStorage.setItem(storageKey, JSON.stringify(next));
        } catch {
          // best-effort - if storage is unavailable there's nothing more we can do
        }
      }
    },
    [storageKey]
  );

  const enqueue = useCallback(
    (item: { exercise: WorkoutExerciseDto; weight: number; reps: number; rpe?: number }) => {
      persist([...pending, { ...item, id: crypto.randomUUID() }]);
    },
    [pending, persist]
  );

  const flush = useCallback(async () => {
    if (!sessionId || pending.length === 0) return;
    const remaining: PendingSet[] = [];
    for (const item of pending) {
      try {
        await api.post(`/api/workouts/${sessionId}/sets`, {
          exerciseId: item.exercise.exerciseId,
          weight: item.weight,
          reps: item.reps,
          rpe: item.rpe,
          isWarmup: false,
          isFailure: false,
        });
      } catch {
        remaining.push(item);
      }
    }
    persist(remaining);
  }, [sessionId, pending, persist]);

  return { pending, enqueue, flush };
}
