package com.scott.payment.admin.support.template;

import com.alibaba.excel.EasyExcel;
import com.scott.payment.admin.infrastructure.storage.ObjectStorageProperties;
import com.scott.payment.component.core.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 模板文件类型、宏和路径安全合同。 */
class TemplateFileSecurityValidatorTest {

    private TemplateFileSecurityValidator validator;

    @BeforeEach
    void setUp() {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setMaxFileSizeBytes(1024 * 1024);
        validator = new TemplateFileSecurityValidator(properties);
    }

    @Test
    void shouldAcceptUtf8CsvAndIgnoreClientMime() {
        MockMultipartFile file = new MockMultipartFile("file", "风险名单.csv", "application/octet-stream",
                "merchantId,reason\nM001,test\n".getBytes(StandardCharsets.UTF_8));

        var validated = validator.validate(file, "csv");

        assertThat(validated.originalFilename()).isEqualTo("风险名单.csv");
        assertThat(validated.contentType()).isEqualTo("text/csv;charset=UTF-8");
        assertThat(validated.sha256()).hasSize(64);
    }

    @Test
    void shouldRejectFilenameWhoseExtensionDoesNotMatchDefinition() {
        MockMultipartFile file = new MockMultipartFile("file", "template.txt", "text/plain",
                "a,b\n".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> validator.validate(file, "csv"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("扩展名");
    }

    @Test
    void shouldAcceptMacroFreeXlsxContainer() throws Exception {
        byte[] content = zip("[Content_Types].xml", "<Types/>", "xl/workbook.xml", "<workbook/>");
        MockMultipartFile file = new MockMultipartFile("file", "template.xlsx", "application/zip", content);

        var validated = validator.validate(file, "xlsx");

        assertThat(validated.contentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @Test
    void shouldAcceptXlsxGeneratedByUnifiedExcelService() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        EasyExcel.write(output)
                .autoCloseStream(false)
                .head(List.of(List.of("订单号")))
                .sheet("导入模板")
                .doWrite(List.of(List.of("SYSTEM_ORDER_EXAMPLE")));
        MockMultipartFile file = new MockMultipartFile(
                "file", "template.xlsx", "application/octet-stream", output.toByteArray());

        var validated = validator.validate(file, "xlsx");

        assertThat(validated.contentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    @Test
    void shouldRejectOfficeMacroPayload() throws Exception {
        byte[] content = zip("[Content_Types].xml", "<Types/>", "xl/workbook.xml", "<workbook/>",
                "xl/vbaProject.bin", "macro");
        MockMultipartFile file = new MockMultipartFile("file", "template.xlsx", "application/zip", content);

        assertThatThrownBy(() -> validator.validate(file, "xlsx"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("宏");
    }

    @Test
    void shouldRejectZipTraversal() throws Exception {
        byte[] content = zip("../escape.txt", "bad");
        MockMultipartFile file = new MockMultipartFile("file", "template.zip", "application/zip", content);

        assertThatThrownBy(() -> validator.validate(file, "zip"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("非法路径");
    }

    private byte[] zip(String... values) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            for (int index = 0; index < values.length; index += 2) {
                zip.putNextEntry(new ZipEntry(values[index]));
                zip.write(values[index + 1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
