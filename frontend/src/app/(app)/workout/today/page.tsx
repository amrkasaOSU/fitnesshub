"use client";

import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { CheckCircle2, Circle, Clock, Trophy } from "lucide-react";
import { api, ApiError } from "@/lib/api-client";
import type { SetCompletionResult, WorkoutExerciseDto, WorkoutSessionDto } from "@/lib/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { RestTimer } from "@/components/rest-timer";
import { WeekStrip } from "@/components/week-strip";
import { SkipWorkoutDialog } from "@/components/skip-workout-dialog";
import { usePendingSetQueue } from "@/lib/use-pending-set-queue";

interface SetInput {
  weight: string;
  reps: string;
  rpe: string;
}

export default function TodayWorkoutPage() {
  const queryClient = useQueryClient();
  const { data: session, isLoading } = useQuery({
    queryKey: ["workout", "today"],
    queryFn: () => api.get<WorkoutSessionDto>("/api/workouts/today"),
  });

  const [inputs, setInputs] = useState<Record<string, SetInput>>({});
  const [activeRest, setActiveRest] = useState<{ exerciseId: string; seconds: number } | null>(null);
  const { pending, enqueue, flush } = usePendingSetQueue(session?.id);

  useEffect(() => {
    const onOnline = () => flush();
    window.addEventListener("online", onOnline);
    return () => window.removeEventListener("online", onOnline);
  }, [flush]);

  const logSet = useMutation({
    mutationFn: async (vars: { exercise: WorkoutExerciseDto; weight: number; reps: number; rpe?: number }) => {
      if (!session) throw new Error("No session");
      return api.post<SetCompletionResult>(`/api/workouts/${session.id}/sets`, {
        exerciseId: vars.exercise.exerciseId,
        weight: vars.weight,
        reps: vars.reps,
        rpe: vars.rpe,
        isWarmup: false,
        isFailure: false,
      });
    },
    onSuccess: (result, vars) => {
      queryClient.invalidateQueries({ queryKey: ["workout", "today"] });
      if (result.newPersonalRecords.length > 0) {
        result.newPersonalRecords.forEach((pr) =>
          toast.success(`New PR! ${vars.exercise.exerciseName} - ${pr.recordType.replace("_", " ")}: ${pr.value}`, {
            icon: <Trophy className="h-4 w-4" />,
          })
        );
      } else {
        toast.success("Set logged");
      }
      if (vars.exercise.restSeconds) {
        setActiveRest({ exerciseId: vars.exercise.exerciseId, seconds: vars.exercise.restSeconds });
      }
    },
    onError: (err, vars) => {
      if (err instanceof ApiError) {
        toast.error(err.message);
        return;
      }
      // Network failure: queue it locally so we never lose the set.
      enqueue(vars);
      toast.message("You're offline - this set will sync once you're back online.");
    },
  });

  const completeWorkout = useMutation({
    mutationFn: () => api.post<WorkoutSessionDto>(`/api/workouts/${session!.id}/complete`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["workout", "today"] });
      toast.success("Workout complete! Great work.");
    },
  });

  const progress = useMemo(() => {
    if (!session) return { done: 0, total: 0 };
    const total = session.exercises.length;
    const done = session.exercises.filter((e) => e.completedSets.length > 0).length;
    return { done, total };
  }, [session]);

  if (isLoading || !session) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-16" />
        {Array.from({ length: 3 }).map((_, i) => (
          <Skeleton key={i} className="h-40" />
        ))}
      </div>
    );
  }

  const isCompleted = session.status === "COMPLETED";
  const isSkipped = session.status === "SKIPPED";
  // No program day resolved at all - the coach hasn't assigned a program.
  // A prescribed rest day is different: it has a programDayId and no exercises.
  const noProgram = !session.programDayId;
  const isRestDay = !noProgram && session.exercises.length === 0;

  return (
    <div className="space-y-4 pb-24">
      <WeekStrip />

      <Card>
        <CardContent className="flex flex-wrap items-center justify-between gap-3 py-4">
          <div>
            <h1 className="text-xl font-bold">
              {noProgram ? "Nothing scheduled" : isRestDay ? "Rest day" : session.dayName}
            </h1>
            <p className="text-sm text-muted-foreground">
              {noProgram
                ? "Your coach hasn't assigned a program yet."
                : isRestDay
                  ? "No training prescribed today. Rest is part of the plan."
                  : `${progress.done} / ${progress.total} exercises`}
              {pending.length > 0 && (
                <span className="ml-2 text-amber-600">· {pending.length} waiting to sync</span>
              )}
            </p>
          </div>
          <div className="flex items-center gap-2">
            {!isCompleted && !isSkipped && !noProgram && !isRestDay && (
              <>
                <SkipWorkoutDialog />
                <Button onClick={() => completeWorkout.mutate()} disabled={completeWorkout.isPending}>
                  Finish Workout
                </Button>
              </>
            )}
            {isCompleted && <Badge variant="secondary">Completed</Badge>}
            {isSkipped && <Badge className="bg-amber-500 hover:bg-amber-500">Skipped</Badge>}
          </div>
        </CardContent>
      </Card>

      {isSkipped && session.skipReason && (
        <Card>
          <CardContent className="py-4 text-sm">
            <span className="font-medium">You skipped this workout:</span>{" "}
            <span className="text-muted-foreground">&ldquo;{session.skipReason}&rdquo;</span>
          </CardContent>
        </Card>
      )}

      {activeRest && (
        <RestTimer seconds={activeRest.seconds} onDismiss={() => setActiveRest(null)} key={activeRest.exerciseId + activeRest.seconds} />
      )}

      {session.exercises.map((exercise) => {
        const key = exercise.exerciseId;
        const input = inputs[key] ?? { weight: "", reps: "", rpe: "" };
        const isDone = exercise.completedSets.length > 0;

        return (
          <Card key={key}>
            <CardHeader className="pb-3">
              <CardTitle className="flex items-center gap-2 text-base">
                {isDone ? (
                  <CheckCircle2 className="h-4 w-4 text-emerald-500" />
                ) : (
                  <Circle className="h-4 w-4 text-muted-foreground" />
                )}
                {exercise.exerciseName}
              </CardTitle>
              <p className="text-xs text-muted-foreground">
                Target: {exercise.targetSets ?? "-"} x {exercise.targetReps ?? "-"}
                {exercise.targetWeight ? ` @ ${exercise.targetWeight}` : ""}
                {exercise.targetRpe ? ` RPE ${exercise.targetRpe}` : ""}
                {exercise.restSeconds ? ` · Rest ${exercise.restSeconds}s` : ""}
              </p>
            </CardHeader>
            <CardContent className="space-y-3">
              {exercise.previousPerformance && (
                <div className="flex items-center justify-between rounded-md bg-muted/50 px-3 py-2 text-sm">
                  <span className="text-muted-foreground">
                    Previous: {exercise.previousPerformance.weight} x {exercise.previousPerformance.reps}
                    {exercise.previousPerformance.rpe ? ` @ RPE ${exercise.previousPerformance.rpe}` : ""}
                  </span>
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() =>
                      setInputs((prev) => ({
                        ...prev,
                        [key]: {
                          weight: String(exercise.previousPerformance!.weight),
                          reps: String(exercise.previousPerformance!.reps),
                          rpe: exercise.previousPerformance!.rpe ? String(exercise.previousPerformance!.rpe) : "",
                        },
                      }))
                    }
                  >
                    Use previous
                  </Button>
                </div>
              )}

              {exercise.completedSets.length > 0 && (
                <div className="space-y-1">
                  {exercise.completedSets.map((set) => (
                    <div key={set.id} className="flex justify-between text-sm text-muted-foreground">
                      <span>Set {set.setNumber}</span>
                      <span>
                        {set.weight} x {set.reps}
                        {set.rpe ? ` @ RPE ${set.rpe}` : ""}
                      </span>
                    </div>
                  ))}
                </div>
              )}

              {!isCompleted && (
                <div className="grid grid-cols-3 gap-2">
                  <Input
                    inputMode="decimal"
                    placeholder="Weight"
                    className="h-12 text-center text-lg"
                    value={input.weight}
                    onChange={(e) => setInputs((p) => ({ ...p, [key]: { ...input, weight: e.target.value } }))}
                  />
                  <Input
                    inputMode="numeric"
                    placeholder="Reps"
                    className="h-12 text-center text-lg"
                    value={input.reps}
                    onChange={(e) => setInputs((p) => ({ ...p, [key]: { ...input, reps: e.target.value } }))}
                  />
                  <Input
                    inputMode="decimal"
                    placeholder="RPE"
                    className="h-12 text-center text-lg"
                    value={input.rpe}
                    onChange={(e) => setInputs((p) => ({ ...p, [key]: { ...input, rpe: e.target.value } }))}
                  />
                </div>
              )}

              {!isCompleted && (
                <Button
                  className="h-12 w-full text-base"
                  disabled={!input.weight || !input.reps || logSet.isPending}
                  onClick={() =>
                    logSet.mutate({
                      exercise,
                      weight: parseFloat(input.weight),
                      reps: parseInt(input.reps, 10),
                      rpe: input.rpe ? parseFloat(input.rpe) : undefined,
                    })
                  }
                >
                  Complete Set
                </Button>
              )}
            </CardContent>
          </Card>
        );
      })}

      {isCompleted && session.feedback.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <Clock className="h-4 w-4" /> Coach Feedback
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            {session.feedback.map((f) => (
              <p key={f.id} className="text-sm">
                {f.content}
              </p>
            ))}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
