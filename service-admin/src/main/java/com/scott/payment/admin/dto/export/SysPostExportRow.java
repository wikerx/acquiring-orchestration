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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysPostExportRow
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Post Export Row，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class SysPostExportRow {

    /**
     * 系统管理编码或编号字段，用于业务识别、查询和幂等关联。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.post.postCode", width = 18)
    private String postCode;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.post.postName", width = 20)
    private String postName;

    /**
     * 系统管理编码或编号字段，用于业务识别、查询和幂等关联。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.post.sortNo", width = 10)
    private Integer sortNo;

    /**
     * 系统管理状态字段，取值需与数据字典或枚举约定保持一致。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.post.status", width = 12)
    private String status;

    /**
     * 系统管理备注字段，用于记录人工说明，不参与核心状态流转。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.post.remark", width = 24)
    private String remark;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.post.createdAt", width = 22)
    private LocalDateTime createdAt;
}
