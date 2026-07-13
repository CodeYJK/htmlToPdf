# 双方租赁服务协议 HTML 模板 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with verification checkpoints.

**Goal:** 按 20260528 修订版双方租赁服务协议 Word 原稿，制作一个适配当前 Flying Saucer 9.1.5 + FreeMarker 渲染器的 A4 横向 HTML 协议模板。

**Architecture:** 使用固定页面容器模拟原 Word 的横向页面和分页；订单信息、租金支付表、签署页和租享优附件使用明确表格/块布局，正文条款采用稳定的普通 div 流式排版。所有动态值沿用当前项目已有字段命名，缺失数据由 FreeMarker 默认值输出为空白。

**Tech Stack:** XHTML、FreeMarker、Flying Saucer 9.1.5、项目内 SimSun 字体、Maven/Spring Boot 工作台。

## Global Constraints

- 页面必须声明 A4 横向：297mm × 210mm。
- CSS 只使用 Flying Saucer 9.1.5 稳定支持的普通布局、table、absolute 定位和 page-break。
- 不使用 flex、grid、transform、position:fixed、rem/vh/vw。
- 中文字体使用项目已有 SimSun/SIMSUN_BOLD 资源。
- 变量沿用现有项目命名；无数据时显示空字符串或可见空白线。
- 正文内容不得擅自改写；只做 HTML 结构和版式映射。

### Task 1: 建立协议模板

**Files:**
- Create: `src/main/resources/templates/双方租赁服务协议.html`

**Interfaces:**
- Consumes: `PDFTemplateUtil.createHtml(Map, template, templateName)` 传入的 FreeMarker 数据。
- Produces: 可被现有 workbench 上传/保存/渲染的 XHTML 模板。

- [ ] 创建完整 XHTML 根节点、A4 横向页面规则、宋体字体规则和页码规则。
- [ ] 将协议主体拆为固定页面块，加入订单信息表和租金明细表。
- [ ] 使用 `${field ! ""}` 和 `${(rentalList[n].field) ! ""}` 接入已有字段命名。
- [ ] 增加签署页及《租享优增值服务协议》附件。

### Task 2: 静态模板检查

**Files:**
- Check: `src/main/resources/templates/双方租赁服务协议.html`

- [ ] 检查 XHTML 标签闭合、FreeMarker if/list 标签配对和动态字段提取。
- [ ] 检查模板不包含 workbench 已知不兼容 CSS。

### Task 3: 生成并检查 PDF

**Files:**
- Generate: `contract-workbench/双方租赁服务协议/output/result.pdf`
- Generate: `contract-workbench/双方租赁服务协议/output/rendered.html`

- [ ] 使用空数据渲染，确认模板不因缺失字段失败。
- [ ] 使用包含 12 期租金列表的最小样例数据渲染，确认表格行和字段替换正常。
- [ ] 使用 Poppler 将 PDF 渲染为 PNG，逐页检查是否有中文缺字、溢出、重叠、异常分页或空白页。
- [ ] 如有版式问题，修改模板并重复渲染检查。
