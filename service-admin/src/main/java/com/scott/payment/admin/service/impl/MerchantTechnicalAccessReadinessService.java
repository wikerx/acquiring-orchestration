package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.mapper.MerchantActivationReadinessMapper;
import com.scott.payment.component.security.openapi.OpenApiMerchantKeyMaterialService;
import com.scott.payment.component.security.openapi.OpenApiMerchantKeyMaterialVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 校验商户开放真实 OpenAPI 交易前必须具备的凭证和入口访问控制。
 *
 * <p>店铺网址是开放真实交易前的强制条件，必须至少存在一条审核通过且启用的规则。
 * 密钥服务只返回安全概要，本服务不会读取或暴露任何密钥明文。</p>
 */
@Service
public class MerchantTechnicalAccessReadinessService {

    private static final String ENABLED = "ENABLED";

    private final OpenApiMerchantKeyMaterialService keyMaterialService;
    private final MerchantActivationReadinessMapper readinessMapper;

    /**
     * 创建技术接入门禁服务。
     *
     * @param keyMaterialService OpenAPI 密钥安全概要服务
     * @param readinessMapper IP 白名单和来源网址只读投影
     */
    public MerchantTechnicalAccessReadinessService(
            OpenApiMerchantKeyMaterialService keyMaterialService,
            MerchantActivationReadinessMapper readinessMapper) {
        this.keyMaterialService = keyMaterialService;
        this.readinessMapper = readinessMapper;
    }

    /**
     * 评估指定商户在当前时点的技术接入条件。
     *
     * @param merchantId 平台商户号
     * @param now 统一评估时点
     * @return 稳定阻塞编码；空列表表示技术接入条件完整
     */
    public List<String> evaluate(String merchantId, LocalDateTime now) {
        if (!StringUtils.hasText(merchantId) || now == null) {
            throw new IllegalArgumentException("merchant and evaluation time are required");
        }
        String normalizedMerchantId = merchantId.trim();
        List<String> issues = new ArrayList<>();
        if (!credentialsReady(keyMaterialService.queryMaterial(normalizedMerchantId))) {
            issues.add("OPENAPI_CREDENTIALS_NOT_READY");
        }
        if (readinessMapper.countActiveIpWhitelists(normalizedMerchantId) <= 0) {
            issues.add("OPENAPI_IP_WHITELIST_NOT_READY");
        }
        if (readinessMapper.countActiveSourceUrlRules(normalizedMerchantId, now) <= 0) {
            issues.add("SOURCE_URL_NOT_READY");
        }
        return List.copyOf(issues);
    }

    /** 凭证、请求加密和响应解密材料必须同时可用。 */
    private boolean credentialsReady(OpenApiMerchantKeyMaterialVO material) {
        return material != null
                && enabled(material.getJwtKeyStatus())
                && enabled(material.getPlatformPayloadKeyStatus())
                && enabled(material.getMerchantResponseKeyStatus())
                && material.isMerchantResponsePrivateKeyAvailable();
    }

    /** 密钥状态值属于协议枚举，比较时仅做大小写兼容。 */
    private boolean enabled(String status) {
        return ENABLED.equalsIgnoreCase(status);
    }
}
