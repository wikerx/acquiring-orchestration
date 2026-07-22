package com.scott.payment.merchant.service;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.dto.system.MerchantDictDTOs.DictDataQuery;
import com.scott.payment.merchant.dto.system.MerchantDictDTOs.DictDataResponse;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantDictService
 * @date : 2026-07-20 00:00
 * @email : scott_x@163.com
 * @description : 商户后台只读字典服务，位于 service-merchant 服务层，为商户页面提供平台统一字典选项，不承担字典维护。
 * @status : create
 */
public interface MerchantDictService {

    /**
     * 分页查询商户后台可用字典项。
     *
     * @param query 查询条件
     * @return 字典项分页结果
     */
    PageResult<DictDataResponse> pageDictData(DictDataQuery query);
}
