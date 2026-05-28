package com.scott.payment.component.mq.message;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : BaseMqMessage
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 基础消息体模型
 * @status : create
 */
public class BaseMqMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String messageId;
    private LocalDateTime createdAt;

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}

