package com.scott.payment.data.service.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Data 生成管理端岗位 Excel 的独立行模型，岗位编码保持文本语义。
 */
@Data
public class PostExportRow {
    @ExcelExportColumn(order = 1, headerKey = "excel.post.postCode", width = 18, forceText = true)
    private String postCode;
    @ExcelExportColumn(order = 2, headerKey = "excel.post.postName", width = 20)
    private String postName;
    @ExcelExportColumn(order = 3, headerKey = "excel.post.sortNo", width = 10)
    private Integer sortNo;
    @ExcelExportColumn(order = 4, headerKey = "excel.post.status", width = 12)
    private String status;
    @ExcelExportColumn(order = 5, headerKey = "excel.post.remark", width = 24)
    private String remark;
    @ExcelExportColumn(order = 6, headerKey = "excel.post.createdAt", width = 22)
    private LocalDateTime createdAt;
}
