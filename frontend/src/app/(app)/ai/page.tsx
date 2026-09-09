"use client";

import { useState } from "react";
import { useMutation } from "@tanstack/react-query";
import { AlertTriangle, Sparkles } from "lucide-react";
import { api } from "@/lib/api-client";
import type { AiAnswer } from "@/lib/types";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";

const suggestions = [
  "Should I attempt a heavier bench press today?",
  "Why has my squat progress stalled?",
  "How has my training volume changed this month?",
  "Am I training consistently enough?",
];

export default function AiAssistantPage() {
  const [question, setQuestion] = useState("");
  const [history, setHistory] = useState<{ question: string; answer: AiAnswer }[]>([]);

  const ask = useMutation({
    mutationFn: (q: string) => api.post<AiAnswer>("/api/ai/fitness-question", { question: q }),
    onSuccess: (answer, q) => {
      setHistory((h) => [...h, { question: q, answer }]);
      setQuestion("");
    },
  });

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="text-center">
        <Sparkles className="mx-auto h-8 w-8 text-primary" />
        <h1 className="mt-2 text-2xl font-bold tracking-tight">FitnessHub AI</h1>
        <p className="text-sm text-muted-foreground">
          Ask about your training. Answers are based on your real logged data.
        </p>
      </div>

      {history.length === 0 && (
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-2">
          {suggestions.map((s) => (
            <Button key={s} variant="outline" className="h-auto justify-start whitespace-normal py-3 text-left" onClick={() => setQuestion(s)}>
              {s}
            </Button>
          ))}
        </div>
      )}

      <div className="space-y-4">
        {history.map((h, i) => (
          <div key={i} className="space-y-2">
            <p className="text-sm font-medium text-muted-foreground">You: {h.question}</p>
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="flex items-center gap-2 text-sm">
                  <Sparkles className="h-4 w-4 text-primary" /> FitnessHub AI
                  {h.answer.confidence !== "n/a" && (
                    <Badge variant="secondary" className="ml-auto">
                      {h.answer.confidence} confidence
                    </Badge>
                  )}
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-3 text-sm">
                <p>{h.answer.answer}</p>
                {h.answer.recommendation && (
                  <p className="rounded-md bg-muted/50 p-3">
                    <span className="font-medium">Suggestion: </span>
                    {h.answer.recommendation}
                  </p>
                )}
                {h.answer.reasoningSummary && (
                  <p className="text-xs text-muted-foreground">{h.answer.reasoningSummary}</p>
                )}
                {h.answer.warnings.length > 0 && (
                  <div className="flex items-start gap-2 rounded-md border border-amber-300 bg-amber-50 p-3 text-xs text-amber-900 dark:bg-amber-950 dark:text-amber-200">
                    <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                    <div>{h.answer.warnings.join(" ")}</div>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>
        ))}
      </div>

      <div className="flex gap-2">
        <Textarea
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="Ask a training question..."
          className="min-h-12 resize-none"
        />
        <Button disabled={!question.trim() || ask.isPending} onClick={() => ask.mutate(question.trim())}>
          {ask.isPending ? "Thinking..." : "Ask"}
        </Button>
      </div>

      <p className="text-center text-xs text-muted-foreground">
        FitnessHub&apos;s AI assistant provides general information based on your data, not medical
        advice. Consult a qualified professional for injuries or health conditions.
      </p>
    </div>
  );
}
