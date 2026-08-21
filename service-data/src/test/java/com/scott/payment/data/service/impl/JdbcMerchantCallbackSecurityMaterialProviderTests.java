package com.scott.payment.data.service.impl;

import com.scott.payment.component.db.auth.entity.BaseMerchantJwtKeyDO;
import com.scott.payment.component.db.auth.entity.BaseMerchantResponseKeyDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantJwtKeyMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantResponseKeyMapper;
import com.scott.payment.component.db.auth.model.MerchantKeyMetadata;
import com.scott.payment.component.db.auth.service.MerchantKeyMetadataCacheService;
import com.scott.payment.data.config.DataMerchantNotificationProperties;
import com.scott.payment.data.model.MerchantCallbackSecurityMaterial;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JdbcMerchantCallbackSecurityMaterialProviderTests
 * @date : 2026-08-21 00:05
 * @email : scott_x@163.com
 * @description : 验证商户回调密钥只在 JVM 按 revision 做有界短时缓存，版本变化和 TTL 到期均回源主库
 * @status : create
 */
class JdbcMerchantCallbackSecurityMaterialProviderTests {

    /** 相同 revision 在 TTL 内复用本地材料，Redis 元数据仍会在每次调用时校验。 */
    @Test
    void shouldReuseLocalMaterialForCurrentRevision() {
        Fixture fixture = fixture(2, Duration.ofMinutes(2));
        when(fixture.metadataService().findKeyMetadata("200045"))
                .thenReturn(metadata("200045", "revision-v1", 11L, 21L));

        MerchantCallbackSecurityMaterial first = fixture.provider().load("200045");
        MerchantCallbackSecurityMaterial second = fixture.provider().load("200045");

        assertThat(second).isSameAs(first);
        assertThat(second.getJwtSecret()).isEqualTo("jwt-secret");
        verify(fixture.metadataService(), times(2)).findKeyMetadata("200045");
        verify(fixture.jwtKeyMapper(), times(1)).selectOne(any());
        verify(fixture.responseKeyMapper(), times(1)).selectOne(any());
    }

    /** revision 变化后立即丢弃旧版本并按新记录 ID 重载。 */
    @Test
    void shouldReloadAndEvictOldMaterialWhenRevisionChanges() {
        Fixture fixture = fixture(2, Duration.ofMinutes(2));
        when(fixture.metadataService().findKeyMetadata("200045"))
                .thenReturn(metadata("200045", "revision-v1", 11L, 21L),
                        metadata("200045", "revision-v2", 12L, 22L));
        when(fixture.jwtKeyMapper().selectOne(any()))
                .thenReturn(jwtKey("jwt-secret-v1"), jwtKey("jwt-secret-v2"));
        when(fixture.responseKeyMapper().selectOne(any()))
                .thenReturn(responseKey("response-key-v1"), responseKey("response-key-v2"));

        MerchantCallbackSecurityMaterial first = fixture.provider().load("200045");
        MerchantCallbackSecurityMaterial second = fixture.provider().load("200045");

        assertThat(first.getJwtSecret()).isEqualTo("jwt-secret-v1");
        assertThat(second.getJwtSecret()).isEqualTo("jwt-secret-v2");
        assertThat(fixture.provider().entryCount()).isEqualTo(1);
        verify(fixture.jwtKeyMapper(), times(2)).selectOne(any());
    }

    /** TTL 到期后相同 revision 也必须重新读取主库。 */
    @Test
    void shouldReloadMaterialAfterTtlExpires() {
        Fixture fixture = fixture(2, Duration.ofNanos(10));
        when(fixture.metadataService().findKeyMetadata("200045"))
                .thenReturn(metadata("200045", "revision-v1", 11L, 21L));

        fixture.provider().load("200045");
        fixture.clock().set(11L);
        fixture.provider().load("200045");

        verify(fixture.jwtKeyMapper(), times(2)).selectOne(any());
        verify(fixture.responseKeyMapper(), times(2)).selectOne(any());
    }

    /** 超过容量时只保留最近访问的本地商户版本。 */
    @Test
    void shouldEnforceLocalCacheCapacity() {
        Fixture fixture = fixture(1, Duration.ofMinutes(2));
        when(fixture.metadataService().findKeyMetadata("200045"))
                .thenReturn(metadata("200045", "revision-v1", 11L, 21L));
        when(fixture.metadataService().findKeyMetadata("200046"))
                .thenReturn(metadata("200046", "revision-v1", 12L, 22L));

        fixture.provider().load("200045");
        fixture.clock().incrementAndGet();
        fixture.provider().load("200046");

        assertThat(fixture.provider().entryCount()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture(int maxEntries, Duration ttl) {
        MerchantKeyMetadataCacheService metadataService = mock(MerchantKeyMetadataCacheService.class);
        BaseMerchantJwtKeyMapper jwtKeyMapper = mock(BaseMerchantJwtKeyMapper.class);
        BaseMerchantResponseKeyMapper responseKeyMapper = mock(BaseMerchantResponseKeyMapper.class);
        when(jwtKeyMapper.selectOne(any())).thenReturn(jwtKey("jwt-secret"));
        when(responseKeyMapper.selectOne(any())).thenReturn(responseKey("response-public-key"));
        DataMerchantNotificationProperties properties = new DataMerchantNotificationProperties();
        properties.setSecurityMaterialCacheTtl(ttl);
        properties.setSecurityMaterialCacheMaxEntries(maxEntries);
        AtomicLong clock = new AtomicLong();
        JdbcMerchantCallbackSecurityMaterialProvider provider =
                new JdbcMerchantCallbackSecurityMaterialProvider(
                        metadataService, jwtKeyMapper, responseKeyMapper, properties, clock::get);
        return new Fixture(provider, metadataService, jwtKeyMapper, responseKeyMapper, clock);
    }

    private MerchantKeyMetadata metadata(String merchantId, String revision, Long jwtKeyId, Long responseKeyId) {
        MerchantKeyMetadata metadata = new MerchantKeyMetadata();
        metadata.setMerchantId(merchantId);
        metadata.setRevision(revision);
        metadata.setJwtKeyId(jwtKeyId);
        metadata.setResponseKeyId(responseKeyId);
        return metadata;
    }

    private BaseMerchantJwtKeyDO jwtKey(String secret) {
        BaseMerchantJwtKeyDO row = new BaseMerchantJwtKeyDO();
        row.setMerchantKey(secret);
        return row;
    }

    private BaseMerchantResponseKeyDO responseKey(String publicKey) {
        BaseMerchantResponseKeyDO row = new BaseMerchantResponseKeyDO();
        row.setPublicKeyX509Base64(publicKey);
        return row;
    }

    private record Fixture(JdbcMerchantCallbackSecurityMaterialProvider provider,
                           MerchantKeyMetadataCacheService metadataService,
                           BaseMerchantJwtKeyMapper jwtKeyMapper,
                           BaseMerchantResponseKeyMapper responseKeyMapper,
                           AtomicLong clock) {
    }
}
