package com.scott.payment.component.db.entity;

import lombok.Data;

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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : BaseEntity
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Base Entity，位于 component-library/component-db 的数据实体层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class BaseEntity implements Serializable {

    /**
     * 序列化版本号，用于保证数据库实体在缓存、消息或 RPC 场景下的反序列化兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 数据库主键 ID，默认作为实体唯一标识。
     */
    private Long id;

    /**
     * 记录创建时间，通常由数据库或 MyBatis 自动填充。
     */
    private LocalDateTime gmtCreate;

    /**
     * 记录最后更新时间，通常由数据库或 MyBatis 自动填充。
     */
    private LocalDateTime gmtModified;
}
