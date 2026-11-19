"use client";

import { AlertTriangle, RotateCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ApiError } from "@/lib/api-client";

/**
 * What a page shows when its data request fails. Without this, pages that
 * render a skeleton "while there's no data yet" showed that skeleton forever
 * on an error, and list pages showed their empty state - telling a coach they
 * had no clients when the server simply didn't answer.
 */
export function QueryError({
  error,
  onRetry,
  what = "this page",
}: {
  error: unknown;
  onRetry: () => void;
  what?: string;
}) {
  const message = error instanceof ApiError ? error.message : "Something went wrong.";
  return (
    <Card role="alert">
      <CardContent className="flex flex-col items-center gap-3 py-12 text-center">
        <AlertTriangle className="h-8 w-8 text-destructive" aria-hidden="true" />
        <div className="space-y-1">
          <p className="font-medium">Couldn&apos;t load {what}</p>
          <p className="text-sm text-muted-foreground">{message}</p>
        </div>
        <Button variant="outline" onClick={onRetry}>
          <RotateCw className="mr-2 h-4 w-4" aria-hidden="true" /> Try again
        </Button>
      </CardContent>
    </Card>
  );
}
