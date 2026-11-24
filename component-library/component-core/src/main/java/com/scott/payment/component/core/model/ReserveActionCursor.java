package com.scott.payment.component.core.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 保证金动作导出的续读位置。动作时间相同时按动作主键继续读取。
 */
public record ReserveActionCursor(LocalDateTime actionTime, long actionId) implements Serializable {

    public ReserveActionCursor {
        if (actionTime == null || actionId <= 0) {
            throw new IllegalArgumentException("reserve action cursor is invalid");
        }
    }
}
