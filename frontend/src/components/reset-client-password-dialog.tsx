"use client";

import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { toast } from "sonner";
import { KeyRound } from "lucide-react";
import { api, ApiError } from "@/lib/api-client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";

/** Generates a readable temporary password - easier to read aloud or text than random hex. */
function suggestPassword() {
  const words = ["Squat", "Bench", "Tempo", "Anchor", "Ladder", "Summit", "Rocket", "Harbor"];
  const word = words[Math.floor(Math.random() * words.length)];
  const digits = Math.floor(1000 + Math.random() * 9000);
  return `${word}-${digits}`;
}

export function ResetClientPasswordDialog({
  clientId,
  clientName,
}: {
  clientId: string;
  clientName: string;
}) {
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState("");
  const [done, setDone] = useState(false);

  const reset = useMutation<void, ApiError, string>({
    mutationFn: (pw) => api.post<void>(`/api/coach/clients/${clientId}/password`, { temporaryPassword: pw }),
    onSuccess: () => {
      setDone(true);
      toast.success("Password reset");
    },
    onError: (e) => toast.error(e.message),
  });

  function handleOpenChange(next: boolean) {
    if (next) {
      setPassword(suggestPassword());
      setDone(false);
    }
    setOpen(next);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button variant="outline" size="sm" className="gap-2">
          <KeyRound className="h-4 w-4" /> Reset password
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Reset password for {clientName}</DialogTitle>
          <DialogDescription>
            Use this when a client is locked out. Give them the password below, and ask them to
            change it under Settings once they&apos;re back in.
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-2">
          <Label htmlFor="tempPassword">Temporary password</Label>
          <Input
            id="tempPassword"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            onFocus={(e) => e.currentTarget.select()}
            className="font-mono"
          />
          <p className="text-xs text-muted-foreground">
            Their old password stops working immediately.
          </p>
        </div>

        {done ? (
          <div className="space-y-2 rounded-md border bg-muted/40 p-3">
            <p className="text-sm font-medium">Done — send them this password:</p>
            <p className="font-mono text-sm">{password}</p>
            <p className="text-xs text-muted-foreground">
              It isn&apos;t stored anywhere readable, so if you lose it you&apos;ll have to reset again.
            </p>
          </div>
        ) : (
          <Button
            className="w-full"
            onClick={() => reset.mutate(password)}
            disabled={password.length < 8 || reset.isPending}
          >
            {reset.isPending ? "Resetting..." : "Reset password"}
          </Button>
        )}
      </DialogContent>
    </Dialog>
  );
}
