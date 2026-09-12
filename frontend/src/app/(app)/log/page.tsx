"use client";

import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Scale, Footprints, Apple } from "lucide-react";
import { api, ApiError } from "@/lib/api-client";
import type { NutritionDashboardDto, StepDashboardDto, WeightDashboardDto } from "@/lib/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { CheckInForm } from "@/components/check-in-form";

/** Today in the browser's own timezone - toISOString() would shift west of UTC. */
function todayLocal() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

export default function LogPage() {
  const queryClient = useQueryClient();

  const { data: weight, isLoading: weightLoading } = useQuery({
    queryKey: ["weight", "dashboard"],
    queryFn: () => api.get<WeightDashboardDto>("/api/weight/dashboard"),
  });
  const { data: steps } = useQuery({
    queryKey: ["steps", "dashboard"],
    queryFn: () => api.get<StepDashboardDto>("/api/steps/dashboard"),
  });
  const { data: nutrition } = useQuery({
    queryKey: ["nutrition", "dashboard"],
    queryFn: () => api.get<NutritionDashboardDto>("/api/nutrition/dashboard"),
  });

  const [weightInput, setWeightInput] = useState("");
  const [stepsInput, setStepsInput] = useState("");
  const [calories, setCalories] = useState("");
  const [protein, setProtein] = useState("");
  const [carbs, setCarbs] = useState("");
  const [fat, setFat] = useState("");

  // Everything on this page feeds the dashboards, so each save refreshes them.
  const refresh = (keys: string[]) => {
    keys.forEach((k) => queryClient.invalidateQueries({ queryKey: [k] }));
    queryClient.invalidateQueries({ queryKey: ["dashboard"] });
  };

  const logWeight = useMutation<unknown, ApiError, void>({
    mutationFn: () => api.post("/api/weight", { weight: Number(weightInput) }),
    onSuccess: () => {
      toast.success("Weight logged");
      setWeightInput("");
      refresh(["weight"]);
    },
    onError: (e) => toast.error(e.message),
  });

  const logSteps = useMutation<unknown, ApiError, void>({
    mutationFn: () => api.post("/api/steps", { steps: Number(stepsInput), date: todayLocal() }),
    onSuccess: () => {
      toast.success("Steps logged");
      setStepsInput("");
      refresh(["steps"]);
    },
    onError: (e) => toast.error(e.message),
  });

  const logNutrition = useMutation<unknown, ApiError, void>({
    mutationFn: () =>
      api.post("/api/nutrition", {
        date: todayLocal(),
        calories: Number(calories),
        proteinGrams: Number(protein),
        carbohydratesGrams: carbs === "" ? undefined : Number(carbs),
        fatGrams: fat === "" ? undefined : Number(fat),
      }),
    onSuccess: () => {
      toast.success("Nutrition logged");
      refresh(["nutrition"]);
    },
    onError: (e) => toast.error(e.message),
  });

  if (weightLoading) return <Skeleton className="h-96" />;

  return (
    <div className="space-y-4 pb-24">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Log</h1>
        <p className="text-sm text-muted-foreground">
          Your daily numbers and weekly check-in. Your coach sees these.
        </p>
      </div>

      <Tabs defaultValue="daily">
        <TabsList className="grid w-full max-w-md grid-cols-2">
          <TabsTrigger value="daily">Today</TabsTrigger>
          <TabsTrigger value="checkin">Weekly check-in</TabsTrigger>
        </TabsList>

        <TabsContent value="daily" className="mt-4 space-y-4">
          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="flex items-center gap-2 text-base">
                <Scale className="h-4 w-4 text-muted-foreground" /> Body weight
              </CardTitle>
              <p className="text-xs text-muted-foreground">
                {weight?.current != null ? `Last logged: ${weight.current}` : "Nothing logged yet"}
              </p>
            </CardHeader>
            <CardContent className="flex gap-2">
              <Input
                type="number"
                inputMode="decimal"
                step="0.1"
                placeholder="Weight"
                value={weightInput}
                onChange={(e) => setWeightInput(e.target.value)}
              />
              <Button
                onClick={() => logWeight.mutate()}
                disabled={!weightInput || logWeight.isPending}
              >
                Save
              </Button>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="flex items-center gap-2 text-base">
                <Footprints className="h-4 w-4 text-muted-foreground" /> Steps today
              </CardTitle>
              <p className="text-xs text-muted-foreground">
                {steps ? `${steps.todaySteps.toLocaleString()} logged · goal ${steps.goal.toLocaleString()}` : ""}
              </p>
            </CardHeader>
            <CardContent className="flex gap-2">
              <Input
                type="number"
                inputMode="numeric"
                placeholder="Steps"
                value={stepsInput}
                onChange={(e) => setStepsInput(e.target.value)}
              />
              <Button onClick={() => logSteps.mutate()} disabled={!stepsInput || logSteps.isPending}>
                Save
              </Button>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-3">
              <CardTitle className="flex items-center gap-2 text-base">
                <Apple className="h-4 w-4 text-muted-foreground" /> Nutrition today
              </CardTitle>
              <p className="text-xs text-muted-foreground">
                {nutrition
                  ? `${nutrition.caloriesConsumedToday} kcal · ${nutrition.proteinConsumedToday}g protein logged so far`
                  : ""}
              </p>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-1.5">
                  <Label htmlFor="calories">Calories</Label>
                  <Input id="calories" type="number" inputMode="numeric" value={calories}
                    onChange={(e) => setCalories(e.target.value)} />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="protein">Protein (g)</Label>
                  <Input id="protein" type="number" inputMode="numeric" value={protein}
                    onChange={(e) => setProtein(e.target.value)} />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="carbs">Carbs (g) — optional</Label>
                  <Input id="carbs" type="number" inputMode="numeric" value={carbs}
                    onChange={(e) => setCarbs(e.target.value)} />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="fat">Fat (g) — optional</Label>
                  <Input id="fat" type="number" inputMode="numeric" value={fat}
                    onChange={(e) => setFat(e.target.value)} />
                </div>
              </div>
              <p className="text-xs text-muted-foreground">
                One entry per day — saving again replaces today&apos;s totals rather than adding to them.
              </p>
              <Button
                className="w-full"
                onClick={() => logNutrition.mutate()}
                disabled={!calories || !protein || logNutrition.isPending}
              >
                Save nutrition
              </Button>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="checkin" className="mt-4">
          <CheckInForm />
        </TabsContent>
      </Tabs>
    </div>
  );
}
