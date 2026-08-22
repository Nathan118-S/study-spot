// Returns the best available display name for a user, used to personalize emails.
export function displayName(user: any): string {
  if (!user) return 'there';
  const n = (user.name || user.data?.name || user.full_name || '').toString().trim();
  return n || 'there';
}