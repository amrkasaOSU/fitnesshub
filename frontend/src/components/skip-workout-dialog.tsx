"use client";

import { useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { SkipForward } from "lucide-react";
import { api, ApiError } from "@/lib/api-client";
import type { WorkoutSessionDto } from "@/lib/types";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";

export function SkipWorkoutDialog() {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState("");
  const queryClient = useQueryClient();

  const skip = useMutation<WorkoutSessionDto, ApiError, string>({
    mutationFn: (r) => api.post<WorkoutSessionDto>("/api/workouts/today/skip", { reason: r }),
    onSuccess: () => {
      // Both the session and the week strip change: the week has shifted.
      queryClient.invalidateQueries({ queryKey: ["workout", "today"] });
      queryClient.invalidateQueries({ queryKey: ["workout", "week"] });
      toast.success("Workout skipped. Your coach has been told why.");
      setReason("");
      setOpen(false);
    },
  });

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="outline" className="gap-2">
          <SkipForward className="h-4 w-4" /> Skip workout
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Skip today&apos;s workout</DialogTitle>
          <DialogDescription>
            Your coach will see this reason. The rest of this week shifts down a day, and your
            schedule returns to normal on Monday.
          </DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          <Label htmlFor="skipReason">Why are you skipping?</Label>
          <Textarea
            id="skipReason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            placeholder="Travelling, feeling run down, shoulder is sore..."
            maxLength={500}
          />
          <p className="text-xs text-muted-foreground">
            This still counts as a missed session in your adherence — the reason is what gives your
            coach the context.
          </p>
        </div>
        {skip.isError && <p className="text-sm text-destructive">{skip.error.message}</p>}
        <Button
          onClick={() => skip.mutate(reason.trim())}
          disabled={!reason.trim() || skip.isPending}
          className="w-full"
        >
          {skip.isPending ? "Skipping..." : "Skip workout"}
        </Button>
      </DialogContent>
    </Dialog>
  );
}
