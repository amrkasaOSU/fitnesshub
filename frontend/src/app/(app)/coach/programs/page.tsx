"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { Plus, CalendarRange } from "lucide-react";
import { api } from "@/lib/api-client";
import type { PageResponse, ProgramSummaryDto } from "@/lib/types";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";

export default function CoachProgramsPage() {
  const { data, isLoading } = useQuery({
    queryKey: ["programs"],
    queryFn: () => api.get<PageResponse<ProgramSummaryDto>>("/api/programs", { pageSize: 50 }),
  });

  const programs = data?.data ?? [];

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold tracking-tight">Programs</h1>
          <p className="text-sm text-muted-foreground">
            Build a training week once, then assign it to as many clients as you like.
          </p>
        </div>
        <Button asChild className="gap-2">
          <Link href="/coach/programs/new">
            <Plus className="h-4 w-4" /> New program
          </Link>
        </Button>
      </div>

      {isLoading && <Skeleton className="h-64" />}

      {!isLoading && programs.length === 0 && (
        <Card>
          <CardContent className="flex flex-col items-center gap-3 py-16 text-center">
            <CalendarRange className="h-8 w-8 text-muted-foreground" />
            <div>
              <p className="font-medium">No programs yet</p>
              <p className="text-sm text-muted-foreground">
                A client can&apos;t log workouts until they have a program assigned.
              </p>
            </div>
            <Button asChild>
              <Link href="/coach/programs/new">Build your first program</Link>
            </Button>
          </CardContent>
        </Card>
      )}

      {programs.length > 0 && (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {programs.map((p) => (
            <Card key={p.id}>
              <CardContent className="space-y-2 py-4">
                <div className="flex items-start justify-between gap-2">
                  <p className="font-medium">{p.name}</p>
                  <Badge variant="secondary">v{p.version}</Badge>
                </div>
                <p className="text-sm text-muted-foreground">
                  {p.goal.replace("_", " ")} · {p.durationWeeks} weeks · {p.dayCount}-day cycle
                </p>
                <p className="text-xs text-muted-foreground">
                  {p.assignedClientCount === 0
                    ? "Not assigned to anyone yet"
                    : `Assigned to ${p.assignedClientCount} client${p.assignedClientCount > 1 ? "s" : ""}`}
                </p>
                <Button asChild variant="outline" size="sm" className="w-full">
                  <Link href={`/coach/programs/${p.id}`}>View & assign</Link>
                </Button>
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
