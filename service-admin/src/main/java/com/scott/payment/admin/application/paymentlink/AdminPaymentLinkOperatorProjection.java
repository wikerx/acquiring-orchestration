package com.scott.payment.admin.application.paymentlink;

import com.scott.payment.admin.dto.paymentlink.PaymentLinkDTOs.*;
import com.scott.payment.admin.mapper.PaymentLinkOperatorMapper;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 名称是当前账号的只读展示投影，审计中的来源和 ID 始终保留。 */
@Service
public class AdminPaymentLinkOperatorProjection {
    private final PaymentLinkOperatorMapper accounts;

    public AdminPaymentLinkOperatorProjection(PaymentLinkOperatorMapper accounts) {
        this.accounts = accounts;
    }

    public View view(View view) {
        if (view != null) view.setCreatedByName(names(java.util.List.of(identity(view.getCreatedAppCode(), view.getCreatedBy())))
                .get(identity(view.getCreatedAppCode(), view.getCreatedBy())));
        return view;
    }

    public PageResult<Event> events(PageResult<Event> page) {
        var names = names(page.getRecords().stream().map(e -> identity(e.getActorAppCode(), e.getActorId())).toList());
        page.getRecords().forEach(e -> e.setActorName(names.get(identity(e.getActorAppCode(), e.getActorId()))));
        return page;
    }

    public PageResult<Delivery> deliveries(PageResult<Delivery> page) {
        var names = names(page.getRecords().stream().map(d -> identity(d.getCreatedAppCode(), d.getCreatedBy())).toList());
        page.getRecords().forEach(d -> d.setCreatedByName(names.get(identity(d.getCreatedAppCode(), d.getCreatedBy()))));
        return page;
    }

    private Map<String, String> names(Collection<String> identities) {
        Set<Long> ids = new LinkedHashSet<>();
        for (String identity : identities) {
            if (!(identity.startsWith("ADMIN:") || identity.startsWith("MERCHANT:"))) continue;
            try {
                long id = Long.parseLong(identity.substring(identity.indexOf(':') + 1));
                if (id > 0) ids.add(id);
            } catch (NumberFormatException ignored) {
                // 链接持有人、系统事件及不可解析的历史身份不查询账号表。
            }
        }
        if (ids.isEmpty()) return Map.of();
        return accounts.find(ids).stream().collect(Collectors.toMap(
                a -> identity(a.appCode(), a.accountId()),
                a -> displayName(a.realName(), a.loginAccount())));
    }

    private String identity(String app, String id) { return app + ":" + id; }

    private String displayName(String name, String login) {
        if (name == null || name.isBlank()) return login == null ? "" : login;
        if (login == null || login.isBlank() || name.equals(login)) return name;
        return name + " (" + login + ")";
    }
}
