package com.scott.payment.component.db.systemconfig.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SystemConfigDO
 * @date : 2026-08-11 00:00
 * @email : scott_x@163.com
 * @description : 跨服务系统参数只读数据库实体，只负责从 sys_config 构建公共缓存快照
 * @status : create
 */
@Data
@TableName("sys_config")
public class SystemConfigDO {

    /** 数据库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 管理端展示名称。 */
    private String configName;

    /** 全局唯一参数键名。 */
    private String configKey;

    /** 数据库原始配置值；encrypted=1 时保持密文，不在公共读取层解密。 */
    private String configValue;

    /** 值类型：1 字符串、2 数字、3 布尔、4 JSON。 */
    private Integer valueType;

    /** 配置业务分组。 */
    private String configGroup;

    /** 是否系统内置。 */
    private Integer systemBuiltin;

    /** 是否允许前端展示。 */
    private Integer visible;

    /** 是否加密存储。 */
    private Integer encrypted;

    /** 启停状态。 */
    private Integer status;

    /** 配置维护说明。 */
    private String remark;

    /** 创建人。 */
    private String createdBy;

    /** 最近更新人。 */
    private String updatedBy;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 最近更新时间。 */
    private LocalDateTime updatedAt;

    /** 逻辑删除标识，0 表示未删除。 */
    private Long deleted;
}
