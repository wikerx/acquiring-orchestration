package com.scott.payment.component.core.model;

import lombok.Data;

import java.io.Serializable;


@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PageRequest
 * @date : 2026-05-28 09:28
 * @email : scott_x@163.com
 * @description : PageRequest 接口传输模型，用于约束请求入参、响应字段和跨层数据边界，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
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

    /**
     * 获取安全页码。前端传入 0 或负数时兜底为第一页。
     *
     * @return 安全页码
     */
    public long safePageNo() {
        return Math.max(pageNo, 1);
    }

    /**
     * 获取安全每页记录数。单页最大限制为 500，避免后台列表误查大结果集。
     *
     * @return 安全每页记录数
     */
    public long safePageSize() {
        if (pageSize <= 0) {
            return 20L;
        }
        return Math.min(pageSize, 500);
    }
}
