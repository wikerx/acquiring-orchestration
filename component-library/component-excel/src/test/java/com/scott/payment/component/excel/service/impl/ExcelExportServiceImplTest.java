package com.scott.payment.component.excel.service.impl;

import com.scott.payment.component.excel.annotation.ExcelExportColumn;
import com.scott.payment.component.excel.model.ExcelExportRequest;
import com.scott.payment.component.excel.support.ExcelExportMetadataResolver;
import com.scott.payment.component.excel.support.ExcelI18nMessageResolver;
import com.scott.payment.component.excel.support.ExcelStyleStrategyFactory;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExcelExportServiceImplTest
 * @date : 2026-06-20 00:45
 * @email : scott_x@163.com
 * @description : Excel 导出服务回归测试
 * @status : create
 *
 * <p>重点验证导出文件是否同时包含标题区、表头区和数据区，
 * 避免标题处理器变更后再次出现“文件能下载但表格为空”的回归问题。</p>
 */
class ExcelExportServiceImplTest {

    /**
     * 验证导出结果同时包含标题、表头和数据。
     *
     * @throws IOException 读取工作簿异常
     */
    @Test
    void shouldWriteTitleHeaderAndDataRows() throws IOException {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames("messages");
        messageSource.setDefaultEncoding("UTF-8");
        ExcelExportServiceImpl service = new ExcelExportServiceImpl(
                new ExcelI18nMessageResolver(messageSource),
                new ExcelExportMetadataResolver(),
                new ExcelStyleStrategyFactory()
        );
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.export(
                ExcelExportRequest.<DemoExportRow>builder()
                        .fileName("demo")
                        .sheetName("demo")
                        .titleKey("excel.user.title")
                        .operator("tester")
                        .exportTime(LocalDateTime.of(2026, 6, 20, 0, 0, 0))
                        .locale(Locale.SIMPLIFIED_CHINESE)
                        .querySummary("状态=启用")
                        .rowClass(DemoExportRow.class)
                        .dataList(List.of(new DemoExportRow("alice", "启用")))
                        .build(),
                response
        );

        Assertions.assertTrue(response.body.size() > 0, "导出的 Excel 二进制内容不能为空");

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.body.toByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            Assertions.assertEquals("用户列表导出", sheet.getRow(0).getCell(0).getStringCellValue());
            Assertions.assertEquals("登录账号", sheet.getRow(2).getCell(0).getStringCellValue());
            Assertions.assertEquals("状态", sheet.getRow(2).getCell(1).getStringCellValue());
            Row dataRow = sheet.getRow(3);
            Assertions.assertNotNull(dataRow, "数据行不能为空");
            Assertions.assertEquals("alice", dataRow.getCell(0).getStringCellValue());
            Assertions.assertEquals("启用", dataRow.getCell(1).getStringCellValue());
        }
    }

    /**
     * 测试导出行对象。
     */
    public static class DemoExportRow {

        /**
         * 登录账号。
         */
        @ExcelExportColumn(order = 1, headerKey = "excel.user.loginAccount", width = 20)
        private final String loginAccount;

        /**
         * 状态文案。
         */
        @ExcelExportColumn(order = 2, headerKey = "excel.user.status", width = 12)
        private final String status;

        /**
         * 创建测试导出行。
         *
         * @param loginAccount 登录账号
         * @param status 状态文案
         */
        DemoExportRow(String loginAccount, String status) {
            this.loginAccount = loginAccount;
            this.status = status;
        }
    }

    /**
     * 极简 HTTP 响应桩对象，仅用于承接导出字节流。
     */
    private static final class MockHttpServletResponse implements HttpServletResponse {

        /**
         * Excel 二进制输出缓存。
         */
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        /**
         * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
         * @return 处理后的业务结果或页面展示数据。
         */
        @Override
        public ServletOutputStream getOutputStream() {
            return new ServletOutputStream() {

                /**
                 * 判断收单支付条件是否满足，供业务分支或权限控制使用。
                 * @return 处理后的业务结果或页面展示数据。
                 */
                @Override
                public boolean isReady() {
                    return true;
                }

                /**
                 * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
                 * @param listener 请求参数或业务处理上下文，不能为空时由上层校验约束。
                 */
                @Override
                public void setWriteListener(WriteListener listener) {
                    // 单元测试场景不需要异步写监听。
                }

                /**
                 * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
                 * @param b 请求参数或业务处理上下文，不能为空时由上层校验约束。
                 */
                @Override
                public void write(int b) {
                    body.write(b);
                }
            };
        }

        @Override public void setCharacterEncoding(String charset) { }
        @Override public String getCharacterEncoding() { return "UTF-8"; }
        @Override public void setContentLength(int len) { }
        @Override public void setContentLengthLong(long len) { }
        @Override public void setContentType(String type) { }
        @Override public String getContentType() { return null; }
        @Override public PrintWriter getWriter() { throw new UnsupportedOperationException(); }
        @Override public void setBufferSize(int size) { }
        @Override public int getBufferSize() { return 0; }
        @Override public void flushBuffer() throws IOException { body.flush(); }
        @Override public void resetBuffer() { }
        @Override public boolean isCommitted() { return false; }
        @Override public void reset() { }
        @Override public void setLocale(Locale loc) { }
        @Override public Locale getLocale() { return Locale.SIMPLIFIED_CHINESE; }
        @Override public void addCookie(Cookie cookie) { }
        @Override public boolean containsHeader(String name) { return false; }
        @Override public String encodeURL(String url) { return url; }
        @Override public String encodeRedirectURL(String url) { return url; }
        @Override public void sendError(int sc, String msg) { }
        @Override public void sendError(int sc) { }
        @Override public void sendRedirect(String location) { }
        @Override public void setDateHeader(String name, long date) { }
        @Override public void addDateHeader(String name, long date) { }
        @Override public void setHeader(String name, String value) { }
        @Override public void addHeader(String name, String value) { }
        @Override public void setIntHeader(String name, int value) { }
        @Override public void addIntHeader(String name, int value) { }
        @Override public void setStatus(int sc) { }
        @Override public int getStatus() { return 200; }
        @Override public String getHeader(String name) { return null; }
        @Override public Collection<String> getHeaders(String name) { return List.of(); }
        @Override public Collection<String> getHeaderNames() { return List.of(); }
    }
}
