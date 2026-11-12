package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Data 生成管理端字典数据 Excel 的独立行模型，类型和值按文本写入避免前导零丢失。
 */
@Data
public class DictDataExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.dictData.dictType", width = 20, forceText = true)
    private String dictType;
    @ExcelExportColumn(order = 2, headerKey = "excel.dictData.dictLabel", width = 22)
    private String dictLabel;
    @ExcelExportColumn(order = 3, headerKey = "excel.dictData.dictValue", width = 22, forceText = true)
    private String dictValue;
    @ExcelExportColumn(order = 4, headerKey = "excel.dictData.locale", width = 14)
    private String locale;
    @ExcelExportColumn(order = 5, headerKey = "excel.dictData.sortNo", width = 10)
    private Integer sortNo;
    @ExcelExportColumn(order = 6, headerKey = "excel.dictData.status", width = 12)
    private String status;
    @ExcelExportColumn(order = 7, headerKey = "excel.dictData.defaultFlag", width = 12)
    private String defaultFlag;
    @ExcelExportColumn(order = 8, headerKey = "excel.dictData.createdAt", width = 22)
    private LocalDateTime createdAt;
}
