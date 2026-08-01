package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.merchant.AdminMerchantResponseKeyRequest;
import com.scott.payment.admin.mapper.BaseMccCodeMapper;
import com.scott.payment.admin.mapper.BaseMccLevel1Mapper;
import com.scott.payment.admin.mapper.BaseMccLevel2Mapper;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.auth.entity.BaseMerchantInfoDO;
import com.scott.payment.component.db.auth.entity.BaseMerchantJwtKeyDO;
import com.scott.payment.component.db.auth.entity.BaseMerchantResponseKeyDO;
import com.scott.payment.component.db.auth.entity.BasePlatformPayloadKeyDO;
import com.scott.payment.component.db.auth.mapper.BaseMerchantInfoMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantJwtKeyMapper;
import com.scott.payment.component.db.auth.mapper.BaseMerchantResponseKeyMapper;
import com.scott.payment.component.db.auth.mapper.BasePlatformPayloadKeyMapper;
import com.scott.payment.component.db.auth.service.MerchantRuntimeProfileCacheService;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import com.scott.payment.component.db.iso.mapper.IsoCountryMapper;
import com.scott.payment.component.db.iso.mapper.IsoCurrencyMapper;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.MerchantJwtKey;
import com.scott.payment.component.security.key.OpenApiKeyMaterialFactory.RsaKeyMaterial;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantKeyMetadataInvalidationTests
 * @date : 2026-08-01 15:20
 * @email : scott_x@163.com
 * @description : 验证管理端密钥初始化、维护和删除在密钥表写入前登记 merchant:keyMeta 可靠失效意图
 * @status : create
 */
@ExtendWith(MockitoExtension.class)
class AdminMerchantKeyMetadataInvalidationTests {

    /** 商户基础资料数据访问组件。 */
    @Mock
    private BaseMerchantInfoMapper merchantInfoMapper;

    /** 商户 JWT 密钥数据访问组件。 */
    @Mock
    private BaseMerchantJwtKeyMapper jwtKeyMapper;

    /** 平台请求体密钥数据访问组件。 */
    @Mock
    private BasePlatformPayloadKeyMapper platformPayloadKeyMapper;

    /** 商户响应密钥数据访问组件。 */
    @Mock
    private BaseMerchantResponseKeyMapper responseKeyMapper;

    /** 密钥材料生成组件。 */
    @Mock
    private OpenApiKeyMaterialFactory keyMaterialFactory;

    /** 永久缓存事务型可靠失效协调器。 */
    @Mock
    private ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator;

    /** 待验证的管理端商户领域服务。 */
    private AdminMerchantInfoServiceImpl service;

    /** 创建仅包含当前测试所需依赖的服务实例。 */
    @BeforeEach
    void setUp() {
        initializeTableInfo(BaseMerchantInfoDO.class);
        initializeTableInfo(BaseMerchantJwtKeyDO.class);
        initializeTableInfo(BasePlatformPayloadKeyDO.class);
        initializeTableInfo(BaseMerchantResponseKeyDO.class);
        service = new AdminMerchantInfoServiceImpl(
                merchantInfoMapper,
                jwtKeyMapper,
                platformPayloadKeyMapper,
                responseKeyMapper,
                mock(BaseMccLevel1Mapper.class),
                mock(BaseMccLevel2Mapper.class),
                mock(BaseMccCodeMapper.class),
                mock(IsoCountryMapper.class),
                mock(IsoCurrencyMapper.class),
                keyMaterialFactory,
                mock(MerchantRuntimeProfileCacheService.class),
                cacheInvalidationCoordinator
        );
        when(merchantInfoMapper.selectOne(any())).thenReturn(merchant());
    }

    /**
     * 验证一次性初始化三类密钥前只登记一次同事务失效意图。
     */
    @Test
    void shouldPrepareInvalidationBeforeProvisioningSecurityMaterial() {
        when(keyMaterialFactory.generateMerchantJwtKey("200045"))
                .thenReturn(new MerchantJwtKey("200045", "jwt-secret", "HS256", 180L));
        when(keyMaterialFactory.generatePlatformPayloadRsaKey("200045"))
                .thenReturn(rsa("platform"));
        when(keyMaterialFactory.generateMerchantResponseRsaKey("200045"))
                .thenReturn(rsa("merchant"));

        service.provisionSecurityMaterial("200045");

        InOrder order = inOrder(cacheInvalidationCoordinator, jwtKeyMapper,
                platformPayloadKeyMapper, responseKeyMapper);
        order.verify(cacheInvalidationCoordinator)
                .prepare(PaymentCacheNames.MERCHANT_KEY_METADATA, "200045");
        order.verify(jwtKeyMapper).update(any(), any());
        order.verify(jwtKeyMapper).insert(any(BaseMerchantJwtKeyDO.class));
        order.verify(platformPayloadKeyMapper).insert(any(BasePlatformPayloadKeyDO.class));
        order.verify(responseKeyMapper).insert(any(BaseMerchantResponseKeyDO.class));
    }

    /**
     * 验证管理端更新商户响应公钥前先登记版本元数据失效意图。
     */
    @Test
    void shouldPrepareInvalidationBeforeUpdatingResponseKey() {
        AdminMerchantResponseKeyRequest request = new AdminMerchantResponseKeyRequest();
        request.setPublicKeyX509Base64("AQID");
        request.setEnabled(1);

        service.updateMerchantResponseKey("200045", request);

        InOrder order = inOrder(cacheInvalidationCoordinator, responseKeyMapper);
        order.verify(cacheInvalidationCoordinator)
                .prepare(PaymentCacheNames.MERCHANT_KEY_METADATA, "200045");
        order.verify(responseKeyMapper).insert(any(BaseMerchantResponseKeyDO.class));
    }

    /**
     * 验证删除商户时先阻止旧密钥版本缓存继续被交易实例使用。
     */
    @Test
    void shouldPrepareInvalidationBeforeDeletingMerchantKeys() {
        service.deleteMerchant(1L);

        InOrder order = inOrder(cacheInvalidationCoordinator, jwtKeyMapper,
                platformPayloadKeyMapper, responseKeyMapper);
        order.verify(cacheInvalidationCoordinator)
                .prepare(PaymentCacheNames.MERCHANT_KEY_METADATA, "200045");
        order.verify(cacheInvalidationCoordinator)
                .prepare(PaymentCacheNames.MERCHANT_ROUTE, "200045");
        order.verify(jwtKeyMapper).update(any(), any());
        order.verify(platformPayloadKeyMapper).update(any(), any());
        order.verify(responseKeyMapper).update(any(), any());
    }

    /** 构造当前测试使用的未删除商户。 */
    private BaseMerchantInfoDO merchant() {
        BaseMerchantInfoDO merchant = new BaseMerchantInfoDO();
        merchant.setId(1L);
        merchant.setMerchantId("200045");
        merchant.setMerchantName("Acquiring Merchant");
        merchant.setDeleted(0);
        return merchant;
    }

    /** 构造不进入日志和 Redis 的测试 RSA 材料。 */
    private RsaKeyMaterial rsa(String owner) {
        return new RsaKeyMaterial(
                "200045",
                owner,
                2048,
                "AQID",
                "BAUG",
                "public-pem",
                "private-pem"
        );
    }

    /** 为无 Spring 容器的单元测试初始化 MyBatis-Plus Lambda 列缓存。 */
    private void initializeTableInfo(Class<?> entityType) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(getClass().getName() + "." + entityType.getSimpleName());
        TableInfoHelper.initTableInfo(assistant, entityType);
    }
}
