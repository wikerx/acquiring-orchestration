package com.scott.payment.job.client.data;

import com.scott.payment.job.client.data.dto.DataMerchantNotificationNotifyClientRequestDTO;
import com.scott.payment.job.client.data.dto.DataMerchantNotificationNotifyDueClientRequestDTO;
import com.scott.payment.job.client.data.dto.DataMerchantNotificationReconcileClientRequestDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataInternalClient
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : service-job 调用 service-data 异步数据补偿接口的客户端契约，当前承载商户通知到期重试
 * @status : create
 */
public interface DataInternalClient {

    /**
     * 触发指定交易时间分表中的到期商户通知补偿。
     *
     * @param requestDTO 商户通知补偿请求
     * @return 成功通知数量
     */
    Integer notifyDueMerchantNotifications(DataMerchantNotificationNotifyDueClientRequestDTO requestDTO);

    /**
     * 触发全部或指定季度的到期通知 MQ 对账。
     *
     * @param requestDTO 对账请求
     * @return 可靠入队事件数量
     */
    Integer reconcileDueMerchantNotifications(DataMerchantNotificationReconcileClientRequestDTO requestDTO);

    /**
     * 精确重试单笔商户通知，分片时间必须由调用方显式传入。
     *
     * @param requestDTO 单笔商户通知补偿请求
     * @return true 表示商户端点返回 2xx
     */
    Boolean notifyMerchantNotification(DataMerchantNotificationNotifyClientRequestDTO requestDTO);
}
