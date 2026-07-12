package com.scott.payment.admin.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysDeptDTO
 * @date : 2026-06-12 20:00
 * @email : scott_x@163.com
 * @description : 部门树形传输对象
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysDeptDTO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Dept 数据传输对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class SysDeptDTO {

    /**
     * 主键 ID。
     */
    private Long id;

    /**
     * 父部门 ID。
     */
    private Long parentId;

    /**
     * 部门名称。
     */
    private String deptName;

    /**
     * 显示排序。
     */
    private Integer sortNo;

    /**
     * 负责人。
     */
    private String leader;

    /**
     * 联系电话。
     */
    private String phone;

    /**
     * 邮箱。
     */
    private String email;

    /**
     * 状态：0 停用，1 启用。
     */
    private Integer status;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 子部门列表。
     */
    private List<SysDeptDTO> children = new ArrayList<>();
}
