package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysDictTypeExportRow
 * @date : 2026-06-19 23:50
 * @email : scott_x@163.com
 * @description : 字典类型导出行对象
 * @status : create
 */
@Data
public class SysDictTypeExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.dict.dictName", width = 20)
    private String dictName;

    @ExcelExportColumn(order = 2, headerKey = "excel.dict.dictType", width = 20)
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
