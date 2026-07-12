package com.scott.payment.component.redis.id;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RedisServerTimeProvider
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : Redis Server Time 提供器，确保分布式编号使用 Redis 服务端时间。
 * @status : create
 */
public class RedisServerTimeProvider {

    /**
     * Spring 字符串 Redis 模板。
     */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 创建 Redis Server Time 提供器。
     *
     * @param stringRedisTemplate Spring 字符串 Redis 模板
     */
    public RedisServerTimeProvider(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 获取 Redis 服务端毫秒时间。
     *
     * @return Redis 服务端 epochMillis
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    public long currentTimeMillis() {
        try {
            Long currentMillis = stringRedisTemplate.execute(
                    (RedisCallback<Long>) connection -> connection.serverCommands().time(TimeUnit.MILLISECONDS)
            );
            if (currentMillis == null || currentMillis <= 0L) {
                throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "Redis TIME 获取失败");
            }
            return currentMillis;
        } catch (ServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "Redis TIME 获取失败", exception);
        }
    }
}
