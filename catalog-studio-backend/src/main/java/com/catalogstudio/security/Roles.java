package com.catalogstudio.security;

import com.catalogstudio.user.entity.User;

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

    public static boolean isStaff(User user) {
        return user != null && user.isStaff();
    }

    public static boolean isSuperAdmin(String role) {
        return SUPERADMIN.equals(role);
    }

    public static boolean isSuperAdmin(User user) {
        return user != null && user.isSuperAdmin();
    }

    public static boolean isWorkspaceUser(User.Role role) {
        return role == User.Role.USER || role == User.Role.SELLER || role == User.Role.TEAM_MEMBER;
    }

    public static String displayName(User.Role role) {
        if (role == null) {
            return "User";
        }
        return switch (role) {
            case SUPERADMIN -> "Super admin";
            case ADMIN -> "Admin";
            case USER, SELLER, TEAM_MEMBER -> "User";
        };
    }
}
