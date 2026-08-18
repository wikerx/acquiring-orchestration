package com.scott.payment.component.redis.script;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentRedisScriptsTests
 * @date : 2026-07-30 09:41
 * @email : scott_x@163.com
 * @description : 验证组件 Lua 脚本能够按稳定版本从 classpath 加载并保持返回契约
 * @status : create
 */
@Slf4j
class PaymentRedisScriptsTests {

    /**
     * 全局 ID v1 脚本必须保留时间回拨保护、状态写入和列表返回契约。
     */
    @Test
    void shouldLoadVersionedGlobalIdSequenceScript() {
        log.info("测试全局 ID Lua 资源，关键输入: v1 脚本、List 返回契约");
        var script = PaymentRedisScripts.globalIdSequenceV1();

        assertThat(script.getResultType()).isEqualTo(List.class);
        assertThat(script.getScriptAsString())
                .contains("restoreFloorMillis")
                .contains("lastMillis > effectiveMillis")
                .contains("redis.call('HSET', stateKey");
        assertThat(script.getSha1()).matches("[0-9a-f]{40}");
        log.info("全局 ID Lua 资源测试完成，结果: 脚本可加载且 SHA1 格式有效");
    }

    /**
     * MQ 去重 v1 脚本必须用 Redis TIME 原子完成双桶清理、查重、容量校验、NX 写入和 Key 续期。
     */
    @Test
    void shouldLoadVersionedMqDedupAcquireScript() {
        log.info("测试 MQ 去重 Lua 资源，关键输入: 双桶 ZSet、Redis TIME、成员容量上限");
        var script = PaymentRedisScripts.mqDedupAcquireV1();

        assertThat(script.getResultType()).isEqualTo(Long.class);
        assertThat(script.getScriptAsString())
                .contains("redis.call('TIME')")
                .contains("ZREMRANGEBYSCORE")
                .contains("redis.call('TIME')")
                .contains("ZCARD', currentBucketKey")
                .contains("ZADD', currentBucketKey, 'NX'")
                .contains("PEXPIRE");
        assertThat(script.getSha1()).matches("[0-9a-f]{40}");
        log.info("MQ 去重 Lua 资源测试完成，结果: 原子双桶与容量保护语句均存在");
    }

    /**
     * token 租约释放 v1 脚本只能删除 token 与持有者一致的门禁。
     */
    @Test
    void shouldLoadVersionedTokenLeaseReleaseScript() {
        log.info("测试 token 租约释放 Lua 资源，关键输入: 门禁 token 与当前持有者一致性校验");
        var script = PaymentRedisScripts.tokenLeaseReleaseV1();

        assertThat(script.getResultType()).isEqualTo(Long.class);
        assertThat(script.getScriptAsString())
                .contains("redis.call('get', KEYS[1]) == ARGV[1]")
                .contains("redis.call('del', KEYS[1])");
        assertThat(script.getSha1()).matches("[0-9a-f]{40}");
        log.info("token 租约释放 Lua 资源测试完成，结果: 比较后删除契约存在");
    }

    /**
     * 并发租约 v1 脚本必须原子清理过期持有者、检查容量并写入新 token。
     */
    @Test
    void shouldLoadVersionedConcurrencyLeaseAcquireScript() {
        var script = PaymentRedisScripts.concurrencyLeaseAcquireV1();

        assertThat(script.getResultType()).isEqualTo(Long.class);
        assertThat(script.getScriptAsString())
                .contains("ZREMRANGEBYSCORE")
                .contains("ZCARD")
                .contains("ZADD")
                .contains("PEXPIRE");
        assertThat(script.getSha1()).matches("[0-9a-f]{40}");
    }

    /**
     * 并发租约续期脚本必须校验 token 仍属于当前 ZSet 后才能延长有效期。
     */
    @Test
    void shouldLoadVersionedConcurrencyLeaseRenewScript() {
        var script = PaymentRedisScripts.concurrencyLeaseRenewV1();

        assertThat(script.getResultType()).isEqualTo(Long.class);
        assertThat(script.getScriptAsString())
                .contains("ZSCORE")
                .contains("TIME")
                .contains("ZADD")
                .contains("PEXPIRE");
        assertThat(script.getSha1()).matches("[0-9a-f]{40}");
    }
}
