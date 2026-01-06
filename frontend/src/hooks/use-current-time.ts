import { useState, useEffect } from 'react';

export function useCurrentTime() {
  const [currentTime, setCurrentTime] = useState(() =>
    typeof window !== 'undefined'
      ? new Date().toLocaleTimeString('en-US', { hour12: false })
      : '--:--:--'
  );

  useEffect(() => {
    const timer = setInterval(() => {
      setCurrentTime(new Date().toLocaleTimeString('en-US', { hour12: false }));
    }, 1000);
    return () => clearInterval(timer);
  }, []);

  return currentTime;
}
