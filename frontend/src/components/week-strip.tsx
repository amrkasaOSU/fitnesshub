"use client";

import { useQuery } from "@tanstack/react-query";
import { CheckCircle2, SkipForward } from "lucide-react";
import { api } from "@/lib/api-client";
import type { WeekDayDto } from "@/lib/types";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

const LABELS: Record<string, string> = {
  MONDAY: "Mon",
  TUESDAY: "Tue",
  WEDNESDAY: "Wed",
  THURSDAY: "Thu",
  FRIDAY: "Fri",
  SATURDAY: "Sat",
  SUNDAY: "Sun",
};

function dayNumber(isoDate: string) {
  // The API sends a plain LocalDate ("2026-09-09"); parsing it directly would
  // read as UTC midnight and can render as the previous day west of Greenwich.
  return Number(isoDate.slice(8, 10));
}

export function WeekStrip() {
  const { data: week, isLoading } = useQuery({
    queryKey: ["workout", "week"],
    queryFn: () => api.get<WeekDayDto[]>("/api/workouts/week"),
  });

  if (isLoading) return <Skeleton className="h-28" />;
  if (!week) return null;

  const skipped = week.filter((d) => d.status === "SKIPPED");

  return (
    <Card>
      <CardContent className="space-y-3 py-4">
        <div className="grid grid-cols-7 gap-1.5">
          {week.map((day) => (
            <div
              key={day.date}
              className={cn(
                "flex min-h-24 flex-col items-center gap-1 rounded-md border p-1.5 text-center",
                day.today && "border-primary bg-primary/5",
                !day.today && day.past && "opacity-60",
                day.restDay && !day.today && "bg-muted/40"
              )}
            >
              <span className="text-[10px] font-medium uppercase tracking-wide text-muted-foreground">
                {LABELS[day.dayOfWeek] ?? day.dayOfWeek.slice(0, 3)}
              </span>
              <span className={cn("text-sm font-semibold leading-none", day.today && "text-primary")}>
                {dayNumber(day.date)}
              </span>
              <span className="line-clamp-2 text-[10px] leading-tight text-muted-foreground">
                {day.restDay ? "Rest" : (day.programDayName ?? "—")}
              </span>
              <div className="mt-auto h-4">
                {day.status === "COMPLETED" && <CheckCircle2 className="h-4 w-4 text-emerald-500" />}
                {day.status === "SKIPPED" && <SkipForward className="h-4 w-4 text-amber-500" />}
              </div>
            </div>
          ))}
        </div>

        {skipped.length > 0 && (
          <div className="space-y-1 border-t pt-3">
            {skipped.map((day) => (
              <p key={day.date} className="text-xs text-muted-foreground">
                <span className="font-medium text-amber-600">
                  {LABELS[day.dayOfWeek] ?? day.dayOfWeek} skipped
                </span>
                {day.skipReason ? ` — "${day.skipReason}"` : ""}
              </p>
            ))}
            <p className="text-xs text-muted-foreground">
              The rest of this week has shifted down a day. Your schedule returns to normal on Monday.
            </p>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
