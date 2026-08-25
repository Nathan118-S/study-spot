// Shared Blackboard Learn 3-legged OAuth helpers used by the
// blackboardAuthUrl, blackboardCallback, and syncBlackboard functions.
import { secrets } from "base44:runtime";

function basicHeader() {
  const id = secrets.get("BLACKBOARD_CLIENT_ID");
  const secret = secrets.get("BLACKBOARD_CLIENT_SECRET");
  if (!id || !secret) throw new Error("Blackboard credentials not configured");
  return "Basic " + btoa(`${id}:${secret}`);
}

// Validate a user-supplied Blackboard instance URL before any authenticated
// outbound request is made against it. Returns the normalized origin so a
// path or query string cannot be smuggled into credential-bearing requests.
export function validateInstanceUrl(instanceUrl) {
  let url;
  try {
    url = new URL(instanceUrl);
  } catch {
    throw new Error("Invalid Blackboard instance URL");
  }
  if (url.protocol !== "https:") {
    throw new Error("Blackboard instance URL must use https");
  }
  const host = url.hostname.toLowerCase();
  if (!host) throw new Error("Blackboard instance URL missing host");
  if (url.username || url.password || url.port) {
    throw new Error("Blackboard instance URL must not include credentials or a port");
  }
  // Reject raw IP addresses (SSRF vector).
  if (/^\d{1,3}(\.\d{1,3}){3}$/.test(host) || host.includes(":")) {
    throw new Error("Blackboard instance URL must be a domain name, not an IP address");
  }
  // Reject localhost / local-only / link-local hosts.
  if (host === "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) {
    throw new Error("Blackboard instance URL must be a public domain");
  }
  // Reject cloud metadata endpoints.
  if (host === "metadata.google.internal" || host === "169.254.169.254" || host.includes("metadata")) {
    throw new Error("Blackboard instance URL must not target a metadata service");
  }
  // Require a fully-qualified public domain (at least one dot).
  if (!host.includes(".")) {
    throw new Error("Blackboard instance URL must be a fully-qualified domain");
  }
  return url.origin;
}

export function buildAuthUrl(instanceUrl, clientId, redirectUri, state) {
  const params = new URLSearchParams({
    redirect_uri: redirectUri,
    response_type: "code",
    client_id: clientId,
    scope: "read offline",
    state,
  });
  return `${instanceUrl}/learn/api/public/v1/oauth2/authorizationcode?${params.toString()}`;
}

// Exchange an authorization code for an access token (and refresh token).
export async function exchangeCode(instanceUrl, code, redirectUri) {
  const base = validateInstanceUrl(instanceUrl);
  const url = `${base}/learn/api/public/v1/oauth2/token?code=${encodeURIComponent(code)}&redirect_uri=${encodeURIComponent(redirectUri)}`;
  const res = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: basicHeader(),
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: "grant_type=authorization_code",
  });
  if (!res.ok) throw new Error("Blackboard token exchange failed: " + await res.text());
  return await res.json();
}

// Refresh an expired access token using a refresh token.
export async function refreshAccessToken(instanceUrl, refreshToken, redirectUri) {
  const base = validateInstanceUrl(instanceUrl);
  const url = `${base}/learn/api/public/v1/oauth2/token?refresh_token=${encodeURIComponent(refreshToken)}&redirect_uri=${encodeURIComponent(redirectUri)}`;
  const res = await fetch(url, {
    method: "POST",
    headers: {
      Authorization: basicHeader(),
      "Content-Type": "application/x-www-form-urlencoded",
    },
    body: "grant_type=refresh_token",
  });
  if (!res.ok) throw new Error("Blackboard token refresh failed: " + await res.text());
  return await res.json();
}