package com.pdf.htmltopdf.workbench;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FixedLayoutAnalyzer {
    private static final Pattern PAGE = Pattern.compile("<div[^>]*(?:id=\\\"pf(\\d+)\\\"|class=\\\"page\\\")[^>]*>(.*?)</div>\\s*(?=<div[^>]*(?:id=\\\"pf|class=\\\"page\\\")|</body>)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern BLOCK = Pattern.compile("<(?:div|p|table)[^>]*style\\s*=\\s*\\\"([^\\\"]*position\\s*:\\s*absolute[^\\\"]*)\\\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern VALUE = Pattern.compile("(left|top|width|height)\\s*:\\s*([0-9.]+)\\s*(px|pt)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern FIELD = Pattern.compile("\\$\\{([^}]+)}");

    public LayoutAnalysis analyze(MultipartFile file) throws Exception {
        return analyze(new String(file.getBytes(), StandardCharsets.UTF_8));
    }

    public LayoutAnalysis analyze(String html) {
        Matcher pageMatcher = PAGE.matcher(html);
        List<PageLayout> pages = new ArrayList<>();
        int pageNumber = 1;
        while (pageMatcher.find()) {
            String content = pageMatcher.group(2);
            List<Map<String, String>> blocks = new ArrayList<>();
            Matcher firstBlock = Pattern.compile("^\\s*<div[^>]*style\\s*=\\s*\\\"([^\\\"]+)\\\"", Pattern.CASE_INSENSITIVE).matcher(content);
            if (firstBlock.find() && !firstBlock.group(1).toLowerCase(Locale.ROOT).contains("position") && firstBlock.group(1).contains("width") && firstBlock.group(1).contains("height")) {
                Map<String, String> block = new LinkedHashMap<>();
                Matcher valueMatcher = VALUE.matcher(firstBlock.group(1));
                while (valueMatcher.find()) block.put(valueMatcher.group(1).toLowerCase(Locale.ROOT), valueMatcher.group(2) + valueMatcher.group(3));
                blocks.add(block);
            }
            Matcher blockMatcher = BLOCK.matcher(content);
            while (blockMatcher.find()) {
                Map<String, String> block = new LinkedHashMap<>();
                Matcher valueMatcher = VALUE.matcher(blockMatcher.group(1));
                while (valueMatcher.find()) block.put(valueMatcher.group(1).toLowerCase(Locale.ROOT), valueMatcher.group(2) + valueMatcher.group(3));
                blocks.add(block);
            }
            List<Map<String, String>> columns = new ArrayList<>();
            for (Map<String, String> block : blocks) {
                String height = block.get("height");
                if (height != null && Double.parseDouble(height.replaceAll("[^0-9.]", "")) >= 600) columns.add(block);
            }
            pages.add(new PageLayout(pageMatcher.group(1) == null ? String.valueOf(pageNumber) : pageMatcher.group(1), blocks, columns));
            pageNumber++;
        }
        Set<String> fields = new TreeSet<>();
        Matcher fieldMatcher = FIELD.matcher(html);
        while (fieldMatcher.find()) fields.add(fieldMatcher.group(1).trim().split("[ .?]", 2)[0]);
        String pageRule = html.replaceAll("(?s).*?@page\\s*\\{([^}]*)}.*", "$1").replaceAll("\\s+", " ").trim();
        return new LayoutAnalysis(pageRule, pages, new ArrayList<>(fields), html.contains("page-break-after"));
    }

    public String apply(String html, String layoutJson) {
        JSONObject config = JSONObject.parseObject(layoutJson);
        JSONArray pages = config.getJSONArray("pages");
        if (pages == null) return html;
        List<Map<String, String>> blocks = new ArrayList<>();
        for (Object pageObject : pages) {
            JSONObject page = (JSONObject) pageObject;
            JSONArray pageBlocks = page.getJSONArray("columns");
            if (pageBlocks == null) pageBlocks = page.getJSONArray("blocks");
            if (pageBlocks != null) for (Object blockObject : pageBlocks) blocks.add(new LinkedHashMap<String, String>((Map<String, String>) (Map) blockObject));
        }
        Matcher matcher = Pattern.compile("(<div[^>]*style\\s*=\\s*\\\")([^\\\"]*)(\\\")", Pattern.CASE_INSENSITIVE).matcher(html);
        StringBuffer result = new StringBuffer();
        int index = 0;
        while (matcher.find()) {
            String style = matcher.group(2);
            Matcher heightMatcher = Pattern.compile("height\\s*:\\s*([0-9.]+)", Pattern.CASE_INSENSITIVE).matcher(style);
            if (!heightMatcher.find() || Double.parseDouble(heightMatcher.group(1)) < 600 || index >= blocks.size()) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            Map<String, String> block = blocks.get(index++);
            for (String property : Arrays.asList("left", "top", "width", "height")) {
                String value = block.get(property);
                if (value != null) style = style.replaceAll("(?i)" + property + "\\s*:\\s*[0-9.]+\\s*(px|pt)?", property + ":" + value);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(1) + style + matcher.group(3)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    public static class LayoutAnalysis {
        public final String pageRule;
        public final List<PageLayout> pages;
        public final List<String> fields;
        public final boolean hasPageBreak;
        public LayoutAnalysis(String pageRule, List<PageLayout> pages, List<String> fields, boolean hasPageBreak) { this.pageRule = pageRule; this.pages = pages; this.fields = fields; this.hasPageBreak = hasPageBreak; }
    }
    public static class PageLayout {
        public final String page;
        public final List<Map<String, String>> blocks;
        public final List<Map<String, String>> columns;
        public PageLayout(String page, List<Map<String, String>> blocks, List<Map<String, String>> columns) { this.page = page; this.blocks = blocks; this.columns = columns; }
    }
}
