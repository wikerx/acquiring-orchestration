package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 基础商户JWT密钥持久化模型，位于 公共组件库，映射数据库中的业务事实、状态、版本和审计字段，不作为外部接口模型。
 */
@Data
@TableName("base_merchant_jwt_key")
public class BaseMerchantJwtKeyDO {


    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
     */
    private String merchantId;

    /**
     * 收单支付敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private String keyVersion;

    /**
     * 收单支付敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
     */
    private String merchantKey;

    /**
     * 卡数据混合加密协议标识，调用双方必须使用完全一致的算法组合。
     */
    private String algorithm;

    /**
     * 持久化的{@code expiresSeconds}，用于还原当前记录的业务事实。
     */
    private Long expiresSeconds;

    /**
     * 启用标识，表示当前配置项或业务能力的启停开关。
     */
    private Integer enabled;

    /**
     * 业务配置或汇率开始生效的具体时刻。
     */
    private LocalDateTime effectiveTime;

    /**
     * 业务配置、令牌或缓存条目的失效时刻。
     */
    private LocalDateTime expireTime;

    /**
     * 持久化的{@code gmtCreate}，用于还原当前记录的业务事实。
     */
    private LocalDateTime gmtCreate;

    /**
     * 持久化的{@code gmtModified}，用于还原当前记录的业务事实。
     */
    private LocalDateTime gmtModified;

    /**
     * 逻辑删除标识；0 表示有效，1 表示已删除，查询必须沿用统一软删除口径。
     */
    private Integer deleted;
}
