package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeeTemplateExportRow
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 管理端费用模板列表导出模型，不展开不可变版本中的费用规则明细。
 * @status : create
 */
@Data
public class FeeTemplateExportRow {
    /** 费用模板编码。 */
    @ExcelExportColumn(order = 1, headerKey = "excel.fee.planCode", width = 22)
    private String planCode;
    /** 费用模板名称。 */
    @ExcelExportColumn(order = 2, headerKey = "excel.fee.planName", width = 24)
    private String planName;
    /** 当前生效版本号；未生效时允许为空。 */
    @ExcelExportColumn(order = 3, headerKey = "excel.fee.version", width = 12)
    private Integer currentVersionNo;
    /** 模板状态。 */
    @ExcelExportColumn(order = 4, headerKey = "excel.fee.status", width = 14)
    private String status;
    /** 模板备注。 */
    @ExcelExportColumn(order = 5, headerKey = "excel.fee.remark", width = 32)
    private String remark;
    /** 模板最后修改时间。 */
    @ExcelExportColumn(order = 6, headerKey = "excel.fee.updateTime", width = 22)
    private LocalDateTime updateTime;
}
