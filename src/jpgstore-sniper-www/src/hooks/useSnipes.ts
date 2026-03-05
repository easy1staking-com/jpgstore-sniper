"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { SnipeOrder } from "@/lib/types";
import { fetchSnipes } from "@/lib/api";

const POLL_INTERVAL = 15_000; // 15 seconds

export function useSnipes(walletPkh?: string) {
  const [snipes, setSnipes] = useState<SnipeOrder[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const intervalRef = useRef<ReturnType<typeof setInterval>>(undefined);

  const refresh = useCallback(async () => {
    try {
      const data = await fetchSnipes(walletPkh);
      setSnipes(data);
      setError(null);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to fetch snipes");
    } finally {
      setLoading(false);
    }
  }, [walletPkh]);

  useEffect(() => {
    refresh();
    intervalRef.current = setInterval(refresh, POLL_INTERVAL);
    return () => clearInterval(intervalRef.current);
  }, [refresh]);

  const cancelSnipes = useCallback((ids: string[]) => {
    setSnipes((prev) => prev.filter((s) => !ids.includes(s.id)));
  }, []);

  const addSnipe = useCallback((order: SnipeOrder) => {
    setSnipes((prev) => [order, ...prev]);
  }, []);

  return { snipes, loading, error, cancelSnipes, addSnipe, refresh };
}
