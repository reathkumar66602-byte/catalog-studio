package com.catalogstudio.security;

public final class Roles {

    public static final String SUPERADMIN = "SUPERADMIN";
    public static final String ADMIN = "ADMIN";
    public static final String USER = "USER";
    public static final String SELLER = "SELLER";
    public static final String TEAM_MEMBER = "TEAM_MEMBER";

    private Roles() {}

    public static boolean isStaff(String role) {
        return SUPERADMIN.equals(role) || ADMIN.equals(role);
    }

    public static boolean isSuperAdmin(String role) {
        return SUPERADMIN.equals(role);
    }

    public static boolean isWorkspaceUser(String role) {
        return USER.equals(role) || SELLER.equals(role) || TEAM_MEMBER.equals(role);
    }

    public static String displayName(String role) {
        if (role == null) {
            return "User";
        }
        return switch (role) {
            case SUPERADMIN -> "Super admin";
            case ADMIN -> "Admin";
            default -> "User";
        };
    }
}
