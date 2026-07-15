package com.wallet.config;

import com.wallet.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Адаптер нашей сущности User → Spring Security UserDetails.
 * Spring работает только с UserDetails, поэтому оборачиваем.
 *
 * username здесь = userId.toString() — потому что в JWT мы кладём userId в sub.
 * Когда фильтр прочитает токен и зовёт loadUserByUsername(userId), мы найдём User по id.
 */
@RequiredArgsConstructor
public class AppUserDetails implements UserDetails {

    private final User user;

    public UUID getUserId() {
        return user.getId();
    }

    public String getEmail() {
        return user.getEmail();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // Пока одна роль на всех. В будущем — поле role в User.
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getId().toString();
    }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }
}
