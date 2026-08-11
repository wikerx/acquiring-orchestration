package com.scott.payment.component.db.reference.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IpLibraryShardDO
 * @date : 2026-08-11 15:35
 * @email : scott_x@163.com
 * @description : IP 库只读分片配置实体，供公共基础数据检索服务选择当前已就绪的数据分表
 * @status : create
 */
@Data
@TableName("ip_library_split_model")
public class IpLibraryShardDO {

    /** 分片配置主键，不允许为空，非敏感字段。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** IP 类型，格式为 IPV4 或 IPV6，不允许为空，非敏感字段。 */
    private String ipType;

    /** 分片编号，范围为 1 至 8，不允许为空，非敏感字段。 */
    private Integer shardNo;

    /** 物理分表名，不允许为空，仅可在服务端白名单内使用。 */
    private String tableName;

    /** 当前生效数据版本，不允许为空，非敏感字段。 */
    private String dataVersion;

    /** 生效标识，1 表示生效，不允许为空，非敏感字段。 */
    private Integer activeFlag;

    /** 装载状态，READY 表示可查询，不允许为空，非敏感字段。 */
    private String loadStatus;
}
