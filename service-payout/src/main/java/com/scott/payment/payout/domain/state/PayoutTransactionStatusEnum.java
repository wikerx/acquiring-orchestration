package com.scott.payment.payout.domain.state;

import lombok.Getter;

/**
 * 定义当前代付受理骨架允许返回的平台状态。
 *
 * <p>完整生命周期落地后应由独立状态机扩展状态及合法迁移，渠道原始状态不能直接覆盖平台状态。</p>
 */
@Getter
public enum PayoutTransactionStatusEnum {

    /**
     * 代付服务已接收交易，后续结果以查询、回调或通知为准。
     */
    RECEIVED("RECEIVED");

    /**
     * 对外和内部协议使用的稳定状态编码。
     */
    private final String code;

    /**
     * 创建代付交易状态。
     *
     * @param code 对外和内部接口传递的状态编码
     */
    PayoutTransactionStatusEnum(String code) {
        this.code = code;
    }
}
