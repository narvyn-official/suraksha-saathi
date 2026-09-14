import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Suraksha Saathi · Training centre",
  description:
    "Review offline safety training and verify pilot simulation credentials.",
  other: {
    "codex-preview": "development",
  },
  icons: {
    icon: "/favicon.svg",
    shortcut: "/favicon.svg",
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className="antialiased">{children}</body>
    </html>
  );
}
