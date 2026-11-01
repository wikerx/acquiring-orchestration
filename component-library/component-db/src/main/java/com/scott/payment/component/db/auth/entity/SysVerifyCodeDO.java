package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态验证码数据库实体
 */
@Data
@TableName("sys_verify_code")
public class SysVerifyCodeDO {


    @TableId(type = IdType.AUTO)
    private Long id;


    private Long appId;

    /**
     * 持久化的场景，用于还原当前记录的业务事实。
     */
    private String scene;

    /**
     * {@code receiverType}，用于区分 {@code SysVerifyCodeDO} 记录的处理类别、配置维度或外部协议枚举。
     */
    private String receiverType;

    /**
     * 持久化的{@code receiver}，用于还原当前记录的业务事实。
     */
    private String receiver;


    private String codeHash;


    private String codeSalt;

    /**
     * 持久化的失效时刻，用于还原当前记录的业务事实。
     */
    private LocalDateTime expireAt;

    /**
     * 持久化的{@code used}，用于还原当前记录的业务事实。
     */
    private Integer used;

    /**
     * 持久化的{@code usedAt}，用于还原当前记录的业务事实。
     */
    private LocalDateTime usedAt;


    private Integer verifyCount;

    /**
     * 持久化的{@code sendIp}，用于还原当前记录的业务事实。
     */
    private String sendIp;

    /**
     * 持久化的{@code sendChannel}，用于还原当前记录的业务事实。
     */
    private String sendChannel;


    private Integer sendStatus;

    /**
     * 持久化的{@code sendFailReason}，用于还原当前记录的业务事实。
     */
    private String sendFailReason;

    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    private LocalDateTime createdAt;
}
