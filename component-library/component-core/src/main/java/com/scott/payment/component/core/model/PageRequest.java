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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PageRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Page 请求对象，位于 component-library/component-core 的业务组件层，用于承载该模块对应的业务职责和数据流转边界。
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

    /**
     * 获取安全页码。前端传入 0 或负数时兜底为第一页。
     *
     * @return 安全页码
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    public long safePageNo() {
        return Math.max(pageNo, 1);
    }

    /**
     * 获取安全每页记录数。单页最大限制为 500，避免后台列表误查大结果集。
     *
     * @return 安全每页记录数
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    public long safePageSize() {
        if (pageSize <= 0) {
            return 20L;
        }
        return Math.min(pageSize, 500);
    }
}
