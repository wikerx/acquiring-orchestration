package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Data 生成管理端系统参数 Excel 的独立行模型。
 *
 * <p>参数值必须由 Admin 在跨服务前完成脱敏，Data 不持有也不恢复敏感原值。</p>
 */
@Data
public class SystemConfigExportRow {
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
