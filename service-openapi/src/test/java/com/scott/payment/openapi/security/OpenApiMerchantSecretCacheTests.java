package com.scott.payment.openapi.security;

import com.scott.payment.component.db.auth.model.MerchantKeyMetadata;
import com.scott.payment.component.db.auth.service.MerchantKeyMetadataCacheService;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.openapi.config.OpenApiMerchantSecretCacheProperties;
import com.scott.payment.openapi.entity.MerchantJwtKeyDO;
import com.scott.payment.openapi.mapper.MerchantJwtKeyMapper;
import com.scott.payment.openapi.mapper.MerchantResponseKeyMapper;
import com.scott.payment.openapi.mapper.PlatformPayloadKeyMapper;
import lombok.extern.slf4j.Slf4j;
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
 * @classname : OpenApiMerchantSecretCacheTests
 * @date : 2026-08-01 15:10
 * @email : scott_x@163.com
 * @description : 验证 OpenAPI 敏感密钥只在单实例内按 Redis revision 短时复用，并受 TTL 与容量上限约束
 * @status : create
 */
@Slf4j
class OpenApiMerchantSecretCacheTests {

    /** 相同 revision 应复用本地材料，revision 变化后必须立即从主库加载新材料。 */
    @Test
    void shouldReloadSecretWhenMetadataRevisionChanges() {
        log.info("测试 OpenAPI 密钥 revision 切换，关键输入: 同一商户 revision 从 r1 变为 r2");
        MerchantKeyMetadataCacheService metadataService = mock(MerchantKeyMetadataCacheService.class);
        MerchantJwtKeyMapper jwtMapper = mock(MerchantJwtKeyMapper.class);
        when(metadataService.findKeyMetadata("200045"))
                .thenReturn(metadata("200045", 11L, "r1"), metadata("200045", 11L, "r1"),
                        metadata("200045", 12L, "r2"));
        when(jwtMapper.selectOne(any())).thenReturn(jwt("secret-v1"), jwt("secret-v2"));
        OpenApiMerchantSecretCache cache = cache(metadataService, jwtMapper, new AtomicLong(), 8);

        assertThat(cache.getMerchantKey("200045")).isEqualTo("secret-v1");
        assertThat(cache.getMerchantKey("200045")).isEqualTo("secret-v1");
        assertThat(cache.getMerchantKey("200045")).isEqualTo("secret-v2");

        verify(jwtMapper, times(2)).selectOne(any());
        log.info("OpenAPI 密钥 revision 切换完成，结果: 相同版本复用一次且新版本重新加载");
    }

    /** TTL 到期后必须重新读取主库，避免 revision 异常未更新时长期持有旧密钥。 */
    @Test
    void shouldReloadSecretAfterLocalTtlExpires() {
        log.info("测试 OpenAPI 本地密钥 TTL，关键输入: 固定 revision、2 分钟有效期");
        MerchantKeyMetadataCacheService metadataService = mock(MerchantKeyMetadataCacheService.class);
        MerchantJwtKeyMapper jwtMapper = mock(MerchantJwtKeyMapper.class);
        when(metadataService.findKeyMetadata("200045")).thenReturn(metadata("200045", 11L, "r1"));
        when(jwtMapper.selectOne(any())).thenReturn(jwt("secret-v1"), jwt("secret-v2"));
        AtomicLong nanoTime = new AtomicLong();
        OpenApiMerchantSecretCache cache = cache(metadataService, jwtMapper, nanoTime, 8);

        assertThat(cache.getMerchantKey("200045")).isEqualTo("secret-v1");
        nanoTime.set(Duration.ofMinutes(3).toNanos());
        assertThat(cache.getMerchantKey("200045")).isEqualTo("secret-v2");

        verify(jwtMapper, times(2)).selectOne(any());
        log.info("OpenAPI 本地密钥 TTL 验证完成，结果: 到期后已重新读取主库");
    }

