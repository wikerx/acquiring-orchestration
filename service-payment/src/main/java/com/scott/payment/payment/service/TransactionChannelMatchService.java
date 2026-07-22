package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchResultDTO;

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
}
