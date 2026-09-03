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
 * @date : 2026-06-25 10:37
 * @email : scott_x@163.com
 * @description : redisserver时间提供方协作组件，位于 公共组件库，封装该业务的本地校验、转换或运行时协作入口。
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
