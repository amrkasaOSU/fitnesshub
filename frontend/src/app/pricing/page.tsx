import Link from "next/link";
import { Check, Dumbbell } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

const coachPlans = [
  {
    name: "Free",
    price: "$0",
    features: ["Up to 3 clients", "Full workout & program builder", "Basic messaging"],
  },
  {
    name: "Pro",
    price: "$49/mo",
    features: [
      "Unlimited clients",
      "Advanced analytics & attention flags",
      "AI coach insights",
      "Custom branding on reports",
      "Exportable PDF/CSV reports",
    ],
  },
];

const clientPlans = [
  { name: "Free", price: "$0", features: ["Workout & nutrition tracking", "5 AI questions/day"] },
  {
    name: "Premium",
    price: "$9/mo",
    features: ["50 AI questions/day", "Advanced progress charts", "Data export", "Extended history"],
  },
];

export default function PricingPage() {
  return (
    <div className="mx-auto max-w-5xl px-4 py-16">
      <div className="mb-10 flex items-center gap-2 font-bold">
        <Link href="/" className="flex items-center gap-2">
          <Dumbbell className="h-5 w-5 text-primary" /> FitnessHub
        </Link>
      </div>
      <h1 className="text-center text-4xl font-bold tracking-tight">Pricing</h1>
      <p className="mx-auto mt-3 max-w-xl text-center text-muted-foreground">
        Simple plans for coaches and clients. Cancel anytime.
      </p>

      <h2 className="mt-12 text-xl font-semibold">For Coaches</h2>
      <div className="mt-4 grid gap-6 sm:grid-cols-2">
        {coachPlans.map((plan) => (
          <Card key={plan.name} className={plan.name === "Pro" ? "border-primary" : ""}>
            <CardHeader>
              <CardTitle>{plan.name}</CardTitle>
              <div className="text-3xl font-bold">{plan.price}</div>
            </CardHeader>
            <CardContent>
              <ul className="space-y-2 text-sm">
                {plan.features.map((f) => (
                  <li key={f} className="flex items-center gap-2">
                    <Check className="h-4 w-4 text-primary" /> {f}
                  </li>
                ))}
              </ul>
              <Button asChild className="mt-6 w-full">
                <Link href="/register">Get started</Link>
              </Button>
            </CardContent>
          </Card>
        ))}
      </div>

      <h2 className="mt-12 text-xl font-semibold">For Clients</h2>
      <div className="mt-4 grid gap-6 sm:grid-cols-2">
        {clientPlans.map((plan) => (
          <Card key={plan.name} className={plan.name === "Premium" ? "border-primary" : ""}>
            <CardHeader>
              <CardTitle>{plan.name}</CardTitle>
              <div className="text-3xl font-bold">{plan.price}</div>
            </CardHeader>
            <CardContent>
              <ul className="space-y-2 text-sm">
                {plan.features.map((f) => (
                  <li key={f} className="flex items-center gap-2">
                    <Check className="h-4 w-4 text-primary" /> {f}
                  </li>
                ))}
              </ul>
              <p className="mt-6 text-center text-xs text-muted-foreground">
                Ask your coach for an invite to join FitnessHub.
              </p>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
