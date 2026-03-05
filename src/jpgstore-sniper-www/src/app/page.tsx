"use client";

import { useState } from "react";
import { useWallet } from "@meshsdk/react";
import Header from "@/components/Header";
import Footer from "@/components/Footer";
import MySnipes from "@/components/MySnipes";
import NewSnipe from "@/components/NewSnipe";
import { useSnipes } from "@/hooks/useSnipes";
import { useSettings } from "@/hooks/useSettings";

type Tab = "my-snipes" | "new-snipe";

export default function Home() {
  const [activeTab, setActiveTab] = useState<Tab>("my-snipes");
  const { connected } = useWallet();

  const { snipes, loading: snipesLoading, error: snipesError, cancelSnipes, addSnipe, refresh } = useSnipes();
  const { settings, loading: settingsLoading, error: settingsError } = useSettings();

  return (
    <div className="flex min-h-screen flex-col bg-void">
      <Header activeTab={activeTab} onTabChange={setActiveTab} />
      <main className="mx-auto w-full max-w-4xl flex-1 px-5 py-8">
        {activeTab === "my-snipes" ? (
          <MySnipes
            snipes={snipes}
            loading={snipesLoading}
            error={snipesError}
            onCancel={cancelSnipes}
            onRefresh={refresh}
          />
        ) : (
          <NewSnipe
            onCreated={(order) => {
              addSnipe(order);
              refresh();
            }}
            walletConnected={connected}
            settings={settings}
            settingsLoading={settingsLoading}
            settingsError={settingsError}
          />
        )}
      </main>
      <Footer />
    </div>
  );
}
