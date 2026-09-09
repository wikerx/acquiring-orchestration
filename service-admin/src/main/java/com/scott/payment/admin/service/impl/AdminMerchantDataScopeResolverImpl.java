package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.entity.system.AdminRoleMerchantScopeDO;
import com.scott.payment.admin.mapper.AdminRoleDataScopeMapper;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantDataScopeResolverImpl
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 按 ALL 优先、CUSTOM 并集、SELF 空集的规则解析平台 Admin 商户范围。
 * @status : create
 */
@Service
public class AdminMerchantDataScopeResolverImpl implements AdminMerchantDataScopeResolver {

    private final AdminRoleDataScopeMapper mapper;

    public AdminMerchantDataScopeResolverImpl(AdminRoleDataScopeMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 解析{@code resolve}，将原始输入转换为当前调用链需要的规范化结果。
     * <p>
     * 仅返回规范化或计算结果，不直接提交交易状态。
     * </p>
     * @param account 可信登录账号上下文，用于解析角色授权和商户数据范围
     * @return 构造、转换或解析后的业务值
     */
    @Override
    public AdminMerchantDataScope resolve(InternalAuthAccount account) {
        if (account == null || account.getAppId() == null || account.getAccountId() == null
                || !"ADMIN".equalsIgnoreCase(account.getAppCode())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        }
        List<AdminRoleMerchantScopeDO> rows = mapper.selectActiveMerchantScopes(
                account.getAppId(), account.getAccountId());
        if (rows.stream().map(AdminRoleMerchantScopeDO::getDataScope)
                .filter(StringUtils::hasText)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .anyMatch("ALL"::equals)) {
            return AdminMerchantDataScope.all();
        }
        Set<String> merchantIds = new LinkedHashSet<>();
        for (AdminRoleMerchantScopeDO row : rows) {
            if (row != null && "CUSTOM".equalsIgnoreCase(row.getDataScope())
                    && StringUtils.hasText(row.getScopeValue())) {
                merchantIds.add(row.getScopeValue().trim());
            }
        }
        return AdminMerchantDataScope.limited(merchantIds);
    }
}