    /** 超过容量上限时应淘汰最久未访问条目，防止攻击者用随机商户号撑大敏感内存。 */
    @Test
    void shouldEvictLeastRecentlyUsedEntryAtCapacity() {
        log.info("测试 OpenAPI 本地密钥容量，关键输入: 最大 1 个商户、依次访问两个商户");
        MerchantKeyMetadataCacheService metadataService = mock(MerchantKeyMetadataCacheService.class);
        MerchantJwtKeyMapper jwtMapper = mock(MerchantJwtKeyMapper.class);
        when(metadataService.findKeyMetadata("200045")).thenReturn(metadata("200045", 11L, "r1"));
        when(metadataService.findKeyMetadata("200046")).thenReturn(metadata("200046", 12L, "r1"));
        when(jwtMapper.selectOne(any())).thenReturn(jwt("secret-a"), jwt("secret-b"), jwt("secret-a-new"));
        OpenApiMerchantSecretCache cache = cache(metadataService, jwtMapper, new AtomicLong(), 1);

        assertThat(cache.getMerchantKey("200045")).isEqualTo("secret-a");
        assertThat(cache.getMerchantKey("200046")).isEqualTo("secret-b");
        assertThat(cache.getMerchantKey("200045")).isEqualTo("secret-a-new");

        verify(jwtMapper, times(3)).selectOne(any());
        assertThat(cache.entryCount()).isEqualTo(1);
        log.info("OpenAPI 本地密钥容量验证完成，结果: 最久未访问条目已淘汰且容量保持为 1");
    }

    /** 商户密钥变更后必须清除该商户全部本地 revision，其他商户材料不受影响。 */
    @Test
    void shouldEvictOnlySpecifiedMerchantRevisions() {
        log.info("测试 OpenAPI 商户级密钥清理，关键输入: 两个商户分别命中一个本地 revision");
        MerchantKeyMetadataCacheService metadataService = mock(MerchantKeyMetadataCacheService.class);
        MerchantJwtKeyMapper jwtMapper = mock(MerchantJwtKeyMapper.class);
        when(metadataService.findKeyMetadata("200045")).thenReturn(metadata("200045", 11L, "r1"));
        when(metadataService.findKeyMetadata("200046")).thenReturn(metadata("200046", 12L, "r1"));
        when(jwtMapper.selectOne(any())).thenReturn(jwt("secret-a"), jwt("secret-b"));
        OpenApiMerchantSecretCache cache = cache(metadataService, jwtMapper, new AtomicLong(), 8);
        cache.getMerchantKey("200045");
        cache.getMerchantKey("200046");

        cache.evictMerchant("200045");

        assertThat(cache.entryCount()).isEqualTo(1);
        log.info("OpenAPI 商户级密钥清理完成，结果: 目标商户全部 revision 已清理且其他商户保持不变");
    }

    /** 创建可控制单调时钟和容量的缓存实例。 */
    private OpenApiMerchantSecretCache cache(MerchantKeyMetadataCacheService metadataService,
                                              MerchantJwtKeyMapper jwtMapper,
                                              AtomicLong nanoTime,
                                              int maxEntries) {
        OpenApiMerchantSecretCacheProperties properties = new OpenApiMerchantSecretCacheProperties();
        properties.setTtl(Duration.ofMinutes(2));
        properties.setMaxEntries(maxEntries);
        return new OpenApiMerchantSecretCache(
                metadataService,
                jwtMapper,
                mock(PlatformPayloadKeyMapper.class),
                mock(MerchantResponseKeyMapper.class),
                mock(OpenApiPayloadCrypto.class),
                properties,
                nanoTime::get
        );
    }

    /** 构造仅包含 JWT 版本的 Redis 元数据。 */
    private MerchantKeyMetadata metadata(String merchantId, Long keyId, String revision) {
        MerchantKeyMetadata metadata = new MerchantKeyMetadata();
        metadata.setMerchantId(merchantId);
        metadata.setJwtKeyId(keyId);
        metadata.setRevision(revision);
        return metadata;
    }

    /** 构造数据库返回的虚构 JWT 密钥记录。 */
    private MerchantJwtKeyDO jwt(String secret) {
        MerchantJwtKeyDO row = new MerchantJwtKeyDO();
        row.setMerchantKey(secret);
        return row;
    }
}
