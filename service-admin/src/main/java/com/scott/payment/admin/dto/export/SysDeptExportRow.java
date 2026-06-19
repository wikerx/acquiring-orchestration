package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysDeptExportRow
 * @date : 2026-06-19 23:50
 * @email : scott_x@163.com
 * @description : 部门导出行对象
 * @status : create
 */
@Data
public class SysDeptExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.dept.deptName", width = 24)
    private String deptName;

    @ExcelExportColumn(order = 2, headerKey = "excel.dept.parentName", width = 24)
    private String parentName;

    @ExcelExportColumn(order = 3, headerKey = "excel.dept.sortNo", width = 10)
    private Integer sortNo;

    @ExcelExportColumn(order = 4, headerKey = "excel.dept.leader", width = 16)
    private String leader;

    @ExcelExportColumn(order = 5, headerKey = "excel.dept.phone", width = 18)
    private String phone;

    @ExcelExportColumn(order = 6, headerKey = "excel.dept.email", width = 24)
    private String email;

    @ExcelExportColumn(order = 7, headerKey = "excel.dept.status", width = 12)
    private String status;
}
