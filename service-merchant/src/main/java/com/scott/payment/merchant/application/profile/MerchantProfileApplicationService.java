package com.scott.payment.merchant.application.profile;

import com.scott.payment.merchant.dto.profile.MerchantProfileResponse;
import com.scott.payment.merchant.dto.profile.MerchantProfileChangeDTOs;
import com.scott.payment.merchant.dto.profile.MerchantProfileUpdateRequest;
import com.scott.payment.merchant.service.MerchantProfileService;
import com.scott.payment.merchant.service.impl.MerchantProfileChangeRequestService;
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

    /** 商户高风险资料变更申请服务。 */
    private final MerchantProfileChangeRequestService changeRequestService;

    /**
     * 创建商户主体资料应用服务。
     *
     * @param merchantProfileService 商户主体资料领域服务
     */
    public MerchantProfileApplicationService(
            MerchantProfileService merchantProfileService,
            MerchantProfileChangeRequestService changeRequestService) {
        this.merchantProfileService = merchantProfileService;
        this.changeRequestService = changeRequestService;
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

    /** 查询当前商户资料维护工作区。 */
    public MerchantProfileChangeDTOs.Workspace getWorkspace(String merchantId) {
        return changeRequestService.getWorkspace(merchantId);
    }

    /** 保存高风险资料变更草稿，正式资料保持不变。 */
    public MerchantProfileChangeDTOs.ChangeRequest saveDraft(
            String merchantId, MerchantProfileChangeDTOs.ProfileSnapshot request) {
        return changeRequestService.saveDraft(merchantId, request);
    }

    /** 查询当前商户指定资料变更申请。 */
    public MerchantProfileChangeDTOs.ChangeRequest getChangeRequest(
            String merchantId, String requestNo) {
        return changeRequestService.getRequest(merchantId, requestNo);
    }

    /** 提交高风险资料变更申请进入管理端审核。 */
    public MerchantProfileChangeDTOs.ChangeRequest submitChangeRequest(
            String merchantId, String requestNo, String comment) {
        return changeRequestService.submit(merchantId, requestNo, comment);
    }

    /** 撤回尚未完成审核的高风险资料变更申请。 */
    public MerchantProfileChangeDTOs.ChangeRequest withdrawChangeRequest(
            String merchantId, String requestNo) {
        return changeRequestService.withdraw(merchantId, requestNo);
    }
}
