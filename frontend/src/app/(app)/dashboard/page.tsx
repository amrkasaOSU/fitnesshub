"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { Flame, Footprints, Scale, Trophy, Target, Dumbbell } from "lucide-react";
import { api } from "@/lib/api-client";
import type { ClientDashboardDto } from "@/lib/types";
import { StatCard } from "@/components/stat-card";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";

export default function DashboardPage() {
  const { data, isLoading } = useQuery({
    queryKey: ["dashboard", "client"],
    queryFn: () => api.get<ClientDashboardDto>("/api/dashboard"),
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

  const { todaysWorkout, nutrition, steps, weight, weeklyAdherence, recentPrs, activeGoals } = data;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Dashboard</h1>
        <Button asChild>
          <Link href="/workout/today">
            <Dumbbell className="mr-2 h-4 w-4" /> Go to today&apos;s workout
          </Link>
        </Button>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          title="Calories today"
          icon={Flame}
          value={nutrition.caloriesConsumedToday}
          subtext={
            nutrition.calorieTarget
              ? `${nutrition.caloriesRemaining} remaining of ${nutrition.calorieTarget}`
              : "No target set"
          }
        />
        <StatCard
          title="Protein today"
          icon={Flame}
          value={`${nutrition.proteinConsumedToday}g`}
          subtext={nutrition.proteinTarget ? `Target: ${nutrition.proteinTarget}g` : "No target set"}
        />
        <StatCard
          title="Steps today"
          icon={Footprints}
          value={steps.todaySteps.toLocaleString()}
          subtext={`${steps.goalCompletionPercentage}% of ${steps.goal.toLocaleString()} goal`}
        />
        <StatCard
          title="Weight"
          icon={Scale}
          value={weight.current ?? "—"}
          subtext={
            weight.weeklyChange !== null
              ? `${weight.weeklyChange > 0 ? "+" : ""}${weight.weeklyChange} this week`
              : weight.weeklyAverageMessage ?? undefined
          }
          accent={weight.weeklyChange && weight.weeklyChange < 0 ? "positive" : "default"}
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle>Today&apos;s Workout</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex items-center justify-between">
              <div>
                <p className="font-semibold">{todaysWorkout.dayName}</p>
                <p className="text-sm text-muted-foreground">
                  {todaysWorkout.exercises.length} exercises · Status: {todaysWorkout.status.replace("_", " ")}
                </p>
              </div>
              <Button asChild>
                <Link href="/workout/today">
                  {todaysWorkout.status === "COMPLETED" ? "View" : "Start"}
                </Link>
              </Button>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Weekly Adherence</CardTitle>
          </CardHeader>
          <CardContent>
            {weeklyAdherence.plannedWorkouts === 0 ? (
              <p className="text-sm text-muted-foreground">No active program this week.</p>
            ) : (
              <>
                <div className="text-3xl font-bold">{weeklyAdherence.adherencePercentage}%</div>
                <p className="mt-1 text-sm text-muted-foreground">
                  {weeklyAdherence.completedWorkouts} / {weeklyAdherence.plannedWorkouts} planned workouts
                </p>
                <Progress
                  className="mt-3"
                  value={Math.min(100, weeklyAdherence.adherencePercentage ?? 0)}
                />
              </>
            )}
          </CardContent>
        </Card>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Trophy className="h-4 w-4" /> Recent Personal Records
            </CardTitle>
          </CardHeader>
          <CardContent>
            {recentPrs.length === 0 ? (
              <p className="text-sm text-muted-foreground">No PRs yet - keep logging your workouts.</p>
            ) : (
              <ul className="space-y-2">
                {recentPrs.map((pr) => (
                  <li key={pr.id} className="flex items-center justify-between text-sm">
                    <span>{pr.exerciseName}</span>
                    <Badge variant="secondary">
                      {pr.recordType.replace("_", " ")}: {pr.value}
                    </Badge>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Target className="h-4 w-4" /> Goal Progress
            </CardTitle>
          </CardHeader>
          <CardContent>
            {activeGoals.length === 0 ? (
              <p className="text-sm text-muted-foreground">No active goals set.</p>
            ) : (
              <ul className="space-y-3">
                {activeGoals.map((goal) => (
                  <li key={goal.id}>
                    <div className="flex justify-between text-sm">
                      <span>{goal.name}</span>
                      <span className="text-muted-foreground">
                        {goal.currentValue} / {goal.targetValue} {goal.unit}
                      </span>
                    </div>
                    <Progress className="mt-1" value={goal.percentComplete ?? 0} />
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
