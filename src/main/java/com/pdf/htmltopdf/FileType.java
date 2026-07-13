package com.pdf.htmltopdf;

public enum FileType {
    CSV("csv", "CSV文件"),
    XLS("xls", "Excel 97-2003"),
    XLSX("xlsx", "Excel 2007+"),
    DOC("doc", "Word 97-2003"),
    DOCX("docx", "Word 2007+");

    private final String extension;
    private final String description;

    FileType(String extension, String description) {
        this.extension = extension;
        this.description = description;
    }

    public static FileType fromFileName(String fileName) {
        if (fileName == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".csv")) return CSV;
        if (lowerName.endsWith(".xls")) return XLS;
        if (lowerName.endsWith(".xlsx")) return XLSX;
        if (lowerName.endsWith(".doc")) return DOC;
        if (lowerName.endsWith(".docx")) return DOCX;

        throw new IllegalArgumentException("不支持的文件格式: " + fileName);
    }

    public String getExtension() {
        return extension;
    }

    public String getDescription() {
        return description;
    }
}
