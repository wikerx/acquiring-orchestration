package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionCallbackInboxAcceptResultDTO;
import com.scott.payment.payment.entity.TransactionCallbackInboxDO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionCallbackInboxService
 * @date : 2026-10-17 12:00
 * @email : scott_x@163.com
 * @description : 渠道回调 Inbox 服务，位于 service-payment 服务层，负责提交前持久化、数据库幂等和异步消费状态 CAS。
 * @status : create
 */
public interface TransactionCallbackInboxService {

    /** 可靠受理已完成验签和 IP 校验的标准事件。 */
    TransactionCallbackInboxAcceptResultDTO accept(TransactionChannelCallbackCommandDTO commandDTO);

    /** 查询到期待处理记录，limit 会在实现层限制上限。 */
    List<TransactionCallbackInboxDO> listDue(LocalDateTime now, int limit);

    /** 使用版本号 CAS 抢占单条 Inbox 记录。 */
    boolean claim(TransactionCallbackInboxDO inbox, LocalDateTime now);

    /** 使用抢占后的版本号 CAS 标记处理完成。 */
    boolean markProcessed(TransactionCallbackInboxDO inbox, LocalDateTime now);

    /** 使用抢占后的版本号 CAS 记录失败、安排重试或封顶关闭。 */
    boolean markFailed(TransactionCallbackInboxDO inbox, Throwable failure, LocalDateTime now);

    /** 分批恢复超过处理租约的 PROCESSING 记录，limit 会在实现层限制上限。 */
    int recoverStaleProcessing(LocalDateTime staleBefore, LocalDateTime now, int limit);
}
