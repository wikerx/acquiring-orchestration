package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysUserAccountExportRow
 * @date : 2026-06-19 23:50
 * @email : scott_x@163.com
 * @description : 后台用户导出行对象
 * @status : create
 */
@Data
public class SysUserAccountExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.user.loginAccount", width = 20)
    private String loginAccount;

    @ExcelExportColumn(order = 2, headerKey = "excel.user.realName", width = 18)
    private String realName;

    @ExcelExportColumn(order = 3, headerKey = "excel.user.mobile", width = 18)
    private String mobile;

    @ExcelExportColumn(order = 4, headerKey = "excel.user.email", width = 28)
    private String email;

    @ExcelExportColumn(order = 5, headerKey = "excel.user.status", width = 12)
    private String status;

    @ExcelExportColumn(order = 6, headerKey = "excel.user.locked", width = 12)
    private String locked;

    @ExcelExportColumn(order = 7, headerKey = "excel.user.lastLoginAt", width = 22)
    private LocalDateTime lastLoginAt;

    @ExcelExportColumn(order = 8, headerKey = "excel.user.lastLoginIp", width = 18)
    private String lastLoginIp;

    @ExcelExportColumn(order = 9, headerKey = "excel.user.createdAt", width = 22)
    private LocalDateTime createdAt;
}
