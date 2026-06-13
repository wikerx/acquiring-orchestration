package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.merchant.AdminMerchantInfoDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantKeyBundleDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantQueryRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantResponseKeyRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantSaveRequest;
import com.scott.payment.admin.dto.merchant.AdminMerchantSecurityMaterialDTO;
import com.scott.payment.component.core.model.PageResult;

/**
 * 管理后台商户信息服务。
 */
public interface AdminMerchantInfoService {

    PageResult<AdminMerchantInfoDTO> pageMerchants(AdminMerchantQueryRequest request);

    AdminMerchantInfoDTO getMerchant(Long id);

    AdminMerchantInfoDTO createMerchant(AdminMerchantSaveRequest request);

    AdminMerchantInfoDTO updateMerchant(Long id, AdminMerchantSaveRequest request);

    AdminMerchantInfoDTO updateStatus(Long id, Integer merchantStatus);

    AdminMerchantSecurityMaterialDTO provisionSecurityMaterial(String merchantId);

    AdminMerchantKeyBundleDTO getMerchantKeys(String merchantId);

    AdminMerchantSecurityMaterialDTO rotateJwtKey(String merchantId);

    AdminMerchantSecurityMaterialDTO rotatePlatformPayloadKey(String merchantId);

    AdminMerchantSecurityMaterialDTO rotateMerchantResponseKey(String merchantId);

    AdminMerchantInfoDTO updateMerchantResponseKey(String merchantId, AdminMerchantResponseKeyRequest request);
}
