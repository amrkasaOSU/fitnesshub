"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { Users, Dumbbell, TrendingDown, AlertTriangle, Trophy } from "lucide-react";
import { api } from "@/lib/api-client";
import type { CoachDashboardDto } from "@/lib/types";
import { StatCard } from "@/components/stat-card";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Skeleton } from "@/components/ui/skeleton";

export default function CoachDashboardPage() {
  const { data, isLoading } = useQuery({
    queryKey: ["dashboard", "coach"],
    queryFn: () => api.get<CoachDashboardDto>("/api/coach/dashboard"),
  });

  if (isLoading || !data) {
    return (
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {Array.from({ length: 8 }).map((_, i) => (
          <Skeleton key={i} className="h-28" />
        ))}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold tracking-tight">Coach Dashboard</h1>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard title="Active Clients" icon={Users} value={data.activeClients} subtext={`${data.totalClients} total`} />
        <StatCard title="Training Today" icon={Dumbbell} value={data.clientsTrainingToday} />
        <StatCard
          title="Avg Weekly Adherence"
          icon={TrendingDown}
          value={data.averageWeeklyAdherence != null ? `${data.averageWeeklyAdherence}%` : "—"}
        />
        <StatCard
          title="Need Attention"
          icon={AlertTriangle}
          value={data.clientsNeedingAttention.length}
          accent={data.clientsNeedingAttention.length > 0 ? "warning" : "default"}
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <AlertTriangle className="h-4 w-4 text-amber-500" /> Attention Required
            </CardTitle>
          </CardHeader>
          <CardContent>
            {data.clientsNeedingAttention.length === 0 ? (
              <p className="text-sm text-muted-foreground">No clients need attention right now.</p>
            ) : (
              <ul className="space-y-3">
                {data.clientsNeedingAttention.map((c) => (
                  <li key={c.clientId}>
                    <Link href={`/coach/clients/${c.clientId}`} className="block hover:underline">
                      <div className="flex items-center justify-between">
                        <span className="font-medium">
                          {c.firstName} {c.lastName}
                        </span>
                      </div>
                      <div className="mt-1 flex flex-wrap gap-1">
                        {c.attentionFlags.map((f) => (
                          <Badge key={f} variant="outline" className="text-xs">
                            {f.replace(/_/g, " ")}
                          </Badge>
                        ))}
                      </div>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Trophy className="h-4 w-4" /> Recent Client PRs
            </CardTitle>
          </CardHeader>
          <CardContent>
            {data.recentPrs.length === 0 ? (
              <p className="text-sm text-muted-foreground">No PRs yet this cycle.</p>
            ) : (
              <ul className="space-y-2 text-sm">
                {data.recentPrs.map((pr) => (
                  <li key={pr.id} className="flex justify-between">
                    <Link href={`/coach/clients/${pr.clientId}`} className="hover:underline">
                      {pr.exerciseName}
                    </Link>
                    <Badge variant="secondary">{pr.recordType.replace("_", " ")}: {pr.value}</Badge>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <Card>
          <CardContent className="flex items-center justify-between py-4">
            <span className="text-sm text-muted-foreground">Unread messages</span>
            <Link href="/coach/messages">
              <Badge>{data.unreadMessages}</Badge>
            </Link>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="flex items-center justify-between py-4">
            <span className="text-sm text-muted-foreground">Check-ins pending review</span>
            <Badge variant={data.checkInsPendingReview > 0 ? "default" : "secondary"}>
              {data.checkInsPendingReview}
            </Badge>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
