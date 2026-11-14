package com.scott.payment.admin.support.risk;

import com.scott.payment.admin.dto.risk.TradeBlackWorkflowDTOs;
import com.scott.payment.component.core.exception.ServiceException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TradeBlackUploadParserTest {

    private final TradeBlackUploadParser parser = new TradeBlackUploadParser();

    @Test
    void shouldParseOnlyTransactionLocatorColumnsFromCsv() {
        String metadata = """
                {"scopeType":"GLOBAL","actionType":"ADD","functionCodes":["cardNo"],"orders":[]}
                """;
        String csv = "identifierType,lookupMerchantId,orderNo,blackTargetHash,blackTargetValueMasked\n"
                + "SYSTEM_ORDER_NO,,TX-001,client-hash,411111******1111\n"
                + "商户订单号,M1001,MO-002,ignored,ignored\n";
        MockMultipartFile file = new MockMultipartFile("file", "trades.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        TradeBlackWorkflowDTOs.PreviewRequest request = parser.parse(metadata, file);

        assertThat(request.getInputMode()).isEqualTo("FILE");
        assertThat(request.getOrders()).hasSize(2);
        assertThat(request.getOrders().get(0).getOrderNo()).isEqualTo("TX-001");
        assertThat(request.getOrders().get(1).getIdentifierType()).isEqualTo("MERCHANT_ORDER_NO");
        assertThat(request.getOrders().get(1).getLookupMerchantId()).isEqualTo("M1001");
    }

    @Test
    void shouldRejectUnsupportedFileType() {
        MockMultipartFile file = new MockMultipartFile("file", "trades.txt", "text/plain", "x".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> parser.parse("{}", file))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("仅支持XLSX、XLS、CSV文件");
    }

    @Test
    void shouldParseStyledInternationalizedExcelTemplateAfterTitleRows() throws Exception {
        String metadata = """
                {"scopeType":"GLOBAL","actionType":"ADD","functionCodes":["cardNo"],"orders":[]}
                """;
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Transaction Blacklist Template");
            sheet.createRow(0).createCell(0).setCellValue("Transaction Blacklist Import Template");
            sheet.createRow(1).createCell(0).setCellValue("Export Time: 2026-11-14 14:39:40");
            var header = sheet.createRow(2);
            header.createCell(0).setCellValue("Identifier Type");
            header.createCell(1).setCellValue("Source Merchant ID (Required for Merchant Order)");
            header.createCell(2).setCellValue("Order Number");
            var systemOrder = sheet.createRow(3);
            systemOrder.createCell(0).setCellValue("System Order Number");
            systemOrder.createCell(2).setCellValue("202611141040357845269");
            var merchantOrder = sheet.createRow(4);
            merchantOrder.createCell(0).setCellValue("Merchant Order Number");
            merchantOrder.createCell(1).setCellValue("M200045");
            merchantOrder.createCell(2).setCellValue("SDKPAY999261114104034860");
            workbook.write(output);
            workbookBytes = output.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "Transaction_Blacklist_Import_Template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes);

        TradeBlackWorkflowDTOs.PreviewRequest request = parser.parse(metadata, file);

        assertThat(request.getOrders()).hasSize(2);
        assertThat(request.getOrders().get(0).getIdentifierType()).isEqualTo("SYSTEM_ORDER_NO");
        assertThat(request.getOrders().get(0).getOrderNo()).isEqualTo("202611141040357845269");
        assertThat(request.getOrders().get(1).getIdentifierType()).isEqualTo("MERCHANT_ORDER_NO");
        assertThat(request.getOrders().get(1).getLookupMerchantId()).isEqualTo("M200045");
    }

    @Test
    void shouldParseChineseSourceMerchantHeaderFromGeneratedTemplate() throws Exception {
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("系统交易加黑导入模板");
            sheet.createRow(0).createCell(0).setCellValue("系统交易加黑导入模板");
            sheet.createRow(1).createCell(0).setCellValue("导出时间：2026-11-14 14:39:40");
            var header = sheet.createRow(2);
            header.createCell(0).setCellValue("订单号类型");
            header.createCell(1).setCellValue("查询来源商户号（商户订单号时必填）");
            header.createCell(2).setCellValue("订单号");
            var merchantOrder = sheet.createRow(3);
            merchantOrder.createCell(0).setCellValue("商户订单号");
            merchantOrder.createCell(1).setCellValue("M200045");
            merchantOrder.createCell(2).setCellValue("SDKPAY999261114104034860");
            workbook.write(output);
            workbookBytes = output.toByteArray();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "系统交易加黑导入模板.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbookBytes);

        String metadata = """
                {"scopeType":"GLOBAL","actionType":"ADD","functionCodes":["cardNo"],"orders":[]}
                """;
        TradeBlackWorkflowDTOs.PreviewRequest request = parser.parse(metadata, file);

        assertThat(request.getOrders()).singleElement().satisfies(order -> {
            assertThat(order.getIdentifierType()).isEqualTo("MERCHANT_ORDER_NO");
            assertThat(order.getLookupMerchantId()).isEqualTo("M200045");
            assertThat(order.getOrderNo()).isEqualTo("SDKPAY999261114104034860");
        });
    }
}
