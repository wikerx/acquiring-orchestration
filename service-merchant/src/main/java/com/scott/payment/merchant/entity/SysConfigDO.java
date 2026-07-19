package com.scott.payment.merchant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysConfigDO
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : 商户系统只读系统参数实体，位于 service-merchant 数据实体层，仅用于读取平台公共参数，不承担参数管理写入职责。
 * @status : create
 */
@Data
@TableName("sys_config")
public class SysConfigDO {

    /**
     * 主键ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 参数键名，全局唯一。
     */
    private String configKey;

    /**
     * 参数键值，支持普通文本或 JSON 字符串。
     */
    private String configValue;

    /**
     * 状态：0停用，1启用。
     */
    private Integer status;

    /**
     * 删除标识：0未删除，大于0为删除记录ID。
     */
    private Long deleted;
}
