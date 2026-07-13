package com.pdf.htmltopdf.workbench;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/workbench")
public class TemplateWorkbenchController {
    private final TemplateWorkbenchService service;
    public TemplateWorkbenchController(TemplateWorkbenchService service) { this.service = service; }

    @PostMapping(value = "/render", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TemplateWorkbenchService.WorkbenchResult render(
            @RequestParam String projectName,
            @RequestPart MultipartFile template,
            @RequestPart(required = false) MultipartFile dataFile,
            @RequestParam(required = false) String dataText) throws Exception {
        return service.render(projectName, template, dataFile, dataText);
    }
}
