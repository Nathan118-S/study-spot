const VALID = ['totp', 'email', 'passkey'];

// Returns the list of enabled 2FA methods for a user row.
export function getMethods(user) {
  if (!user) return [];
  let methods = [];
  if (Array.isArray(user.twofa_methods)) methods = user.twofa_methods;
  else if (user.twofa_method) methods = [user.twofa_method];
  else if (user.twofa_enabled) methods = ['totp'];
  return methods.filter((m) => VALID.includes(m));
}

export function withMethod(methods, method) {
  return [...new Set([...methods, method])];
}

export function withoutMethod(methods, method) {
  return methods.filter((m) => m !== method);
}

export function displayName(user) {
  if (!user) return 'there';
  const n = (user.name || user.full_name || '').toString().trim();
  return n || 'there';
}
