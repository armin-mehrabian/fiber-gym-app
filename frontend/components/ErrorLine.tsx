// The main error line of a form, plus any field messages that have no input on screen to sit under.
import { leftoverFieldMessages } from "@/lib/errors";
import type { ApiError } from "@/lib/types";

type ErrorLineProps = {
  error: ApiError | null;
  shownFields?: string[]; // fields already shown next to their inputs
  testId?: string;
};

export default function ErrorLine({ error, shownFields = [], testId }: ErrorLineProps) {
  if (!error) {
    return null;
  }
  const leftovers = leftoverFieldMessages(error, shownFields);
  return (
    <div role="alert" className="grid gap-1">
      <p data-testid={testId} className="text-sm font-bold tracking-[0.08em] text-acc">
        {error.error}
      </p>
      {leftovers.map((message) => (
        <p key={message} className="text-xs tracking-[0.1em] text-acc">
          {message}
        </p>
      ))}
    </div>
  );
}
