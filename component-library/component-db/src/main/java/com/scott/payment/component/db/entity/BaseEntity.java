package com.scott.payment.component.db.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : BaseEntity
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 数据库实体基础字段模型
 * @status : create
 */
public class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private LocalDateTime gmtCreate;
    private LocalDateTime gmtModified;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getGmtCreate() {
        return gmtCreate;
    }

    public void setGmtCreate(LocalDateTime gmtCreate) {
        this.gmtCreate = gmtCreate;
    }

    public LocalDateTime getGmtModified() {
        return gmtModified;
    }

    public void setGmtModified(LocalDateTime gmtModified) {
        this.gmtModified = gmtModified;
    }
}

