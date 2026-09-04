package com.bingo.app.infrastructure.security;

import com.bingo.app.master.entity.User;
import com.bingo.app.master.enums.Role;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class UserPrincipal implements UserDetails {

    private final User user;

    public UserPrincipal(User user) {
        this.user = user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return String.valueOf(user.getTelegramId());
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.isActive();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isActive();
    }

    /**
     * Enforce account status at the request boundary. This project authenticates
     * through a stateless filter that bypasses DaoAuthenticationProvider, so the
     * {@link #isEnabled()}/{@link #isAccountNonLocked()} overrides are never consulted.
     * A suspended/disabled admin still carries ROLE_ADMIN authority, so we must block
     * them explicitly here. Super-admins are never locked out; players are not blocked
     * at this layer (their freeze is enforced by ending the agent's games).
     */
    public static boolean isActiveAndEnabled(User user) {
        if (user == null) {
            return false;
        }
        if (user.getRole() == Role.SUPER_ADMIN) {
            return true;
        }
        return user.isActive();
    }
}