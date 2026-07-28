package com.scott.payment.openapi.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统参数配置数据库实体，service-openapi 仅按白名单 key 读取平台运行配置。
 */
@Data
@TableName("sys_config")
public class SysConfigDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String configName;
    private String configKey;
    private String configValue;
    private Integer valueType;
    private String configGroup;
    private Integer systemBuiltin;
    private Integer visible;
    private Integer encrypted;
    private Integer status;
    private String remark;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long deleted;
}
