package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

/**
 * 部门导出行对象
 */
@Data
public class SysDeptExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.dept.deptName", width = 24)
    private String deptName;

    @ExcelExportColumn(order = 2, headerKey = "excel.dept.parentName", width = 24)
    private String parentName;

    /**
     * 排序号，数值越小越优先展示或匹配。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.dept.sortNo", width = 10)
    private Integer sortNo;

    @ExcelExportColumn(order = 4, headerKey = "excel.dept.leader", width = 16)
    private String leader;

    /**
     * 电话，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.dept.phone", width = 18)
    private String phone;

    /**
     * 系统管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.dept.email", width = 24)
    private String email;

    @ExcelExportColumn(order = 7, headerKey = "excel.dept.status", width = 12)
    private String status;
}
