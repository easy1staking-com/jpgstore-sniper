import type { Metadata } from "next";
import { Outfit, JetBrains_Mono } from "next/font/google";
import WalletProvider from "@/components/WalletProvider";
import "./globals.css";

const outfit = Outfit({
  variable: "--font-outfit",
  subsets: ["latin"],
  weight: ["300", "400", "500", "600", "700"],
});

const jetbrainsMono = JetBrains_Mono({
  variable: "--font-jetbrains-mono",
  subsets: ["latin"],
  weight: ["400", "500", "600"],
});

export const metadata: Metadata = {
  title: "JPG Sniper — Decentralized NFT Sniping",
  description:
    "Trustless on-chain NFT sniping for jpg.store on Cardano",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className="dark">
      <body
        className={`${outfit.variable} ${jetbrainsMono.variable} antialiased`}
        style={{ fontFamily: "var(--font-body)" }}
      >
        <WalletProvider>{children}</WalletProvider>
      </body>
    </html>
  );
}
