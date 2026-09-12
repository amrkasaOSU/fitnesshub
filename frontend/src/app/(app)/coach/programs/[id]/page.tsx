"use client";

import { use, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { UserPlus } from "lucide-react";
import { api, ApiError } from "@/lib/api-client";
import type { ClientSummaryDto, ProgramDto } from "@/lib/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

function todayLocal() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

export default function ProgramDetailPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const queryClient = useQueryClient();
  const [clientId, setClientId] = useState("");
  const [startDate, setStartDate] = useState(todayLocal());

  const { data: program, isLoading } = useQuery({
    queryKey: ["program", id],
    queryFn: () => api.get<ProgramDto>(`/api/programs/${id}`),
  });
  const { data: clients } = useQuery({
    queryKey: ["coach", "clients"],
    queryFn: () => api.get<ClientSummaryDto[]>("/api/clients"),
  });

  const assign = useMutation<unknown, ApiError, void>({
    mutationFn: () => api.post(`/api/programs/${id}/assign`, { clientId, startDate }),
    onSuccess: () => {
      const who = clients?.find((c) => c.clientId === clientId);
      toast.success(`Assigned to ${who ? who.firstName : "client"}`);
      queryClient.invalidateQueries({ queryKey: ["programs"] });
      queryClient.invalidateQueries({ queryKey: ["program", id] });
    },
    onError: (e) => toast.error(e.message),
  });

  if (isLoading || !program) return <Skeleton className="h-96" />;

  return (
    <div className="space-y-4 pb-24">
      <div>
        <div className="flex flex-wrap items-center gap-2">
          <h1 className="text-2xl font-bold tracking-tight">{program.name}</h1>
          <Badge variant="secondary">v{program.version}</Badge>
        </div>
        <p className="text-sm text-muted-foreground">
          {program.goal.replace("_", " ")} · {program.durationWeeks} weeks ·{" "}
          {program.days.length}-day cycle
        </p>
      </div>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="flex items-center gap-2 text-base">
            <UserPlus className="h-4 w-4 text-muted-foreground" /> Assign to a client
          </CardTitle>
          <p className="text-xs text-muted-foreground">
            Day 1 runs on the start date, then the cycle repeats. Assigning replaces whatever
            program that client is on now.
          </p>
        </CardHeader>
        <CardContent className="flex flex-wrap items-end gap-2">
          <div className="min-w-52 flex-1 space-y-1.5">
            <Label>Client</Label>
            <Select value={clientId} onValueChange={setClientId}>
              <SelectTrigger className="w-full">
                <SelectValue placeholder="Pick a client" />
              </SelectTrigger>
              <SelectContent>
                {(clients ?? []).map((c) => (
                  <SelectItem key={c.clientId} value={c.clientId}>
                    {c.firstName} {c.lastName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="startDate">Start date</Label>
            <Input
              id="startDate"
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
            />
          </div>
          <Button onClick={() => assign.mutate()} disabled={!clientId || assign.isPending}>
            {assign.isPending ? "Assigning..." : "Assign"}
          </Button>
        </CardContent>
      </Card>

      {program.days.map((day, i) => (
        <Card key={day.id}>
          <CardHeader className="pb-2">
            <CardTitle className="text-base">
              Day {i + 1} — {day.name}
            </CardTitle>
          </CardHeader>
          <CardContent>
            {day.exercises.length === 0 ? (
              <p className="text-sm text-muted-foreground">Rest day.</p>
            ) : (
              <div className="space-y-1">
                {day.exercises.map((ex) => (
                  <div key={ex.id} className="flex justify-between border-b py-1.5 text-sm last:border-0">
                    <span>{ex.exerciseName}</span>
                    <span className="text-muted-foreground">
                      {ex.sets} × {ex.targetReps}
                      {ex.targetWeight ? ` @ ${ex.targetWeight}` : ""}
                      {ex.targetRpe ? ` · RPE ${ex.targetRpe}` : ""}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
