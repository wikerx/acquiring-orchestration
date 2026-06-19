package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysConfigExportRow
 * @date : 2026-06-19 23:50
 * @email : scott_x@163.com
 * @description : 系统参数导出行对象
 * @status : create
 */
@Data
public class SysConfigExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.config.configName", width = 20)
    private String configName;

    @ExcelExportColumn(order = 2, headerKey = "excel.config.configKey", width = 24)
    private String configKey;

    @ExcelExportColumn(order = 3, headerKey = "excel.config.configGroup", width = 18)
    private String configGroup;

    @ExcelExportColumn(order = 4, headerKey = "excel.config.configValue", width = 28)
    private String configValue;

    @ExcelExportColumn(order = 5, headerKey = "excel.config.status", width = 12)
    private String status;

    @ExcelExportColumn(order = 6, headerKey = "excel.config.createdAt", width = 22)
    private LocalDateTime createdAt;
}
