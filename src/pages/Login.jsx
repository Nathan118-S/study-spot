import React, { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import { api } from "@/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Loader2, Fingerprint } from "lucide-react";
import AuthLayout from "@/components/AuthLayout";
import { safeReturnTo } from "@/lib/authReturnTo";
import { completeIosHandoff, isIosHandoff } from "@/lib/iosHandoff";

export default function Login() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const [passkeyLoading, setPasskeyLoading] = useState(false);
  // Post-login destination (e.g. the MCP OAuth consent page sends users here
  // with returnTo so the grant flow can resume). Same-origin paths only.
  const returnTo = safeReturnTo();
  const [disabledReason, setDisabledReason] = useState("");
  const [showForgot, setShowForgot] = useState(false);

  useEffect(() => {
    const stored = sessionStorage.getItem("cf-disabled-reason");
    if (stored !== null) {
      setDisabledReason(stored || "Your account has been disabled.");
      sessionStorage.removeItem("cf-disabled-reason");
    }
  }, []);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    setDisabledReason("");
    setLoading(true);
    try {
      const result = await api.auth.loginWithPassword(username.trim(), password);
      if (completeIosHandoff(result?.access_token)) return;
      window.location.href = returnTo;
    } catch (err) {
      setError(err.message || "Invalid username or password");
    } finally {
      setLoading(false);
    }
  };

  const handlePasskey = async () => {
    setError("");
    setDisabledReason("");
    setPasskeyLoading(true);
    try {
      const result = await api.auth.loginWithPasskey(username.trim());
      if (completeIosHandoff(result?.access_token)) return;
      window.location.href = returnTo;
    } catch (err) {
      // NotAllowedError = the user cancelled or no passkey was chosen.
      if (err?.name !== "NotAllowedError") setError(err.message || "Passkey sign-in failed");
    } finally {
      setPasskeyLoading(false);
    }
  };

  return (
    <AuthLayout
      title="Welcome back"
      subtitle={isIosHandoff() ? "Log in to continue in the Study Spot app" : "Log in to your Study Spot account"}
      footer={
        <>
          No account yet?{" "}
          <Link
            to={"/register" + (returnTo !== "/" ? "?returnTo=" + encodeURIComponent(returnTo) : "")}
            className="ml-1 text-primary font-semibold hover:underline"
          >
            Sign up
          </Link>
        </>
      }
    >
      {disabledReason && (
        <div className="mb-5 p-3 rounded-lg bg-red-500/10 border border-red-500/40 text-red-700 dark:text-red-300 text-sm">
          <p className="font-medium">Your account has been disabled.</p>
          <p className="mt-0.5">{disabledReason}</p>
        </div>
      )}

      {error && (
        <div className="mb-5 p-3 rounded-lg bg-destructive/10 text-destructive text-sm">
          {error}
        </div>
      )}

      {api.auth.passkeySupported() && (
        <>
          <Button
            type="button"
            className="w-full h-12 rounded-full text-base font-semibold shadow-sm"
            onClick={handlePasskey}
            disabled={passkeyLoading}
          >
            {passkeyLoading ? <Loader2 className="w-5 h-5 animate-spin" /> : <Fingerprint className="w-5 h-5" />}
            Sign in with a passkey
          </Button>
          <p className="mt-3 text-center text-[13px] leading-snug text-muted-foreground">
            Face ID, Touch ID, Windows Hello, your phone or a security key. No password needed.
          </p>

          <div className="relative my-6">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full border-t border-border" />
            </div>
            <div className="relative flex justify-center text-[13px]">
              <span className="bg-card px-3 text-muted-foreground">or use your password</span>
            </div>
          </div>
        </>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="username" className="text-[15px] font-medium">Username</Label>
          <Input
            id="username"
            type="text"
            autoComplete="username webauthn"
            autoCapitalize="none"
            autoCorrect="off"
            spellCheck={false}
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            className="h-12 rounded-xl"
            required
          />
        </div>
        <div className="space-y-1.5">
          <div className="flex items-baseline justify-between">
            <Label htmlFor="password" className="text-[15px] font-medium">Password</Label>
            <button
              type="button"
              onClick={() => setShowForgot((v) => !v)}
              className="text-[13px] font-medium text-primary hover:underline"
            >
              Forgot password?
            </button>
          </div>
          <Input
            id="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="h-12 rounded-xl"
            required
          />
          {showForgot && (
            <p className="text-[13px] text-muted-foreground">
              Ask your Study Spot admin to reset it. They&apos;ll give you a temporary password.
            </p>
          )}
        </div>
        <Button
          type="submit"
          variant="ghost"
          className="w-full h-12 rounded-full border border-border bg-card text-base font-semibold text-foreground hover:bg-accent"
          disabled={loading}
        >
          {loading ? (
            <>
              <Loader2 className="w-4 h-4 mr-2 animate-spin" />
              Logging in...
            </>
          ) : (
            "Log in"
          )}
        </Button>
      </form>
    </AuthLayout>
  );
}
