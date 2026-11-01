package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统参数导出行对象
 */
@Data
public class SysConfigExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.config.configName", width = 20)
    private String configName;

    /**
     * 系统管理敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.config.configKey", width = 24)
    private String configKey;

    @ExcelExportColumn(order = 3, headerKey = "excel.config.configGroup", width = 18)
    private String configGroup;

    @ExcelExportColumn(order = 4, headerKey = "excel.config.configValue", width = 28)
    private String configValue;

    @ExcelExportColumn(order = 5, headerKey = "excel.config.status", width = 12)
    private String status;

    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.config.createdAt", width = 22)
    private LocalDateTime createdAt;
}
