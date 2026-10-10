// Field messages from the backend that have no input on screen to sit under.
import type { ApiError } from "@/lib/types";

// shownFields = the fields whose messages are already shown next to their inputs.
export function leftoverFieldMessages(error: ApiError, shownFields: string[]): string[] {
  return Object.entries(error.fields)
    .filter(([field]) => !shownFields.includes(field))
    .map(([field, message]) => `${field}: ${message}`);
}
