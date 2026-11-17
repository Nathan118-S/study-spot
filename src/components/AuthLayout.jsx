import React from "react";
import { Link } from "react-router-dom";
import logo from "@/assets/icons/apple-touch-icon.png";

// Sign-in / sign-up layout: logo and name above a single centered card, with
// the card's own footer link and small site links underneath.
export default function AuthLayout({ title, subtitle, footer, children }) {
  return (
    <div className="min-h-screen flex flex-col items-center px-4 py-10">
      <div className="flex-1 flex flex-col items-center justify-center w-full">
        <div className="flex flex-col items-center mb-7">
          <img
            src={logo}
            alt=""
            width={64}
            height={64}
            className="h-16 w-16 rounded-[22%] shadow-md ring-1 ring-black/5"
          />
          <p className="mt-3 text-2xl font-bold tracking-tight text-[hsl(var(--navy))]">Study Spot</p>
        </div>

        <div className="w-full max-w-[420px] rounded-2xl bg-card shadow-[0_8px_30px_rgba(31,43,71,0.08)] ring-1 ring-black/5 dark:ring-white/10 px-6 py-8 sm:px-8">
          <div className="text-center mb-7">
            <h1 className="text-[28px] leading-tight font-bold tracking-tight text-foreground">{title}</h1>
            {subtitle && <p className="text-muted-foreground mt-1.5 text-[15px]">{subtitle}</p>}
          </div>
          {children}
          {footer && <p className="text-center text-sm text-muted-foreground mt-7">{footer}</p>}
        </div>
      </div>

      <nav className="mt-10 flex items-center gap-6 text-sm text-muted-foreground">
        <Link to="/" className="hover:text-foreground">Home</Link>
        <Link to="/privacy" className="hover:text-foreground">Privacy</Link>
      </nav>
      <p className="mt-3 text-xs text-muted-foreground">© {new Date().getFullYear()} Study Spot</p>
    </div>
  );
}
