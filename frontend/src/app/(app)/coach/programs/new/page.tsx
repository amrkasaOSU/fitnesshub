"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { Plus, Trash2, GripVertical } from "lucide-react";
import { api, ApiError } from "@/lib/api-client";
import type { ExerciseDto, FitnessGoal, PageResponse, ProgramDto } from "@/lib/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

const GOALS: FitnessGoal[] = [
  "STRENGTH",
  "MUSCLE_GAIN",
  "FAT_LOSS",
  "GENERAL_FITNESS",
  "ATHLETIC_PERFORMANCE",
  "RECOMPOSITION",
];

interface DraftExercise {
  key: string;
  exerciseId: string;
  sets: string;
  targetReps: string;
  targetWeight: string;
  targetRpe: string;
  restSeconds: string;
}

interface DraftDay {
  key: string;
  name: string;
  exercises: DraftExercise[];
}

const newKey = () => Math.random().toString(36).slice(2);

/** A sensible starting shape: an upper/lower split with rest days built in. */
function starterDays(): DraftDay[] {
  return [
    { key: newKey(), name: "Upper A", exercises: [] },
    { key: newKey(), name: "Lower A", exercises: [] },
    { key: newKey(), name: "Rest", exercises: [] },
    { key: newKey(), name: "Upper B", exercises: [] },
    { key: newKey(), name: "Lower B", exercises: [] },
    { key: newKey(), name: "Rest", exercises: [] },
    { key: newKey(), name: "Rest", exercises: [] },
  ];
}

