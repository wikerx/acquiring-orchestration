package com.scott.payment.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysConfigSaveRequest
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 系统参数配置保存请求
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysConfigSaveRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Config Save 请求对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class SysConfigSaveRequest {

    /**
     * 参数名称，用于后台列表展示。
     */
    @NotBlank(message = "configName不能为空")
    private String configName;

    /**
     * 参数键名，全局唯一。
     */
    @NotBlank(message = "configKey不能为空")
    private String configKey;

    /**
     * 参数键值。
     */
    private String configValue;

    /**
     * 值类型：1字符串，2数字，3布尔，4JSON。
     */
    @NotNull(message = "valueType不能为空")
    private Integer valueType;

    /**
     * 配置分组。
     */
    private String configGroup;

    /**
     * 是否系统内置：0否，1是。
     */
    private Integer systemBuiltin;

    /**
     * 是否前端可见：0否，1是。
     */
    private Integer visible;

    /**
     * 是否加密存储：0否，1是。
     */
    private Integer encrypted;

    /**
     * 状态：0停用，1启用。
     */
    private Integer status;

    /**
     * 备注说明。
     */
    private String remark;

    /**
     * 当前操作人。
     */
    private String operator;
}
