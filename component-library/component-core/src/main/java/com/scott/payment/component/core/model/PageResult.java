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
     * 当前页数据列表，无数据时返回空集合，避免调用方处理 null。
     */
    private List<T> records = Collections.emptyList();
}
