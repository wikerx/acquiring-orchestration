package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeeReviewExportRow
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 管理端费率复核记录导出模型，仅承载筛选结果中的审核摘要字段。
 * @status : create
 */
@Data
public class FeeReviewExportRow {
    /** 费用方案编码。 */
    @ExcelExportColumn(order = 1, headerKey = "excel.fee.planCode", width = 22)
    private String planCode;
    /** 费用方案名称。 */
    @ExcelExportColumn(order = 2, headerKey = "excel.fee.planName", width = 24)
    private String planName;
    /** 方案类型，区分模板与商户配置。 */
    @ExcelExportColumn(order = 3, headerKey = "excel.fee.planType", width = 14)
    private String planType;
    /** 商户号；模板方案为空。 */
    @ExcelExportColumn(order = 4, headerKey = "excel.fee.merchantId", width = 20)
    private String merchantId;
    /** 待复核版本号。 */
    @ExcelExportColumn(order = 5, headerKey = "excel.fee.version", width = 12)
    private Integer versionNo;
    /** 版本变更原因。 */
    @ExcelExportColumn(order = 6, headerKey = "excel.fee.changeReason", width = 36)
    private String changeReason;
    /** 提交人姓名快照。 */
    @ExcelExportColumn(order = 7, headerKey = "excel.fee.submitter", width = 18)
    private String submitByName;
    /** 提交审核时间，精确到系统记录时间。 */
    @ExcelExportColumn(order = 8, headerKey = "excel.fee.submitTime", width = 22)
    private LocalDateTime submitTime;
}
