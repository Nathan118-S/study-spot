const VALID = ["totp", "email", "passkey"];

// Returns the list of enabled 2FA methods for a user, accounting for the
// legacy single-method field and the new multi-method array.
export function getMethods(user: any): string[] {
  if (!user) return [];
  let methods: string[] = [];
  if (Array.isArray(user.twofa_methods)) methods = user.twofa_methods;
  else if (Array.isArray(user.data?.twofa_methods)) methods = user.data.twofa_methods;
  else if (user.twofa_method) methods = [user.twofa_method];
  else if (user.twofa_enabled) methods = ["totp"];
  return methods.filter((m) => VALID.includes(m));
}

export function withMethod(methods: string[], method: string): string[] {
  return [...new Set([...methods, method])];
}

export function withoutMethod(methods: string[], method: string): string[] {
  return methods.filter((m) => m !== method);
}