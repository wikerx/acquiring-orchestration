package com.scott.payment.component.redis.script;

import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentRedisScripts
 * @date : 2026-07-30 09:42
 * @email : scott_x@163.com
 * @description : 集中注册支付系统公共 Redis Lua 脚本，固定资源路径、版本和返回类型
 * @status : create
 */
public final class PaymentRedisScripts {

    /**
     * 组件 Lua 脚本的 classpath 根目录；路径固定，不包含运行时输入或敏感数据。
     */
    private static final String SCRIPT_ROOT = "META-INF/payment/redis/scripts/";

    private static final DefaultRedisScript<List> GLOBAL_ID_SEQUENCE_V1 =
            load("v1/global-id-sequence.lua", List.class);

    private static final DefaultRedisScript<Long> MQ_DEDUP_ACQUIRE_V1 =
            load("v1/mq-dedup-acquire.lua", Long.class);

    private static final DefaultRedisScript<Long> TOKEN_LEASE_RELEASE_V1 =
            load("v1/token-lease-release.lua", Long.class);

    private static final DefaultRedisScript<String> CACHE_GENERATION_READ_V1 =
            load("v1/cache-generation-read.lua", String.class);

    private static final DefaultRedisScript<Long> CACHE_GENERATION_BEGIN_V1 =
            load("v1/cache-generation-begin.lua", Long.class);

    private static final DefaultRedisScript<Long> CACHE_GENERATION_COMMIT_V1 =
            load("v1/cache-generation-commit.lua", Long.class);

    private PaymentRedisScripts() {
    }

    /**
     * 获取全局 ID 毫秒序列 v1 脚本。
     *
     * @return 返回有效毫秒、序列和溢出标识的脚本
     */
    public static RedisScript<List> globalIdSequenceV1() {
        return GLOBAL_ID_SEQUENCE_V1;
    }

    /**
     * 获取 MQ 去重处理权 v1 脚本。
     *
     * @return 返回首次写入、重复、容量超限或参数非法结果码的脚本
     */
    public static RedisScript<Long> mqDedupAcquireV1() {
        return MQ_DEDUP_ACQUIRE_V1;
    }

    /**
     * 获取 token 租约持有者安全释放 v1 脚本。
     *
     * @return 返回删除数量的脚本
     */
    public static RedisScript<Long> tokenLeaseReleaseV1() {
        return TOKEN_LEASE_RELEASE_V1;
    }

    /**
     * 获取缓存代际原子读取和初始化 v1 脚本。
     *
     * @return 返回发布中标识或当前缓存代际的脚本
     */
    public static RedisScript<String> cacheGenerationReadV1() {
        return CACHE_GENERATION_READ_V1;
    }

    /**
     * 获取缓存代际发布门禁获取 v1 脚本。
     *
     * @return 返回是否取得唯一发布权的脚本
     */
    public static RedisScript<Long> cacheGenerationBeginV1() {
        return CACHE_GENERATION_BEGIN_V1;
    }

    /**
     * 获取缓存代际发布提交 v1 脚本。
     *
     * @return 返回是否由门禁持有者完成切换或命中幂等结果的脚本
     */
    public static RedisScript<Long> cacheGenerationCommitV1() {
        return CACHE_GENERATION_COMMIT_V1;
    }

    /**
     * 从固定 classpath 目录加载脚本并声明返回类型。
     *
     * @param relativePath 相对版本路径，必须由组件常量提供
     * @param resultType   Spring Data Redis 反序列化返回类型
     * @param <T>          Lua 返回值 Java 类型
     * @return 已完成资源和返回类型校验的 Redis 脚本
     */
    private static <T> DefaultRedisScript<T> load(String relativePath, Class<T> resultType) {
        DefaultRedisScript<T> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(SCRIPT_ROOT + relativePath));
        script.setResultType(resultType);
        script.afterPropertiesSet();
        return script;
    }
}
