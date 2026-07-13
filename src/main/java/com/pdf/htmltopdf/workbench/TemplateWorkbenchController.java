package com.pdf.htmltopdf.workbench;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/workbench")
public class TemplateWorkbenchController {
    private final TemplateWorkbenchService service;
    private final FixedLayoutAnalyzer fixedLayoutAnalyzer;
    public TemplateWorkbenchController(TemplateWorkbenchService service, FixedLayoutAnalyzer fixedLayoutAnalyzer) { this.service = service; this.fixedLayoutAnalyzer = fixedLayoutAnalyzer; }

    @PostMapping(value = "/render", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TemplateWorkbenchService.WorkbenchResult render(
            @RequestParam String projectName,
            @RequestPart MultipartFile template,
            @RequestPart(required = false) MultipartFile dataFile,
            @RequestParam(required = false) String dataText,
            @RequestParam(required = false) String layoutText) throws Exception {
        return service.render(projectName, template, dataFile, dataText, layoutText);
    }

    @GetMapping("/projects")
    public List<String> projects() throws Exception { return service.listProjects(); }

    @GetMapping("/projects/{projectName}")
    public TemplateWorkbenchService.SavedProject project(@PathVariable String projectName) throws Exception { return service.loadProject(projectName); }

    @PostMapping("/projects/{projectName}/render")
    public TemplateWorkbenchService.WorkbenchResult renderSaved(@PathVariable String projectName, @RequestParam(required = false) String dataText, @RequestParam(required = false) String layoutText) throws Exception {
        return service.renderSaved(projectName, dataText, layoutText);
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TemplateWorkbenchService.TemplateAnalysis analyze(@RequestPart MultipartFile template) throws Exception { return service.analyze(template); }

    @PostMapping(value = "/analyze-fixed-layout", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FixedLayoutAnalyzer.LayoutAnalysis analyzeFixedLayout(@RequestPart MultipartFile template) throws Exception { return fixedLayoutAnalyzer.analyze(template); }
}
