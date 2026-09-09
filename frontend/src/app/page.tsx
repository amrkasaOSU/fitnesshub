import Link from "next/link";
import {
  Dumbbell,
  TrendingUp,
  Apple,
  LineChart,
  MessageSquare,
  Sparkles,
  LayoutDashboard,
  Check,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

const features = [
  {
    icon: Dumbbell,
    title: "Workout Tracking",
    body: "Log sets, reps, weight, and RPE in seconds, right from the gym floor.",
  },
  {
    icon: TrendingUp,
    title: "Progressive Overload",
    body: "Estimated 1RM, volume trends, and personal records - calculated automatically from every set you log.",
  },
  {
    icon: Apple,
    title: "Nutrition",
    body: "Track calories, protein, and macros against coach-set targets, with a clearly-labeled calorie balance estimate.",
  },
  {
    icon: LineChart,
    title: "Progress Tracking",
    body: "Weight, steps, strength, and adherence in one place, with charts that separate noise from real trends.",
  },
  {
    icon: MessageSquare,
    title: "Coach Communication",
    body: "A direct line to your coach: messages, feedback on completed workouts, and weekly check-ins.",
  },
  {
    icon: Sparkles,
    title: "AI Training Assistant",
    body: "Ask training questions and get answers grounded in your actual logged data, not generic advice.",
  },
];

const faqs = [
  {
    q: "Is the AI assistant a replacement for my coach?",
    a: "No. It's a data-driven assistant that answers questions using your real training history. Your coach makes the calls on your program.",
  },
  {
    q: "Can I use FitnessHub without a coach?",
    a: "FitnessHub is built around the coach-client relationship. Coaches can invite clients directly from their dashboard.",
  },
  {
    q: "Is my data private?",
    a: "Your coach can see your training data to coach you. Other clients never can, and coach notes about you stay private to your coach.",
  },
];

export default function LandingPage() {
  return (
    <div className="flex flex-col">
      <header className="border-b bg-background">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4">
          <div className="flex items-center gap-2 font-bold text-lg">
            <Dumbbell className="h-5 w-5 text-primary" />
            FitnessHub
          </div>
          <nav className="flex items-center gap-2">
            <Button variant="ghost" asChild>
              <Link href="/pricing">Pricing</Link>
            </Button>
            <Button variant="ghost" asChild>
              <Link href="/login">Log in</Link>
            </Button>
            <Button asChild>
              <Link href="/register">Start Training</Link>
            </Button>
          </nav>
        </div>
      </header>

      <section className="border-b bg-gradient-to-b from-muted/50 to-background">
        <div className="mx-auto max-w-4xl px-4 py-24 text-center">
          <h1 className="text-4xl font-bold tracking-tight sm:text-6xl">
            Train smarter. Track everything. Get coached.
          </h1>
          <p className="mx-auto mt-6 max-w-2xl text-lg text-muted-foreground">
            FitnessHub connects your workouts, nutrition, progress, and coach communication in one place.
          </p>
          <div className="mt-10 flex justify-center gap-4">
            <Button size="lg" asChild>
              <Link href="/register">Start Training</Link>
            </Button>
            <Button size="lg" variant="outline" asChild>
              <Link href="#features">See how it works</Link>
            </Button>
          </div>
        </div>
      </section>

      <section id="features" className="mx-auto max-w-6xl px-4 py-20">
        <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {features.map((f) => (
            <Card key={f.title}>
              <CardHeader>
                <f.icon className="h-8 w-8 text-primary" />
                <CardTitle className="mt-2">{f.title}</CardTitle>
              </CardHeader>
              <CardContent className="text-sm text-muted-foreground">{f.body}</CardContent>
            </Card>
          ))}
        </div>
      </section>

      <section className="border-y bg-muted/30">
        <div className="mx-auto flex max-w-6xl flex-col items-center gap-8 px-4 py-20 lg:flex-row">
          <div className="flex-1">
            <LayoutDashboard className="h-8 w-8 text-primary" />
            <h2 className="mt-4 text-3xl font-bold tracking-tight">
              An operational command center for coaches
            </h2>
            <p className="mt-4 text-muted-foreground">
              See who trained today, who missed workouts, who&apos;s progressing, and who needs
              attention - all without digging through spreadsheets.
            </p>
            <ul className="mt-6 space-y-2 text-sm">
              {["Attention flags for stalled or inconsistent clients", "One-click program builder with drag-and-drop exercises", "Automatic PR detection and progress reports"].map(
                (item) => (
                  <li key={item} className="flex items-center gap-2">
                    <Check className="h-4 w-4 text-primary" /> {item}
                  </li>
                )
              )}
            </ul>
          </div>
          <div className="flex-1">
            <div className="rounded-xl border bg-background p-6 shadow-sm">
              <div className="text-sm font-semibold text-muted-foreground">Coach Dashboard</div>
              <div className="mt-4 grid grid-cols-2 gap-3">
                {[
                  ["Active Clients", "24"],
                  ["Training Today", "17"],
                  ["Avg Adherence", "88%"],
                  ["Need Attention", "3"],
                ].map(([label, value]) => (
                  <div key={label} className="rounded-lg border p-3">
                    <div className="text-xs text-muted-foreground">{label}</div>
                    <div className="text-2xl font-bold">{value}</div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </div>
      </section>

      <section id="pricing" className="mx-auto max-w-6xl px-4 py-20">
        <h2 className="text-center text-3xl font-bold tracking-tight">Pricing</h2>
        <div className="mx-auto mt-10 grid max-w-4xl gap-6 sm:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle>Free</CardTitle>
              <div className="text-3xl font-bold">$0</div>
            </CardHeader>
            <CardContent className="space-y-2 text-sm text-muted-foreground">
              <p>Full workout, nutrition, and progress tracking.</p>
              <p>5 AI questions/day.</p>
            </CardContent>
          </Card>
          <Card className="border-primary">
            <CardHeader>
              <CardTitle>Premium / Pro</CardTitle>
              <div className="text-3xl font-bold">$19/mo</div>
            </CardHeader>
            <CardContent className="space-y-2 text-sm text-muted-foreground">
              <p>Unlimited clients, advanced analytics, and reports (coaches).</p>
              <p>50 AI questions/day, data export (clients).</p>
            </CardContent>
          </Card>
        </div>
      </section>

      <section className="border-t bg-muted/30">
        <div className="mx-auto max-w-3xl px-4 py-20">
          <h2 className="text-center text-3xl font-bold tracking-tight">FAQ</h2>
          <div className="mt-8 space-y-6">
            {faqs.map((f) => (
              <div key={f.q}>
                <h3 className="font-semibold">{f.q}</h3>
                <p className="mt-1 text-sm text-muted-foreground">{f.a}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      <footer className="border-t py-8 text-center text-xs text-muted-foreground">
        FitnessHub is a fitness tracking and coaching tool. Information provided by the application
        or AI assistant is for general informational purposes and is not medical advice. Consult an
        appropriately qualified healthcare professional for medical concerns, injuries, or health
        conditions.
      </footer>
    </div>
  );
}
