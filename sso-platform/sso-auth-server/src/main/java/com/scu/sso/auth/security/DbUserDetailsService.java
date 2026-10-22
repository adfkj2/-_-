package com.scu.sso.auth.security;

import com.scu.sso.auth.domain.AppUser;
import com.scu.sso.auth.repository.AppUserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 从 sys_user 加载登录主体。账号状态与锁定直接映射为 Spring Security 的可用性标志。
 */
@Service
@RequiredArgsConstructor
public class DbUserDetailsService implements UserDetailsService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final AppUserRepository userRepository;
    private final UserRoleReader roleReader;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser user = userRepository.findByUsername(username.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("账号不存在"));
        boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());
        boolean enabled = STATUS_ACTIVE.equals(user.getStatus());
        List<String> roles = roleReader.findRoleCodes(user.getId());
        String[] authorities = roles.stream().map(role -> "ROLE_" + role).toArray(String[]::new);
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .accountLocked(locked)
                .disabled(!enabled)
                .build();
    }
}
