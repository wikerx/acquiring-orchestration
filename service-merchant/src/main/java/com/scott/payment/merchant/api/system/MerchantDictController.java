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
     * dict Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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
