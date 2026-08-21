package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFeeExportRow
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 管理端商户费率列表导出模型，仅展示商户当前配置来源和生效版本摘要。
 * @status : create
 */
@Data
public class MerchantFeeExportRow {
    /** 商户号。 */
    @ExcelExportColumn(order = 1, headerKey = "excel.fee.merchantId", width = 20)
    private String merchantId;
    /** 商户名称。 */
    @ExcelExportColumn(order = 2, headerKey = "excel.fee.merchantName", width = 28)
    private String merchantName;
    /** 商户费率方案编码；未配置时允许为空。 */
    @ExcelExportColumn(order = 3, headerKey = "excel.fee.planCode", width = 22)
    private String planCode;
    /** 配置来源，区分模板复制、基于模板调整和独立配置。 */
    @ExcelExportColumn(order = 4, headerKey = "excel.fee.origin", width = 20)
    private String originType;
    /** 当前生效版本号；未配置时允许为空。 */
    @ExcelExportColumn(order = 5, headerKey = "excel.fee.version", width = 12)
    private Integer currentVersionNo;
    /** 商户费率方案状态。 */
    @ExcelExportColumn(order = 6, headerKey = "excel.fee.status", width = 14)
    private String status;
    /** 商户费率最后修改时间。 */
    @ExcelExportColumn(order = 7, headerKey = "excel.fee.updateTime", width = 22)
    private LocalDateTime updateTime;
}
