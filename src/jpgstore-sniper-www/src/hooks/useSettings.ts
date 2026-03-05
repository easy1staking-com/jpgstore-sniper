"use client";

import { useEffect, useState } from "react";
import { Settings } from "@/lib/types";
import { fetchSettings } from "@/lib/api";

export function useSettings() {
  const [settings, setSettings] = useState<Settings | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchSettings()
      .then((data) => {
        setSettings(data);
        setError(null);
      })
      .catch((e) => {
        setError(e instanceof Error ? e.message : "Failed to fetch settings");
      })
      .finally(() => setLoading(false));
  }, []);

  return { settings, loading, error };
}
