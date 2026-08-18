package com.scott.payment.openapi.service;

import com.scott.payment.openapi.dto.body.reference.CardBinLookupRequestDTO;
import com.scott.payment.openapi.dto.body.reference.IpLookupRequestDTO;
import com.scott.payment.openapi.vo.reference.CardBinLookupVO;
import com.scott.payment.openapi.vo.reference.IpLookupVO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiReferenceDataService
 * @date : 2026-08-11 15:44
 * @email : scott_x@163.com
 * @description : 商户基础数据检索服务契约，负责外部参数异常映射和最小响应字段输出
 * @status : create
 */
public interface OpenApiReferenceDataService {

    /**
     * 查询 IP 归属信息。
     *
     * @param requestDTO 解密后的商户请求
     * @return IP 归属响应
     */
    IpLookupVO queryIp(IpLookupRequestDTO requestDTO);

    /**
     * 查询卡 BIN 归属信息。
     *
     * @param requestDTO 解密后的商户请求
     * @return 卡 BIN 归属响应
     */
    CardBinLookupVO queryCardBin(CardBinLookupRequestDTO requestDTO);
}
