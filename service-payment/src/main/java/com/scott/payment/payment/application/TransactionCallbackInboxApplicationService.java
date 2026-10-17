package com.scott.payment.payment.application;

import com.scott.payment.payment.api.internal.dto.TransactionCallbackInboxAcceptResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackCommandDTO;
import com.scott.payment.payment.service.TransactionCallbackInboxService;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionCallbackInboxApplicationService
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : 渠道回调 Inbox 应用服务，位于 service-payment 应用层，作为内部 HTTP 入口与可靠受理服务之间的唯一编排边界。
 * @status : create
 */
@Service
public class TransactionCallbackInboxApplicationService {

    private final TransactionCallbackInboxService inboxService;

    /** 创建渠道回调 Inbox 应用服务。 */
    public TransactionCallbackInboxApplicationService(TransactionCallbackInboxService inboxService) {
        this.inboxService = inboxService;
    }

    /**
     * 可靠受理已验证回调；只有底层事务提交成功或确认数据库重复后才返回。
     *
     * @param commandDTO 已验证标准事件
     * @return Inbox 受理结果
     */
    public TransactionCallbackInboxAcceptResultDTO accept(TransactionChannelCallbackCommandDTO commandDTO) {
        return inboxService.accept(commandDTO);
    }
}
