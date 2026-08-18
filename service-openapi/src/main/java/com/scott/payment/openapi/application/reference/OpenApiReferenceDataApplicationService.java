package com.scott.payment.openapi.application.reference;

import com.scott.payment.openapi.dto.body.reference.CardBinLookupRequestDTO;
import com.scott.payment.openapi.dto.body.reference.IpLookupRequestDTO;
import com.scott.payment.openapi.service.OpenApiReferenceDataService;
import com.scott.payment.openapi.vo.reference.CardBinLookupVO;
import com.scott.payment.openapi.vo.reference.IpLookupVO;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiReferenceDataApplicationService
 * @date : 2026-08-11 15:44
 * @email : scott_x@163.com
 * @description : 商户基础数据检索应用服务，编排独立 IP 与卡 BIN 外部入口到只读查询服务
 * @status : create
 */
@Service
public class OpenApiReferenceDataApplicationService {

    /** 商户基础数据检索服务，不允许为空。 */
    private final OpenApiReferenceDataService referenceDataService;

    /**
     * 创建商户基础数据检索应用服务。
     *
     * @param referenceDataService 基础数据检索服务
     */
    public OpenApiReferenceDataApplicationService(OpenApiReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    /**
     * 查询 IP 归属。
     *
     * @param requestDTO 解密后的商户请求
     * @return IP 归属响应
     */
    public IpLookupVO queryIp(IpLookupRequestDTO requestDTO) {
        return referenceDataService.queryIp(requestDTO);
    }

    /**
     * 查询卡 BIN 归属。
     *
     * @param requestDTO 解密后的商户请求
     * @return 卡 BIN 归属响应
     */
    public CardBinLookupVO queryCardBin(CardBinLookupRequestDTO requestDTO) {
        return referenceDataService.queryCardBin(requestDTO);
    }
}
