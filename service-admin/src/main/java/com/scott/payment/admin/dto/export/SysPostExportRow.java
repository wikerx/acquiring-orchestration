package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 岗位导出行对象
 */
@Data
public class SysPostExportRow {

    @ExcelExportColumn(order = 1, headerKey = "excel.post.postCode", width = 18)
    private String postCode;

    @ExcelExportColumn(order = 2, headerKey = "excel.post.postName", width = 20)
    private String postName;

    /**
     * 排序号，数值越小越优先展示或匹配。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.post.sortNo", width = 10)
    private Integer sortNo;

    @ExcelExportColumn(order = 4, headerKey = "excel.post.status", width = 12)
    private String status;

    /**
     * 系统管理备注字段，用于记录人工说明，不参与核心状态流转。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.post.remark", width = 24)
    private String remark;

    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.post.createdAt", width = 22)
    private LocalDateTime createdAt;
}
