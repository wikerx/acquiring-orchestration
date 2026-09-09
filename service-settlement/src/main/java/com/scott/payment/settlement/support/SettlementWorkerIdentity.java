package com.scott.payment.settlement.support;

import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementWorkerIdentity
 * @date : 2026-08-26 23:50
 * @email : scott_x@163.com
 * @description : 当前 service-settlement 进程的稳定租约所有者标识；只用于数据库处理租约，不作为业务幂等键。
 * @status : create
 */
@Component
public class SettlementWorkerIdentity {

    private final String value = "service-settlement:" + UUID.randomUUID();

    /** @return 当前进程生命周期内稳定且不超过数据库长度的所有者标识 */
    public String value() {
        return value;
    }
}
