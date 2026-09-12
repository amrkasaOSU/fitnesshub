"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { api, ApiError } from "@/lib/api-client";
import type { CheckInDto, PageResponse } from "@/lib/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { cn } from "@/lib/utils";

/** Monday of the current week, in local time. Check-ins are keyed per week. */
function currentWeekStart() {
  const d = new Date();
  const day = (d.getDay() + 6) % 7; // Sunday(0) -> 6, Monday(1) -> 0
  d.setDate(d.getDate() - day);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

const SCORES = [
  { key: "energyScore", label: "Energy", low: "Drained", high: "Great" },
  { key: "sleepScore", label: "Sleep", low: "Poor", high: "Excellent" },
  { key: "stressScore", label: "Stress", low: "Very high", high: "Very low" },
  { key: "hungerScore", label: "Hunger", low: "Ravenous", high: "Satisfied" },
  { key: "workoutAdherence", label: "Stuck to workouts", low: "Barely", high: "Fully" },
  { key: "nutritionAdherence", label: "Stuck to nutrition", low: "Barely", high: "Fully" },
] as const;

type ScoreKey = (typeof SCORES)[number]["key"];

export function CheckInForm() {
  const queryClient = useQueryClient();
  const weekStart = currentWeekStart();

  const { data: history } = useQuery({
    queryKey: ["checkins"],
    queryFn: () => api.get<PageResponse<CheckInDto>>("/api/checkins", { pageSize: 5 }),
  });

  const alreadySubmitted = history?.data.find((c) => c.weekStartDate === weekStart);

  const [scores, setScores] = useState<Record<ScoreKey, number | null>>({
    energyScore: null,
    sleepScore: null,
    stressScore: null,
    hungerScore: null,
    workoutAdherence: null,
    nutritionAdherence: null,
  });
  const [weight, setWeight] = useState("");
  const [notes, setNotes] = useState("");

  const complete = SCORES.every((s) => scores[s.key] !== null);

  const submit = useMutation<CheckInDto, ApiError, void>({
    mutationFn: () =>
      api.post<CheckInDto>("/api/checkins", {
        weekStartDate: weekStart,
        weight: weight === "" ? undefined : Number(weight),
        ...scores,
        notes: notes || undefined,
      }),
    onSuccess: () => {
      toast.success("Check-in sent to your coach");
      queryClient.invalidateQueries({ queryKey: ["checkins"] });
      queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    },
    onError: (e) => toast.error(e.message),
  });

  return (
    <div className="space-y-4">
      {alreadySubmitted && (
        <Card>
          <CardContent className="py-4 text-sm">
            <p className="font-medium">You&apos;ve already checked in this week.</p>
            <p className="text-muted-foreground">
              Submitting again will update it.
              {alreadySubmitted.coachResponse
                ? ` Your coach replied: "${alreadySubmitted.coachResponse}"`
                : " Your coach hasn't replied yet."}
            </p>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">How did this week go?</CardTitle>
          <p className="text-xs text-muted-foreground">
            Week of {weekStart}. Rate each from 1 to 5 — this is what your coach reads first.
          </p>
        </CardHeader>
        <CardContent className="space-y-5">
          {SCORES.map((s) => (
            <div key={s.key} className="space-y-1.5">
              <Label>{s.label}</Label>
              <div className="flex gap-1.5">
                {[1, 2, 3, 4, 5].map((n) => (
                  <button
                    key={n}
                    type="button"
                    onClick={() => setScores((prev) => ({ ...prev, [s.key]: n }))}
                    className={cn(
                      "h-10 flex-1 rounded-md border text-sm font-medium transition-colors",
                      scores[s.key] === n
                        ? "border-primary bg-primary text-primary-foreground"
                        : "hover:bg-muted"
                    )}
                    aria-label={`${s.label}: ${n} of 5`}
                    aria-pressed={scores[s.key] === n}
                  >
                    {n}
                  </button>
                ))}
              </div>
              <div className="flex justify-between text-[11px] text-muted-foreground">
                <span>{s.low}</span>
                <span>{s.high}</span>
              </div>
            </div>
          ))}

          <div className="space-y-1.5">
            <Label htmlFor="checkinWeight">Weight this week — optional</Label>
            <Input
              id="checkinWeight"
              type="number"
              inputMode="decimal"
              step="0.1"
              value={weight}
              onChange={(e) => setWeight(e.target.value)}
            />
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="checkinNotes">Anything your coach should know?</Label>
            <Textarea
              id="checkinNotes"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              placeholder="Travel, soreness, a lift that felt off, a win worth mentioning..."
            />
          </div>

          <Button className="w-full" onClick={() => submit.mutate()} disabled={!complete || submit.isPending}>
            {submit.isPending ? "Sending..." : alreadySubmitted ? "Update check-in" : "Send check-in"}
          </Button>
          {!complete && (
            <p className="text-center text-xs text-muted-foreground">
              Rate all six to send.
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
