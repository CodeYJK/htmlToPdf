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
    private final FixedLayoutAnalyzer fixedLayoutAnalyzer;

    public TemplateWorkbenchService(@Value("${contract.workbench.root:contract-workbench}") String root, FixedLayoutAnalyzer fixedLayoutAnalyzer) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
        this.fixedLayoutAnalyzer = fixedLayoutAnalyzer;
    }

    public WorkbenchResult render(String projectName, MultipartFile templateFile, MultipartFile dataFile, String dataText, String layoutText) throws Exception {
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
        return renderContent(safeName, project, template, originalData, layoutText);
    }

    public WorkbenchResult renderSaved(String projectName, String dataText, String layoutText) throws Exception {
        Path project = root.resolve(projectName).normalize();
        if (!project.startsWith(root) || !Files.exists(project.resolve("template.html"))) throw new IllegalArgumentException("模板项目不存在");
        String template = new String(Files.readAllBytes(project.resolve("template.html")), StandardCharsets.UTF_8);
        Path layoutPath = project.resolve("layout.json");
        String layout = layoutText != null && !layoutText.trim().isEmpty() ? layoutText : (Files.exists(layoutPath) ? new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8) : null);
        return renderContent(projectName, project, template, dataText, layout);
    }

    private WorkbenchResult renderContent(String safeName, Path project, String template, String originalData, String layoutText) throws Exception {
        if (layoutText != null && !layoutText.trim().isEmpty()) template = fixedLayoutAnalyzer.apply(template, layoutText);
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
        FixedLayoutAnalyzer.LayoutAnalysis layout = fixedLayoutAnalyzer.analyze(template);
        if (layoutText != null && !layoutText.trim().isEmpty()) JSON.parseObject(layoutText);
        if (!layout.pages.isEmpty()) Files.write(project.resolve("layout.json"), (layoutText == null || layoutText.trim().isEmpty() ? JSON.toJSONString(layout, true) : layoutText).getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        util.createPDF(data, pdf, template, safeName);
        byte[] pdfBytes = pdf.toByteArray();
        Files.write(project.resolve("output/result.pdf"), pdfBytes);

        return new WorkbenchResult(safeName, html, Base64.getEncoder().encodeToString(pdfBytes), new ArrayList<>(fields), new ArrayList<>(missingFields), skeleton);
    }

    public List<String> listProjects() throws IOException {
        if (!Files.exists(root)) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path path : stream) {
                if (Files.isDirectory(path) && Files.exists(path.resolve("template.html"))) result.add(path.getFileName().toString());
            }
        }
        Collections.sort(result);
        return result;
    }

    public SavedProject loadProject(String projectName) throws IOException {
        Path project = root.resolve(projectName).normalize();
        if (!project.startsWith(root) || !Files.exists(project.resolve("template.html"))) throw new IllegalArgumentException("模板项目不存在");
        String template = new String(Files.readAllBytes(project.resolve("template.html")), StandardCharsets.UTF_8);
        Path dataPath = project.resolve("data.json");
        String data = Files.exists(dataPath) ? new String(Files.readAllBytes(dataPath), StandardCharsets.UTF_8) : "{}";
        Path layoutPath = project.resolve("layout.json");
        String layout = Files.exists(layoutPath) ? new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8) : "{}";
        return new SavedProject(projectName, template, data, layout);
    }

    public TemplateAnalysis analyze(MultipartFile templateFile) throws IOException {
        return analyzeContent(new String(templateFile.getBytes(), StandardCharsets.UTF_8));
    }

    private TemplateAnalysis analyzeContent(String template) {
        Set<String> fields = extractFields(template);
        List<String> warnings = new ArrayList<>();
        String lower = template.toLowerCase(Locale.ROOT);
        if (lower.contains("display:flex") || lower.contains("display: flex")) warnings.add("检测到 flex：Flying Saucer 9.1.5 不建议使用，请改用 table 或普通 div。");
        if (lower.contains("display:grid") || lower.contains("display: grid")) warnings.add("检测到 grid：Flying Saucer 9.1.5 不支持，建议改用 table。");
        if (lower.matches("(?s).*\\b(?:rem|vh|vw)\\b.*")) warnings.add("检测到 rem/vh/vw 单位：建议改用 pt、px、mm 或 cm，保证 PDF 尺寸稳定。");
        if (lower.contains("transform:") || lower.contains("position:fixed")) warnings.add("检测到 transform 或 fixed 定位：可能导致 PDF 坐标漂移，请谨慎使用。");
        if (!lower.contains("<html")) warnings.add("模板没有完整 html 根节点，工具会自动补齐，但建议显式维护 XHTML 结构。");
        if (!lower.contains("<meta") || !lower.contains("charset")) warnings.add("模板未声明 UTF-8 字符集，中文渲染可能出现乱码。");
        if (count(template, "<#if") != count(template, "</#if>")) warnings.add("FreeMarker if 标签数量不匹配，请检查 <#if> 与 </#if>。");
        if (count(template, "<#list") != count(template, "</#list>")) warnings.add("FreeMarker list 标签数量不匹配，请检查 <#list> 与 </#list>。");
        Matcher indexed = Pattern.compile("([A-Za-z][A-Za-z0-9_]*)\\[(\\d+)\\]").matcher(template);
        Set<String> indexedFields = new TreeSet<>();
        while (indexed.find()) indexedFields.add(indexed.group(1) + "[" + indexed.group(2) + "]");
        if (!indexedFields.isEmpty()) warnings.add("检测到固定列表下标：" + String.join("、", indexedFields) + "，请确认测试数据长度足够。");
        Matcher page = Pattern.compile("@page\\s*\\{([^}]*)}", Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(template);
        String pageRule = page.find() ? page.group(1).replaceAll("\\s+", " ").trim() : "未声明";
        String templateType = lower.contains("position: absolute") || lower.contains("position:absolute") ? "FIXED_LAYOUT" : (lower.contains("<table") ? "TABLE_OR_FLOW" : "FLOW");
        return new TemplateAnalysis(templateType, new ArrayList<>(fields), pageRule, warnings);
    }

    private int count(String text, String token) {
        int count = 0, start = 0;
        while ((start = text.indexOf(token, start)) >= 0) { count++; start += token.length(); }
        return count;
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

    public static class SavedProject {
        public final String projectName, template, data, layout;
        public SavedProject(String projectName, String template, String data, String layout) { this.projectName = projectName; this.template = template; this.data = data; this.layout = layout; }
    }

    public static class TemplateAnalysis {
        public final String templateType;
        public final List<String> fields;
        public final String pageRule;
        public final List<String> warnings;
        public TemplateAnalysis(String templateType, List<String> fields, String pageRule, List<String> warnings) { this.templateType = templateType; this.fields = fields; this.pageRule = pageRule; this.warnings = warnings; }
    }
}
