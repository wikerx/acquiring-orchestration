package com.scott.payment.data.client.admin;

import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeePlanPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeReviewPageResponse;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeSimulationPageRequest;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeSimulationPageResponse;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.FeeTemplatePageResponse;
import com.scott.payment.data.client.admin.dto.AdminFeeExportSourceDTOs.MerchantFeePageResponse;

/**
 * Data 分页读取管理端费用域安全导出投影的内部客户端。
 */
public interface AdminFeeExportSourceClient {

    /** 读取一页费用模板。 */
    FeeTemplatePageResponse loadFeeTemplatePage(FeePlanPageRequest request);

    /** 读取一页商户费率。 */
    MerchantFeePageResponse loadMerchantFeePage(FeePlanPageRequest request);

    /** 读取一页费率复核记录。 */
    FeeReviewPageResponse loadFeeReviewPage(FeePlanPageRequest request);

    /** 读取一页费用试算逐项记录。 */
    FeeSimulationPageResponse loadFeeSimulationPage(FeeSimulationPageRequest request);
}
