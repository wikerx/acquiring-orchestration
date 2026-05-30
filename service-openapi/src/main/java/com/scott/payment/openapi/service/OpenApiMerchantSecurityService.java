package com.scott.payment.openapi.service;

import com.scott.payment.openapi.dto.security.OpenApiMerchantSecurityMaterialDTO;
import com.scott.payment.openapi.dto.security.OpenApiMerchantSecuritySeedDTO;
import com.scott.payment.openapi.entity.OpenApiMerchantInfoDO;
import com.scott.payment.openapi.security.MerchantKeyProvider;
import com.scott.payment.openapi.security.OpenApiPayloadKeyProvider;

import java.security.PublicKey;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiMerchantSecurityService
 * @date : 2026-05-30 00:00
 * @email : scott_x@163.com
 * @description : OpenAPI 商户安全材料服务
 * @status : create
 */
public interface OpenApiMerchantSecurityService extends MerchantKeyProvider, OpenApiPayloadKeyProvider {

    /**
     * 初始化商户基础信息、商户 JWT 密钥、平台请求体 RSA 密钥和商户响应公钥。
     *
     * @param seedDTO 商户开户与测试初始化入参
     * @return 商户侧需要保存的安全材料
     */
    OpenApiMerchantSecurityMaterialDTO provisionMerchantSecurityMaterial(OpenApiMerchantSecuritySeedDTO seedDTO);

    /**
     * 根据商户号查询可用商户基础信息。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 商户基础信息
     */
    OpenApiMerchantInfoDO getActiveMerchant(String merchantId);

    /**
     * 查询商户响应加密公钥。
     *
     * @param merchantId    支付框架颁发的商户号
     * @param responseKeyId 商户响应公钥编号
     * @return 商户响应 RSA 公钥
     */
    PublicKey getMerchantResponsePublicKey(String merchantId, String responseKeyId);

    /**
     * 查询商户当前启用的响应公钥编号。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 响应公钥编号
     */
    String getEnabledMerchantResponseKeyId(String merchantId);
}
