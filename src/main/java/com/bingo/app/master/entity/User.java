package com.bingo.app.master.entity;

import com.bingo.app.master.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Master user record for all roles.
 * For {@link Role#ADMIN}: {@link #adminApproved} and {@link #businessName} hold onboarding metadata
 * (formerly a separate admin_profiles/agents table).
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_telegram", columnList = "telegram_id"),
        @Index(name = "idx_users_admin", columnList = "admin_user_id"),
        @Index(name = "idx_users_parent", columnList = "parent_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", unique = true, nullable = false)
    private Long telegramId;

    @Column(name = "username")
    private String username;
    @Column(name = "first_name")
    private String firstName;
    @Column(name = "last_name")
    private String lastName;

    @Enumerated(EnumType.STRING)
    private Role role;

    /** Owning admin's {@code users.id} — set for {@link Role#PLAYER} only. */
    @Column(name = "admin_user_id")
    private Long adminUserId;
    @Column(name = "parent_id")
    private Long parentId;

    /** Display name for bingo operations — meaningful for {@link Role#ADMIN}. */
    @Column(name = "business_name")
    private String businessName;

    /** Agent's deposit account info shown to players (e.g. TeleBirr number). */
    @Column(name = "deposit_account_info", columnDefinition = "TEXT")
    private String depositAccountInfo;

    /** Super-admin onboarding approval — meaningful for {@link Role#ADMIN}. */
    @Builder.Default
    @Column(name = "admin_approved")
    private boolean adminApproved = false;

    @Builder.Default
    @Column(name = "balance")
    private BigDecimal balance = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "frozen_balance")
    private BigDecimal frozenBalance = BigDecimal.ZERO;

    @Builder.Default
    private boolean active = true;

    @Builder.Default
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() { return ""; }

    @Override
    public String getUsername() { return String.valueOf(telegramId); }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return active; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return active; }
}
