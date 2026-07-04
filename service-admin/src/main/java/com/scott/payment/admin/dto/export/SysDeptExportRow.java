package com.scott.payment.admin.dto.export;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysDeptExportRow
 * @date : 2026-06-19 23:50
 * @email : scott_x@163.com
 * @description : 部门导出行对象
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysDeptExportRow
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Dept Export Row，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class SysDeptExportRow {

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 1, headerKey = "excel.dept.deptName", width = 24)
    private String deptName;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 2, headerKey = "excel.dept.parentName", width = 24)
    private String parentName;

    /**
     * 系统管理编码或编号字段，用于业务识别、查询和幂等关联。
     */
    @ExcelExportColumn(order = 3, headerKey = "excel.dept.sortNo", width = 10)
    private Integer sortNo;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 4, headerKey = "excel.dept.leader", width = 16)
    private String leader;

    /**
     * 系统管理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @ExcelExportColumn(order = 5, headerKey = "excel.dept.phone", width = 18)
    private String phone;

    /**
     * 系统管理邮箱字段，需满足邮箱格式校验，日志展示时应按敏感信息处理。
     */
    @ExcelExportColumn(order = 6, headerKey = "excel.dept.email", width = 24)
    private String email;

    /**
     * 系统管理状态字段，取值需与数据字典或枚举约定保持一致。
     */
    @ExcelExportColumn(order = 7, headerKey = "excel.dept.status", width = 12)
    private String status;
}
