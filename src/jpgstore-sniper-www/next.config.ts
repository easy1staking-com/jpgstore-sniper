import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Turbopack handles WASM natively — empty config silences the webpack warning
  turbopack: {},
  webpack: (config) => {
    // Fallback for --webpack builds: MeshJS needs WASM support
    config.experiments = {
      ...config.experiments,
      asyncWebAssembly: true,
      layers: true,
    };
    return config;
  },
};

export default nextConfig;
