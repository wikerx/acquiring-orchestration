package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileSummary;
import com.scott.payment.component.core.model.PageResult;

/**
 * Admin 结算档案本地只读边界；所有档案写入必须调用 service-settlement 命令接口。
 */
public interface AdminSettlementProfileService {

    /**
     * 按当前 Admin 商户数据范围分页查询结算档案。
     */
    PageResult<ProfileSummary> search(ProfileSearchRequest request, AdminMerchantDataScope dataScope);

    /**
     * 按稳定档案号读取运营详情。
     */
    ProfileSummary detail(String settlementProfileNo, AdminMerchantDataScope dataScope);

}
