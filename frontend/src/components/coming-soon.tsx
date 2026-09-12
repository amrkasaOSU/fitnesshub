import { Sparkles } from "lucide-react";
import { Card, CardContent } from "@/components/ui/card";

/** Shown in place of a feature whose integration isn't configured yet. */
export function ComingSoon({ title, body }: { title: string; body: string }) {
  return (
    <Card>
      <CardContent className="flex flex-col items-center gap-3 py-16 text-center">
        <div className="rounded-full bg-muted p-3">
          <Sparkles className="h-5 w-5 text-muted-foreground" />
        </div>
        <div className="max-w-md space-y-1">
          <p className="font-medium">{title}</p>
          <p className="text-sm text-muted-foreground">{body}</p>
        </div>
        <span className="rounded-full border px-3 py-1 text-xs font-medium text-muted-foreground">
          Coming soon
        </span>
      </CardContent>
    </Card>
  );
}
