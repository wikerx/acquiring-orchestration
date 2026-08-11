package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.db.reference.service.ReferenceDataLookupService;
import com.scott.payment.openapi.converter.OpenApiReferenceDataConverter;
import com.scott.payment.openapi.dto.body.reference.CardBinLookupRequestDTO;
import com.scott.payment.openapi.dto.body.reference.IpLookupRequestDTO;
import com.scott.payment.openapi.service.OpenApiReferenceDataService;
import com.scott.payment.openapi.vo.reference.CardBinLookupVO;
import com.scott.payment.openapi.vo.reference.IpLookupVO;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiReferenceDataServiceImpl
 * @date : 2026-08-11 15:44
 * @email : scott_x@163.com
 * @description : 商户基础数据检索服务实现，将外部参数映射到公共从库查询并隔离数据库内部字段
 * @status : create
 */
@Service
public class OpenApiReferenceDataServiceImpl implements OpenApiReferenceDataService {

    /** 公共基础数据从库检索服务，不允许为空。 */
    private final ReferenceDataLookupService referenceDataLookupService;

    /** 公共结果到 OpenAPI VO 的字段转换器，不允许为空。 */
    private final OpenApiReferenceDataConverter converter;

    /**
     * 创建商户基础数据检索服务。
     *
     * @param referenceDataLookupService 公共基础数据检索服务
     * @param converter                 响应转换器
     */
    public OpenApiReferenceDataServiceImpl(ReferenceDataLookupService referenceDataLookupService,
                                           OpenApiReferenceDataConverter converter) {
        this.referenceDataLookupService = referenceDataLookupService;
        this.converter = converter;
    }

    /**
     * 查询 IP 归属，并把精确 IP 格式错误映射为统一参数错误。
     *
     * @param requestDTO 解密后的商户请求
     * @return IP 归属响应
     */
    @Override
    public IpLookupVO queryIp(IpLookupRequestDTO requestDTO) {
        try {
            return converter.toIpLookupVO(referenceDataLookupService.lookupIp(requestDTO.getIpAddress()));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "ipAddress must be a valid IPv4 or IPv6 literal");
        }
    }

    /**
     * 查询卡 BIN 归属，并把防御性格式校验异常映射为统一参数错误。
     *
     * @param requestDTO 解密后的商户请求
     * @return 卡 BIN 归属响应
     */
    @Override
    public CardBinLookupVO queryCardBin(CardBinLookupRequestDTO requestDTO) {
        try {
            return converter.toCardBinLookupVO(referenceDataLookupService.lookupCardBin(requestDTO.getCardBin()));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ApiResultEnum.PARAM_INVALID, "cardBin must be 6 to 11 digits");
        }
    }
}
