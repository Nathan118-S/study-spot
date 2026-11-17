import React from "react";
import logo from "@/assets/icons/apple-touch-icon.png";

export default function AuthLayout({ title, subtitle, footer, children }) {
  return (
    <div className="min-h-screen flex items-center justify-center px-4 py-10">
      <div className="w-full max-w-md">
        <div className="text-center mb-10">
          <img
            src={logo}
            alt="Study Spot"
            width={72}
            height={72}
            className="mx-auto mb-4 h-[72px] w-[72px] rounded-[22%] shadow-md ring-1 ring-black/5"
          />
          <h1 className="text-3xl font-bold tracking-tight text-foreground">{title}</h1>
          {subtitle && <p className="text-muted-foreground mt-2">{subtitle}</p>}
        </div>
        <div className="bg-card rounded-2xl shadow-sm border border-white/40 dark:border-white/10 p-8">
          {children}
        </div>
        {footer && (
          <p className="text-center text-sm text-muted-foreground mt-6">{footer}</p>
        )}
      </div>
    </div>
  );
}
