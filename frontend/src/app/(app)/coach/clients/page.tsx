"use client";

import { useState } from "react";
import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { AlertTriangle } from "lucide-react";
import { api } from "@/lib/api-client";
import type { ClientSummaryDto } from "@/lib/types";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

export default function CoachClientsPage() {
  const [search, setSearch] = useState("");
  const { data, isLoading } = useQuery({
    queryKey: ["coach", "clients"],
    queryFn: () => api.get<ClientSummaryDto[]>("/api/clients"),
  });

  const filtered = (data ?? []).filter((c) => {
    const q = search.toLowerCase();
    return (
      c.firstName.toLowerCase().includes(q) ||
      c.lastName.toLowerCase().includes(q) ||
      c.email.toLowerCase().includes(q) ||
      (c.goal ?? "").toLowerCase().includes(q)
    );
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Clients</h1>
        <Input
          placeholder="Search by name, email, or goal..."
          className="w-72"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      {isLoading && <Skeleton className="h-96" />}

      {!isLoading && (
        <Card>
          <CardContent className="p-0">
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Client</TableHead>
                    <TableHead>Goal</TableHead>
                    <TableHead>Weight</TableHead>
                    <TableHead>Weekly Δ</TableHead>
                    <TableHead>Adherence</TableHead>
                    <TableHead>Last Workout</TableHead>
                    <TableHead>Steps</TableHead>
                    <TableHead>Calories</TableHead>
                    <TableHead>Status</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filtered.map((c) => (
                    <TableRow key={c.clientId} className="cursor-pointer">
                      <TableCell>
                        <Link href={`/coach/clients/${c.clientId}`} className="font-medium hover:underline">
                          {c.firstName} {c.lastName}
                        </Link>
                        <div className="text-xs text-muted-foreground">{c.email}</div>
                      </TableCell>
                      <TableCell>{c.goal?.replace("_", " ") ?? "—"}</TableCell>
                      <TableCell>{c.currentWeight ?? "—"}</TableCell>
                      <TableCell
                        className={
                          c.weeklyWeightChange && c.weeklyWeightChange < 0
                            ? "text-emerald-600"
                            : undefined
                        }
                      >
                        {c.weeklyWeightChange ?? "—"}
                      </TableCell>
                      <TableCell>
                        {c.adherencePercentage !== null ? `${c.adherencePercentage}%` : "—"}
                      </TableCell>
                      <TableCell>
                        {c.lastWorkoutAt ? new Date(c.lastWorkoutAt).toLocaleDateString() : "Never"}
                      </TableCell>
                      <TableCell>{c.todaySteps.toLocaleString()}</TableCell>
                      <TableCell>{c.caloriesToday}</TableCell>
                      <TableCell>
                        {c.attentionFlags.length > 0 ? (
                          <Badge variant="outline" className="gap-1 text-amber-600">
                            <AlertTriangle className="h-3 w-3" /> {c.attentionFlags.length}
                          </Badge>
                        ) : (
                          <Badge variant="secondary">On track</Badge>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
