"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { api, ApiError } from "@/lib/api-client";
import type { CheckInDto, ClientSummaryDto } from "@/lib/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";

const SCORES = [
  ["Energy", "energyScore"],
  ["Sleep", "sleepScore"],
  ["Stress", "stressScore"],
  ["Hunger", "hungerScore"],
  ["Workouts", "workoutAdherence"],
  ["Nutrition", "nutritionAdherence"],
] as const;

/** 1-2 is worth a coach's attention; 4-5 is going well. */
function scoreTone(n: number) {
  if (n <= 2) return "text-red-600 dark:text-red-400";
  if (n >= 4) return "text-emerald-600 dark:text-emerald-400";
  return "text-foreground";
}

export default function CoachCheckInsPage() {
  const queryClient = useQueryClient();
  const [replies, setReplies] = useState<Record<string, string>>({});

  const { data: pending, isLoading } = useQuery({
    queryKey: ["coach", "checkins"],
    queryFn: () => api.get<CheckInDto[]>("/api/coach/checkins"),
  });
  const { data: clients } = useQuery({
    queryKey: ["coach", "clients"],
    queryFn: () => api.get<ClientSummaryDto[]>("/api/clients"),
  });

  const nameFor = (clientId: string) => {
    const c = clients?.find((x) => x.clientId === clientId);
    return c ? `${c.firstName} ${c.lastName}` : "Client";
  };

  const review = useMutation<CheckInDto, ApiError, { id: string; response: string }>({
    mutationFn: ({ id, response }) =>
      api.patch<CheckInDto>(`/api/checkins/${id}/review`, { coachResponse: response }),
    onSuccess: (_r, vars) => {
      toast.success("Reply sent");
      setReplies((prev) => ({ ...prev, [vars.id]: "" }));
      queryClient.invalidateQueries({ queryKey: ["coach", "checkins"] });
      queryClient.invalidateQueries({ queryKey: ["coach", "dashboard"] });
    },
    onError: (e) => toast.error(e.message),
  });

  if (isLoading) return <Skeleton className="h-96" />;

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Check-ins</h1>
        <p className="text-sm text-muted-foreground">
          Weekly self-reports waiting on your reply.
        </p>
      </div>

      {(pending ?? []).length === 0 && (
        <Card>
          <CardContent className="py-14 text-center">
            <p className="font-medium">Nothing to review</p>
            <p className="text-sm text-muted-foreground">
              You&apos;re caught up on every client check-in.
            </p>
          </CardContent>
        </Card>
      )}

      {(pending ?? []).map((c) => (
        <Card key={c.id}>
          <CardHeader className="pb-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <CardTitle className="text-base">{nameFor(c.clientId)}</CardTitle>
              <div className="flex items-center gap-2">
                {c.weight != null && <Badge variant="outline">{c.weight} lb</Badge>}
                <span className="text-xs text-muted-foreground">Week of {c.weekStartDate}</span>
              </div>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="grid grid-cols-3 gap-3 sm:grid-cols-6">
              {SCORES.map(([label, key]) => (
                <div key={key} className="text-center">
                  <p className="text-[11px] uppercase tracking-wide text-muted-foreground">{label}</p>
                  <p className={`text-lg font-bold ${scoreTone(c[key])}`}>{c[key]}</p>
                </div>
              ))}
            </div>

            {c.notes && (
              <p className="rounded-md bg-muted/50 p-3 text-sm">
                &ldquo;{c.notes}&rdquo;
              </p>
            )}

            <div className="space-y-2">
              <Textarea
                value={replies[c.id] ?? ""}
                onChange={(e) => setReplies((prev) => ({ ...prev, [c.id]: e.target.value }))}
                placeholder="Reply to your client..."
              />
              <Button
                onClick={() => review.mutate({ id: c.id, response: replies[c.id] ?? "" })}
                disabled={!replies[c.id]?.trim() || review.isPending}
              >
                Send reply
              </Button>
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
