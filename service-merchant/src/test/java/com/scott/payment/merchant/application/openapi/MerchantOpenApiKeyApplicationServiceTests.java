package com.scott.payment.merchant.application.openapi;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationCoordinator;
import com.scott.payment.component.db.constant.DataSourceName;
import com.scott.payment.component.security.openapi.OpenApiKeyType;
import com.scott.payment.component.security.openapi.OpenApiMerchantKeyMaterialService;
import com.scott.payment.component.security.openapi.OpenApiMerchantKeyMaterialVO;
import com.scott.payment.merchant.service.impl.MerchantOpenApiKeyNotificationService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOpenApiKeyApplicationServiceTests
 * @date : 2026-08-01 13:00
 * @email : scott_x@163.com
 * @description : 验证商户门户密钥轮换使用主库事务并在实际密钥写入前登记共享缓存可靠失效
 * @status : create
 */
class MerchantOpenApiKeyApplicationServiceTests {

    /**
     * 验证轮换先准备同事务失效意图，再调用密钥领域服务写库。
     */
    @Test
    void shouldPrepareSharedInvalidationBeforeRotatingKey() {
        OpenApiMerchantKeyMaterialService keyMaterialService = mock(OpenApiMerchantKeyMaterialService.class);
        ManagedCacheInvalidationCoordinator coordinator = mock(ManagedCacheInvalidationCoordinator.class);
        MerchantOpenApiKeyApplicationService service =
                new MerchantOpenApiKeyApplicationService(
                        keyMaterialService,
                        coordinator,
                        mock(MerchantOpenApiKeyNotificationService.class)
                );
        OpenApiMerchantKeyMaterialVO expected = new OpenApiMerchantKeyMaterialVO();
        when(keyMaterialService.rotate("200045", OpenApiKeyType.JWT_KEY)).thenReturn(expected);

        OpenApiMerchantKeyMaterialVO actual = service.rotate("200045", OpenApiKeyType.JWT_KEY);

        assertThat(actual).isSameAs(expected);
        InOrder order = inOrder(coordinator, keyMaterialService);
        order.verify(coordinator).prepare(PaymentCacheNames.MERCHANT_KEY_METADATA, "200045");
        order.verify(keyMaterialService).rotate("200045", OpenApiKeyType.JWT_KEY);
    }

    /**
     * 验证密钥轮换入口明确固定到 MASTER 并声明回滚事务。
     *
     * @throws NoSuchMethodException 方法签名变更时由测试显式失败
     */
    @Test
    void shouldRouteRotationToMasterTransaction() throws NoSuchMethodException {
        var method = MerchantOpenApiKeyApplicationService.class
                .getMethod("rotate", String.class, OpenApiKeyType.class);
        DS dataSource = method.getAnnotation(DS.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(dataSource).isNotNull();
        assertThat(dataSource.value()).isEqualTo(DataSourceName.MASTER);
        assertThat(transactional).isNotNull();
        assertThat(transactional.rollbackFor()).contains(Exception.class);
    }

    /** 验证启停先登记缓存失效，再更新密钥状态并安排对应通知。 */
    @Test
    void shouldPrepareInvalidationBeforeDisablingAndNotifyWithFingerprintSnapshot() {
        OpenApiMerchantKeyMaterialService keyMaterialService = mock(OpenApiMerchantKeyMaterialService.class);
        ManagedCacheInvalidationCoordinator coordinator = mock(ManagedCacheInvalidationCoordinator.class);
        MerchantOpenApiKeyNotificationService notificationService = mock(MerchantOpenApiKeyNotificationService.class);
        MerchantOpenApiKeyApplicationService service = new MerchantOpenApiKeyApplicationService(
                keyMaterialService,
                coordinator,
                notificationService
        );
        OpenApiMerchantKeyMaterialVO snapshot = new OpenApiMerchantKeyMaterialVO();
        when(keyMaterialService.queryMaterial("200045")).thenReturn(snapshot);

        service.setEnabled("200045", OpenApiKeyType.JWT_KEY, false);

        InOrder order = inOrder(keyMaterialService, coordinator, notificationService);
        order.verify(keyMaterialService).queryMaterial("200045");
        order.verify(coordinator).prepare(PaymentCacheNames.MERCHANT_KEY_METADATA, "200045");
        order.verify(keyMaterialService).setEnabled("200045", OpenApiKeyType.JWT_KEY, false);
        order.verify(notificationService).sendAfterCommit(
                "200045",
                MerchantOpenApiKeyNotificationService.TEMPLATE_DISABLED,
                OpenApiKeyType.JWT_KEY,
                snapshot
        );
    }
}
