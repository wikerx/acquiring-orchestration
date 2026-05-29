package com.scott.payment.openapi.security;

import com.scott.payment.component.core.enums.ApiCoResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : LocalMerchantKeyProvider
 * @date : 2026-05-28 11:42
 * @email : scott_x@163.com
 * @description : 本地商户密钥获取实现
 * @status : create
 */
@Component
public class LocalMerchantKeyProvider implements MerchantKeyProvider {

    /**
     * 本地默认商户 JWT 签名密钥。
     * <p>
     * 当前用于脚手架联调和本地测试，生产环境应按 merchantId 从商户配置服务、数据库或 Nacos 独立配置中获取。
     */
    private final String defaultMerchantKey;

    /**
     * 创建本地商户密钥提供器。
     * <p>
     * 这里不写死任何示例密钥，避免测试密钥被误带到生产；本地可通过
     * `payment.openapi.default-merchant-key` 或 `OPENAPI_DEFAULT_MERCHANT_KEY` 注入。
     *
     * @param defaultMerchantKey 本地默认商户 JWT 签名密钥
     */
    public LocalMerchantKeyProvider(@Value("${payment.openapi.default-merchant-key:${OPENAPI_DEFAULT_MERCHANT_KEY:}}") String defaultMerchantKey) {
        this.defaultMerchantKey = defaultMerchantKey;
    }

    /**
     * 获取商户 JWT HS256 签名密钥。
     *
     * @param merchantId 支付平台分配的商户号
     * @return 商户签名密钥
     */
    @Override
    public String getMerchantKey(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_MER_INVALID);
        }
        // TODO 后续替换为商户服务/数据库/Nacos 密钥查询，并支持密钥轮换。
        if (!StringUtils.hasText(defaultMerchantKey)) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED_JWT_NO_KEY);
        }
        return defaultMerchantKey;
    }
}
