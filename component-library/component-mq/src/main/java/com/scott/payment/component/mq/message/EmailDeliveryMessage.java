package com.scott.payment.component.mq.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 邮件异步投递定位消息，只携带发送记录标识，不携带 SMTP 密码、收件地址或真实邮件正文
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EmailDeliveryMessage extends BaseMqMessage {

    /**
     * 邮件发送记录主键。
     */
    private Long recordId;
    /**
     * 邮件流水号，用于与主键共同校验消息归属。
     */
    private String emailNo;
    /**
     * 应用编码，用于选择 Admin 或 Merchant 消费边界。
     */
    private String appCode;
}
