package com.catalogstudio.user.repository;

import com.catalogstudio.user.entity.User;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class UserSpecifications {

    private UserSpecifications() {}

    public static Specification<User> listing(String query, Collection<User.Role> roles) {
        return (root, ignored, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (roles != null && !roles.isEmpty()) {
                predicates.add(root.get("role").in(roles));
            }
            if (StringUtils.hasText(query)) {
                String like = "%" + query.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("username"), "")), like),
                        cb.like(cb.lower(cb.coalesce(root.get("mobile"), "")), like)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
