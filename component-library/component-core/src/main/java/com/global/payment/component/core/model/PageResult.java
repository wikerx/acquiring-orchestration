package com.global.payment.component.core.model;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

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

