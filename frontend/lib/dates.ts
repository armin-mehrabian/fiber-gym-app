// Date and time text for the workout and history screens.

// Workout clock: "4:05" under an hour, "1:02:05" after that. Negative input (clock skew) shows 0:00.
export function formatElapsed(ms: number): string {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  const ss = String(seconds).padStart(2, "0");
  if (hours === 0) {
    return `${minutes}:${ss}`;
  }
  return `${hours}:${String(minutes).padStart(2, "0")}:${ss}`;
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

// "2026-10-05" → "Week of 5 Oct". Read straight from the text, because the API already gives the
// Monday in the user's timezone; turning it into a Date could shift it a day.
export function formatWeekOf(weekStart: string): string {
  const [, month, day] = weekStart.split("-").map(Number);
  return `Week of ${day} ${MONTHS[month - 1]}`;
}

// A workout's start as the user saw it on their own calendar, e.g. "Mon 5".
export function formatSessionDay(isoTimestamp: string, timeZone: string): string {
  return new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", timeZone }).format(
    new Date(isoTimestamp),
  );
}

// The same with the month, for the session detail header: "Mon 5 Oct".
export function formatSessionDate(isoTimestamp: string, timeZone: string): string {
  return new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "short", timeZone }).format(
    new Date(isoTimestamp),
  );
}

// A day from the API ("2026-10-05", already the user's calendar day) → "Mon 5 Oct".
export function formatDay(date: string): string {
  // Noon UTC on that date, read in UTC: the same calendar day whatever the server's timezone.
  return formatSessionDate(`${date}T12:00:00Z`, "UTC");
}
