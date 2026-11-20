package com.scott.payment.payment.service.link;

import com.scott.payment.component.db.auth.service.MerchantRuntimeProfileCacheService;
import com.scott.payment.component.db.iso.service.IsoDictionaryService;
import com.scott.payment.payment.api.internal.dto.PaymentLinkDTOs.*;
import com.scott.payment.payment.api.internal.dto.PaymentCheckoutSessionCreateCommandDTO.AllowedPaymentMethodDTO;
import com.scott.payment.payment.config.PaymentLinkProperties;
import com.scott.payment.payment.service.impl.PaymentCheckoutCardCapabilityService;
import com.scott.payment.link.model.PaymentLinkManagementModels.AllowedMethod;
import com.scott.payment.link.service.PaymentLinkManagementPolicy;
import com.scott.payment.link.service.PaymentLinkMethodScope;
import jakarta.validation.Validator;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import java.util.List;

/** Payment 付款入口保留既有类型和缓存读取边界，链接规则由公共管理域维护。 */
@Service
public class PaymentLinkPolicy extends PaymentLinkManagementPolicy {
    public PaymentLinkPolicy(PaymentLinkProperties properties, MerchantRuntimeProfileCacheService merchants,
                             IsoDictionaryService currencies, PaymentCheckoutCardCapabilityService capabilities,
                             Validator validator, Environment environment) {
        this(properties, merchants, currencies, capabilities, validator, environment, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PaymentLinkPolicy(PaymentLinkProperties properties, MerchantRuntimeProfileCacheService merchants,
                             IsoDictionaryService currencies, PaymentCheckoutCardCapabilityService capabilities,
                             Validator validator, Environment environment, PaymentLinkMethodScope methodScope) {
        super(properties, merchants, currencies, (merchant, requested, currency) -> capabilities.resolveAllowedMethods(
                merchant, requested == null ? null : requested.stream().map(PaymentLinkPolicy::checkout).toList(), currency)
                .stream().map(PaymentLinkPolicy::management).toList(), validator, environment, methodScope);
    }
    /** 把创建时全部通过能力校验的支付方式转换为收银台契约。 */
    public List<AllowedPaymentMethodDTO> methods(String merchant, String currency, List<String> brands) {
        return allowedMethods(merchant, currency, brands).stream().map(PaymentLinkPolicy::checkout).toList();
    }
    /** 将链接冻结的卡品牌与当前有效能力取交集，再生成付款会话允许方式。 */
    public List<AllowedPaymentMethodDTO> currentMethods(String merchant, String currency, List<String> brands) {
        return currentAllowedMethods(merchant, currency, brands).stream().map(PaymentLinkPolicy::checkout).toList();
    }
    /** 仅使用冻结的规范方式并按主库现行能力重新收窄。 */
    public List<AllowedPaymentMethodDTO> currentExplicitMethods(String merchant, String currency, String scopeJson) {
        List<String> keys = PaymentLinkMethodScope.deserialize(scopeJson);
        return resolveMethodScope(merchant, currency, keys, false).stream().map(PaymentLinkPolicy::checkout).toList();
    }
    /** 在操作者商户范围内返回支付域兼容的创建选项，不授予额外付款能力。 */
    public Options options(Actor actor, String merchant) { return options(actor, merchant, Options::new); }
    private static AllowedMethod management(AllowedPaymentMethodDTO value) {
        var result = new AllowedMethod(); result.setPaymentMethod(value.getPaymentMethod()); result.setChannelCode(value.getChannelCode());
        result.setBrands(value.getBrands()); result.setThreeDsMode(value.getThreeDsMode()); return result;
    }
    private static AllowedPaymentMethodDTO checkout(AllowedMethod value) {
        var result = new AllowedPaymentMethodDTO(); result.setPaymentMethod(value.getPaymentMethod()); result.setChannelCode(value.getChannelCode());
        result.setBrands(value.getBrands()); result.setThreeDsMode(value.getThreeDsMode()); return result;
    }
}
