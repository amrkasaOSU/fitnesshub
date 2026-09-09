"use client";

import { use } from "react";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { WorkoutSessionDto } from "@/lib/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";

export default function WorkoutDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { data: session, isLoading } = useQuery({
    queryKey: ["workout", id],
    queryFn: () => api.get<WorkoutSessionDto>(`/api/workouts/${id}`),
  });

  if (isLoading || !session) {
    return (
      <div className="space-y-4">
        <Skeleton className="h-20" />
        <Skeleton className="h-64" />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <Card>
        <CardContent className="py-4">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-xl font-bold">{session.dayName}</h1>
              <p className="text-sm text-muted-foreground">
                {new Date(session.startedAt).toLocaleString()}
              </p>
            </div>
            <div className="text-right">
              <p className="font-medium">{session.totalVolume.toLocaleString()} lb volume</p>
              {session.prCount > 0 && <Badge className="mt-1">{session.prCount} PR{session.prCount > 1 ? "s" : ""}</Badge>}
            </div>
          </div>
          {session.notes && <p className="mt-3 text-sm text-muted-foreground">Notes: {session.notes}</p>}
        </CardContent>
      </Card>

      {session.exercises.map((exercise) => (
        <Card key={exercise.exerciseId}>
          <CardHeader className="pb-2">
            <CardTitle className="text-base">{exercise.exerciseName}</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-1">
              {exercise.completedSets.map((set) => (
                <div key={set.id} className="flex justify-between text-sm">
                  <span className="text-muted-foreground">
                    Set {set.setNumber} {set.isWarmup && "(warmup)"}
                  </span>
                  <span>
                    {set.weight} x {set.reps}
                    {set.rpe ? ` @ RPE ${set.rpe}` : ""}
                  </span>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      ))}

      {session.feedback.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Coach Feedback</CardTitle>
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
