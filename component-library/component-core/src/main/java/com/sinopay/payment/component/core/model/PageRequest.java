package com.sinopay.payment.component.core.model;

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
public class PageRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private int pageNo = 1;
    private int pageSize = 20;

    public int getPageNo() {
        return pageNo;
    }

    public void setPageNo(int pageNo) {
        this.pageNo = pageNo;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}

