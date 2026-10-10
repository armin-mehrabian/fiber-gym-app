// A true/false that switches itself off again after a moment: for short "Saved" notes.
import { useEffect, useRef, useState } from "react";

export function useFlash(durationMs = 2000): [visible: boolean, flash: () => void] {
  const [visible, setVisible] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Stop a pending timer when the component goes away.
  useEffect(() => {
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, []);

  function flash() {
    // A second save restarts the countdown instead of hiding the note early.
    if (timer.current) clearTimeout(timer.current);
    setVisible(true);
    timer.current = setTimeout(() => setVisible(false), durationMs);
  }

  return [visible, flash];
}
