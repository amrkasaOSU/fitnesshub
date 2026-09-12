"use client";

import { PhotoGallery } from "@/components/photo-gallery";

export default function PhotosPage() {
  return (
    <div className="space-y-4 pb-24">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">Progress photos</h1>
        <p className="text-sm text-muted-foreground">
          Private to you and your coach.
        </p>
      </div>
      <PhotoGallery />
    </div>
  );
}
