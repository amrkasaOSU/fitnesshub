"use client";

import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { ImagePlus, Trash2, Lock } from "lucide-react";
import { api, apiUpload, ApiError } from "@/lib/api-client";
import type { ProgressPhotoDto } from "@/lib/types";
import { Card, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

function todayLocal() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

/**
 * Shared by the client's own photo page and the coach's view of a client.
 * `clientId` is set only for the coach; passing it switches the gallery to
 * read-only, because a coach may look at a client's photos but not add to or
 * delete from them.
 */
export function PhotoGallery({ clientId }: { clientId?: string }) {
  const queryClient = useQueryClient();
  const readOnly = Boolean(clientId);
  const fileRef = useRef<HTMLInputElement>(null);
  const [takenOn, setTakenOn] = useState(todayLocal());
  const [caption, setCaption] = useState("");

  const { data: photos, isLoading } = useQuery({
    queryKey: ["progress-photos", clientId ?? "me"],
    queryFn: () =>
      api.get<ProgressPhotoDto[]>("/api/progress-photos", clientId ? { clientId } : undefined),
  });

  const upload = useMutation<ProgressPhotoDto, ApiError, File>({
    mutationFn: (file) => {
      const form = new FormData();
      form.append("file", file);
      form.append("takenOn", takenOn);
      if (caption) form.append("caption", caption);
      return apiUpload<ProgressPhotoDto>("/api/progress-photos", form);
    },
    onSuccess: () => {
      toast.success("Photo added");
      setCaption("");
      if (fileRef.current) fileRef.current.value = "";
      queryClient.invalidateQueries({ queryKey: ["progress-photos"] });
    },
    onError: (e) => toast.error(e.message),
  });

  const remove = useMutation<void, ApiError, string>({
    mutationFn: (id) => api.delete<void>(`/api/progress-photos/${id}`),
    onSuccess: () => {
      toast.success("Photo deleted");
      queryClient.invalidateQueries({ queryKey: ["progress-photos"] });
    },
    onError: (e) => toast.error(e.message),
  });

  return (
    <div className="space-y-4">
      {!readOnly && (
        <Card>
          <CardContent className="space-y-3 py-4">
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="takenOn">Date taken</Label>
                <Input id="takenOn" type="date" value={takenOn} onChange={(e) => setTakenOn(e.target.value)} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="caption">Caption — optional</Label>
                <Input
                  id="caption"
                  value={caption}
                  onChange={(e) => setCaption(e.target.value)}
                  placeholder="Week 12, front"
                />
              </div>
            </div>
            <input
              ref={fileRef}
              type="file"
              accept="image/jpeg,image/png,image/webp"
              className="hidden"
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) upload.mutate(file);
              }}
            />
            <Button
              className="w-full gap-2"
              onClick={() => fileRef.current?.click()}
              disabled={upload.isPending}
            >
              <ImagePlus className="h-4 w-4" />
              {upload.isPending ? "Uploading..." : "Add a photo"}
            </Button>
            <p className="flex items-start gap-1.5 text-xs text-muted-foreground">
              <Lock className="mt-0.5 h-3 w-3 shrink-0" />
              Only you and your coach can see these. They are not public and have no shareable
              link. JPEG, PNG, or WebP, up to 5 MB.
            </p>
          </CardContent>
        </Card>
      )}

      {isLoading && <Skeleton className="h-64" />}

      {!isLoading && (photos ?? []).length === 0 && (
        <Card>
          <CardContent className="py-14 text-center">
            <p className="font-medium">No photos yet</p>
            <p className="text-sm text-muted-foreground">
              {readOnly
                ? "This client hasn't added any progress photos."
                : "Progress photos show change the scale misses. Same lighting and angle each time works best."}
            </p>
          </CardContent>
        </Card>
      )}

      {(photos ?? []).length > 0 && (
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
          {photos!.map((p) => (
            <Card key={p.id} className="overflow-hidden py-0">
              {/* Served through an authenticated endpoint; the cookie rides along
                  because the backend sets Access-Control-Allow-Credentials. */}
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                src={`${API_URL}/api/progress-photos/${p.id}/image`}
                alt={p.caption ?? `Progress photo from ${p.takenOn}`}
                className="aspect-[3/4] w-full object-cover"
                loading="lazy"
              />
              <CardContent className="space-y-1 p-2.5">
                <p className="text-xs font-medium">{p.takenOn}</p>
                {p.caption && <p className="text-xs text-muted-foreground">{p.caption}</p>}
                {!readOnly && (
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-7 w-full gap-1.5 text-xs text-muted-foreground hover:text-destructive"
                    onClick={() => remove.mutate(p.id)}
                    disabled={remove.isPending}
                  >
                    <Trash2 className="h-3 w-3" /> Delete
                  </Button>
                )}
              </CardContent>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
