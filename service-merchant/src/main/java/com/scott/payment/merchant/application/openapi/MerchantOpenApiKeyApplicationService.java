package com.scott.payment.merchant.application.openapi;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.security.openapi.OpenApiKeyCopyResponse;
import com.scott.payment.component.security.openapi.OpenApiKeyDownloadFile;
import com.scott.payment.component.security.openapi.OpenApiKeyExportFormat;
import com.scott.payment.component.security.openapi.OpenApiKeyExportRequest;
import com.scott.payment.component.security.openapi.OpenApiKeyType;
import com.scott.payment.component.security.openapi.OpenApiMerchantKeyMaterialService;
import com.scott.payment.component.security.openapi.OpenApiMerchantKeyMaterialVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOpenApiKeyApplicationService
 * @date : 2026-08-01 13:00
 * @email : scott_x@163.com
 * @description : 商户门户 OpenAPI 密钥用例编排，统一读写数据源并将密钥轮换与共享商户缓存失效绑定到同一事务
 * @status : create
 */
@Service
public class MerchantOpenApiKeyApplicationService {

    /** 密钥材料查询、导出和持久化领域能力；密钥明文不会写入 Redis。 */
    private final OpenApiMerchantKeyMaterialService keyMaterialService;

    /** Admin 与 Merchant Portal 共用的事务缓存失效协调器。 */
    private final ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator;

    /**
     * 创建商户 OpenAPI 密钥用例编排服务。
     *
     * @param keyMaterialService 密钥材料领域服务
     * @param cacheInvalidationCoordinator 共享缓存可靠失效协调器
     */
    public MerchantOpenApiKeyApplicationService(
            OpenApiMerchantKeyMaterialService keyMaterialService,
            ManagedCacheInvalidationCoordinator cacheInvalidationCoordinator) {
        this.keyMaterialService = keyMaterialService;
        this.cacheInvalidationCoordinator = cacheInvalidationCoordinator;
    }

    /**
     * 从只读库查询当前商户可展示的密钥概要，不返回密钥明文。
     *
     * @param merchantId 认证上下文中的商户号
     * @return 密钥状态、算法、版本和指纹概要
     */
    @DS(DataSourceName.SLAVE)
    public OpenApiMerchantKeyMaterialVO queryMaterial(String merchantId) {
        return keyMaterialService.queryMaterial(merchantId);
    }

    /**
     * 从只读库生成当前商户主动请求复制的接入材料。
     *
     * <p>权限校验和敏感操作审计由 Controller 完成，返回内容不得记录日志或持久化到浏览器存储。</p>
     *
     * @param merchantId 认证上下文中的商户号
     * @param request 导出类型与格式
     * @return 有短时展示语义的复制内容
     */
    @DS(DataSourceName.SLAVE)
    public OpenApiKeyCopyResponse copy(String merchantId, OpenApiKeyExportRequest request) {
        return keyMaterialService.copy(merchantId, request);
    }

    /**
     * 从只读库生成当前商户主动请求下载的接入文件。
     *
     * @param merchantId 认证上下文中的商户号
     * @param keyType 材料类型
     * @param format 文件格式
     * @return 仅供当前 HTTP 响应下载的文件内容
     */
    @DS(DataSourceName.SLAVE)
    public OpenApiKeyDownloadFile download(String merchantId,
                                           OpenApiKeyType keyType,
                                           OpenApiKeyExportFormat format) {
        return keyMaterialService.download(merchantId, keyType, format);
    }

    /**
     * 在主库事务中轮换当前商户密钥并登记共享商户缓存精确失效。
     *
     * <p>pending 门禁、Outbox 意图和密钥表变更共享提交边界。Redis 只删除
     * {@code merchant:keyMeta:{merchantId}} 对应的非敏感版本元数据，JWT Secret、RSA 私钥和其他
     * 密钥明文始终只保存在受控密钥表并按权限返回。</p>
     *
     * @param merchantId 认证上下文中的商户号
     * @param keyType 允许轮换的密钥类型
     * @return 轮换后的非明文密钥概要
     */
    @DS(DataSourceName.MASTER)
    @Transactional(rollbackFor = Exception.class)
    public OpenApiMerchantKeyMaterialVO rotate(String merchantId, OpenApiKeyType keyType) {
        cacheInvalidationCoordinator.prepare(
                PaymentCacheNames.MERCHANT_KEY_METADATA,
                merchantId
        );
        return keyMaterialService.rotate(merchantId, keyType);
    }
}
