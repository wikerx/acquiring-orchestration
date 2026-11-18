package com.scott.payment.data.service.export;

import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.PaymentLinkQuery;
import com.scott.payment.data.client.admin.AdminPaymentLinkExportSourceClient;
import com.scott.payment.data.client.merchant.MerchantPaymentLinkExportSourceClient;
import com.scott.payment.data.client.paymentlink.PaymentLinkExportDTOs.*;
import com.scott.payment.data.entity.export.DataExportTaskDO;
import com.scott.payment.data.infrastructure.export.ExportObjectStorageService;
import com.scott.payment.data.service.export.impl.*;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

/**
 * 验证导出冻结身份、分页、安全列与金额原文；不连接实际对象存储。
 */
class PaymentLinkExportProcessorTest {
    private final ExcelExportArtifactWriter writer=mock(ExcelExportArtifactWriter.class);
    private final ExportObjectStorageService storage=mock(ExportObjectStorageService.class);
    private final ExportProgressListener progress=mock(ExportProgressListener.class);
    private final ExcelI18nMessageResolver messages=mock(ExcelI18nMessageResolver.class);

    /**
     * 构建冻结任务，只使用虚构标识和安全查询。
     */
    private DataExportTaskDO task(String app) {
        DataExportTaskDO task=new DataExportTaskDO();task.setCreatorAppCode(app);task.setCreatorAccountId(42L);task.setLocale("en-US");task.setQuerySnapshot(JsonUtils.toJsonString(new PaymentLinkQuery()));
        if(app.equals("MERCHANT")){task.setMerchantId("m1");MerchantExportScopeSnapshot scope=new MerchantExportScopeSnapshot();scope.setMerchantId("m1");task.setDataScopeSnapshot(JsonUtils.toJsonString(scope));}
        else {AdminExportScopeSnapshot scope=new AdminExportScopeSnapshot();scope.setMerchantIds(Set.of("m1"));task.setDataScopeSnapshot(JsonUtils.toJsonString(scope));}
        return task;
    }

    /**
     * 驱动真实分页转换，确认财务字符串和白名单输出不受 UI 格式化影响。
     */
    @SuppressWarnings("unchecked")
    private void writerLoadsPage() {
        when(writer.write(any(),same(storage),same(progress),any(),anyString(),anyString(),anyString(),eq(PaymentLinkExportRow.class),eq(100),any())).thenAnswer(invocation->{
            IntFunction<ExportPage<PaymentLinkExportRow>> loader=invocation.getArgument(9);
            var page=loader.apply(3);assertThat(page.records()).singleElement().satisfies(row->{assertThat(row.getAmount()).isEqualTo("123456789.120000");assertThat(row.getCurrency()).isEqualTo("USD");});return null;
        });
    }

    /**
     * 提供来源响应，不含分享 URL、邮件正文或客户身份。
     */
    private PageResult<Row> page() {Row row=new Row();row.setLinkId("link-1");row.setMerchantId("m1");row.setAmount("123456789.120000");row.setCurrency("USD");row.setStatus("ACTIVE");return PageResult.of(1,3,100,List.of(row));}

    /**
     * Merchant 文件分页身份必须来自冻结商户，不能变成 Admin 范围。
     */
    @Test void merchantKeepsFrozenIdentityAndExactAmount() {
        var source=mock(MerchantPaymentLinkExportSourceClient.class);when(source.loadPaymentLinkPage(any())).thenReturn(page());writerLoadsPage();
        new MerchantPaymentLinkExportProcessor(source,writer,messages).process(task("MERCHANT"),storage,progress,LocalDateTime.now());
        var request=ArgumentCaptor.forClass(PageRequest.class);verify(source).loadPaymentLinkPage(request.capture());assertThat(request.getValue().getMerchantId()).isEqualTo("m1");assertThat(request.getValue().getCreatorAccountId()).isEqualTo(42);assertThat(request.getValue().getPageNo()).isEqualTo(3);assertThat(request.getValue().getPageSize()).isEqualTo(100);assertThat(request.getValue().isAllMerchants()).isFalse();
    }

