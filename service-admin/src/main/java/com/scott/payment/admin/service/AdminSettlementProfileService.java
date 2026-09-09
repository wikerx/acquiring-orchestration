package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileSummary;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ProfileUpdateRequest;
import com.scott.payment.component.core.model.PageResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementProfileService
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : Admin 结算档案本地管理边界，读取和 CAS 编辑均直接使用交易逻辑数据源。
 * @status : create
 */
public interface AdminSettlementProfileService {

    /** 按当前 Admin 商户数据范围分页查询结算档案。 */
    PageResult<ProfileSummary> search(ProfileSearchRequest request, AdminMerchantDataScope dataScope);

    /** 按稳定档案号读取运营详情。 */
    ProfileSummary detail(String settlementProfileNo, AdminMerchantDataScope dataScope);

    /** 按档案号、商户范围和期望版本修改后续调度参数。 */
    ProfileSummary update(String settlementProfileNo,
                          ProfileUpdateRequest request,
                          AdminMerchantDataScope dataScope);
}
