package com.scu.sso.auth.repository;

import com.scu.sso.auth.domain.AppUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 用户读取。写入由账号管理模块（F07）负责。
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);
}
