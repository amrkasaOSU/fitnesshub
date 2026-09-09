"use client";

import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { Check, Copy, UserPlus } from "lucide-react";
import { api, ApiError } from "@/lib/api-client";
import { useCurrentUser } from "@/lib/use-auth";
import type { FitnessGoal, User } from "@/lib/types";
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

const GOALS: { value: FitnessGoal; label: string }[] = [
  { value: "FAT_LOSS", label: "Fat loss" },
  { value: "MUSCLE_GAIN", label: "Muscle gain" },
  { value: "STRENGTH", label: "Strength" },
  { value: "GENERAL_FITNESS", label: "General fitness" },
  { value: "ATHLETIC_PERFORMANCE", label: "Athletic performance" },
  { value: "RECOMPOSITION", label: "Recomposition" },
];

const schema = z.object({
  firstName: z.string().min(1, "Required"),
  lastName: z.string().min(1, "Required"),
  email: z.string().email("Enter a valid email"),
  temporaryPassword: z.string().min(8, "At least 8 characters"),
  fitnessGoal: z.string().min(1, "Pick a goal"),
});

type FormValues = z.infer<typeof schema>;

function InviteLinkTab({ coach, origin }: { coach: User | undefined; origin: string }) {
  const [copied, setCopied] = useState(false);

  if (!coach) return <p className="text-sm text-muted-foreground">Loading your invite link...</p>;

  const link = `${origin}/register?coachId=${coach.id}`;

  async function copy() {
    try {
      await navigator.clipboard.writeText(link);
      setCopied(true);
      toast.success("Invite link copied");
      window.setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard access can be denied outright; the field is selectable, so
      // say so rather than failing silently.
      toast.error("Couldn't copy - select the link and copy it manually.");
    }
  }

  return (
    <div className="space-y-3">
      <p className="text-sm text-muted-foreground">
        Send this to your client. It opens a signup form with you already attached as their
        coach, and they choose their own password.
      </p>
      <div className="flex gap-2">
        <Input readOnly value={link} onFocus={(e) => e.currentTarget.select()} className="font-mono text-xs" />
        <Button type="button" variant="secondary" onClick={copy} aria-label="Copy invite link">
          {copied ? <Check className="h-4 w-4" /> : <Copy className="h-4 w-4" />}
        </Button>
      </div>
      <p className="text-xs text-muted-foreground">
        The link doesn&apos;t expire and anyone holding it can join your roster, so share it
        directly rather than posting it publicly.
      </p>
    </div>
  );
}

function CreateAccountTab({ onCreated }: { onCreated: () => void }) {
  const queryClient = useQueryClient();
  // The Select is driven by local state rather than react-hook-form's watch():
  // watch() returns a fresh function the React Compiler can't memoize, which
  // makes it bail out of optimising this whole component.
  const [goal, setGoal] = useState("");
  const {
    register,
    handleSubmit,
    setValue,
    reset,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const create = useMutation<User, ApiError, FormValues>({
    mutationFn: (values) => api.post<User>("/api/coach/clients", values),
    onSuccess: (user, values) => {
      queryClient.invalidateQueries({ queryKey: ["coach", "clients"] });
      toast.success(`${user.firstName} added. Their temporary password is ${values.temporaryPassword}`, {
        duration: 10000,
      });
      reset();
      setGoal("");
      onCreated();
    },
  });

  return (
    <form className="space-y-4" onSubmit={handleSubmit((values) => create.mutate(values))}>
      <div className="grid grid-cols-2 gap-3">
        <div className="space-y-2">
          <Label htmlFor="firstName">First name</Label>
          <Input id="firstName" {...register("firstName")} />
          {errors.firstName && <p className="text-sm text-destructive">{errors.firstName.message}</p>}
        </div>
        <div className="space-y-2">
          <Label htmlFor="lastName">Last name</Label>
          <Input id="lastName" {...register("lastName")} />
          {errors.lastName && <p className="text-sm text-destructive">{errors.lastName.message}</p>}
        </div>
      </div>
      <div className="space-y-2">
        <Label htmlFor="clientEmail">Email</Label>
        <Input id="clientEmail" type="email" {...register("email")} />
        {errors.email && <p className="text-sm text-destructive">{errors.email.message}</p>}
      </div>
      <div className="space-y-2">
        <Label htmlFor="temporaryPassword">Temporary password</Label>
        <Input id="temporaryPassword" {...register("temporaryPassword")} />
        <p className="text-xs text-muted-foreground">
          You&apos;ll need to pass this to them - it&apos;s shown once here and never again.
        </p>
        {errors.temporaryPassword && (
          <p className="text-sm text-destructive">{errors.temporaryPassword.message}</p>
        )}
      </div>
      <div className="space-y-2">
        <Label htmlFor="fitnessGoal">Primary goal</Label>
        <Select
          value={goal}
          onValueChange={(v) => {
            setGoal(v);
            setValue("fitnessGoal", v, { shouldValidate: true });
          }}
        >
          <SelectTrigger id="fitnessGoal" className="w-full">
            <SelectValue placeholder="Select a goal" />
          </SelectTrigger>
          <SelectContent>
            {GOALS.map((g) => (
              <SelectItem key={g.value} value={g.value}>
                {g.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
        {errors.fitnessGoal && <p className="text-sm text-destructive">{errors.fitnessGoal.message}</p>}
      </div>
      {create.isError && <p className="text-sm text-destructive">{create.error.message}</p>}
      <Button type="submit" className="w-full" disabled={create.isPending}>
        {create.isPending ? "Creating..." : "Create client account"}
      </Button>
    </form>
  );
}

export function AddClientDialog() {
  const [open, setOpen] = useState(false);
  // Read on open rather than during render: the page is prerendered, so
  // touching window.location while rendering would desync the hydrated markup.
  const [origin, setOrigin] = useState("");
  const { data: coach } = useCurrentUser();

  function handleOpenChange(next: boolean) {
    if (next) setOrigin(window.location.origin);
    setOpen(next);
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>
        <Button className="gap-2">
          <UserPlus className="h-4 w-4" /> Add client
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Add a client</DialogTitle>
          <DialogDescription>
            Invite them to sign up themselves, or create the account for them.
          </DialogDescription>
        </DialogHeader>
        <Tabs defaultValue="invite">
          <TabsList className="grid w-full grid-cols-2">
            <TabsTrigger value="invite">Send invite link</TabsTrigger>
            <TabsTrigger value="create">Create account</TabsTrigger>
          </TabsList>
          <TabsContent value="invite" className="mt-4">
            <InviteLinkTab coach={coach} origin={origin} />
          </TabsContent>
          <TabsContent value="create" className="mt-4">
            <CreateAccountTab onCreated={() => setOpen(false)} />
          </TabsContent>
        </Tabs>
      </DialogContent>
    </Dialog>
  );
}
