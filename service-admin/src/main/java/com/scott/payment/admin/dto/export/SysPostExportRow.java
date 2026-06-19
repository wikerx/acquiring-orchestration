package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysPostExportRow
 * @date : 2026-06-19 23:50
 * @email : scott_x@163.com
 * @description : 岗位导出行对象
 * @status : create
 */
@Data
public class SysPostExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.post.postCode", width = 18)
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
