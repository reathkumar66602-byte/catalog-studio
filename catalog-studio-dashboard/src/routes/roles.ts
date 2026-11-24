import { detectLocale, translate, type LocaleCode } from "../i18n/catalog";

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

export function roleLabel(role?: string | null, locale?: LocaleCode) {
  const loc = locale ?? detectLocale();
  if (role === "SUPERADMIN") return translate(loc, "role.superAdmin");
  if (role === "ADMIN") return translate(loc, "role.admin");
  return translate(loc, "role.user");
}
