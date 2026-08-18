package com.scott.payment.merchant.application.profile;

import com.scott.payment.merchant.dto.profile.MerchantProfileResponse;
import com.scott.payment.merchant.dto.profile.MerchantProfileUpdateRequest;
import com.scott.payment.merchant.service.MerchantProfileService;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantProfileApplicationService
 * @date : 2026-08-01 12:00
 * @email : scott_x@163.com
 * @description : 商户主体资料应用服务，承接接口层当前商户身份并编排资料查询和受限更新用例
 * @status : create
 */
@Service
public class MerchantProfileApplicationService {

    /** 商户主体资料领域服务。 */
    private final MerchantProfileService merchantProfileService;

    /**
     * 创建商户主体资料应用服务。
     *
     * @param merchantProfileService 商户主体资料领域服务
     */
    public MerchantProfileApplicationService(MerchantProfileService merchantProfileService) {
        this.merchantProfileService = merchantProfileService;
    }

    /**
     * 查询当前认证商户主体资料。
     *
     * @param merchantId 认证上下文中的商户号
     * @return 当前商户主体资料
     */
    public MerchantProfileResponse getProfile(String merchantId) {
        return merchantProfileService.getProfile(merchantId);
    }

    /**
     * 更新当前认证商户允许维护的主体资料。
     *
     * @param merchantId 认证上下文中的商户号
     * @param request 商户自助更新字段
     * @return 更新后的当前商户主体资料
     */
    public MerchantProfileResponse updateProfile(String merchantId,
                                                 MerchantProfileUpdateRequest request) {
        return merchantProfileService.updateProfile(merchantId, request);
    }
}
