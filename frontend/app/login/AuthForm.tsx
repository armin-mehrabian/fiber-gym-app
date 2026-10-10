"use client"; // a form: typing, toggling and submitting happen in the browser

import { useState } from "react";
import { useRouter } from "next/navigation";
import ErrorLine from "@/components/ErrorLine";
import PennantButton from "@/components/PennantButton";
import { login, signup } from "@/lib/api";
import { stageFor } from "@/lib/stage";
import type { ApiError } from "@/lib/types";

type Mode = "signin" | "signup";

export default function AuthForm() {
  const router = useRouter();
  const [mode, setMode] = useState<Mode>("signin");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    // A new account stores the browser's timezone, so "this week" matches the user's own calendar.
    const result =
      mode === "signup"
        ? await signup(email, password, Intl.DateTimeFormat().resolvedOptions().timeZone)
        : await login(email, password);
    if (!result.ok) {
      setBusy(false);
      setError(result.error);
      return;
    }
    // busy stays true: the button remains disabled while the next page loads.
    setPassword(""); // don't leave the password in memory once we're in
    router.replace(stageFor(result.data));
  }

  function toggleMode() {
    setMode(mode === "signin" ? "signup" : "signin");
    setError(null);
  }

  return (
    // noValidate: the backend checks the input and its messages are shown under the fields.
    <form onSubmit={handleSubmit} noValidate className="grid gap-3">
      <Field
        name="email"
        type="email"
        placeholder="Email"
        autoComplete="email"
        value={email}
        onChange={setEmail}
        error={error?.fields.email}
      />
      <Field
        name="password"
        type="password"
        placeholder="Password"
        autoComplete={mode === "signin" ? "current-password" : "new-password"}
        value={password}
        onChange={setPassword}
        error={error?.fields.password}
      />
      <ErrorLine error={error} shownFields={["email", "password"]} testId="auth-error" />
      <div className="pt-2">
        <PennantButton type="submit" disabled={busy} testId="auth-submit">
          {mode === "signin" ? "Sign in" : "Create account"}
        </PennantButton>
      </div>
      <button
        type="button"
        onClick={toggleMode}
        data-testid="auth-toggle"
        className="min-h-12 text-[0.85rem] font-bold tracking-[0.1em] text-mut"
      >
        {mode === "signin" ? "New here? Create account" : "I have an account"}
      </button>
    </form>
  );
}

type FieldProps = {
  name: "email" | "password";
  type: string;
  placeholder: string;
  autoComplete: string;
  value: string;
  onChange: (value: string) => void;
  error?: string; // the backend's message for this field
};

// One underlined input with its error message right below it.
function Field({ name, type, placeholder, autoComplete, value, onChange, error }: FieldProps) {
  const errorId = `field-error-${name}`;
  return (
    <div className="grid gap-1">
      <input
        name={name}
        type={type}
        placeholder={placeholder}
        aria-label={placeholder}
        autoComplete={autoComplete}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        data-testid={`auth-${name}`}
        // Typed text keeps its real case (emails, passwords); only the placeholder is all caps.
        className="min-h-14 w-full border-b-[1.5px] border-edge bg-transparent px-0.5 text-base normal-case tracking-[0.06em] text-fg outline-none placeholder:uppercase placeholder:text-mut focus:border-acc"
      />
      {error && (
        <p id={errorId} data-testid={errorId} className="text-xs tracking-[0.1em] text-acc">
          {error}
        </p>
      )}
    </div>
  );
}
