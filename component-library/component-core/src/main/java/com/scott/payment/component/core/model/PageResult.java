package com.scott.payment.component.core.model;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;


@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PageResult
 * @date : 2026-05-28 09:28
 * @email : scott_x@163.com
 * @description : PageResult Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
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
