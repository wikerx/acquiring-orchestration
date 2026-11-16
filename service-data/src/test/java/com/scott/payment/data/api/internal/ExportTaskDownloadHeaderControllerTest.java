package com.scott.payment.data.api.internal;

import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.DownloadRequest;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.TaskSummary;
import com.scott.payment.data.application.export.DataExportTaskApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 异步导出下载响应头测试，验证管理端和商户端都使用 RFC 5987 文件名编码。
 */
class ExportTaskDownloadHeaderControllerTest {

    /**
     * 管理端下载文件名中的空格必须编码为百分号形式，不能被浏览器保存为加号。
     */
    @Test
    void shouldEncodeAdminDownloadFileNameWithRfc5987Space() throws Exception {
        DataExportTaskApplicationService applicationService = mock(DataExportTaskApplicationService.class);
        DownloadRequest request = downloadRequest();
        when(applicationService.detail(request)).thenReturn(taskSummary());
        MockHttpServletResponse response = new MockHttpServletResponse();

        new DataExportTaskInternalController(applicationService).download(request, response);

        assertContentDisposition(response);
    }

    /**
     * 商户端下载复用同一编码语义，避免两个门户保存出的文件名不一致。
     */
    @Test
    void shouldEncodeMerchantDownloadFileNameWithRfc5987Space() throws Exception {
        DataExportTaskApplicationService applicationService = mock(DataExportTaskApplicationService.class);
        DownloadRequest request = downloadRequest();
        when(applicationService.detail(request)).thenReturn(taskSummary());
        MockHttpServletResponse response = new MockHttpServletResponse();

        new MerchantDataExportTaskInternalController(applicationService).download(request, response);

        assertContentDisposition(response);
    }

    /**
     * 构造满足下载接口归属校验的最小请求。
     *
     * @return 下载请求
     */
    private DownloadRequest downloadRequest() {
        DownloadRequest request = new DownloadRequest();
        request.setTaskNo("EXP-HEADER-001");
        request.setCreatorAccountId(1L);
        request.setMerchantId("M-DEMO");
        return request;
    }

    /**
     * 构造同时包含空格、中文和真实加号的文件名，区分两种编码语义。
     *
     * @return 任务安全摘要
     */
    private TaskSummary taskSummary() {
        TaskSummary summary = new TaskSummary();
        summary.setFileName("MCC 编码+导出.xlsx");
        summary.setFileSizeBytes(0L);
        return summary;
    }

    /**
     * 断言空格、中文和真实加号均按 RFC 5987 语义进入下载响应头。
     *
     * @param response 下载响应
     */
    private void assertContentDisposition(MockHttpServletResponse response) {
        assertThat(response.getHeader(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo("attachment;filename*=utf-8''MCC%20%E7%BC%96%E7%A0%81%2B%E5%AF%BC%E5%87%BA.xlsx");
    }
}
