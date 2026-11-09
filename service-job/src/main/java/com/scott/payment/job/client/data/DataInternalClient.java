package com.scott.payment.job.client.data;

import com.scott.payment.job.client.data.dto.DataMerchantNotificationNotifyClientRequestDTO;
import com.scott.payment.job.client.data.dto.DataMerchantNotificationNotifyDueClientRequestDTO;
import com.scott.payment.job.client.data.dto.DataMerchantNotificationReconcileClientRequestDTO;
import com.scott.payment.job.client.data.dto.DataExportTaskDispatchClientDTOs.Request;
import com.scott.payment.job.client.data.dto.DataExportTaskDispatchClientDTOs.Response;

/**
 * service-job 调用 service-data 异步数据补偿接口的客户端契约，当前承载商户通知到期重试
 */
public interface DataInternalClient {

    /**
     * 触发一轮异步导出任务领取、执行和过期文件清理。
     *
     * @param request 有界批次和调度时间
     * @return 本轮真实执行统计
     */
    Response dispatchExportTasks(Request request);

    /**
     * 触发指定交易时间分表中的到期商户通知补偿。
     *
     * @param requestDTO 商户通知补偿请求
     * @return 可靠入队数量
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
     * @return true 表示单笔补偿命令已可靠入队
     */
    Boolean notifyMerchantNotification(DataMerchantNotificationNotifyClientRequestDTO requestDTO);
}
