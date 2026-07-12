package com.scott.payment.openapi.security;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : LocalOpenApiPayloadKeyProvider
 * @date : 2026-05-29 21:45
 * @email : scott_x@163.com
 * @description : 本地 OpenAPI 报文加密 RSA 密钥提供实现
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : LocalOpenApiPayloadKeyProvider
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPILocal Open Api Payload Key Provider，位于 service-openapi 的安全组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Component
public class LocalOpenApiPayloadKeyProvider implements OpenApiPayloadKeyProvider {

    /**
     * 配置的支付平台私钥，优先从 Nacos 或环境变量注入。
     */
    private final PrivateKey configuredPrivateKey;

    /**
     * 配置的支付平台公钥，主要用于本地生成测试密文或对外提供给商户。
     */
    private final PublicKey configuredPublicKey;

    /**
     * 未配置密钥时的本地临时密钥对，仅用于脚手架启动和单元测试，不允许作为生产密钥使用。
     */
    private final KeyPair volatileKeyPair;

    /**
     * 创建本地 OpenAPI 报文加密密钥提供器。
     *
     * @param payloadCrypto     OpenAPI 加解密工具
     * @param privateKeyBase64  平台私钥配置
     * @param publicKeyBase64   平台公钥配置
     */
    public LocalOpenApiPayloadKeyProvider(OpenApiPayloadCrypto payloadCrypto,
                                          @Value("${payment.openapi.crypto.private-key-pkcs8-base64:}") String privateKeyBase64,
                                          @Value("${payment.openapi.crypto.public-key-x509-base64:}") String publicKeyBase64) {
        this.configuredPrivateKey = StringUtils.hasText(privateKeyBase64) ? payloadCrypto.readPrivateKey(privateKeyBase64) : null;
        this.configuredPublicKey = StringUtils.hasText(publicKeyBase64) ? payloadCrypto.readPublicKey(publicKeyBase64) : null;
        this.volatileKeyPair = this.configuredPrivateKey == null || this.configuredPublicKey == null
                ? payloadCrypto.generateRsaKeyPair(2048)
                : null;
    }

    /**
     * 根据商户号获取本地平台私钥。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 平台 RSA 私钥
     */
    /**
     * 获取商户 OpenAPI明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public PrivateKey getPlatformPrivateKey(String merchantId) {
        validateMerchantId(merchantId);
        return configuredPrivateKey != null ? configuredPrivateKey : volatileKeyPair.getPrivate();
    }

    /**
     * 根据商户号获取本地平台公钥。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 平台 RSA 公钥
     */
    /**
     * 获取商户 OpenAPI明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param merchantId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public PublicKey getPlatformPublicKey(String merchantId) {
        validateMerchantId(merchantId);
        return configuredPublicKey != null ? configuredPublicKey : volatileKeyPair.getPublic();
    }

    /**
     * 校验商户号，避免请求体解密阶段绕过 JWT 上下文直接调用本地密钥。
     *
     * @param merchantId 支付框架颁发的商户号
     */
    private void validateMerchantId(String merchantId) {
        if (!StringUtils.hasText(merchantId)) {
            throw new ApiException(ApiResultEnum.MERCHANT_INVALID);
        }
    }
}
