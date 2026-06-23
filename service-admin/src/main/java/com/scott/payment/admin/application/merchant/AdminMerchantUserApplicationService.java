package com.scott.payment.admin.application.merchant;

import com.scott.payment.admin.dto.merchant.AdminMerchantUserDetailDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantUserListDTO;
import com.scott.payment.admin.dto.merchant.AdminMerchantUserQueryRequest;
import com.scott.payment.admin.service.AdminMerchantUserService;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/**
 * 管理端商户用户查询应用服务。
 */
@Service
public class AdminMerchantUserApplicationService {

    private final AdminMerchantUserService adminMerchantUserService;

    public AdminMerchantUserApplicationService(AdminMerchantUserService adminMerchantUserService) {
        this.adminMerchantUserService = adminMerchantUserService;
    }

    public PageResult<AdminMerchantUserListDTO> pageMerchantUsers(AdminMerchantUserQueryRequest request) {
        return adminMerchantUserService.pageMerchantUsers(request);
    }

    public AdminMerchantUserDetailDTO getMerchantUser(Long accountId) {
        return adminMerchantUserService.getMerchantUser(accountId);
    }
}
