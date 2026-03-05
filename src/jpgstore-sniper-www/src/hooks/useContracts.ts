"use client";

import { useEffect, useState } from "react";
import { ContractInfo } from "@/lib/types";
import { fetchContracts } from "@/lib/api";

export function useContracts() {
  const [contracts, setContracts] = useState<ContractInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchContracts()
      .then((data) => {
        setContracts(data);
        setError(null);
      })
      .catch((e) => {
        setError(e instanceof Error ? e.message : "Failed to fetch contracts");
      })
      .finally(() => setLoading(false));
  }, []);

  return { contracts, loading, error };
}
