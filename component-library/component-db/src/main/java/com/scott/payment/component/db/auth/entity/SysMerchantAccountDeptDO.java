package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_merchant_account_dept")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysMerchantAccountDeptDO
 * @date : 2026-06-23 12:55
 * @email : scott_x@163.com
 * @description : Sys Merchant Account Dept DO 持久化模型，位于 公共组件库，映射数据库记录字段，承载主键、业务标识、状态、时间和审计信息。
 * @status : create
 */
public class SysMerchantAccountDeptDO {

    @TableId(type = IdType.AUTO)
    /**
     * Sys Merchant Account Dept DO 数据库主键，用于唯一标识当前记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private Long id;
    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：与 merchantOrderNo、transactionId 共同限定商户交易归属。
     * </p>
     */
    private String merchantId;
    /**
     * account ID，用于定位 Sys Merchant Account Dept DO 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；可识别字段，日志输出必须脱敏或截断。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private Long accountId;
    /**
     * dept ID，用于定位 Sys Merchant Account Dept DO 关联的上游配置、渠道、账号、角色或业务记录。
     * <p>
     * 单位：无；格式：业务编号字符串；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：长度、唯一性和可空性由接口校验或数据库唯一约束限制；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private Long deptId;
    /**
     * created At，用于保存 Sys Merchant Account Dept DO 中与 createdat 相关的业务属性。
     * <p>
     * 单位：系统业务时区时间；格式：ISO 日期或日期时间；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：时间范围由业务流程或查询条件限定；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private LocalDateTime createdAt;
    /**
     * created By，用于保存 Sys Merchant Account Dept DO 中与 createdby 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：数据库表记录或持久化写入对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private Long createdBy;
}
