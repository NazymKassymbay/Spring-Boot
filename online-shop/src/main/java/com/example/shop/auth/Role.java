package com.example.shop.auth;

/**
 * Practice 7: the roles of the shop (lecture week 12, slide 12). Roles say who you are.
 * Spring Security sees each one as the authority {@code ROLE_<name>}, which {@code hasRole("ADMIN")} checks.
 */
public enum Role {

    /** A shopper: may place orders. */
    CUSTOMER,

    /** Shop staff: manages the catalogue and every order. */
    ADMIN;

    /** The Spring Security authority of this role, e.g. {@code ROLE_ADMIN}. */
    public String authority() {
        return "ROLE_" + name();
    }
}
