package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 后台用户导出行对象。
 *
 * <p>只承载 Excel 展示字段，表头文案通过国际化资源解析，避免导出文件被固定为单一语言。</p>
 */
@Data
public class SysUserAccountExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.user.loginAccount", width = 20)
    private String loginAccount;

    @ExcelExportColumn(order = 2, headerKey = "excel.user.realName", width = 18)
    private String realName;

    @ExcelExportColumn(order = 3, headerKey = "excel.user.deptName", width = 18)
    private String deptName;

    @ExcelExportColumn(order = 4, headerKey = "excel.user.postNames", width = 24)
    private String postNamesText;

    @ExcelExportColumn(order = 5, headerKey = "excel.user.mobile", width = 18)
    private String mobile;

    @ExcelExportColumn(order = 6, headerKey = "excel.user.email", width = 28)
    private String email;

    @ExcelExportColumn(order = 7, headerKey = "excel.user.status", width = 12)
    private String status;

    @ExcelExportColumn(order = 8, headerKey = "excel.user.locked", width = 12)
    private String locked;

    @ExcelExportColumn(order = 9, headerKey = "excel.user.lastLoginAt", width = 22)
    private LocalDateTime lastLoginAt;

    @ExcelExportColumn(order = 10, headerKey = "excel.user.lastLoginIp", width = 18)
    private String lastLoginIp;

    @ExcelExportColumn(order = 11, headerKey = "excel.user.createdAt", width = 22)
    private LocalDateTime createdAt;
}
