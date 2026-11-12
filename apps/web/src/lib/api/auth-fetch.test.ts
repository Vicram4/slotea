import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import { server } from "@/test/msw";

import { authFetch, CSRF_HEADER } from "./auth-fetch";

const url = (path: string) => new URL(path, location.origin).toString();

describe("authFetch", () => {
  it("fetches the CSRF cookie once and sends it as a header on mutating requests", async () => {
    let csrfCalls = 0;
    const seenHeaders: (string | null)[] = [];
    server.use(
      http.get("/api/v1/auth/csrf", () => {
        csrfCalls++;
        document.cookie = "XSRF-TOKEN=token-123; path=/";
        return new HttpResponse(null, { status: 204 });
      }),
      http.patch("/api/v1/business", ({ request }) => {
        seenHeaders.push(request.headers.get(CSRF_HEADER));
        return HttpResponse.json({});
      }),
    );

    await authFetch(new Request(url("/api/v1/business"), { method: "PATCH" }));
    await authFetch(new Request(url("/api/v1/business"), { method: "PATCH" }));

    expect(seenHeaders).toEqual(["token-123", "token-123"]);
    expect(csrfCalls).toBe(1);
  });

  it("does not add the CSRF header to GET requests", async () => {
    let header: string | null = "unset";
    server.use(
      http.get("/api/v1/me", ({ request }) => {
        header = request.headers.get(CSRF_HEADER);
        return HttpResponse.json({});
      }),
    );

    await authFetch(new Request(url("/api/v1/me")));

    expect(header).toBeNull();
  });

  it("refreshes the session on 401 and retries the request once", async () => {
    document.cookie = "XSRF-TOKEN=t; path=/";
    let sessionValid = false;
    server.use(
      http.post("/api/v1/auth/refresh", () => {
        sessionValid = true;
        return HttpResponse.json({});
      }),
      http.get("/api/v1/me", () =>
        sessionValid ? HttpResponse.json({ ok: true }) : new HttpResponse(null, { status: 401 }),
      ),
    );

    const response = await authFetch(new Request(url("/api/v1/me")));

    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ ok: true });
  });

  it("shares a single refresh between concurrent 401 responses", async () => {
    document.cookie = "XSRF-TOKEN=t; path=/";
    let refreshCalls = 0;
    let sessionValid = false;
    server.use(
      http.post("/api/v1/auth/refresh", async () => {
        refreshCalls++;
        await new Promise((resolve) => setTimeout(resolve, 20));
        sessionValid = true;
        return HttpResponse.json({});
      }),
      http.get("/api/v1/business", () =>
        sessionValid ? HttpResponse.json({}) : new HttpResponse(null, { status: 401 }),
      ),
    );

    const responses = await Promise.all(
      Array.from({ length: 3 }, () => authFetch(new Request(url("/api/v1/business")))),
    );

    expect(responses.map((r) => r.status)).toEqual([200, 200, 200]);
    expect(refreshCalls).toBe(1);
  });

  it("returns the original 401 when the session cannot be refreshed", async () => {
    document.cookie = "XSRF-TOKEN=t; path=/";
    server.use(
      http.post("/api/v1/auth/refresh", () => new HttpResponse(null, { status: 401 })),
      http.get("/api/v1/me", () => new HttpResponse(null, { status: 401 })),
    );

    const response = await authFetch(new Request(url("/api/v1/me")));

    expect(response.status).toBe(401);
  });

  it("never retries the auth endpoints themselves", async () => {
    document.cookie = "XSRF-TOKEN=t; path=/";
    let refreshCalls = 0;
    server.use(
      http.post("/api/v1/auth/refresh", () => {
        refreshCalls++;
        return HttpResponse.json({});
      }),
      http.post("/api/v1/auth/login", () => HttpResponse.json({ detail: "Email o contraseña incorrectos." }, { status: 401 })),
    );

    const response = await authFetch(new Request(url("/api/v1/auth/login"), { method: "POST" }));

    expect(response.status).toBe(401);
    expect(refreshCalls).toBe(0);
  });
});
