"use client";

import dynamic from "next/dynamic";

const MeshProviderClient = dynamic(
  () => import("@meshsdk/react").then((mod) => mod.MeshProvider),
  { ssr: false }
);

export default function WalletProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  return <MeshProviderClient>{children}</MeshProviderClient>;
}
