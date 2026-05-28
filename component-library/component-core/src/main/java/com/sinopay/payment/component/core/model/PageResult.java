package com.sinopay.payment.component.core.model;

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
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private long total;
    private List<T> records = Collections.emptyList();

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }
}

