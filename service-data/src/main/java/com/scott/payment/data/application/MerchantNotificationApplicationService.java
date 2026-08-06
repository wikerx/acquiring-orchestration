package com.scott.payment.data.application;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.data.api.internal.dto.MerchantNotificationNotifyCommandDTO;
import com.scott.payment.data.api.internal.dto.MerchantNotificationNotifyDueCommandDTO;
import com.scott.payment.data.service.MerchantNotificationDeliveryService;
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

    /** 商户通知投递服务。 */
    private final MerchantNotificationDeliveryService deliveryService;

    /**
     * 创建商户通知应用服务。
     *
     * @param deliveryService 商户通知投递服务
     */
    public MerchantNotificationApplicationService(MerchantNotificationDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    /**
     * 执行一个交易时间分片中的到期商户通知补偿。
     *
     * @param commandDTO 补偿命令
     * @return 本次成功通知数量
     */
    public int notifyDue(MerchantNotificationNotifyDueCommandDTO commandDTO) {
        if (commandDTO == null || commandDTO.getTransactionDateTime() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_date_time is required");
        }
        int limit = normalizeLimit(commandDTO.getLimit());
        return deliveryService.notifyDue(commandDTO.getTransactionDateTime(), limit);
    }

    /**
     * 按平台交易号和上游传入的交易时间精确重试一条通知。
     *
     * @param commandDTO 单笔通知补偿命令
     * @return true 表示本次回调成功，false 表示任务未到期、不存在或回调失败
     */
    public boolean notifyTransaction(MerchantNotificationNotifyCommandDTO commandDTO) {
        if (commandDTO == null || commandDTO.getTransactionDateTime() == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_date_time is required");
        }
        if (commandDTO.getTransactionId() == null || commandDTO.getTransactionId().isBlank()) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "transaction_id is required");
        }
        return deliveryService.notifyTransaction(commandDTO.getTransactionDateTime(), commandDTO.getTransactionId());
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
