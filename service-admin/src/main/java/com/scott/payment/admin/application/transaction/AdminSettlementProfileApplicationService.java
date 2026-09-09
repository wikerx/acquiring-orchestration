package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileUpdateRequest;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.admin.service.AdminSettlementProfileService;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementProfileApplicationService
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Admin 结算档案管理编排；登录主体只从内部认证上下文解析。
 * @status : create
 */
@Service
public class AdminSettlementProfileApplicationService {

    private final AdminSettlementProfileService profileService;
    private final AdminMerchantDataScopeResolver dataScopeResolver;

    public AdminSettlementProfileApplicationService(AdminSettlementProfileService profileService,
                                                     AdminMerchantDataScopeResolver dataScopeResolver) {
        this.profileService = profileService;
        this.dataScopeResolver = dataScopeResolver;
    }

    /** 查询当前账号可见的结算档案。 */
    public PageResult<ProfileSummary> search(ProfileSearchRequest request) {
        InternalAuthAccount account = currentAdminAccount();
        return profileService.search(request, dataScopeResolver.resolve(account));
    }

    /** 查询当前账号可见的单个结算档案。 */
    public ProfileSummary detail(String settlementProfileNo) {
        InternalAuthAccount account = currentAdminAccount();
        return profileService.detail(settlementProfileNo, dataScopeResolver.resolve(account));
    }

    /** 更新当前账号数据范围内的结算档案后续调度参数。 */
    public ProfileSummary update(String settlementProfileNo, ProfileUpdateRequest request) {
        InternalAuthAccount account = currentAdminAccount();
        return profileService.update(settlementProfileNo, request, dataScopeResolver.resolve(account));
    }

    private InternalAuthAccount currentAdminAccount() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || account.getAccountId() == null
                || !"ADMIN".equalsIgnoreCase(account.getAppCode())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        return account;
    }
}
