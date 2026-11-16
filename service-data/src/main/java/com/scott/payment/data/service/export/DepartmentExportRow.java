package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

/**
 * Data 生成管理端部门 Excel 的独立行模型，联系方式按文本写入避免格式变化。
 */
@Data
public class DepartmentExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.dept.deptName", width = 24)
    private String deptName;
    @ExcelExportColumn(order = 2, headerKey = "excel.dept.parentName", width = 24)
    private String parentName;
    @ExcelExportColumn(order = 3, headerKey = "excel.dept.sortNo", width = 10)
    private Integer sortNo;
    @ExcelExportColumn(order = 4, headerKey = "excel.dept.leader", width = 16)
    private String leader;
    @ExcelExportColumn(order = 5, headerKey = "excel.dept.phone", width = 18, forceText = true)
    private String phone;
    @ExcelExportColumn(order = 6, headerKey = "excel.dept.email", width = 24)
    private String email;
    @ExcelExportColumn(order = 7, headerKey = "excel.dept.status", width = 12)
    private String status;
}
