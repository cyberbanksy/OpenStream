import { defineConfig, type Plugin } from "vite";
import react from "@vitejs/plugin-react";
import {
  createDevSessionBoundary,
  randomSessionToken,
  rewriteGatewayRequestTarget,
} from "./server/dev-session.mjs";
import { createGatewayServer } from "./server/openstream-gateway.mjs";

const gatewayHost = "127.0.0.1";
const gatewayPort = Number(process.env.OPENSTREAM_GATEWAY_PORT ?? 8787);
const documentSecurityHeaders = {
  "Content-Security-Policy": "frame-ancestors 'none'",
  "X-Frame-Options": "DENY",
};
const gatewayRuntime = globalThis as typeof globalThis & {
  __openStreamDevGateway?: ReturnType<typeof createGatewayServer>;
};

function closeGateway(server: ReturnType<typeof createGatewayServer>) {
  if (!server.listening) return Promise.resolve();
  return new Promise<void>((resolve, reject) => {
    server.close((error) => error ? reject(error) : resolve());
  });
}

function secureGatewayPlugin(sessionToken: string, boundary: ReturnType<typeof createDevSessionBoundary>): Plugin {
  return {
    name: "openstream-secure-gateway",
    async configureServer(server) {
      server.middlewares.use(boundary.middleware);
      if (gatewayRuntime.__openStreamDevGateway) {
        await closeGateway(gatewayRuntime.__openStreamDevGateway);
      }
      const gateway = createGatewayServer({ sessionToken });
      await new Promise<void>((resolve, reject) => {
        gateway.once("error", reject);
        gateway.listen(gatewayPort, gatewayHost, resolve);
      });
      gatewayRuntime.__openStreamDevGateway = gateway;
      server.httpServer?.once("close", () => {
        if (gatewayRuntime.__openStreamDevGateway !== gateway) return;
        gatewayRuntime.__openStreamDevGateway = undefined;
        void closeGateway(gateway);
      });

      const logSecureLaunch = () => {
        const address = server.httpServer?.address();
        const boundOrigin = address && typeof address !== "string"
          ? `http://${address.address.includes(":") ? `[${address.address}]` : address.address}:${address.port}/`
          : undefined;
        const origin = boundOrigin ?? server.resolvedUrls?.local[0] ?? "http://127.0.0.1:5173/";
        server.config.logger.info(`OpenStream secure launch: ${new URL(boundary.launchPath, origin)}`);
      };
      if (server.httpServer?.listening) logSecureLaunch();
      else server.httpServer?.once("listening", logSecureLaunch);
    },
  };
}

export default defineConfig(({ command }) => {
  const useLocalGateway = command === "serve" && process.env.VITEST !== "true";
  const gatewaySessionToken = randomSessionToken();
  const browserBoundary = createDevSessionBoundary();

  return {
    ...(process.env.OPENSTREAM_VITE_CACHE_DIR ? { cacheDir: process.env.OPENSTREAM_VITE_CACHE_DIR } : {}),
    plugins: [react(), ...(useLocalGateway ? [secureGatewayPlugin(gatewaySessionToken, browserBoundary)] : [])],
    build: {
      chunkSizeWarningLimit: 520,
      rollupOptions: {
        output: {
          manualChunks: {
            three: ["three"],
          },
        },
      },
    },
    server: {
      host: "127.0.0.1",
      headers: documentSecurityHeaders,
      proxy: {
        "/gateway": {
          target: `http://${gatewayHost}:${gatewayPort}`,
          changeOrigin: true,
          headers: {
            "X-OpenStream-Proxy": "loopback",
            "X-OpenStream-Session": gatewaySessionToken,
          },
          rewrite: rewriteGatewayRequestTarget,
        },
      },
    },
    preview: {
      headers: documentSecurityHeaders,
    },
  };
});
