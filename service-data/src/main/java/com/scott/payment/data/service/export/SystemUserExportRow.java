package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Data 生成管理端系统账户 Excel 的独立行模型。
 *
 * <p>字段来自 Admin 裁剪后的账户安全投影，不承载密码、MFA 密钥或会话凭证。</p>
 */
@Data
public class SystemUserExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.user.loginAccount", width = 20)
    private String loginAccount;
    @ExcelExportColumn(order = 2, headerKey = "excel.user.realName", width = 18)
    private String realName;
    @ExcelExportColumn(order = 3, headerKey = "excel.user.deptName", width = 18)
    private String deptName;
    @ExcelExportColumn(order = 4, headerKey = "excel.user.postNames", width = 24)
    private String postNamesText;
    @ExcelExportColumn(order = 5, headerKey = "excel.user.roleNames", width = 28)
    private String roleNamesText;
    @ExcelExportColumn(order = 6, headerKey = "excel.user.mobile", width = 18)
    private String mobile;
    @ExcelExportColumn(order = 7, headerKey = "excel.user.email", width = 28)
    private String email;
    @ExcelExportColumn(order = 8, headerKey = "excel.user.status", width = 12)
    private String status;
    @ExcelExportColumn(order = 9, headerKey = "excel.user.locked", width = 12)
    private String locked;
    @ExcelExportColumn(order = 10, headerKey = "excel.user.lastLoginAt", width = 22)
    private LocalDateTime lastLoginAt;
    @ExcelExportColumn(order = 11, headerKey = "excel.user.lastLoginIp", width = 18)
    private String lastLoginIp;
    @ExcelExportColumn(order = 12, headerKey = "excel.user.remark", width = 28)
    private String remark;
    @ExcelExportColumn(order = 13, headerKey = "excel.user.createdAt", width = 22)
    private LocalDateTime createdAt;
}
