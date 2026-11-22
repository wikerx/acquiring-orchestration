package com.scott.payment.payment.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.db.sharding.TransactionPrimaryRouteScope;
import com.scott.payment.payment.entity.TransactionChannelRequestDO;
import com.scott.payment.payment.mapper.TransactionChannelRequestMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** 读取准备阶段冻结的 Scott 参考号，并在首次外呼前冻结回调地址。 */
@Service
public class ScottChannelRequestIdentityStore {
    private final TransactionChannelRequestMapper requestMapper;

    public ScottChannelRequestIdentityStore(TransactionChannelRequestMapper requestMapper) {
        this.requestMapper = requestMapper;
    }

    @DS(DataSourceName.TRANSACTION)
    public TransactionChannelRequestDO freezeNotifyUrl(String requestId, LocalDateTime time, String notifyUrl) {
        try (TransactionPrimaryRouteScope ignored = TransactionPrimaryRouteScope.open()) {
            TransactionChannelRequestDO request = requestMapper.selectByRequestId(requestId, time);
            if (request != null && request.getChannelMerchantReference() != null
                    && request.getChannelNotifyUrl() == null) {
                requestMapper.freezeScottNotifyUrl(requestId, time, notifyUrl == null ? "" : notifyUrl);
                return requestMapper.selectByRequestId(requestId, time);
            }
            return request;
        }
    }
}
