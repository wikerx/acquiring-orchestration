package com.scott.payment.data.service;

import com.scott.payment.data.model.MerchantCallbackSecurityMaterial;

/** 从权威存储读取商户回调安全材料。 */
public interface MerchantCallbackSecurityMaterialProvider {

    MerchantCallbackSecurityMaterial load(String merchantId);
}
