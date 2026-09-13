// Empty string means "same origin" - used when Next proxies /api/* to the
// backend (see BACKEND_ORIGIN in next.config.ts). Unset falls back to the
// local backend for development.
const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export class ApiError extends Error {
  code: string;
  details: string[];
  status: number;

  constructor(status: number, code: string, message: string, details: string[] = []) {
    super(message);
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

const NETWORK_ERROR_MESSAGE =
  "Can't reach the server. Check that the backend is running, then try again.";

/**
 * `fetch` rejects only on a network-level failure - the server being down, DNS
 * failing, a refused CORS preflight. An HTTP error status still resolves. Left
 * alone, that rejection surfaces to the UI as the browser's raw
 * "TypeError: Failed to fetch", so translate it into an ApiError like any other
 * failure and give callers a single error type to render.
 */
async function fetchOrThrow(url: string, init: RequestInit): Promise<Response> {
  try {
    return await fetch(url, init);
  } catch {
    throw new ApiError(0, "NETWORK_ERROR", NETWORK_ERROR_MESSAGE);
  }
}

function readCookie(name: string): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.match(new RegExp("(^| )" + name + "=([^;]+)"));
  return match ? decodeURIComponent(match[2]) : null;
}

async function ensureCsrfCookie(): Promise<void> {
  if (readCookie("XSRF-TOKEN")) return;
  await fetchOrThrow(`${API_URL}/api/auth/csrf`, { credentials: "include" });
}

/** The `{ data }` / `{ error }` envelope every backend endpoint responds with. */
interface ApiEnvelope {
  data?: unknown;
  error?: { code?: string; message?: string; details?: string[] };
}

interface RequestOptions {
  method?: "GET" | "POST" | "PATCH" | "PUT" | "DELETE";
  body?: unknown;
  query?: Record<string, string | number | boolean | undefined | null>;
}

function buildQuery(query?: RequestOptions["query"]): string {
  if (!query) return "";
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== undefined && value !== null && value !== "") {
      params.set(key, String(value));
    }
  }
  const qs = params.toString();
  return qs ? `?${qs}` : "";
}

export async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? "GET";
  const isMutating = method !== "GET";

  if (isMutating) {
    await ensureCsrfCookie();
  }

  const headers: Record<string, string> = {};
  if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (isMutating) {
    const csrf = readCookie("XSRF-TOKEN");
    if (csrf) headers["X-XSRF-TOKEN"] = csrf;
  }

  const response = await fetchOrThrow(`${API_URL}${path}${buildQuery(options.query)}`, {
    method,
    credentials: "include",
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  let json: ApiEnvelope;
  try {
    json = (text ? JSON.parse(text) : {}) as ApiEnvelope;
  } catch {
    // Something that isn't our JSON envelope answered - a proxy error page, a
    // gateway timeout. Don't let the raw SyntaxError reach the UI.
    throw new ApiError(
      response.status,
      "INVALID_RESPONSE",
      "The server returned an unexpected response."
    );
  }

  if (!response.ok) {
    const err = json.error;
    throw new ApiError(
      response.status,
      err?.code ?? "UNKNOWN",
      err?.message ?? "Something went wrong.",
      err?.details ?? []
    );
  }

  return json.data as T;
}

/**
 * Multipart upload. Deliberately does NOT set Content-Type: the browser has to
 * generate it so it can append the multipart boundary, and setting it by hand
 * produces a body the server can't parse.
 */
export async function apiUpload<T>(path: string, form: FormData): Promise<T> {
  await ensureCsrfCookie();
  const headers: Record<string, string> = {};
  const csrf = readCookie("XSRF-TOKEN");
  if (csrf) headers["X-XSRF-TOKEN"] = csrf;

  const response = await fetchOrThrow(`${API_URL}${path}`, {
    method: "POST",
    credentials: "include",
    headers,
    body: form,
  });

  const text = await response.text();
  let json: ApiEnvelope;
  try {
    json = (text ? JSON.parse(text) : {}) as ApiEnvelope;
  } catch {
    throw new ApiError(response.status, "INVALID_RESPONSE", "The server returned an unexpected response.");
  }
  if (!response.ok) {
    const err = json.error;
    throw new ApiError(response.status, err?.code ?? "UNKNOWN", err?.message ?? "Upload failed.", err?.details ?? []);
  }
  return json.data as T;
}

export const api = {
  get: <T>(path: string, query?: RequestOptions["query"]) => apiFetch<T>(path, { method: "GET", query }),
  post: <T>(path: string, body?: unknown, query?: RequestOptions["query"]) =>
    apiFetch<T>(path, { method: "POST", body, query }),
  patch: <T>(path: string, body?: unknown, query?: RequestOptions["query"]) =>
    apiFetch<T>(path, { method: "PATCH", body, query }),
  delete: <T>(path: string) => apiFetch<T>(path, { method: "DELETE" }),
};
