"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Dumbbell } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useRegister } from "@/lib/use-auth";
import { Suspense, useState } from "react";

const schema = z.object({
  firstName: z.string().min(1, "Required"),
  lastName: z.string().min(1, "Required"),
  email: z.string().email("Enter a valid email"),
  password: z.string().min(8, "At least 8 characters"),
  coachId: z.string().optional(),
});

type FormValues = z.infer<typeof schema>;

function RegisterForm() {
  const params = useSearchParams();
  const [role, setRole] = useState<"COACH" | "CLIENT">(
    params.get("coachId") ? "CLIENT" : "COACH"
  );
  const register_ = useRegister();
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { coachId: params.get("coachId") ?? "" },
  });

  return (
    <Card className="w-full max-w-sm">
      <CardHeader className="text-center">
        <Link href="/" className="mx-auto flex items-center gap-2 font-bold">
          <Dumbbell className="h-5 w-5 text-primary" /> FitnessHub
        </Link>
        <CardTitle className="mt-4">Create your account</CardTitle>
        <CardDescription>Start training or start coaching.</CardDescription>
      </CardHeader>
      <CardContent>
        <Tabs value={role} onValueChange={(v) => setRole(v as "COACH" | "CLIENT")} className="mb-4">
          <TabsList className="grid w-full grid-cols-2">
            <TabsTrigger value="COACH">I&apos;m a coach</TabsTrigger>
            <TabsTrigger value="CLIENT">I have an invite</TabsTrigger>
          </TabsList>
        </Tabs>
        <form
          className="space-y-4"
          onSubmit={handleSubmit((values) =>
            register_.mutate({ ...values, role, coachId: role === "CLIENT" ? values.coachId : undefined })
          )}
        >
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
            <Label htmlFor="email">Email</Label>
            <Input id="email" type="email" autoComplete="email" {...register("email")} />
            {errors.email && <p className="text-sm text-destructive">{errors.email.message}</p>}
          </div>
          <div className="space-y-2">
            <Label htmlFor="password">Password</Label>
            <Input id="password" type="password" autoComplete="new-password" {...register("password")} />
            {errors.password && <p className="text-sm text-destructive">{errors.password.message}</p>}
          </div>
          {role === "CLIENT" && (
            <div className="space-y-2">
              <Label htmlFor="coachId">Coach invite code</Label>
              <Input id="coachId" placeholder="Paste the invite link's coach ID" {...register("coachId")} />
              <p className="text-xs text-muted-foreground">
                Ask your coach for their invite link - it includes this automatically.
              </p>
            </div>
          )}
          {register_.isError && <p className="text-sm text-destructive">{register_.error.message}</p>}
          <Button type="submit" className="w-full" disabled={register_.isPending}>
            {register_.isPending ? "Creating account..." : "Create account"}
          </Button>
        </form>
        <p className="mt-6 text-center text-sm text-muted-foreground">
          Already have an account?{" "}
          <Link href="/login" className="font-medium text-primary hover:underline">
            Log in
          </Link>
        </p>
      </CardContent>
    </Card>
  );
}

export default function RegisterPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-muted/30 px-4">
      <Suspense>
        <RegisterForm />
      </Suspense>
    </div>
  );
}
