package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchResultDTO;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionChannelMatchService
 * @date : 2026-07-19 22:20
 * @email : scott_x@163.com
 * @description : 渠道交易查询勾兑服务，位于 service-payment 服务层，用于定时任务查询渠道状态并通过状态机推进最终结果。
 * @status : create
 */
public interface TransactionChannelMatchService {

    /**
     * 处理待渠道查询确认的交易动作。
     *
     * @param commandDTO 查询勾兑命令
     * @return 本次处理结果
     */
    TransactionChannelMatchResultDTO matchDue(TransactionChannelMatchCommandDTO commandDTO);

    /**
     * 使用真实分片时间主动查询单笔交易。
     *
     * @param transactionId 平台交易号
     * @param transactionDateTime 动作真实分片时间
     * @return 单笔勾兑处理结果
     */
    default TransactionChannelMatchResultDTO matchOne(String transactionId,
                                                       LocalDateTime transactionDateTime) {
        return new TransactionChannelMatchResultDTO();
    }
}
