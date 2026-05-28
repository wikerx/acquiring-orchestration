package com.global.payment.component.mq.message;

import java.io.Serializable;
import java.time.LocalDateTime;

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

