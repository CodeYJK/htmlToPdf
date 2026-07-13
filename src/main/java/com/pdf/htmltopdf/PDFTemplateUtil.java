package com.pdf.htmltopdf;

import com.alibaba.fastjson.JSONObject;
import com.lowagie.text.pdf.BaseFont;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.xhtmlrenderer.pdf.ITextFontResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.swing.Java2DRenderer;
import org.xhtmlrenderer.util.FSImageWriter;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * Created by jary on 2018/8/8.
 */
public class PDFTemplateUtil {

    private Logger logger = LoggerFactory.getLogger(this.getClass().getName());

    /**
     * classpath路径
     */
    private String classpath = getClass().getResource("/").getPath();

    /**
     * 指定FreeMarker模板文件的位置
     */
    private String templatePath = "/pdf";

    /**
     * freeMarker模板文件名称
     */
    private String templateFileName = "pdf.ftl";

    /**
     * 图片路径 —— 默认是classpath下面的images文件夹
     */
    private String imagePath = "/images/";

    /**
     * 字体资源文件 存放路径
     */
    private String fontPath = "font/";

    /**
     * 字体   [宋体][simsun.ttc]   [黑体][simhei.ttf]
     */
    private String font = "simsun.ttc";
    private String fontBold = "simsun_bold.ttf";

    /**
     * 指定编码
     */
    private String encoding = "UTF-8";

    public static Map<String, Object> getClassToMap(Object obj) {
        String jsonText = JSONUtils.formObj2JSON(obj);
        return (Map<String, Object>) JSONObject.parseObject(jsonText, Map.class);
    }


    /**
     * 生成html 文件
     *
     * @param data
     * @param templeteBuffer
     * @param templateName
     * @return
     */
    public String createHtml(Object data, String templeteBuffer, String templateName) {
        if (templeteBuffer.contains("xmlns") && templeteBuffer.contains("html")) {
            return createHtmlBody(data, templeteBuffer, templateName);
        }

        return createHtmlBody(data, htmlHead + templeteBuffer + htmlEnd, templateName);
    }


    /**
     * 生成html 文件
     *
     * @param data
     * @param templeteBuffer
     * @param templateName
     * @return
     */
    public String createHtmlBody(Object data, String templeteBuffer, String templateName){
        try {
            logger.info("HTML模板名称：{}", templateName);
            //logger.info("222222协议模板内容：{}", templeteBuffer);
            logger.info("HTML模板内容参数：{}", JSONObject.toJSONString(data));

            // 创建一个FreeMarker实例, 负责管理FreeMarker模板的Configuration实例
            Configuration cfg = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
            // 指定FreeMarker模板文件的位置
            cfg.setClassForTemplateLoading(getClass(), templatePath);
            StringTemplateLoader stringLoader = new StringTemplateLoader();
            stringLoader.putTemplate(templateName, templeteBuffer);
            cfg.setTemplateLoader(stringLoader);

            // 设置模板的编码格式
            cfg.setEncoding(Locale.CHINA, encoding);
            // 获取模板文件 template.ftl
            Template template = cfg.getTemplate(templateName, encoding);
            StringWriter writer = new StringWriter();
            // 将数据输出到html中
            template.process(data, writer);
            writer.flush();

            String html = writer.toString();

            return html;
        } catch (Exception e) {
            logger.error("生成HTML异常：", e);
            return null;
        }
    }

