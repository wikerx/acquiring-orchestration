package com.scott.payment.openapi.service;

import com.scott.payment.openapi.dto.security.MerchantSecurityMaterialDTO;
import com.scott.payment.openapi.dto.security.MerchantSecuritySeedDTO;
import com.scott.payment.openapi.dto.security.MerchantInfoDTO;
import com.scott.payment.openapi.dto.security.MerchantKeyRevisionDTO;
import com.scott.payment.openapi.dto.security.ServerSecurityMaterialDTO;
import com.scott.payment.openapi.entity.MerchantInfoDO;
import com.scott.payment.openapi.security.MerchantKeyProvider;
import com.scott.payment.openapi.security.OpenApiPayloadKeyProvider;

import java.security.PublicKey;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSecurityService
 * @date : 2026-05-30 00:00
 * @email : scott_x@163.com
 * @description : OpenAPI 商户安全材料服务
 * @status : create
 */
public interface MerchantSecurityService extends MerchantKeyProvider, OpenApiPayloadKeyProvider {

    /**
     * 初始化商户基础信息、商户 JWT 密钥、平台请求体 RSA 密钥和商户响应公钥。
     *
     * @param seedDTO 商户开户与测试初始化入参
     * @return 商户侧需要保存的安全材料
     */
    MerchantSecurityMaterialDTO provisionMerchantSecurityMaterial(MerchantSecuritySeedDTO seedDTO);

    /**
     * 根据商户号查询商户侧默认需要保存的对接密钥材料。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 商户侧默认密钥材料
     */
    MerchantSecurityMaterialDTO getMerchantClientSecurityMaterial(String merchantId);

    /**
     * 根据商户号查询服务端验签和解密所需的内部密钥材料。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 服务端内部密钥材料
     */
    ServerSecurityMaterialDTO getServerSecurityMaterial(String merchantId);

    /**
     * 查询所有未删除的商户基础信息。
     *
     * @return 商户基础信息列表
     */
    List<MerchantInfoDTO> listMerchantInfos();

    /**
     * 查询商户密钥迭代记录。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 商户密钥迭代记录
     */
    List<MerchantKeyRevisionDTO> listMerchantKeyRevisions(String merchantId);

    /**
     * 为商户生成新的 JWT 签名密钥版本。
     *
     * @param merchantId  支付框架颁发的商户号
     * @param keyVersion  新密钥版本号
     * @return 新密钥迭代记录
     */
    MerchantKeyRevisionDTO rotateMerchantJwtKey(String merchantId, String keyVersion);

    /**
     * 根据商户号查询可用商户基础信息。
     *
     * @param merchantId 支付框架颁发的商户号
     * @return 商户基础信息
     */
    MerchantInfoDO getActiveMerchant(String merchantId);

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
