"use client";

import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Sparkles } from "lucide-react";
import { api } from "@/lib/api-client";
import type { AiAnswer, ClientSummaryDto } from "@/lib/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";

export default function CoachAiPage() {
  const [clientId, setClientId] = useState<string>("");
  const { data: clients } = useQuery({
    queryKey: ["coach", "clients"],
    queryFn: () => api.get<ClientSummaryDto[]>("/api/clients"),
  });

  const analyze = useMutation({
    mutationFn: () => api.post<AiAnswer>("/api/ai/client-analysis", { clientId }),
  });

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="text-center">
        <Sparkles className="mx-auto h-8 w-8 text-primary" />
        <h1 className="mt-2 text-2xl font-bold tracking-tight">AI Client Insights</h1>
        <p className="text-sm text-muted-foreground">
          Get a data-driven summary before your next check-in.
        </p>
      </div>

      <div className="flex gap-2">
        <Select value={clientId} onValueChange={setClientId}>
          <SelectTrigger className="flex-1">
            <SelectValue placeholder="Select a client" />
          </SelectTrigger>
          <SelectContent>
            {clients?.map((c) => (
              <SelectItem key={c.clientId} value={c.clientId}>
                {c.firstName} {c.lastName}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        <Button disabled={!clientId || analyze.isPending} onClick={() => analyze.mutate()}>
          {analyze.isPending ? "Analyzing..." : "Analyze"}
        </Button>
      </div>

      {analyze.data && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-sm">
              <Sparkles className="h-4 w-4 text-primary" /> Summary
              {analyze.data.confidence !== "n/a" && (
                <Badge variant="secondary" className="ml-auto">
                  {analyze.data.confidence} confidence
                </Badge>
              )}
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-3 text-sm">
            <p className="whitespace-pre-line">{analyze.data.answer}</p>
            {analyze.data.relevantMetrics.length > 0 && (
              <div className="flex flex-wrap gap-1">
                {analyze.data.relevantMetrics.map((m) => (
                  <Badge key={m} variant="outline">
                    {m}
                  </Badge>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      )}
    </div>
  );
}
