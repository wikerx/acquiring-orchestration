package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SysVerifyCodeDO
 * @date : 2026-06-06 00:00
 * @description : 动态验证码数据库实体
 * @status : create
 */
@Data
@TableName("sys_verify_code")
public class SysVerifyCodeDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long appId;

    private String scene;

    private String receiverType;

    private String receiver;

    private String codeHash;

    private String codeSalt;

    private LocalDateTime expireAt;

    private Integer used;

    private LocalDateTime usedAt;

    private Integer verifyCount;

    private String sendIp;

    private String sendChannel;

    private Integer sendStatus;

    private String sendFailReason;

    private LocalDateTime createdAt;
}