export default function NewProgramPage() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [goal, setGoal] = useState<FitnessGoal>("STRENGTH");
  const [durationWeeks, setDurationWeeks] = useState("12");
  const [days, setDays] = useState<DraftDay[]>(starterDays);

  const { data: exercisePage, isLoading: exercisesLoading } = useQuery({
    queryKey: ["exercises", "all"],
    queryFn: () => api.get<PageResponse<ExerciseDto>>("/api/exercises", { pageSize: 200 }),
  });
  const exercises = useMemo(() => exercisePage?.data ?? [], [exercisePage]);

  function updateDay(key: string, patch: Partial<DraftDay>) {
    setDays((prev) => prev.map((d) => (d.key === key ? { ...d, ...patch } : d)));
  }

  function addExercise(dayKey: string) {
    const first = exercises[0]?.id ?? "";
    updateDayExercises(dayKey, (list) => [
      ...list,
      { key: newKey(), exerciseId: first, sets: "4", targetReps: "8", targetWeight: "", targetRpe: "8", restSeconds: "120" },
    ]);
  }

  function updateDayExercises(dayKey: string, fn: (list: DraftExercise[]) => DraftExercise[]) {
    setDays((prev) => prev.map((d) => (d.key === dayKey ? { ...d, exercises: fn(d.exercises) } : d)));
  }

  function updateExercise(dayKey: string, exKey: string, patch: Partial<DraftExercise>) {
    updateDayExercises(dayKey, (list) => list.map((e) => (e.key === exKey ? { ...e, ...patch } : e)));
  }

  const create = useMutation<ProgramDto, ApiError, void>({
    mutationFn: () =>
      api.post<ProgramDto>("/api/programs", {
        name,
        durationWeeks: Number(durationWeeks),
        goal,
        // dayNumber is 1-based and drives the cycle: day 1 runs on the program's
        // start date, then it repeats every days.length days.
        days: days.map((d, i) => ({
          dayNumber: i + 1,
          name: d.name,
          exercises: d.exercises.map((e, j) => ({
            exerciseId: e.exerciseId,
            orderIndex: j,
            sets: Number(e.sets),
            targetReps: Number(e.targetReps),
            targetWeight: e.targetWeight === "" ? undefined : Number(e.targetWeight),
            targetRpe: e.targetRpe === "" ? undefined : Number(e.targetRpe),
            restSeconds: e.restSeconds === "" ? undefined : Number(e.restSeconds),
          })),
        })),
      }),
    onSuccess: (program) => {
      toast.success("Program created");
      router.push(`/coach/programs/${program.id}`);
    },
    onError: (e) => toast.error(e.message),
  });

  const totalExercises = days.reduce((n, d) => n + d.exercises.length, 0);
  const ready = name.trim().length > 0 && totalExercises > 0;

  if (exercisesLoading) return <Skeleton className="h-96" />;

  return (
    <div className="space-y-4 pb-24">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">New program</h1>
        <p className="text-sm text-muted-foreground">
          Lay out one training cycle. It repeats from the client&apos;s start date — a 7-day cycle
          lands on the same weekdays each week.
        </p>
      </div>

      <Card>
        <CardContent className="grid gap-3 py-4 sm:grid-cols-3">
          <div className="space-y-1.5 sm:col-span-2">
            <Label htmlFor="programName">Program name</Label>
            <Input
              id="programName"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="12-Week Strength Block"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="weeks">Duration (weeks)</Label>
            <Input
              id="weeks"
              type="number"
              min={1}
              value={durationWeeks}
              onChange={(e) => setDurationWeeks(e.target.value)}
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="goal">Goal</Label>
            <Select value={goal} onValueChange={(v) => setGoal(v as FitnessGoal)}>
              <SelectTrigger id="goal" className="w-full">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {GOALS.map((g) => (
                  <SelectItem key={g} value={g}>
                    {g.replace("_", " ")}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </CardContent>
      </Card>

      {days.map((day, dayIndex) => (
        <Card key={day.key}>
          <CardHeader className="pb-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <CardTitle className="flex items-center gap-2 text-base">
                <GripVertical className="h-4 w-4 text-muted-foreground" />
                Day {dayIndex + 1}
              </CardTitle>
              <div className="flex items-center gap-2">
                <Input
                  value={day.name}
                  onChange={(e) => updateDay(day.key, { name: e.target.value })}
                  className="h-8 w-44"
                  aria-label={`Name for day ${dayIndex + 1}`}
                />
                <Button variant="outline" size="sm" onClick={() => addExercise(day.key)}>
                  <Plus className="h-3.5 w-3.5" /> Exercise
                </Button>
              </div>
            </div>
            {day.exercises.length === 0 && (
              <p className="text-xs text-muted-foreground">
                No exercises — this counts as a rest day and never affects adherence.
              </p>
            )}
          </CardHeader>
          {day.exercises.length > 0 && (
            <CardContent className="space-y-2">
              {day.exercises.map((ex) => (
                <div key={ex.key} className="flex flex-wrap items-end gap-2 rounded-md border p-2">
                  <div className="min-w-48 flex-1">
                    <Label className="text-[11px]">Exercise</Label>
                    <Select
                      value={ex.exerciseId}
                      onValueChange={(v) => updateExercise(day.key, ex.key, { exerciseId: v })}
                    >
                      <SelectTrigger className="h-8 w-full">
                        <SelectValue placeholder="Pick one" />
                      </SelectTrigger>
                      <SelectContent>
                        {exercises.map((e) => (
                          <SelectItem key={e.id} value={e.id}>
                            {e.name}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  {([
                    ["Sets", "sets"],
                    ["Reps", "targetReps"],
                    ["Weight", "targetWeight"],
                    ["RPE", "targetRpe"],
                    ["Rest (s)", "restSeconds"],
                  ] as const).map(([label, field]) => (
                    <div key={field} className="w-20">
                      <Label className="text-[11px]">{label}</Label>
                      <Input
                        className="h-8"
                        type="number"
                        value={ex[field]}
                        onChange={(e) => updateExercise(day.key, ex.key, { [field]: e.target.value })}
                      />
                    </div>
                  ))}
                  <div className="ml-auto">
                    <Button
                      variant="ghost"
                      size="sm"
                      className="h-8 text-muted-foreground hover:text-destructive"
                      onClick={() => updateDayExercises(day.key, (l) => l.filter((x) => x.key !== ex.key))}
                      aria-label="Remove exercise"
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </div>
              ))}
            </CardContent>
          )}
        </Card>
      ))}

      <div className="flex flex-wrap items-center gap-3">
        <Button variant="outline" onClick={() => setDays((p) => [...p, { key: newKey(), name: `Day ${p.length + 1}`, exercises: [] }])}>
          <Plus className="h-4 w-4" /> Add day to cycle
        </Button>
        <Button onClick={() => create.mutate()} disabled={!ready || create.isPending}>
          {create.isPending ? "Creating..." : "Create program"}
        </Button>
        {!ready && (
          <p className="text-sm text-muted-foreground">
            Give it a name and at least one exercise.
          </p>
        )}
      </div>
    </div>
  );
}
