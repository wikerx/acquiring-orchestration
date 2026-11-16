package com.scott.payment.data.client.admin;

import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundAccountPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundAccountPageResponse;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundDeductionPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundDeductionPageResponse;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundLedgerPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundLedgerPageResponse;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundRechargePageRequest;
import com.scott.payment.data.client.admin.dto.AdminFundExportSourceDTOs.FundRechargePageResponse;

/**
 * Data 分页读取管理端资金账户域安全导出投影的内部客户端。
 */
public interface AdminFundExportSourceClient {

    /** 读取一页资金账户。 */
    FundAccountPageResponse loadFundAccountPage(FundAccountPageRequest request);

    /** 读取一页指定账户或全局余额流水。 */
    FundLedgerPageResponse loadFundLedgerPage(FundLedgerPageRequest request);

    /** 读取一页充值申请。 */
    FundRechargePageResponse loadFundRechargePage(FundRechargePageRequest request);

    /** 读取一页扣减申请。 */
    FundDeductionPageResponse loadFundDeductionPage(FundDeductionPageRequest request);
}
