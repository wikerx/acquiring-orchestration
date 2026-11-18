package com.scott.payment.payment.application;

import com.scott.payment.payment.api.internal.dto.PaymentLinkDTOs.*;
import com.scott.payment.payment.service.link.PaymentLinkPolicy;
import com.scott.payment.payment.service.link.PaymentLinkQueryService;
import com.scott.payment.payment.service.link.PaymentLinkService;
import org.springframework.stereotype.Service;

/** 内部用例编排与协议分派，业务校验和本地事务由支付链接领域服务负责。 */
@Service
public class PaymentLinkApplicationService {
    private final PaymentLinkService commands;
    private final PaymentLinkQueryService queries;
    private final PaymentLinkPolicy policy;

    public PaymentLinkApplicationService(PaymentLinkService commands, PaymentLinkQueryService queries, PaymentLinkPolicy policy) {
        this.commands = commands; this.queries = queries; this.policy = policy;
    }

    public Object execute(String action, Command command) {
        return switch (action) {
            case "create" -> commands.create(command.getActor(), command.getMerchantId(), command.getCreate());
            case "disable" -> commands.disable(command.getActor(), command.getLinkId(), command.getVersion(), command.getReason(), command.getRequestId());
            case "share" -> commands.share(command.getActor(), command.getLinkId());
            case "search" -> queries.search(command.getActor(), command.getQuery());
            case "detail" -> queries.detail(command.getActor(), command.getLinkId());
            case "summary" -> queries.summary(command.getActor(), command.getQuery());
            case "filter-currencies" -> queries.filterCurrencies(command.getActor());
            case "options" -> policy.options(command.getActor(), command.getMerchantId());
            case "collections" -> queries.collections(command.getActor(), command.getLinkId(), command.getQuery());
            case "transactions" -> queries.attempts(command.getActor(), command.getLinkId(), command.getQuery());
            case "events" -> queries.events(command.getActor(), command.getLinkId(), command.getQuery());
            case "resolve" -> commands.resolve(command.getOpaqueToken());
            case "start" -> commands.start(command.getOpaqueToken(), command.getRequestId());
            default -> throw new com.scott.payment.component.core.exception.ServiceException(
                    com.scott.payment.component.core.enums.ApiResultEnum.PARAM_INVALID);
        };
    }
}
