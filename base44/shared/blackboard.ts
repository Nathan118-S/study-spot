// Shared Blackboard Learn 3-legged OAuth helpers used by the
// blackboardAuthUrl, blackboardCallback, and syncBlackboard functions.
import { secrets } from "base44:runtime";

function basicHeader() {
  const id = secrets.get("BLACKBOARD_CLIENT_ID");
  const secret = secrets.get("BLACKBOARD_CLIENT_SECRET");
  if (!id || !secret) throw new Error("Blackboard credentials not configured");
  return "Basic " + btoa(`${id}:${secret}`);
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
  const url = `${instanceUrl}/learn/api/public/v1/oauth2/token?code=${encodeURIComponent(code)}&redirect_uri=${encodeURIComponent(redirectUri)}`;
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
  const url = `${instanceUrl}/learn/api/public/v1/oauth2/token?refresh_token=${encodeURIComponent(refreshToken)}&redirect_uri=${encodeURIComponent(redirectUri)}`;
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