    public void createHtml(Object data, OutputStream out, String templeteBuffer, String templateName) throws Exception {
        try {
            String html = this.createHtml(data, templeteBuffer, templateName);
            out.write(html.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (Exception e) {
            logger.error("生成HTML文件发生异常", e);
            throw e;
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }


    /**
     * 生成pdf
     *
     * @param data 传入到freemarker模板里的数据
     * @param out  生成的pdf文件流
     */
    public void createPDF(Object data, OutputStream out, String templeteBuffer, String templateName) throws Exception {
        logger.info("准备协议文件6666");
        ITextRenderer renderer = new ITextRenderer();
        try {
            //设置 css中 的字体样式（暂时仅支持宋体和黑体）
            ITextFontResolver fontResolver = renderer.getFontResolver();

            String fontBoldPath = getFontsPath(font);
            logger.info("准备协议文件7777--begin_fontBoldPath:{}", fontBoldPath);
            if (StringUtils.isNotEmpty(fontBoldPath)) {
                logger.info("准备协议文件7777--fontBoldPath:{}", fontBoldPath);
                fontResolver.addFont(fontBoldPath, BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                //renderer.getSharedContext().setFontResolver(fontResolver);
            }

            String fontPath = getFontsPath(fontBold);
            logger.info("准备协议文件7777--begin_fontPath:{}", fontPath);
            if (StringUtils.isNotEmpty(fontPath)) {
                logger.info("准备协议文件7777--fontPath:{}", fontPath);
                fontResolver.addFont(fontPath, BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
                //renderer.getSharedContext().setFontResolver(fontResolver);
            }

            logger.info("准备协议文件7777");
            String html = this.createHtml(data, templeteBuffer, templateName);
            logger.info("准备协议文件8888");
            // 把html代码传入渲染器中
            renderer.setDocumentFromString(html);
            // 解决图片的相对路径问题 ##必须在设置document后再设置图片路径，不然不起作用
            // 如果使用绝对路径依然有问题，可以在路径前面加"file:/"
            renderer.getSharedContext().setBaseURL(classpath + imagePath);
            renderer.layout();

            renderer.createPDF(out, false);
            renderer.finishPDF();
            out.flush();
            out.close();

            logger.info("准备协议文件9999");
        } catch (Exception e) {
            logger.error("生成pdf发生异常", e);
            if (out != null) {
                out.close();
            }
            throw e;
        }
    }


    private String getFontsPath(String fontName) {
        try {
            String pdfFont = "/Users/changxin/Documents/dev-tools/idea/workspace/htmltopdf/src/main/resources/font/";
            File fileDir = new File(pdfFont);
            if (!fileDir.exists()) {
                fileDir.mkdirs();
            }
            File fontFile = new File(pdfFont + fontName);
            if (!fontFile.exists()) {
                Resource resource = new ClassPathResource(fontPath + fontName);
                InputStream inputStream = resource.getInputStream();
                FileOutputStream fontFileOut = new FileOutputStream(fontFile);
                int bytes = inputStream.read();
                //循环将文件fileText.txt中的内容读取到字节数组fileInput中
                while (bytes != -1) {
                    fontFileOut.write(bytes);
                    bytes = inputStream.read();
                }

                inputStream.close();
                fontFileOut.flush();
                fontFileOut.close();
            }

            return fontFile.getPath();
        } catch (Exception e) {
            logger.info("设置字体异常", e);
        }

        return null;
    }


    public void createImage(String urlPath, String outputFilename, int width) {
        try {

            File file = new File(urlPath);
            Java2DRenderer renderer = new Java2DRenderer(file, width);
            BufferedImage img = renderer.getImage();
            FSImageWriter imageWriter = new FSImageWriter();

            imageWriter.setWriteCompressionQuality(0.9f);
            imageWriter.write(img, outputFilename);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    public void setClasspath(String classpath) {
        this.classpath = classpath;
    }


    public void setTemplatePath(String templatePath) {
        this.templatePath = templatePath;
    }


    public void setTemplateFileName(String templateFileName) {
        this.templateFileName = templateFileName;
    }


    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }


    public void setFontPath(String fontPath) {
        this.fontPath = fontPath;
    }


    public void setFont(String font) {
        this.font = font;
    }

    public void setFontBold(String fontBold) {
        this.fontBold = fontBold;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }


    public static final String htmlHead = " <html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
            " \t<head>\n" +
            " \t\t<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n" +
            " \t\t<meta http-equiv=\"pragma\" content=\"no-cache\" />\n" +
            "\t\t<meta http-equiv=\"cache-control\" content=\"no-cache\" />\n" +
            "\t\t<meta http-equiv=\"expires\" content=\"0\" />    \n" +
            " \t\t<title></title>\n" +
            " \t\t<style type=\"text/css\">\n" +
            " \t\t\t*{margin:0; \n" +
            " \t\t\t\tpadding:10; \n" +
            " \t\t\t\tlist-style:none; \n" +
            " \t\t\t\tfont-family:SimSun;\n" +
            "\t\t\t\t}\n" +
            "\t\t\tp {\n" +
            "\t\t\t\twidth: 100%;\n" +
            "\t\t\t\tmax-height: 90px;\n" +
            "\t\t\t\tline-height: 25px;\n" +
            "\t\t\t\tfont-size: 14px;\n" +
            "\t\t\t\tletter-spacing: 2px;\n" +
            "\t\t\t\tpadding-bottom: 10px;\n" +
            "\t\t\t\tword-wrap: break-word;white-space : normal\n" +
            "\n" +
            "\t\t\t}\n" +
            "\t\t\t@page {\n" +
            "\t\t\t  size: A4;\n" +
            "\t\t\t}\n" +
            " \t\t\n" +
            " \t\t\t</style>\n" +
            " \t\t</head>\n" +
            " \t<body>";

    public static final String htmlEnd = "\n</body>\n" +
            "</html>";


}