package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典数据导出行对象
 */
@Data
public class SysDictDataExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.dictData.dictType", width = 20)
    private String dictType;

    @ExcelExportColumn(order = 2, headerKey = "excel.dictData.dictLabel", width = 22)
    private String dictLabel;

    @ExcelExportColumn(order = 3, headerKey = "excel.dictData.dictValue", width = 22)
    private String dictValue;

    /**
     * 语言区域标识，用于选择国际化文案和导出标题。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.dictData.locale", width = 14)
    private String locale;

    /**
     * 排序号，数值越小越优先展示或匹配。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.dictData.sortNo", width = 10)
    private Integer sortNo;

    @ExcelExportColumn(order = 6, headerKey = "excel.dictData.status", width = 12)
    private String status;

    @ExcelExportColumn(order = 7, headerKey = "excel.dictData.defaultFlag", width = 12)
    private String defaultFlag;

    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    @ExcelExportColumn(order = 8, headerKey = "excel.dictData.createdAt", width = 22)
    private LocalDateTime createdAt;
}
