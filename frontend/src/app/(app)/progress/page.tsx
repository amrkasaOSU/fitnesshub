"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api } from "@/lib/api-client";
import type { ExerciseDto, ExerciseProgressSummary, PageResponse } from "@/lib/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { StatCard } from "@/components/stat-card";
import { Skeleton } from "@/components/ui/skeleton";

export default function ProgressPage() {
  const [exerciseId, setExerciseId] = useState<string>("");

  const { data: exercises } = useQuery({
    queryKey: ["exercises", "all"],
    queryFn: () => api.get<PageResponse<ExerciseDto>>("/api/exercises", { pageSize: 100 }),
  });

  const { data: progress, isLoading } = useQuery({
    queryKey: ["exercise-progress", exerciseId],
    queryFn: () => api.get<ExerciseProgressSummary>(`/api/exercises/${exerciseId}/progress`),
    enabled: !!exerciseId,
  });

  const chartData = progress?.recentSessions
    .slice()
    .reverse()
    .map((s) => ({
      date: new Date(s.date).toLocaleDateString(undefined, { month: "short", day: "numeric" }),
      estimated1Rm: s.estimated1Rm,
      volume: s.volume,
    }));

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Progress</h1>
        <Select value={exerciseId} onValueChange={setExerciseId}>
          <SelectTrigger className="w-64">
            <SelectValue placeholder="Select an exercise" />
          </SelectTrigger>
          <SelectContent>
            {exercises?.data.map((e) => (
              <SelectItem key={e.id} value={e.id}>
                {e.name}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {!exerciseId && (
        <p className="text-sm text-muted-foreground">Pick an exercise above to see its progress.</p>
      )}

      {exerciseId && isLoading && <Skeleton className="h-96" />}

      {exerciseId && progress && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard title="Best Weight" value={progress.bestWeight} />
            <StatCard title="Best Reps" value={progress.bestReps} />
            <StatCard title="Estimated 1RM" value={progress.bestEstimated1Rm} subtext="Epley formula estimate" />
            <StatCard
              title="Performance Trend"
              value={progress.performanceTrend}
              accent={
                progress.performanceTrend === "UP"
                  ? "positive"
                  : progress.performanceTrend === "DOWN"
                    ? "negative"
                    : "default"
              }
            />
          </div>

          <Card>
            <CardHeader>
              <CardTitle>Estimated 1RM over time</CardTitle>
            </CardHeader>
            <CardContent className="h-80">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                  <XAxis dataKey="date" fontSize={12} />
                  <YAxis fontSize={12} domain={["auto", "auto"]} />
                  <Tooltip />
                  <Line type="monotone" dataKey="estimated1Rm" stroke="var(--primary)" strokeWidth={2} dot />
                </LineChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Volume over time</CardTitle>
            </CardHeader>
            <CardContent className="h-80">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={chartData}>
                  <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                  <XAxis dataKey="date" fontSize={12} />
                  <YAxis fontSize={12} />
                  <Tooltip />
                  <Line type="monotone" dataKey="volume" stroke="var(--chart-2)" strokeWidth={2} dot />
                </LineChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}
