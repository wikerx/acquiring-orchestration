package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackResultDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionCallbackService
 * @date : 2026-07-14 22:40
 * @email : scott_x@163.com
 * @description : 交易渠道回调服务，位于 service-payment 服务层，负责渠道回调原文审计、业务幂等记录和后续状态机推进入口。
 * @status : create
 */
public interface TransactionCallbackService {

    /**
     * 记录渠道回调并生成业务处理记录。
     *
     * @param commandDTO 渠道回调内部命令
     * @return 回调处理结果
     */
    TransactionChannelCallbackResultDTO recordChannelCallback(TransactionChannelCallbackCommandDTO commandDTO);
}
