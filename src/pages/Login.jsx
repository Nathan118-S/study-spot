import React, { useState, useEffect } from "react";
import { Link } from "react-router-dom";
import { api } from "@/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { LogIn, AtSign, Lock, Loader2, KeyRound } from "lucide-react";
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
      icon={LogIn}
      title="Welcome back"
      subtitle={isIosHandoff() ? "Log in to continue in the Study Spot app" : "Log in to your account"}
      footer={
        <>
          Don't have an account?{" "}
          <Link
            to={"/register" + (returnTo !== "/" ? "?returnTo=" + encodeURIComponent(returnTo) : "")}
            className="text-primary font-medium hover:underline"
          >
            Create one
          </Link>
        </>
      }
    >
      {api.auth.passkeySupported() && (
        <>
          <Button
            variant="outline"
            className="w-full h-12 text-sm font-medium mb-6"
            onClick={handlePasskey}
            disabled={passkeyLoading}
          >
            {passkeyLoading ? <Loader2 className="w-5 h-5 animate-spin" /> : <KeyRound className="w-5 h-5" />}
            Sign in with a passkey
          </Button>

          <div className="relative mb-6">
            <div className="absolute inset-0 flex items-center">
              <div className="w-full border-t border-border" />
            </div>
            <div className="relative flex justify-center text-xs uppercase">
              <span className="bg-card px-3 text-muted-foreground">or</span>
            </div>
          </div>
        </>
      )}

      {disabledReason && (
        <div className="mb-4 p-3 rounded-lg bg-red-500/10 border border-red-500/40 text-red-700 dark:text-red-300 text-sm">
          <p className="font-medium">Your account has been disabled.</p>
          <p className="mt-0.5">{disabledReason}</p>
        </div>
      )}

      {error && (
        <div className="mb-4 p-3 rounded-lg bg-destructive/10 text-destructive text-sm">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="username">Username</Label>
          <div className="relative">
            <AtSign className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" aria-hidden="true" />
            <Input
              id="username"
              type="text"
              autoComplete="username"
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
              autoFocus
              placeholder="your-username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              className="pl-10 h-12"
              required
            />
          </div>
        </div>
        <div className="space-y-2">
          <Label htmlFor="password">Password</Label>
          <div className="relative">
            <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" aria-hidden="true" />
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="pl-10 h-12"
              required
            />
          </div>
        </div>
        <Button type="submit" className="w-full h-12 font-medium" disabled={loading}>
          {loading ? (
            <>
              <Loader2 className="w-4 h-4 mr-2 animate-spin" />
              Logging in...
            </>
          ) : (
            "Log in"
          )}
        </Button>
        <p className="text-center text-xs text-muted-foreground">
          Forgot your password? Ask your Study Spot admin to reset it.
        </p>
      </form>
    </AuthLayout>
  );
}