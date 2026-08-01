package com.scott.payment.job.client.data;

import com.scott.payment.job.client.data.dto.DataMerchantNotificationNotifyDueClientRequestDTO;

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
}
