package com.scott.payment.component.db.auth.service.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.auth.entity.BaseMerchantJwtKeyDO;
import com.scott.payment.component.db.auth.entity.BaseMerchantResponseKeyDO;
import com.scott.payment.component.db.auth.entity.BasePlatformPayloadKeyDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantJwtKeyMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantResponseKeyMapper;
import com.scott.payment.component.db.auth.mapper.BasePlatformPayloadKeyMapper;
import com.scott.payment.component.db.auth.model.MerchantKeyMetadata;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantKeyMetadataCacheReaderTests
 * @date : 2026-08-01 15:05
 * @email : scott_x@163.com
 * @description : 验证 merchant:keyMeta 只保存密钥版本元数据，并生成可驱动 OpenAPI 本地密钥缓存切换的稳定 revision
 * @status : create
 */
@Slf4j
class MerchantKeyMetadataCacheReaderTests {

    /**
     * 元数据快照必须包含三类当前密钥的版本信息，但不得携带 JWT Secret 或 RSA 密钥正文。
     */
    @Test
    void shouldBuildRevisionWithoutSensitiveKeyMaterial() {
        log.info("测试商户密钥元数据缓存，关键输入: JWT、平台载荷和响应密钥均已启用");
        BaseMerchantJwtKeyMapper jwtMapper = mock(BaseMerchantJwtKeyMapper.class);
        BasePlatformPayloadKeyMapper platformMapper = mock(BasePlatformPayloadKeyMapper.class);
        BaseMerchantResponseKeyMapper responseMapper = mock(BaseMerchantResponseKeyMapper.class);
        when(jwtMapper.selectOne(any())).thenReturn(jwtKey());
        when(platformMapper.selectOne(any())).thenReturn(platformKey());
        when(responseMapper.selectOne(any())).thenReturn(responseKey());
        MerchantKeyMetadataCacheReader reader =
                new MerchantKeyMetadataCacheReader(jwtMapper, platformMapper, responseMapper);

        MerchantKeyMetadata metadata = reader.findFresh("200045");

        assertThat(metadata.getMerchantId()).isEqualTo("200045");
        assertThat(metadata.getJwtKeyVersion()).isEqualTo("jwt-v3");
        assertThat(metadata.getJwtExpireTime()).isEqualTo(LocalDateTime.of(2026, 9, 1, 15, 0));
        assertThat(metadata.getRevision()).isNotBlank();
        String json = JsonUtils.toJsonString(metadata);
        assertThat(json)
                .doesNotContain("jwt-secret-value")
                .doesNotContain("platform-private-key")
                .doesNotContain("merchant-public-key")
                .doesNotContain("merchantKey")
                .doesNotContain("privateKey")
                .doesNotContain("publicKey");
        log.info("商户密钥元数据缓存验证完成，结果: revision 已生成且缓存值不含任何密钥正文");
    }

    /** 构造包含敏感原值的 JWT 数据库记录，验证读取器只选择元数据字段。 */
    private BaseMerchantJwtKeyDO jwtKey() {
        BaseMerchantJwtKeyDO row = new BaseMerchantJwtKeyDO();
        row.setId(11L);
        row.setMerchantId("200045");
        row.setKeyVersion("jwt-v3");
        row.setMerchantKey("jwt-secret-value");
        row.setAlgorithm("HS256");
        row.setExpiresSeconds(180L);
        row.setEffectiveTime(LocalDateTime.of(2026, 8, 1, 15, 0));
        row.setExpireTime(LocalDateTime.of(2026, 9, 1, 15, 0));
        row.setGmtModified(LocalDateTime.of(2026, 8, 1, 15, 0));
        return row;
    }

    /** 构造平台载荷密钥数据库记录。 */
    private BasePlatformPayloadKeyDO platformKey() {
        BasePlatformPayloadKeyDO row = new BasePlatformPayloadKeyDO();
        row.setId(12L);
        row.setMerchantId("200045");
        row.setPrivateKeyPkcs8Base64("platform-private-key");
        row.setAlgorithm("RSA-OAEP-256+A256GCM");
        row.setKeySize(2048);
        row.setGmtModified(LocalDateTime.of(2026, 8, 1, 15, 1));
        return row;
    }

    /** 构造商户响应密钥数据库记录。 */
    private BaseMerchantResponseKeyDO responseKey() {
        BaseMerchantResponseKeyDO row = new BaseMerchantResponseKeyDO();
        row.setId(13L);
        row.setMerchantId("200045");
        row.setPublicKeyX509Base64("merchant-public-key");
        row.setAlgorithm("RSA-OAEP-256+A256GCM");
        row.setKeySize(2048);
        row.setGmtModified(LocalDateTime.of(2026, 8, 1, 15, 2));
        return row;
    }
}
