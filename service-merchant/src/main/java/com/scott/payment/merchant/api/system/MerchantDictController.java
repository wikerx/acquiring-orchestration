package com.scott.payment.merchant.api.system;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.scott.payment.merchant.dto.system.MerchantDictDTOs.DictDataQuery;
import com.scott.payment.merchant.dto.system.MerchantDictDTOs.DictDataResponse;
import com.scott.payment.merchant.service.MerchantDictService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantDictController
 * @date : 2026-07-20 00:00
 * @email : scott_x@163.com
 * @description : 商户后台只读字典接口，位于 service-merchant 接口层，为商户页面提供统一交易状态、交易类型、支付方式和时区选项。
 * @status : create
 */
@RestController
@RequestMapping("/merchant/system/dicts")
public class MerchantDictController {

    /**
     * dict Service 依赖，用于 Merchant Dict Controller 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：构造器注入的应用服务或 HTTP 请求对象。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final MerchantDictService dictService;

    /**
     * 创建商户后台只读字典接口。
     *
     * @param dictService 商户后台只读字典服务
     */
    public MerchantDictController(MerchantDictService dictService) {
        this.dictService = dictService;
    }

    /**
     * 分页查询商户后台可用字典项。
     *
     * @param query 查询条件
     * @return 字典项分页结果
     */
    @PostMapping("/data/search")
    @RequiresPermission("merchant:transaction:dict:list")
    @OperationLog(moduleName = "商户数据字典", businessType = OperationTypeConstants.QUERY, operation = "查询商户页面字典项")
    public CommonResult<PageResult<DictDataResponse>> searchDictData(@RequestBody(required = false) DictDataQuery query) {
        return success(dictService.pageDictData(query));
    }
}