    /**
     * Admin 有限范围不得被导出处理器提升为全商户。
     */
    @Test void adminPreservesFiniteScopeAndExactAmount() {
        var source=mock(AdminPaymentLinkExportSourceClient.class);when(source.loadPaymentLinkPage(any())).thenReturn(page());writerLoadsPage();
        new AdminPaymentLinkExportProcessor(source,writer,messages).process(task("ADMIN"),storage,progress,LocalDateTime.now());
        var request=ArgumentCaptor.forClass(PageRequest.class);verify(source).loadPaymentLinkPage(request.capture());assertThat(request.getValue().getMerchantIds()).containsExactly("m1");assertThat(request.getValue().isAllMerchants()).isFalse();
    }

    /**
     * 归属和快照不一致时，不能发出任何来源请求。
     */
    @Test void merchantSnapshotMismatchFailsClosed() {
        var source=mock(MerchantPaymentLinkExportSourceClient.class);DataExportTaskDO task=task("MERCHANT");task.setMerchantId("m2");
        when(writer.write(any(),any(),any(),any(),anyString(),anyString(),anyString(),eq(PaymentLinkExportRow.class),eq(100),any())).thenAnswer(invocation->{IntFunction<?> loader=invocation.getArgument(9);return loader.apply(1);});
        assertThatThrownBy(()->new MerchantPaymentLinkExportProcessor(source,writer,messages).process(task,storage,progress,LocalDateTime.now())).isInstanceOf(IllegalStateException.class);verifyNoInteractions(source);
    }

    /**
     * 错误的账号应用不允许交叉使用导出处理器。
     */
    @Test void wrongApplicationIsRejectedBeforeWriting() {
        var source=mock(AdminPaymentLinkExportSourceClient.class);
        assertThatThrownBy(()->new AdminPaymentLinkExportProcessor(source,writer,messages).process(task("MERCHANT"),storage,progress,LocalDateTime.now())).isInstanceOf(IllegalStateException.class);verifyNoInteractions(source,writer);
    }

    /**
     * 新增详情字段不能自动扩展导出文件中的客户信息或支付凭据。
     */
    @Test void exportRowContainsOnlyApprovedSafeColumns() {
        assertThat(java.util.Arrays.stream(PaymentLinkExportRow.class.getDeclaredFields()).map(java.lang.reflect.Field::getName)).containsExactly("linkId","merchantId","merchantOrderNo","linkName","currency","amount","status","paymentStatus","expireTime","createTime","paidTime","createdAppCode","createdBy");
    }
    /**
     * 同一来源事实按任务冻结的时区输出，精确保留毫秒和空成功时间。
     */
    @Test @SuppressWarnings("unchecked") void bothApplicationsRenderFrozenUtcTimezone() {
        for(String app:List.of("ADMIN","MERCHANT")) {
            Row record=new Row(); record.setCreateTime(LocalDateTime.of(2026,11,18,8,12,34,123000000));
            record.setExpireTime(LocalDateTime.of(2026,11,19,8,12,34,123000000));
            var response=PageResult.of(1,1,100,List.of(record));
            var task=task(app);task.setTimezone("UTC");
            when(writer.write(any(),same(storage),same(progress),any(),anyString(),anyString(),anyString(),eq(PaymentLinkExportRow.class),eq(100),any())).thenAnswer(invocation->{
                IntFunction<ExportPage<PaymentLinkExportRow>> loader=invocation.getArgument(9);
                assertThat(loader.apply(1).records()).singleElement().satisfies(row->{
                    assertThat(row.getCreateTime()).isEqualTo(LocalDateTime.of(2026,11,18,0,12,34,123000000));
                    assertThat(row.getExpireTime()).isEqualTo(LocalDateTime.of(2026,11,19,0,12,34,123000000));
                    assertThat(row.getPaidTime()).isNull();
                });return null;
            });
            if(app.equals("ADMIN")) {var source=mock(AdminPaymentLinkExportSourceClient.class);when(source.loadPaymentLinkPage(any())).thenReturn(response);new AdminPaymentLinkExportProcessor(source,writer,messages).process(task,storage,progress,LocalDateTime.now());}
            else {var source=mock(MerchantPaymentLinkExportSourceClient.class);when(source.loadPaymentLinkPage(any())).thenReturn(response);new MerchantPaymentLinkExportProcessor(source,writer,messages).process(task,storage,progress,LocalDateTime.now());}
        }
    }

}
