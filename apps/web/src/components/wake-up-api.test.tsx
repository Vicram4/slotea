import { render } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { WakeUpApi } from "./wake-up-api";

describe("WakeUpApi", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("pings the API once and renders nothing", () => {
    const fetchMock = vi.fn().mockResolvedValue(Response.json({ status: "ok" }));
    vi.stubGlobal("fetch", fetchMock);

    const { container } = render(<WakeUpApi />);

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/ping", expect.anything());
    expect(container).toBeEmptyDOMElement();
  });

  it("stays silent when the API is down", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("Failed to fetch")));
    const errors = vi.spyOn(console, "error");

    render(<WakeUpApi />);
    await Promise.resolve();

    expect(errors).not.toHaveBeenCalled();
  });
});
