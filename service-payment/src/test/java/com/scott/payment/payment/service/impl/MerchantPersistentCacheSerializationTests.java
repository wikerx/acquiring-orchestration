package com.scott.payment.payment.service.impl;

import com.scott.payment.component.db.auth.model.MerchantKeyMetadata;
import com.scott.payment.component.db.route.model.MerchantRouteProfile;
import com.scott.payment.component.db.route.model.MerchantRouteProfile.RouteOption;
import com.scott.payment.component.redis.config.PaymentRedisSerializerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantPersistentCacheSerializationTests
 * @date : 2026-08-01 15:55
 * @email : scott_x@163.com
 * @description : 验证密钥元数据和商户路由永久缓存模型经过精确类型登记后能够完整往返
 * @status : create
 */
class MerchantPersistentCacheSerializationTests {

    /**
     * 验证新增业务 DTO 不依赖支付包通配白名单，并保持嵌套路由候选类型完整。
     */
    @Test
    void shouldSerializeRegisteredMerchantKeyAndRouteProfiles() {
        RedisSerializer<Object> serializer = PaymentRedisSerializerFactory.create();
        MerchantKeyMetadata keyMetadata = new MerchantKeyMetadata();
        keyMetadata.setMerchantId("200045");
        keyMetadata.setJwtKeyId(11L);
        keyMetadata.setJwtKeyVersion("jwt-v2");
        keyMetadata.setRevision("revision-sha256");
        MerchantRouteProfile routeProfile = new MerchantRouteProfile();
        routeProfile.setMerchantId("200045");
        routeProfile.setBindingCount(1);
        RouteOption routeOption = new RouteOption();
        routeOption.setBindingId(21L);
        routeOption.setMidConfigId(31L);
        routeOption.setChannelCode("MPGS");
        routeOption.setSupportedCurrencies(new ArrayList<>(List.of("USD")));
        routeProfile.setRouteOptions(new ArrayList<>(List.of(routeOption)));

        Object restoredKeyMetadata = serializer.deserialize(serializer.serialize(keyMetadata));
        Object restoredRouteProfile = serializer.deserialize(serializer.serialize(routeProfile));

        assertThat(restoredKeyMetadata).usingRecursiveComparison().isEqualTo(keyMetadata);
        assertThat(restoredRouteProfile).usingRecursiveComparison().isEqualTo(routeProfile);
    }
}
