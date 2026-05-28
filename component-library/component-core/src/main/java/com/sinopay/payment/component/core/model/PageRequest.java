package com.sinopay.payment.component.core.model;

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

    private static final long serialVersionUID = 1L;

    private int pageNo = 1;
    private int pageSize = 20;
}
