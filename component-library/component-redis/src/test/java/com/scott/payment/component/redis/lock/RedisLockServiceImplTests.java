package com.scott.payment.component.redis.lock;

import com.scott.payment.component.redis.lock.impl.RedisLockServiceImpl;
import com.scott.payment.component.redis.script.PaymentRedisScripts;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisLockServiceImplTests
 * @date : 2026-07-30 09:48
 * @email : scott_x@163.com
 * @description : 验证 Redis 锁释放始终使用集中注册的持有者 token 校验脚本
 * @status : create
 */
class RedisLockServiceImplTests {

    /**
     * 解锁必须使用版本化安全释放脚本并传入原始锁 Key 与持有者 token。
     */
    @Test
    void shouldReleaseLockWithRegisteredTokenCheckScript() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        RedisLockServiceImpl lockService = new RedisLockServiceImpl(stringRedisTemplate);

        lockService.unlock("payment:operation:lock", "owner-token");

        verify(stringRedisTemplate).execute(
                same(PaymentRedisScripts.lockReleaseV1()),
                eq(List.of("payment:operation:lock")),
                eq("owner-token")
        );
    }
}
