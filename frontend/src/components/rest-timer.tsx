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
      <div className="font-mono text-2xl font-bold tabular-nums">
        {minutes.toString().padStart(2, "0")}:{secs.toString().padStart(2, "0")}
      </div>
      <span className="text-sm text-muted-foreground">Rest</span>
      <div className="ml-auto flex gap-2">
        <Button size="icon" variant="outline" onClick={() => setPaused((p) => !p)}>
          {paused ? <Play className="h-4 w-4" /> : <Pause className="h-4 w-4" />}
        </Button>
        <Button size="icon" variant="outline" onClick={() => setRemaining(seconds)}>
          <RotateCcw className="h-4 w-4" />
        </Button>
        <Button size="icon" variant="outline" onClick={onDismiss}>
          <X className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}
