"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Send, ArrowLeft } from "lucide-react";
import { api } from "@/lib/api-client";
import type { ConversationDto, MessageDto, PageResponse } from "@/lib/types";
import { useCurrentUser } from "@/lib/use-auth";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Skeleton } from "@/components/ui/skeleton";
import { Badge } from "@/components/ui/badge";
import { cn } from "@/lib/utils";

export default function CoachMessagesPage() {
  const { data: user } = useCurrentUser();
  const queryClient = useQueryClient();
  const [explicitClientId, setExplicitClientId] = useState<string | null>(null);
  const [draft, setDraft] = useState("");
  const bottomRef = useRef<HTMLDivElement>(null);

  const { data: conversations, isLoading: loadingConvos } = useQuery({
    queryKey: ["coach", "conversations"],
    queryFn: () => api.get<ConversationDto[]>("/api/coach/conversations"),
  });

  // Defaults to the first conversation until the coach explicitly picks another one.
  const selectedClientId = useMemo(
    () => explicitClientId ?? conversations?.[0]?.clientId ?? null,
    [explicitClientId, conversations]
  );
  const setSelectedClientId = setExplicitClientId;

  const { data: messages } = useQuery({
    queryKey: ["messages", selectedClientId],
    queryFn: () => api.get<PageResponse<MessageDto>>("/api/messages", { clientId: selectedClientId!, pageSize: 100 }),
    enabled: !!selectedClientId,
    refetchInterval: 5000,
  });

  const send = useMutation({
    mutationFn: (content: string) => api.post("/api/messages", { clientId: selectedClientId, content }),
    onSuccess: () => {
      setDraft("");
      queryClient.invalidateQueries({ queryKey: ["messages", selectedClientId] });
      queryClient.invalidateQueries({ queryKey: ["coach", "conversations"] });
    },
  });

  const ordered = messages ? [...messages.data].reverse() : [];

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [ordered.length]);

  return (
    // Phones get list-then-thread: a fixed-width list beside the thread left the
    // thread about 40px wide. Until the coach picks someone, a phone shows the
    // list; from md up both panes sit side by side as before.
    <div className="flex h-[calc(100dvh-9rem)] gap-4 md:h-[calc(100vh-8rem)]">
      <Card
        className={cn(
          "w-full shrink-0 overflow-y-auto md:block md:w-72",
          explicitClientId ? "hidden" : "block"
        )}
      >
        <CardContent className="space-y-1 p-2">
          {loadingConvos && <Skeleton className="h-40" />}
          {conversations?.map((c) => (
            <button
              key={c.id}
              onClick={() => setSelectedClientId(c.clientId)}
              aria-current={selectedClientId === c.clientId ? "true" : undefined}
              className={cn(
                "flex w-full flex-col items-start rounded-md p-2 text-left text-sm transition-colors",
                selectedClientId === c.clientId ? "bg-primary text-primary-foreground" : "hover:bg-muted"
              )}
            >
              <div className="flex w-full items-center justify-between">
                <span className="font-medium">{c.otherPartyName}</span>
                {c.unreadCount > 0 && <Badge variant="secondary">{c.unreadCount}</Badge>}
              </div>
              {c.lastMessagePreview && (
                <span className="truncate text-xs opacity-70">{c.lastMessagePreview}</span>
              )}
            </button>
          ))}
        </CardContent>
      </Card>

      <Card className={cn("min-w-0 flex-1 flex-col overflow-hidden md:flex", explicitClientId ? "flex" : "hidden")}>
        <div className="flex items-center gap-2 border-b p-2 md:hidden">
          <Button variant="ghost" size="sm" onClick={() => setSelectedClientId(null)}>
            <ArrowLeft className="mr-1 h-4 w-4" aria-hidden="true" /> All conversations
          </Button>
          <span className="truncate text-sm font-medium">
            {conversations?.find((c) => c.clientId === selectedClientId)?.otherPartyName}
          </span>
        </div>
        <CardContent className="flex-1 space-y-3 overflow-y-auto p-4">
          {ordered.map((m) => {
            const isMine = m.senderId === user?.id;
            return (
              <div key={m.id} className={cn("flex", isMine ? "justify-end" : "justify-start")}>
                <div
                  className={cn(
                    "max-w-[75%] rounded-2xl px-4 py-2 text-sm",
                    isMine ? "bg-primary text-primary-foreground" : "bg-muted"
                  )}
                >
                  {m.content}
                </div>
              </div>
            );
          })}
          <div ref={bottomRef} />
        </CardContent>
        <div className="flex items-end gap-2 border-t p-3">
          <Textarea
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="Type a message..."
            className="min-h-10 resize-none"
            aria-label="Message"
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                if (draft.trim() && selectedClientId) send.mutate(draft.trim());
              }
            }}
          />
          <Button
            size="icon"
            aria-label="Send message"
            disabled={!draft.trim() || !selectedClientId || send.isPending}
            onClick={() => send.mutate(draft.trim())}
          >
            <Send className="h-4 w-4" aria-hidden="true" />
          </Button>
        </div>
      </Card>
    </div>
  );
}
