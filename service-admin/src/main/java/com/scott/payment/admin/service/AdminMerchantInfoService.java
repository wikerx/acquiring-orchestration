package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.merchant.AdminMerchantInfoDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantFormOptionsDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantKeyBundleDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantQueryRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantResponseKeyRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantSaveRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantSecurityMaterialDTO;
import com.scott.payment.component.security.openapi.OpenApiKeyType;
import com.scott.payment.component.core.model.PageResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantInfoService
 * @date : 2026-06-19 21:53
 * @email : scott_x@163.com
 * @description : 管理后台商户接入资料领域服务
 * @status : create
 *
 * <p>负责商户基础资料维护、状态管理与 OpenAPI 密钥材料编排等核心领域能力，不处理 HTTP 协议细节。</p>
 */
public interface AdminMerchantInfoService {

    /**
     * 查询商户新增和编辑表单选项。
     *
     * @return 表单选项
     */
    AdminMerchantFormOptionsDTO getFormOptions();

    /**
     * 分页查询商户资料。
     *
     * @param request 查询条件
     * @return 商户分页结果
     */
    PageResult<AdminMerchantInfoDTO> pageMerchants(AdminMerchantQueryRequest request);

    /**
     * 查询单个商户详情。
     *
     * @param id 商户主键
     * @return 商户详情
     */
    AdminMerchantInfoDTO getMerchant(Long id);

    /**
     * 新增商户资料。
     *
     * @param request 保存请求
     * @return 商户详情
     */
    AdminMerchantInfoDTO createMerchant(AdminMerchantSaveRequest request);

    /**
     * 更新商户资料。
     *
     * @param id      商户主键
     * @param request 保存请求
     * @return 商户详情
     */
    AdminMerchantInfoDTO updateMerchant(Long id, AdminMerchantSaveRequest request);

    /**
     * 更新商户状态。
     *
     * @param id             商户主键
     * @param merchantStatus 商户状态
     * @return 商户详情
     */
    AdminMerchantInfoDTO updateStatus(Long id, Integer merchantStatus);

    /**
     * 软删除商户及其 OpenAPI 密钥记录。
     *
     * @param id 商户主键
     */
    void deleteMerchant(Long id);

    /**
     * 初始化商户安全材料。
     *
     * @param merchantId 商户号
     * @return 安全材料
     */
    AdminMerchantSecurityMaterialDTO provisionSecurityMaterial(String merchantId);

    /**
     * 查询商户密钥概览。
     *
     * @param merchantId 商户号
     * @return 密钥集合
     */
    AdminMerchantKeyBundleDTO getMerchantKeys(String merchantId);

    /**
     * 轮换商户 JWT 密钥。
     *
     * @param merchantId 商户号
     * @return 最新安全材料
     */
    AdminMerchantSecurityMaterialDTO rotateJwtKey(String merchantId);

    /**
     * 轮换平台请求体密钥。
     *
     * @param merchantId 商户号
     * @return 最新安全材料
     */
    AdminMerchantSecurityMaterialDTO rotatePlatformPayloadKey(String merchantId);

    /**
     * 轮换商户响应密钥。
     *
     * @param merchantId 商户号
     * @return 最新安全材料
     */
    AdminMerchantSecurityMaterialDTO rotateMerchantResponseKey(String merchantId);

    /**
     * 启用或停用商户当前 OpenAPI 密钥材料。
     *
     * @param merchantId 商户号
     * @param keyType 密钥类型
     * @param enabled true 启用，false 停用
     */
    void setOpenApiKeyEnabled(String merchantId, OpenApiKeyType keyType, boolean enabled);

    /**
     * 更新商户响应公钥。
     *
     * @param merchantId 商户号
     * @param request    公钥更新请求
     * @return 商户详情
     */
    AdminMerchantInfoDTO updateMerchantResponseKey(String merchantId, AdminMerchantResponseKeyRequest request);
}
