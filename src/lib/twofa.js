const VALID = ['totp', 'email', 'passkey'];

// Returns the enabled 2FA methods for the current user, accounting for the
// legacy single-method field and the new multi-method array.
export function getMethods(user) {
  if (!user) return [];
  let methods = [];
  if (Array.isArray(user.twofa_methods)) methods = user.twofa_methods;
  else if (Array.isArray(user?.data?.twofa_methods)) methods = user.data.twofa_methods;
  else if (user.twofa_method) methods = [user.twofa_method];
  else if (user.twofa_enabled) methods = ['totp'];
  return methods.filter((m) => VALID.includes(m));
}

export const METHOD_LABEL = { totp: 'Authenticator app', email: 'Email', passkey: 'Passkey' };