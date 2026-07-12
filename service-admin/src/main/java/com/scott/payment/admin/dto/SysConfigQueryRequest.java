package com.scott.payment.admin.dto;

import com.scott.payment.component.core.model.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysConfigQueryRequest
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 系统参数配置查询请求
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysConfigQueryRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 系统管理Sys Config Query 请求对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysConfigQueryRequest extends PageRequest {

    /**
     * 参数名称，支持右模糊查询。
     */
    private String configName;

    /**
     * 参数键名，支持精确查询。
     */
    private String configKey;

    /**
     * 配置分组，支持精确查询。
     */
    private String configGroup;

    /**
     * 状态：0停用，1启用。
     */
    private Integer status;
}
