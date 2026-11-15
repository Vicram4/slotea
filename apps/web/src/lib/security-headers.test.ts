import { describe, expect, it } from "vitest";

import { contentSecurityPolicy, securityHeaders } from "./security-headers";

const directive = (policy: string, name: string) =>
  policy
    .split("; ")
    .find((d) => d.startsWith(`${name} `) || d === name)
    ?.split(" ")
    .slice(1);

describe("security headers", () => {
  it("only allows our own origin for scripts, connections and fonts in production", () => {
    const policy = contentSecurityPolicy({ dev: false });

    expect(directive(policy, "script-src")).toEqual(["'self'", "'unsafe-inline'"]);
    expect(directive(policy, "connect-src")).toEqual(["'self'"]);
    expect(directive(policy, "font-src")).toEqual(["'self'"]);
    expect(directive(policy, "frame-ancestors")).toEqual(["'none'"]);
    expect(directive(policy, "object-src")).toEqual(["'none'"]);
    expect(directive(policy, "upgrade-insecure-requests")).toEqual([]);
    expect(policy).not.toContain("unsafe-eval");
  });

  it("relaxes only what hot reloading needs in development", () => {
    const policy = contentSecurityPolicy({ dev: true });

    expect(directive(policy, "script-src")).toContain("'unsafe-eval'");
    expect(directive(policy, "connect-src")).toEqual(["'self'", "ws:"]);
    expect(policy).not.toContain("upgrade-insecure-requests");
  });

  it("sends the usual hardening headers", () => {
    const headers = Object.fromEntries(securityHeaders({ dev: false }).map(({ key, value }) => [key, value]));

    expect(headers).toMatchObject({
      "X-Content-Type-Options": "nosniff",
      "X-Frame-Options": "DENY",
      "Referrer-Policy": "strict-origin-when-cross-origin",
    });
    expect(headers["Strict-Transport-Security"]).toMatch(/^max-age=63072000/);
    expect(headers["Permissions-Policy"]).toContain("camera=()");
  });
});
