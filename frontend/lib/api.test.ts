import { describe, expect, it } from "vitest";
import { readCookie, toApiError } from "./api";

describe("readCookie", () => {
  it("finds a cookie among others", () => {
    expect(readCookie("a=1; XSRF-TOKEN=abc-123; b=2", "XSRF-TOKEN")).toBe("abc-123");
  });

  it("returns null when the cookie is missing or the string is empty", () => {
    expect(readCookie("a=1; b=2", "XSRF-TOKEN")).toBeNull();
    expect(readCookie("", "XSRF-TOKEN")).toBeNull();
  });

  it("does not match a cookie whose name only ends with the wanted name", () => {
    expect(readCookie("MY-XSRF-TOKEN=nope", "XSRF-TOKEN")).toBeNull();
  });

  it("decodes encoded values and keeps '=' inside the value", () => {
    expect(readCookie("t=a%20b==", "t")).toBe("a b==");
  });
});

describe("toApiError", () => {
  it("keeps the backend's message and field messages", () => {
    const body = { error: "Email already registered", fields: { email: "already used" } };
    expect(toApiError(409, body)).toEqual({
      status: 409,
      error: "Email already registered",
      fields: { email: "already used" },
    });
  });

  it("gives an empty fields object when the reply has none", () => {
    expect(toApiError(401, { error: "Wrong email or password" })).toEqual({
      status: 401,
      error: "Wrong email or password",
      fields: {},
    });
  });

  it("falls back to a generic message for a body that isn't the error shape", () => {
    expect(toApiError(500, null).error).toBe("Something went wrong (500)");
    expect(toApiError(502, "<html>Bad gateway</html>").error).toBe("Something went wrong (502)");
  });

  it("drops field messages that aren't text", () => {
    expect(toApiError(400, { error: "Invalid input", fields: { email: 42, password: "is required" } }).fields).toEqual({
      password: "is required",
    });
  });

  it("explains status 0 as the server being unreachable", () => {
    expect(toApiError(0, null)).toEqual({ status: 0, error: "Can't reach the server", fields: {} });
  });
});
