package com.scott.payment.data.mapper;

import com.scott.payment.data.entity.DataMerchantNotificationLogDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataMerchantNotificationLogMapper
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : service-data 商户通知日志 Mapper，通过 ShardingSphere 逻辑表写入脱敏尝试记录。
 * @status : create
 */
public interface DataMerchantNotificationLogMapper {

    /**
     * 向商户通知日志逻辑表写入一次脱敏尝试记录。
     *
     * @param logDO 脱敏通知日志，必须携带原交易分片时间
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO transaction_merchant_notification_log
            (
              notify_log_id, notify_id, transaction_id, operation_id, merchant_id,
              attempt_no, target_url_hash, http_status, request_header_json_masked,
              request_body_json_masked, response_body_json_masked, success,
              error_message, notify_time, duration_millis, transaction_date_time,
              transaction_utc_time, transaction_time_zone, create_time
            )
            VALUES
            (
              #{logDO.notifyLogId}, #{logDO.notifyId}, #{logDO.transactionId},
              #{logDO.operationId}, #{logDO.merchantId}, #{logDO.attemptNo},
              #{logDO.targetUrlHash}, #{logDO.httpStatus}, #{logDO.requestHeaderJsonMasked},
              #{logDO.requestBodyJsonMasked}, #{logDO.responseBodyJsonMasked},
              #{logDO.success}, #{logDO.errorMessage}, #{logDO.notifyTime},
              #{logDO.durationMillis}, #{logDO.transactionDateTime}, #{logDO.transactionUtcTime},
              #{logDO.transactionTimeZone}, #{logDO.createTime}
            )
            """)
    int insert(@Param("logDO") DataMerchantNotificationLogDO logDO);

}
