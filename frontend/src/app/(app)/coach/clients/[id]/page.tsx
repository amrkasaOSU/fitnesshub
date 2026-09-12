"use client";

import { use, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { api } from "@/lib/api-client";
import type {
  ClientDetailDto,
  GoalDto,
  PageResponse,
  TrainingSummaryDto,
  WeightDashboardDto,
  WorkoutSummaryDto,
} from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Skeleton } from "@/components/ui/skeleton";
import { StatCard } from "@/components/stat-card";
import { PhotoGallery } from "@/components/photo-gallery";
import { Textarea } from "@/components/ui/textarea";
import { Button } from "@/components/ui/button";

interface CoachNoteDto {
  id: string;
  content: string;
  createdAt: string;
}

export default function CoachClientDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const queryClient = useQueryClient();
  const [note, setNote] = useState("");

  const { data: client, isLoading } = useQuery({
    queryKey: ["client", id],
    queryFn: () => api.get<ClientDetailDto>(`/api/clients/${id}`),
  });

  const { data: progress } = useQuery({
    queryKey: ["client", id, "progress"],
    queryFn: () => api.get<TrainingSummaryDto>(`/api/clients/${id}/progress`),
  });

  const { data: weight } = useQuery({
    queryKey: ["client", id, "weight"],
    queryFn: () => api.get<WeightDashboardDto>("/api/weight/dashboard", { clientId: id }),
  });

  const { data: workouts } = useQuery({
    queryKey: ["client", id, "workouts"],
    queryFn: () => api.get<PageResponse<WorkoutSummaryDto>>(`/api/clients/${id}/workouts`, { pageSize: 10 }),
  });

  const { data: goals } = useQuery({
    queryKey: ["client", id, "goals"],
    queryFn: () => api.get<GoalDto[]>("/api/goals", { clientId: id }),
  });

  const { data: notes } = useQuery({
    queryKey: ["client", id, "notes"],
    queryFn: () => api.get<CoachNoteDto[]>(`/api/coach/clients/${id}/notes`),
  });

  const addNote = useMutation({
    mutationFn: () => api.post(`/api/coach/clients/${id}/notes`, { content: note }),
    onSuccess: () => {
      setNote("");
      queryClient.invalidateQueries({ queryKey: ["client", id, "notes"] });
      toast.success("Note saved");
    },
  });

  if (isLoading || !client) {
    return <Skeleton className="h-96" />;
  }

  // Distance to target, stated without direction: a cut and a bulk both read
  // as "X to go", which avoids implying a direction the goal doesn't specify.
  const toTarget =
    weight?.current != null && client.targetWeight != null
      ? Math.abs(weight.current - client.targetWeight).toFixed(1)
      : null;

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">
          {client.firstName} {client.lastName}
        </h1>
        <p className="text-sm text-muted-foreground">{client.email}</p>
      </div>

      <Tabs defaultValue="overview">
        <TabsList>
          <TabsTrigger value="overview">Overview</TabsTrigger>
          <TabsTrigger value="workouts">Workouts</TabsTrigger>
          <TabsTrigger value="goals">Goals</TabsTrigger>
          <TabsTrigger value="photos">Photos</TabsTrigger>
          <TabsTrigger value="notes">Notes</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard title="Goal" value={client.fitnessGoal?.replace("_", " ") ?? "—"} />
            <StatCard
              title="Current weight"
              value={weight?.current ?? "—"}
              subtext={
                weight?.weeklyChange != null
                  ? `${weight.weeklyChange > 0 ? "+" : ""}${weight.weeklyChange} this week`
                  : undefined
              }
              accent={weight?.weeklyChange != null && weight.weeklyChange < 0 ? "positive" : "default"}
            />
            <StatCard
              title="Target weight"
              value={client.targetWeight ?? "—"}
              subtext={toTarget != null ? `${toTarget} to go` : undefined}
            />
            <StatCard title="Adherence (30d)" value={progress?.adherencePercentage ? `${progress.adherencePercentage}%` : "—"} />
          </div>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard title="Workouts completed (30d)" value={progress?.workoutsCompleted ?? 0} />
            <StatCard title="Total sets (30d)" value={progress?.totalSets ?? 0} />
            <StatCard title="Avg RPE (30d)" value={progress?.averageRpe ?? "—"} />
            <StatCard title="PRs (30d)" value={progress?.prCount ?? 0} />
          </div>
        </TabsContent>

        <TabsContent value="workouts">
          <Card>
            <CardHeader>
              <CardTitle>Recent Workouts</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              {workouts?.data.length === 0 && (
                <p className="text-sm text-muted-foreground">No workouts logged yet.</p>
              )}
              {workouts?.data.map((w) => (
                <div key={w.id} className="border-b py-2 text-sm last:border-0">
                  <div className="flex justify-between">
                    <span className="flex items-center gap-2">
                      {w.dayName}
                      {w.status === "SKIPPED" && (
                        <Badge className="bg-amber-500 hover:bg-amber-500">Skipped</Badge>
                      )}
                    </span>
                    <span className="text-muted-foreground">
                      {new Date(w.startedAt).toLocaleDateString()}
                      {w.averageRpe != null ? ` · RPE ${w.averageRpe}` : ""}
                    </span>
                  </div>
                  {w.skipReason && (
                    <p className="mt-1 text-xs text-muted-foreground">
                      Reason: &ldquo;{w.skipReason}&rdquo;
                    </p>
                  )}
                </div>
              ))}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="goals">
          <Card>
            <CardHeader>
              <CardTitle>Goals</CardTitle>
            </CardHeader>
            <CardContent className="space-y-2">
              {goals?.length === 0 && <p className="text-sm text-muted-foreground">No goals set.</p>}
              {goals?.map((g) => (
                <div key={g.id} className="flex justify-between border-b py-2 text-sm last:border-0">
                  <span>{g.name}</span>
                  <span className="text-muted-foreground">
                    {g.currentValue} / {g.targetValue} {g.unit}
                  </span>
                </div>
              ))}
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="photos">
          <PhotoGallery clientId={id} />
        </TabsContent>

        <TabsContent value="notes" className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Private Coach Notes</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <p className="text-xs text-muted-foreground">Never visible to the client.</p>
              <Textarea value={note} onChange={(e) => setNote(e.target.value)} placeholder="Add a note..." />
              <Button onClick={() => addNote.mutate()} disabled={!note.trim() || addNote.isPending}>
                Save note
              </Button>
              <div className="space-y-2 pt-2">
                {notes?.map((n) => (
                  <div key={n.id} className="rounded-md border p-3 text-sm">
                    <p>{n.content}</p>
                    <p className="mt-1 text-xs text-muted-foreground">
                      {new Date(n.createdAt).toLocaleString()}
                    </p>
                  </div>
                ))}
              </div>
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  );
}
