"use client";

import { useRef } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { api } from "@/lib/api-client";
import { useCurrentUser } from "@/lib/use-auth";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import type { User } from "@/lib/types";

export default function SettingsPage() {
  const { data: user } = useCurrentUser();
  const queryClient = useQueryClient();
  const firstNameRef = useRef<HTMLInputElement>(null);
  const lastNameRef = useRef<HTMLInputElement>(null);

  const save = useMutation({
    mutationFn: () =>
      api.patch<User>("/api/users/me", {
        firstName: firstNameRef.current?.value,
        lastName: lastNameRef.current?.value,
      }),
    onSuccess: (updated) => {
      queryClient.setQueryData(["currentUser"], updated);
      toast.success("Profile updated");
    },
  });

  return (
    <div className="mx-auto max-w-lg space-y-6">
      <h1 className="text-2xl font-bold tracking-tight">Settings</h1>
      <Card>
        <CardHeader>
          <CardTitle>Profile</CardTitle>
        </CardHeader>
        {/* key={user?.email} remounts the form (fresh defaultValues) once the user loads, without mirroring server state into local state */}
        <CardContent className="space-y-4" key={user?.email ?? "loading"}>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-2">
              <Label htmlFor="firstName">First name</Label>
              <Input id="firstName" ref={firstNameRef} defaultValue={user?.firstName ?? ""} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="lastName">Last name</Label>
              <Input id="lastName" ref={lastNameRef} defaultValue={user?.lastName ?? ""} />
            </div>
          </div>
          <div className="space-y-2">
            <Label>Email</Label>
            <Input value={user?.email ?? ""} disabled />
          </div>
          <div className="space-y-2">
            <Label>Role</Label>
            <Input value={user?.role ?? ""} disabled />
          </div>
          <Button onClick={() => save.mutate()} disabled={save.isPending}>
            {save.isPending ? "Saving..." : "Save changes"}
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Billing</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <p className="text-sm text-muted-foreground">
            Manage your subscription. Requires Stripe to be configured on the server.
          </p>
          <div className="flex gap-2">
            <Button
              variant="outline"
              onClick={() =>
                api
                  .post<{ url: string }>("/api/billing/checkout")
                  .then((r) => (window.location.href = r.url))
                  .catch((e) => toast.error(e.message))
              }
            >
              Upgrade plan
            </Button>
            <Button
              variant="outline"
              onClick={() =>
                api
                  .post<{ url: string }>("/api/billing/portal")
                  .then((r) => (window.location.href = r.url))
                  .catch((e) => toast.error(e.message))
              }
            >
              Manage billing
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
