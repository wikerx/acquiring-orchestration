package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Data 生成管理端字典类型 Excel 的独立行模型，类型编码强制按文本写入。
 */
@Data
public class DictTypeExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.dict.dictName", width = 20)
    private String dictName;
    @ExcelExportColumn(order = 2, headerKey = "excel.dict.dictType", width = 20, forceText = true)
    private String dictType;
    @ExcelExportColumn(order = 3, headerKey = "excel.dict.bizDomain", width = 20)
    private String bizDomain;
    @ExcelExportColumn(order = 4, headerKey = "excel.dict.status", width = 12)
    private String status;
    @ExcelExportColumn(order = 5, headerKey = "excel.dict.systemBuiltin", width = 12)
    private String systemBuiltin;
    @ExcelExportColumn(order = 6, headerKey = "excel.dict.createdAt", width = 22)
    private LocalDateTime createdAt;
}
