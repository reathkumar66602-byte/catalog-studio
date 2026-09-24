export function isStaff(role?: string | null) {
  return role === "ADMIN" || role === "SUPERADMIN";
}

export function isSuperAdmin(role?: string | null) {
  return role === "SUPERADMIN";
}

export function isWorkspaceUser(role?: string | null) {
  return role === "USER" || role === "SELLER" || role === "TEAM_MEMBER" || !role;
}

export function hasFeature(
  user: { role?: string; enabledFeatures?: string[] } | null | undefined,
  feature: string,
) {
  if (!user) return false;
  if (isStaff(user.role)) return true;
  if (!user.enabledFeatures) return true;
  return user.enabledFeatures.includes(feature);
}

export function roleLabel(role?: string | null) {
  if (role === "SUPERADMIN") return "Super admin";
  if (role === "ADMIN") return "Admin";
  return "User";
}
