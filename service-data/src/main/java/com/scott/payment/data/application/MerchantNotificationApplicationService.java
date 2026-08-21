package com.scott.payment.data.application;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.data.api.internal.dto.MerchantNotificationNotifyCommandDTO;
import com.scott.payment.data.api.internal.dto.MerchantNotificationNotifyDueCommandDTO;
import com.scott.payment.data.api.internal.dto.MerchantNotificationReconcileCommandDTO;
import com.scott.payment.data.service.impl.MerchantNotificationRetryReconciliationService;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationApplicationService
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : service-data 商户通知应用编排，校验内部补偿命令并限制单次扫描规模
 * @status : create
 */
@Service
public class MerchantNotificationApplicationService {

    /** 未显式指定时的单分表补偿批量。 */
    private static final int DEFAULT_LIMIT = 5;

    /** 单次内部补偿允许处理的最大任务数。 */
    private static final int MAX_LIMIT = 5;

    /** 到期任务低频 MQ 对账服务。 */
    private final MerchantNotificationRetryReconciliationService reconciliationService;

    /**
     * 创建商户通知应用服务。
     *
     * @param reconciliationService 只负责重新可靠入 MQ 的通知对账服务
     */
    public MerchantNotificationApplicationService(
            MerchantNotificationRetryReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    /**
     * 执行一个交易时间分片中的到期商户通知补偿。
     *
     * @param commandDTO 补偿命令
     * @return 本次可靠入队数量
     */
    public int notifyDue(MerchantNotificationNotifyDueCommandDTO commandDTO) {
        if (commandDTO == null || commandDTO.getTransactionDateTime() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_date_time is required");
        }
        int limit = normalizeLimit(commandDTO.getLimit());
        return reconciliationService.reconcile(limit, java.util.List.of(commandDTO.getTransactionDateTime()));
    }

    /**
     * 按平台交易号和上游传入的交易时间精确重试一条通知。
     *
     * @param commandDTO 单笔通知补偿命令
     * @return true 表示补偿命令已可靠入队，false 表示任务不存在或尚未到期
     */
    public boolean notifyTransaction(MerchantNotificationNotifyCommandDTO commandDTO) {
        if (commandDTO == null || commandDTO.getTransactionDateTime() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_date_time is required");
        }
        if (commandDTO.getTransactionId() == null || commandDTO.getTransactionId().isBlank()) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_id is required");
        }
        return reconciliationService.reconcileTransaction(
                commandDTO.getTransactionId(), commandDTO.getTransactionDateTime());
    }

    /**
     * 将全部或指定季度的到期任务重新可靠入 MQ，不直接访问商户端点。
     *
     * @param commandDTO 对账命令
     * @return 可靠入队事件数量
     */
    public int reconcileDue(MerchantNotificationReconcileCommandDTO commandDTO) {
        int limit = normalizeLimit(commandDTO == null ? null : commandDTO.getLimit());
        return reconciliationService.reconcile(
                limit,
                commandDTO == null ? null : commandDTO.getTransactionDateTimes());
    }

    /** 校验并限制单次补偿批量。 */
    private int normalizeLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit <= 0) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID.getCode(), "limit must be greater than zero");
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
