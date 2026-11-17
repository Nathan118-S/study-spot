import React, { useState } from "react";
import { Link } from "react-router-dom";
import { api } from "@/api/client";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Loader2 } from "lucide-react";
import AuthLayout from "@/components/AuthLayout";
import { safeReturnTo } from "@/lib/authReturnTo";
import { completeIosHandoff } from "@/lib/iosHandoff";

export default function Register() {
  const [name, setName] = useState("");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError("");
    if (password !== confirmPassword) {
      setError("Passwords do not match");
      return;
    }
    setLoading(true);
    try {
      // No verification step: the new account is signed in right away.
      const result = await api.auth.register({ username: username.trim(), password });
      if (name.trim()) {
        try {
          await api.auth.updateMe({ name: name.trim() });
        } catch {}
      }
      if (completeIosHandoff(result?.access_token)) return;
      window.location.href = safeReturnTo();
    } catch (err) {
      setError(err.message || "Registration failed");
      setLoading(false);
    }
  };

  return (
    <AuthLayout
      title="Create your account"
      subtitle="Sign up to start planning with Study Spot"
      footer={
        <>
          Already have an account?{" "}
          <Link
            to={"/login" + (safeReturnTo() !== "/" ? "?returnTo=" + encodeURIComponent(safeReturnTo()) : "")}
            className="ml-1 text-primary font-semibold hover:underline"
          >
            Log in
          </Link>
        </>
      }
    >
      {error && (
        <div className="mb-4 p-3 rounded-lg bg-destructive/10 text-destructive text-sm">
          {error}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="space-y-1.5">
          <Label htmlFor="name" className="text-[15px] font-medium">Name</Label>
          <Input
            id="name"
            type="text"
            autoComplete="name"
            placeholder="Your name"
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="h-12 rounded-xl"
            required
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="username" className="text-[15px] font-medium">Username</Label>
          <Input
            id="username"
            type="text"
            autoComplete="username"
            autoCapitalize="none"
            autoCorrect="off"
            spellCheck={false}
            placeholder="your-username"
            value={username}
            onChange={(e) => setUsername(e.target.value.toLowerCase())}
            className="h-12 rounded-xl"
            minLength={3}
            maxLength={30}
            required
          />
          <p className="text-xs text-muted-foreground">
            3–30 characters: letters, numbers, dots, dashes and underscores.
          </p>
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="password" className="text-[15px] font-medium">Password</Label>
          <Input
            id="password"
            type="password"
            autoComplete="new-password"
                        value={password}
            onChange={(e) => setPassword(e.target.value)}
            className="h-12 rounded-xl"
            required
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="confirm" className="text-[15px] font-medium">Confirm password</Label>
          <Input
            id="confirm"
            type="password"
            autoComplete="new-password"
                        value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
            className="h-12 rounded-xl"
            required
          />
        </div>
        <Button type="submit" className="w-full h-12 rounded-full text-base font-semibold shadow-sm" disabled={loading}>
          {loading ? (
            <>
              <Loader2 className="w-4 h-4 mr-2 animate-spin" />
              Creating account...
            </>
          ) : (
            "Create account"
          )}
        </Button>
      </form>
    </AuthLayout>
  );
}