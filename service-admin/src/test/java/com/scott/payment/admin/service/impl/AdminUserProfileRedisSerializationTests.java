package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.AdminUserProfileDTO;
import com.scott.payment.component.redis.config.PaymentRedisSerializerFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminUserProfileRedisSerializationTests
 * @date : 2026-08-10 19:37
 * @email : scott_x@163.com
 * @description : 后台用户资料缓存 Value 的 Redis 精确类型白名单和序列化兼容测试
 * @status : create
 */
@Slf4j
class AdminUserProfileRedisSerializationTests {

    /**
     * 后台用户资料必须经过受控 Redis Serializer 完整往返，且关联主键集合类型保持兼容。
     */
    @Test
    void shouldRoundTripRegisteredAdminUserProfile() {
        log.info("测试后台用户资料 Redis 序列化，关键输入: accountId=10001，不包含鉴权字段");
        AdminUserProfileDTO source = new AdminUserProfileDTO();
        source.setAccountId(10001L);
        source.setUserId(20001L);
        source.setDeptId(21001L);
        source.setPostIds(new ArrayList<>(List.of(30001L)));
        source.setRoleIds(new ArrayList<>(List.of(40001L)));
        source.setLoginAccount("admin.operator");
        source.setRealName("Operator A");
        source.setEmail("operator@example.com");
        source.setStatus(1);
        source.setCreatedAt(LocalDateTime.of(2026, 8, 1, 10, 0));
        RedisSerializer<Object> serializer = PaymentRedisSerializerFactory.create();

        Object restored = serializer.deserialize(serializer.serialize(source));

        assertThat(restored)
                .isInstanceOf(AdminUserProfileDTO.class)
                .usingRecursiveComparison()
                .isEqualTo(source);
        log.info("后台用户资料 Redis 序列化验证完成，结果: 受控类型及关联主键完整恢复");
    }
}
