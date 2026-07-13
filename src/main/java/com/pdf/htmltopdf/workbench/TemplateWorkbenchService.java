package com.pdf.htmltopdf.workbench;

import com.alibaba.fastjson.JSON;
import com.pdf.htmltopdf.PDFTemplateUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TemplateWorkbenchService {
    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{([^}]+)}");
    private static final Pattern FIELD = Pattern.compile("(?:^|[^A-Za-z0-9_])([A-Za-z][A-Za-z0-9_]*)");
    private static final Pattern INDEXED_FIELD = Pattern.compile("([A-Za-z][A-Za-z0-9_]*)\\[(\\d+)]\\.([A-Za-z][A-Za-z0-9_]*)");
    private final Path root;

    public TemplateWorkbenchService(@Value("${contract.workbench.root:contract-workbench}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }

    public WorkbenchResult render(String projectName, MultipartFile templateFile, MultipartFile dataFile, String dataText) throws Exception {
        String safeName = projectName == null || projectName.trim().isEmpty() ? "未命名模板" : projectName.trim();
        Path project = root.resolve(safeName).normalize();
        if (!project.startsWith(root)) throw new IllegalArgumentException("模板名称不合法");
        Files.createDirectories(project.resolve("output"));

        String template = new String(templateFile.getBytes(), StandardCharsets.UTF_8);
        Files.write(project.resolve("template.html"), template.getBytes(StandardCharsets.UTF_8));

        String originalData = dataText;
        if ((originalData == null || originalData.trim().isEmpty()) && dataFile != null && !dataFile.isEmpty()) {
            originalData = new String(dataFile.getBytes(), StandardCharsets.UTF_8);
        }
        Map<String, Object> data = originalData == null || originalData.trim().isEmpty()
                ? new LinkedHashMap<String, Object>() : JSON.parseObject(originalData, LinkedHashMap.class);
        Set<String> fields = extractFields(template);
        Set<String> missingFields = new TreeSet<>();
        for (String field : fields) {
            if (!data.containsKey(field)) {
                data.put(field, "");
                missingFields.add(field);
            }
        }
        fillIndexedLists(template, data, missingFields);
        String skeleton = JSON.toJSONString(data, true);
        Files.write(project.resolve("data.json"), skeleton.getBytes(StandardCharsets.UTF_8));

        PDFTemplateUtil util = new PDFTemplateUtil();
        String html = util.createHtml(data, template, safeName);
        if (html == null) throw new IllegalStateException("模板渲染失败，请检查 FreeMarker 语法或 JSON 数据");
        Path htmlPath = project.resolve("output/rendered.html");
        Files.write(htmlPath, html.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        util.createPDF(data, pdf, template, safeName);
        byte[] pdfBytes = pdf.toByteArray();
        Files.write(project.resolve("output/result.pdf"), pdfBytes);

        return new WorkbenchResult(safeName, html, Base64.getEncoder().encodeToString(pdfBytes), new ArrayList<>(fields), new ArrayList<>(missingFields), skeleton);
    }

    private Set<String> extractFields(String template) {
        Set<String> fields = new TreeSet<>();
        Matcher expressionMatcher = EXPRESSION.matcher(template);
        while (expressionMatcher.find()) {
            Matcher fieldMatcher = FIELD.matcher(expressionMatcher.group(1));
            if (fieldMatcher.find()) fields.add(fieldMatcher.group(1));
        }
        return fields;
    }

    private void fillIndexedLists(String template, Map<String, Object> data, Set<String> missingFields) {
        Matcher matcher = INDEXED_FIELD.matcher(template);
        Map<String, Integer> maxIndexes = new LinkedHashMap<>();
        while (matcher.find()) {
            String listName = matcher.group(1);
            int index = Integer.parseInt(matcher.group(2));
            maxIndexes.put(listName, Math.max(maxIndexes.containsKey(listName) ? maxIndexes.get(listName) : -1, index));
        }
        for (Map.Entry<String, Integer> entry : maxIndexes.entrySet()) {
            Object current = data.get(entry.getKey());
            if (!(current instanceof List)) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (int i = 0; i <= entry.getValue(); i++) rows.add(new LinkedHashMap<String, Object>());
                data.put(entry.getKey(), rows);
                missingFields.add(entry.getKey());
            }
        }
    }

    public static class WorkbenchResult {
        public final String projectName, html, pdfBase64, dataSkeleton;
        public final List<String> fields, missingFields;
        public WorkbenchResult(String projectName, String html, String pdfBase64, List<String> fields, List<String> missingFields, String dataSkeleton) {
            this.projectName = projectName; this.html = html; this.pdfBase64 = pdfBase64;
            this.fields = fields; this.missingFields = missingFields; this.dataSkeleton = dataSkeleton;
        }
    }
}
