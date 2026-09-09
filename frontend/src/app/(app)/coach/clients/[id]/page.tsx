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
  WorkoutSummaryDto,
} from "@/lib/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Skeleton } from "@/components/ui/skeleton";
import { StatCard } from "@/components/stat-card";
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
          <TabsTrigger value="notes">Notes</TabsTrigger>
        </TabsList>

        <TabsContent value="overview" className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <StatCard title="Goal" value={client.fitnessGoal?.replace("_", " ") ?? "—"} />
            <StatCard title="Target weight" value={client.targetWeight ?? "—"} />
            <StatCard title="Adherence (30d)" value={progress?.adherencePercentage ? `${progress.adherencePercentage}%` : "—"} />
            <StatCard title="PRs (30d)" value={progress?.prCount ?? 0} />
          </div>
          <div className="grid gap-4 sm:grid-cols-3">
            <StatCard title="Total sets (30d)" value={progress?.totalSets ?? 0} />
            <StatCard title="Total volume (30d)" value={progress?.totalVolume ?? 0} />
            <StatCard title="Avg RPE (30d)" value={progress?.averageRpe ?? "—"} />
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
                <div key={w.id} className="flex justify-between border-b py-2 text-sm last:border-0">
                  <span>{w.dayName}</span>
                  <span className="text-muted-foreground">
                    {new Date(w.startedAt).toLocaleDateString()} · {w.totalVolume} lb
                  </span>
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
