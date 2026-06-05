package com.scott.payment.admin.dto;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysConfigQueryRequest
 * @date : 2026-06-05 00:00
 * @email : scott_x@163.com
 * @description : 系统参数配置查询请求
 * @status : create
 */
@Data
public class SysConfigQueryRequest {

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
