package com.scott.payment.component.core.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PageResult
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 分页响应结果模型
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PageResult
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Page Result，位于 component-library/component-core 的业务组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class PageResult<T> implements Serializable {

    /**
     * 序列化版本号，用于保证分页响应对象在服务间传输时的反序列化兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 满足查询条件的总记录数，用于前端或调用方计算总页数。
     */
    private long total;

    /**
     * 当前页码，从 1 开始。
     */
    private long pageNo;

    /**
     * 每页记录数。
     */
    private long pageSize;

    /**
     * 总页数。
     */
    private long pages;

    /**
     * 当前页数据列表，无数据时返回空集合，避免调用方处理 null。
     */
    private List<T> records = Collections.emptyList();

    /**
     * 构建分页响应。
     *
     * @param total    总记录数
     * @param pageNo   当前页码
     * @param pageSize 每页记录数
     * @param records  当前页记录
     * @param <T>      记录类型
     * @return 分页响应
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param total 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param pageNo 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param pageSize 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param records 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public static <T> PageResult<T> of(long total, long pageNo, long pageSize, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(total);
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        result.setPages(pageSize <= 0 ? 0 : (total + pageSize - 1) / pageSize);
        result.setRecords(records == null ? Collections.emptyList() : records);
        return result;
    }
}
