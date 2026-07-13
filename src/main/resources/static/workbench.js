const $ = id => document.getElementById(id);
const sourceFile = () => new File([$('templateText').value], 'template.html', {type: 'text/html'});
$('chooseData').onclick = () => $('dataFile').click();
$('dataFile').onchange = async () => { const file = $('dataFile').files[0]; if (file) $('dataText').value = await file.text(); };

$('template').onchange = async () => {
  const file = $('template').files[0];
  if (file) $('templateText').value = await file.text();
};

async function loadProjects() {
  const response = await fetch('/api/workbench/projects');
  if (!response.ok) return;
  for (const project of await response.json()) {
    $('projects').insertAdjacentHTML('beforeend', `<option value="${project}">${project}</option>`);
  }
}

$('projects').onchange = async () => {
  if (!$('projects').value) return;
  const response = await fetch('/api/workbench/projects/' + encodeURIComponent($('projects').value));
  const project = await response.json();
  $('projectName').value = project.projectName;
  $('dataText').value = project.data;
  $('template').value = '';
  $('templateText').value = project.template;
  $('layoutText').value = project.layout || '{}';
  $('status').textContent = '已加载 ' + project.projectName;
};

$('analyze').onclick = async () => {
  if (!$('templateText').value.trim()) { $('status').textContent = '请上传 HTML 或输入 HTML 源码'; return; }
  const form = new FormData();
  form.append('template', sourceFile());
  const response = await fetch('/api/workbench/analyze', {method: 'POST', body: form});
  const result = await response.json();
  $('report').innerHTML = `<p>模板类型：${result.templateType}</p><p>页面规则：${result.pageRule}</p><p>识别字段：${result.fields.join('、') || '无'}</p><p class="${result.warnings.length ? 'warn' : 'ok'}">${result.warnings.length ? result.warnings.join('<br>') : '未发现明显兼容性问题'}</p>`;
  $('status').textContent = '模板分析完成';
};

$('render').onclick = async () => {
  const saved = $('projects').value;
  let response;
  if (!$('templateText').value.trim() && saved) {
    const form = new FormData();
    if ($('dataText').value.trim()) form.append('dataText', $('dataText').value);
    if ($('layoutText').value.trim()) form.append('layoutText', $('layoutText').value);
    response = await fetch('/api/workbench/projects/' + encodeURIComponent(saved) + '/render', {method: 'POST', body: form});
  } else {
    if (!$('templateText').value.trim()) { $('status').textContent = '请上传 HTML 或输入 HTML 源码'; return; }
    const form = new FormData();
    form.append('projectName', $('projectName').value);
    form.append('template', sourceFile());
    const dataFile = $('dataFile').files[0];
    if (dataFile) form.append('dataFile', dataFile);
    if ($('dataText').value.trim()) form.append('dataText', $('dataText').value);
    response = await fetch('/api/workbench/render', {method: 'POST', body: form});
  }
  try {
    const result = await response.json();
    if (!response.ok) throw new Error(result.message || '生成失败');
    $('dataText').value = result.dataSkeleton;
    $('htmlPreview').srcdoc = result.html;
    $('pdfPreview').src = 'data:application/pdf;base64,' + result.pdfBase64;
    $('report').innerHTML = `<p>识别字段：${result.fields.length} 个</p><p class="${result.missingFields.length ? 'warn' : 'ok'}">缺失字段：${result.missingFields.length ? result.missingFields.join('、') : '无'}</p>`;
    $('status').textContent = '生成完成';
  } catch (error) { $('status').textContent = error.message; }
};

loadProjects();
$('downloadHtml').onclick = () => download('template.html', $('templateText').value);
$('downloadLayout').onclick = () => download('layout.json', $('layoutText').value || '{}');
function download(name, content) {
  const link = document.createElement('a');
  link.href = URL.createObjectURL(new Blob([content], {type: 'text/plain;charset=utf-8'}));
  link.download = name;
  link.click();
  URL.revokeObjectURL(link.href);
}
$('analyzeLayout').onclick = async () => {
  if (!$('templateText').value.trim()) { $('status').textContent = '请上传或编辑 HTML 模板'; return; }
  const form = new FormData();
  form.append('template', sourceFile());
  const response = await fetch('/api/workbench/analyze-fixed-layout', {method: 'POST', body: form});
  const result = await response.json();
  const summary = result.pages.map(page => `第${page.page}页：${(page.columns || page.blocks).length}个栏位，${page.blocks.length}个定位块`).join('<br>');
  $('report').innerHTML = `<p>页面规则：${result.pageRule}</p><p>页面数：${result.pages.length}，分页标记：${result.hasPageBreak ? '有' : '无'}</p><p>${summary || '未识别到固定页面块'}</p><p>动态字段：${result.fields.join('、') || '无'}</p>`;
  $('status').textContent = '固定布局分析完成';
};
