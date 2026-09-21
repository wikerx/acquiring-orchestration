package com.scott.payment.admin.application.merchant;

import com.scott.payment.admin.dto.merchant.AdminMerchantProfileChangeDTOs;
import com.scott.payment.admin.service.impl.AdminMerchantProfileChangeService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantProfileChangeApplicationService
 * @date : 2026-09-21 10:15
 * @email : scott_x@163.com
 * @description : 管理端商户资料变更应用服务，编排分页、详情和审核用例
 * @status : create
 */
@Service
public class AdminMerchantProfileChangeApplicationService {

    private final AdminMerchantProfileChangeService profileChangeService;

    /** 创建管理端商户资料变更应用服务。 */
    public AdminMerchantProfileChangeApplicationService(
            AdminMerchantProfileChangeService profileChangeService) {
        this.profileChangeService = profileChangeService;
    }

    /** 分页查询资料变更申请。 */
    public PageResult<AdminMerchantProfileChangeDTOs.ChangeRequest> page(
            AdminMerchantProfileChangeDTOs.Query query) {
        return profileChangeService.page(query);
    }

    /** 查询资料变更申请详情。 */
    public AdminMerchantProfileChangeDTOs.ChangeRequest get(String requestNo) {
        return profileChangeService.get(requestNo);
    }

    /** 执行资料变更审核决定。 */
    public AdminMerchantProfileChangeDTOs.ChangeRequest review(
            String requestNo, AdminMerchantProfileChangeDTOs.ReviewRequest request) {
        return profileChangeService.review(requestNo, request);
    }
}
