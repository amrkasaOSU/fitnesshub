"use client";

import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Pause, Play, RotateCcw, X } from "lucide-react";

export function RestTimer({ seconds, onDismiss }: { seconds: number; onDismiss: () => void }) {
  const [remaining, setRemaining] = useState(seconds);
  const [paused, setPaused] = useState(false);
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (paused) return;
    intervalRef.current = setInterval(() => {
      setRemaining((r) => (r <= 0 ? 0 : r - 1));
    }, 1000);
    return () => {
      if (intervalRef.current) clearInterval(intervalRef.current);
    };
  }, [paused]);

  const minutes = Math.floor(remaining / 60);
  const secs = remaining % 60;

  return (
    <div className="flex items-center gap-3 rounded-lg border bg-primary/5 px-4 py-3">
      {/* role="timer" is implicitly aria-live="off": announcing every tick would drown out everything else. */}
      <div className="font-mono text-2xl font-bold tabular-nums" role="timer" aria-label="Rest time remaining">
        {minutes.toString().padStart(2, "0")}:{secs.toString().padStart(2, "0")}
      </div>
      <span className="text-sm text-muted-foreground">Rest</span>
      <div className="ml-auto flex gap-2">
        <Button
          size="icon"
          variant="outline"
          onClick={() => setPaused((p) => !p)}
          aria-label={paused ? "Resume rest timer" : "Pause rest timer"}
        >
          {paused ? <Play className="h-4 w-4" aria-hidden="true" /> : <Pause className="h-4 w-4" aria-hidden="true" />}
        </Button>
        <Button size="icon" variant="outline" onClick={() => setRemaining(seconds)} aria-label="Restart rest timer">
          <RotateCcw className="h-4 w-4" aria-hidden="true" />
        </Button>
        <Button size="icon" variant="outline" onClick={onDismiss} aria-label="Dismiss rest timer">
          <X className="h-4 w-4" aria-hidden="true" />
        </Button>
      </div>
    </div>
  );
}
