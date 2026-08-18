package com.scott.payment.data.service;

import com.scott.payment.data.model.MerchantCallbackSecurityMaterial;

/** 从权威存储读取商户回调安全材料。 */
public interface MerchantCallbackSecurityMaterialProvider {

    /**
     * 加载商户当前有效的回调安全材料，不允许缓存或记录返回值。
     *
     * @param merchantId 商户号
     * @return 本次回调请求使用的 JWT 密钥和响应公钥
     */
    MerchantCallbackSecurityMaterial load(String merchantId);
}
