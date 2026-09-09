"use client";

import { useQuery, useQueryClient, useMutation } from "@tanstack/react-query";
import { api } from "@/lib/api-client";
import type { NotificationDto, PageResponse } from "@/lib/types";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";

export default function NotificationsPage() {
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ["notifications"],
    queryFn: () => api.get<PageResponse<NotificationDto>>("/api/notifications", { pageSize: 50 }),
  });

  const markRead = useMutation({
    mutationFn: (id: string) => api.patch(`/api/notifications/${id}/read`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["notifications"] }),
  });

  const markAllRead = useMutation({
    mutationFn: () => api.patch("/api/notifications/read-all"),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["notifications"] });
      queryClient.invalidateQueries({ queryKey: ["notifications", "unread-count"] });
    },
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight">Notifications</h1>
        <Button variant="outline" size="sm" onClick={() => markAllRead.mutate()}>
          Mark all read
        </Button>
      </div>
      {isLoading && <Skeleton className="h-64" />}
      <div className="space-y-2">
        {data?.data.map((n) => (
          <Card
            key={n.id}
            className={cn("cursor-pointer", !n.readAt && "border-primary/50 bg-primary/5")}
            onClick={() => !n.readAt && markRead.mutate(n.id)}
          >
            <CardContent className="py-3">
              <div className="flex items-center justify-between">
                <p className="text-sm font-medium">{n.title}</p>
                <span className="text-xs text-muted-foreground">
                  {new Date(n.createdAt).toLocaleDateString()}
                </span>
              </div>
              {n.body && <p className="mt-1 text-sm text-muted-foreground">{n.body}</p>}
            </CardContent>
          </Card>
        ))}
        {data?.data.length === 0 && (
          <p className="text-sm text-muted-foreground">You&apos;re all caught up.</p>
        )}
      </div>
    </div>
  );
}
