package com.scott.payment.payment.service.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelTransactionStatusResolution
 * @date : 2026-07-19 22:00
 * @email : scott_x@163.com
 * @description : 渠道结果到平台交易状态的解析结果，位于 service-payment 服务 DTO 层，用于让同步响应、渠道回调和查询勾兑复用同一套状态语义；该对象不代表状态已经落库。
 * @status : create
 */
@Data
public class ChannelTransactionStatusResolution implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 目标平台交易状态；为空表示渠道结果尚不能映射，非终态表示只能继续等待回调或查询。
     */
    private String targetStatus;

    /**
     * 目标内部处理阶段。
     */
    private String processStage;

    /**
     * 挂起原因码，仅 PENDING 状态使用。
     */
    private String pendingReasonCode;

    /**
     * 失败原因码，仅 FAILED 状态使用。
     */
    private String failReasonCode;

    /**
     * 后台可见失败原因描述。
     */
    private String failReasonMessage;

    /**
     * 渠道原始状态。
     */
    private String channelStatus;

    /**
     * 渠道响应码。
     */
    private String channelResponseCode;

    /**
     * 渠道响应描述。
     */
    private String channelResponseMessage;

    /**
     * 是否已经解析出可直接推进平台状态的结果。
     *
     * @return true 表示 targetStatus 不为空
     */
    public boolean resolved() {
        return targetStatus != null && !targetStatus.isBlank();
    }
}
