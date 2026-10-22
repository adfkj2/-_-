package com.scu.sso.auth.session;

import com.scu.sso.auth.domain.SecurityPolicy;
import com.scu.sso.auth.repository.SecurityPolicyRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 读取当前认证策略。策略编辑属于 F17，本模块只读。
 */
@Service
@RequiredArgsConstructor
public class PolicyService {

    private final SecurityPolicyRepository repository;

    /**
     * 读取单例策略；缺失时返回默认值，保证登录流程不被配置问题阻断。
     */
    public SecurityPolicy current() {
        return repository.findById(1L).orElseGet(() -> {
            SecurityPolicy policy = new SecurityPolicy();
            policy.setId(1L);
            policy.setIdleTimeoutMinutes(30);
            policy.setAbsoluteTimeoutHours(8);
            policy.setFailureWindowMinutes(15);
            policy.setFailureThreshold(5);
            policy.setLockMinutes(15);
            policy.setUpdatedAt(Instant.now());
            policy.setVersion(0);
            return policy;
        });
    }
}
