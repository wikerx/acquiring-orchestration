package com.scott.payment.component.core.model;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PageRequest
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 分页请求参数模型
 * @status : create
 */
@Data
public class PageRequest implements Serializable {

    /**
     * 序列化版本号，用于保证分页请求对象在服务间传输时的反序列化兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 当前页码，从 1 开始计数，默认查询第一页。
     */
    private int pageNo = 1;

    /**
     * 每页记录数，默认 20 条，调用方应根据接口约束限制最大值。
     */
    private int pageSize = 20;
}
