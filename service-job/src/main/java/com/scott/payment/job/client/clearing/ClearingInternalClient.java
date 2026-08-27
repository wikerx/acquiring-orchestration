package com.scott.payment.job.client.clearing;

import com.scott.payment.job.client.clearing.dto.ClearingCompensationClientDTOs.Request;
import com.scott.payment.job.client.clearing.dto.ClearingCompensationClientDTOs.Response;

/** 清分补偿内部客户端；Job 不直接写清分或交易表。 */
public interface ClearingInternalClient {
    Response scan(Request request);
}
