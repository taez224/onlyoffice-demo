import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: "http://localhost:8080/api/:path*",
      },
      {
        source: "/files/:path*",
        destination: "http://localhost:8080/files/:path*",
      },
      {
        source: "/callback",
        destination: "http://localhost:8080/callback",
      },
    ];
  },
};

export default nextConfig;
