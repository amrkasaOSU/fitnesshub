"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { PageResponse, WorkoutSummaryDto } from "@/lib/types";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";

export default function WorkoutHistoryPage() {
  const { data, isLoading } = useQuery({
    queryKey: ["workouts", "history"],
    queryFn: () => api.get<PageResponse<WorkoutSummaryDto>>("/api/workouts", { pageSize: 30 }),
  });

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold tracking-tight">Workout History</h1>
      {isLoading && (
        <div className="space-y-2">
          {Array.from({ length: 6 }).map((_, i) => (
            <Skeleton key={i} className="h-16" />
          ))}
        </div>
      )}
      {data?.data.length === 0 && (
        <p className="text-sm text-muted-foreground">No workouts logged yet.</p>
      )}
      <div className="space-y-2">
        {data?.data.map((w) => (
          <Link key={w.id} href={`/workouts/${w.id}`}>
            <Card className="transition-colors hover:bg-muted/50">
              <CardContent className="flex items-center justify-between py-4">
                <div>
                  <p className="font-medium">{w.dayName}</p>
                  <p className="text-sm text-muted-foreground">
                    {new Date(w.startedAt).toLocaleDateString(undefined, {
                      weekday: "short",
                      month: "short",
                      day: "numeric",
                    })}
                    {w.durationSeconds ? ` · ${Math.round(w.durationSeconds / 60)} min` : ""}
                  </p>
                </div>
                <div className="flex items-center gap-2 text-right">
                  <div>
                    <p className="text-sm font-medium">{w.averageRpe ?? "—"}</p>
                    <p className="text-xs text-muted-foreground">avg RPE</p>
                  </div>
                  {w.prCount > 0 && <Badge>{w.prCount} PR{w.prCount > 1 ? "s" : ""}</Badge>}
                  <Badge variant={w.status === "COMPLETED" ? "secondary" : "outline"}>
                    {w.status.replace("_", " ")}
                  </Badge>
                </div>
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
