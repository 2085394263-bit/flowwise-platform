const state = { user: null, projects: [], tasks: [], members: [], approvals: [], projectApprovals: [], managerRegistrations: [], activities: [], loadedForUser: null, filter: 'ALL', page: 'overview', projectId: null, editingTaskId: null, loading: false };
const $ = (selector) => document.querySelector(selector);
const roleNames = { ROLE_ADMIN: 'ADMIN', ROLE_MANAGER: 'PROJECT MANAGER', ROLE_MEMBER: 'MEMBER' };
const statusNames = { TODO: '待开始', IN_PROGRESS: '进行中', REVIEW: '待验收', DONE: '已完成' };
const priorityNames = { HIGH: '高', NORMAL: '普通', LOW: '低' };

function escapeHtml(value) {
  return String(value == null ? '' : value).replace(/[&<>"']/g, (char) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
}

async function api(path, options = {}) {
  const headers = Object.assign({}, options.headers || {});
  if (options.body && !(options.body instanceof URLSearchParams)) headers['Content-Type'] = 'application/json';
  if (options.method && !['GET', 'HEAD', 'OPTIONS'].includes(options.method.toUpperCase())) {
    await fetch('/api/auth/csrf', { credentials: 'same-origin' });
    const csrf = document.cookie.split('; ').find((part) => part.startsWith('XSRF-TOKEN='));
    if (csrf) headers['X-XSRF-TOKEN'] = decodeURIComponent(csrf.slice('XSRF-TOKEN='.length));
  }
  const response = await fetch(path, Object.assign({ credentials: 'same-origin', headers }, options));
  const contentType = response.headers.get('content-type') || '';
  const payload = contentType.includes('application/json') ? await response.json() : await response.text();
  if (response.status === 401) {
    showLogin();
    throw new Error((payload && payload.message) || '请先登录');
  }
  if (!response.ok) throw new Error((payload && payload.message) || '请求失败，请稍后重试');
  return payload;
}

function showLogin(message = '') {
  $('#app').hidden = true;
  $('#login-screen').hidden = false;
  $('#login-error').textContent = message;
}

function initials(value) { return (value || 'F').slice(0, 1).toUpperCase(); }
function dateLabel(value) {
  if (!value) return '—';
  const date = new Date(value.length === 10 ? `${value}T00:00:00` : value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' });
}
function timeLabel(value) {
  if (!value) return '';
  const date = new Date(value);
  const minutes = Math.max(0, Math.floor((Date.now() - date.getTime()) / 60000));
  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes} 分钟前`;
  if (minutes < 1440) return `${Math.floor(minutes / 60)} 小时前`;
  return `${Math.floor(minutes / 1440)} 天前`;
}
function toast(message) {
  const node = $('#toast');
  node.textContent = message;
  node.classList.add('show');
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => node.classList.remove('show'), 2600);
}
function canManage() { return state.user.roles.some((role) => role === 'ROLE_ADMIN' || role === 'ROLE_MANAGER'); }
function isAdmin() { return state.user.roles.includes('ROLE_ADMIN'); }
function isManager() { return state.user.roles.includes('ROLE_MANAGER') && !isAdmin(); }
function isEmployee() { return !isAdmin() && !state.user.roles.includes('ROLE_MANAGER'); }
function isAssignee(task) { return task.assignee && task.assignee.toLowerCase() === state.user.username.toLowerCase(); }

async function loadWorkspace(silent = false) {
  if (state.loading) return;
  state.loading = true;
  try {
    const user = await api('/api/auth/me');
    const [dashboard, approvals, projectApprovals, managerRegistrations, members] = await Promise.all([
      api('/api/dashboard'), api('/api/approvals/pending'),
      user.roles.includes('ROLE_ADMIN') ? api('/api/project-requests/pending') : Promise.resolve([]),
      user.roles.includes('ROLE_ADMIN') ? api('/api/admin/manager-registrations/pending') : Promise.resolve([]),
      user.roles.some((role) => ['ROLE_ADMIN', 'ROLE_MANAGER'].includes(role)) ? api('/api/team/members') : Promise.resolve([])
    ]);
    state.user = user;
    state.projects = dashboard.projects || [];
    state.approvals = approvals;
    state.projectApprovals = projectApprovals;
    state.managerRegistrations = managerRegistrations;
    state.members = members;
    state.activities = dashboard.activities || [];
    const previousTaskIds = new Set(state.tasks.map((task) => String(task.id)));
    const previousUser = state.loadedForUser;
    const groups = await Promise.all(state.projects.map(async (project) => {
      const tasks = await api(`/api/projects/${project.id}/tasks`);
      return tasks.map((task) => Object.assign({}, task, { projectName: project.name }));
    }));
    state.tasks = groups.flat();
    const newAssignments = previousUser === user.username && user.roles.includes('ROLE_MEMBER')
      ? state.tasks.filter((task) => task.assignee && task.assignee.toLowerCase() === user.username.toLowerCase() && !previousTaskIds.has(String(task.id)))
      : [];
    state.loadedForUser = user.username;
    render();
    if (newAssignments.length) toast(newAssignments.length === 1 ? `收到新任务：${newAssignments[0].title}` : `收到 ${newAssignments.length} 项新任务`);
  } catch (error) {
    if (error.message !== '请先登录' && !silent) toast(error.message);
  } finally {
    state.loading = false;
  }
}

function render() {
  $('#login-screen').hidden = true;
  $('#app').hidden = false;
  const displayName = state.user.username;
  $('#current-user').textContent = displayName;
  $('#welcome-name').textContent = displayName;
  $('#avatar').textContent = initials(displayName);
  $('#current-role').textContent = state.user.roles.map((role) => roleNames[role] || role).join(' · ');
  if (isAdmin()) { $('#role-help-title').textContent = '管理员权限'; $('#role-help-copy').textContent = '管理全局项目、团队账号和审批流程。'; }
  else if (isManager()) { $('#role-help-title').textContent = '项目经理权限'; $('#role-help-copy').textContent = '负责项目推进、员工任务分派和交付验收。'; }
  else { $('#role-help-title').textContent = '成员权限'; $('#role-help-copy').textContent = '查看分配给自己的任务，提交完成情况供负责人验收。'; }
  document.querySelector('[data-view="approvals"]').hidden = !canManage();
  $('#notification-view-approvals').hidden = !canManage();
  $('#today').textContent = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'short' });
  const todoTasks = state.tasks.filter((task) => task.status === 'TODO').length;
  const inProgressTasks = state.tasks.filter((task) => task.status === 'IN_PROGRESS').length;
  const done = state.tasks.filter((task) => task.status === 'DONE').length;
  const completion = state.tasks.length ? Math.round(done * 100 / state.tasks.length) : 0;
  if (isAdmin()) {
    $('#home-eyebrow').textContent = 'ADMINISTRATION';
    $('#home-title').textContent = '全局项目与团队运行情况';
    $('#home-copy').textContent = '欢迎回来，'; $('#home-copy-tail').textContent = '。这里汇总所有项目和待处理事项。';
    $('#metric-projects-label').textContent = '活跃项目'; $('#metric-projects-hint').textContent = '全局项目空间';
    $('#metric-tasks-label').textContent = '任务总数'; $('#metric-tasks-hint').textContent = '所有项目任务';
    $('#metric-approvals-label').textContent = '待处理审批'; $('#metric-approvals-hint').textContent = '项目、账号与交付';
    $('#metric-rate-label').textContent = '整体完成率'; $('#metric-rate-hint').textContent = '按已验收任务计算';
    $('#metric-projects').textContent = state.projects.length; $('#metric-tasks').textContent = state.tasks.length;
    $('#metric-approvals').textContent = state.approvals.length + state.projectApprovals.length + state.managerRegistrations.length;
    $('#metric-rate').textContent = completion; $('#metric-rate-unit').hidden = false;
    $('#admin-project-requests').textContent = state.projectApprovals.length;
    $('#admin-manager-requests').textContent = state.managerRegistrations.length;
    $('#admin-task-approvals').textContent = state.approvals.length;
  } else if (isManager()) {
    $('#home-eyebrow').textContent = 'MANAGER DESK';
    $('#home-title').textContent = '把负责的项目稳步推进';
    $('#home-copy').textContent = '欢迎回来，'; $('#home-copy-tail').textContent = '。分派任务并跟进交付验收。';
    $('#metric-projects-label').textContent = '我负责的项目'; $('#metric-projects-hint').textContent = '当前负责人名下';
    $('#metric-tasks-label').textContent = '项目任务'; $('#metric-tasks-hint').textContent = '负责项目内的任务';
    $('#metric-approvals-label').textContent = '待验收交付'; $('#metric-approvals-hint').textContent = '等待你处理';
    $('#metric-rate-label').textContent = '任务完成率'; $('#metric-rate-hint').textContent = '按已验收任务计算';
    $('#metric-projects').textContent = state.projects.length; $('#metric-tasks').textContent = state.tasks.length;
    $('#metric-approvals').textContent = state.approvals.length;
    $('#metric-rate').textContent = completion; $('#metric-rate-unit').hidden = false;
    $('#manager-project-count').textContent = state.projects.length;
    $('#manager-task-count').textContent = state.tasks.length;
    $('#manager-review-count').textContent = state.approvals.length;
  } else {
    $('#home-eyebrow').textContent = 'MY WORK';
    $('#home-title').textContent = '今天，把自己的任务向前推进';
    $('#home-copy').textContent = '你好，'; $('#home-copy-tail').textContent = '。下面是分配给你的任务，可直接开始或提交交付。';
    $('#metric-projects-label').textContent = '分配给我的任务'; $('#metric-projects-hint').textContent = '任务分派后自动同步';
    $('#metric-tasks-label').textContent = '待开始'; $('#metric-tasks-hint').textContent = '点击任务开始执行';
    $('#metric-approvals-label').textContent = '进行中'; $('#metric-approvals-hint').textContent = '完成后提交交付';
    $('#metric-rate-label').textContent = '已验收'; $('#metric-rate-hint').textContent = '负责人验收通过';
    $('#metric-projects').textContent = state.tasks.length; $('#metric-tasks').textContent = todoTasks;
    $('#metric-approvals').textContent = inProgressTasks; $('#metric-rate').textContent = done;
    $('#metric-rate-unit').hidden = true;
  }
  $('#admin-home-panel').hidden = !isAdmin();
  $('#manager-home-panel').hidden = !isManager();
  $('#member-work-panel').hidden = !isEmployee();
  document.querySelector('.content-grid').hidden = isEmployee();
  $('#home-projects-title').textContent = isAdmin() ? '全部项目' : isManager() ? '我负责的项目' : '我参与的项目';
  $('#member-task-summary').textContent = `${state.tasks.length} 项任务 · 每 5 秒自动同步一次新分派`;
  renderMemberTasks();
  $('#approval-badge').textContent = state.approvals.length + state.projectApprovals.length + state.managerRegistrations.length;
  $('#approval-badge').classList.toggle('hide', !state.approvals.length && !state.projectApprovals.length && !state.managerRegistrations.length);
  $('#project-requests-panel').hidden = !state.user.roles.includes('ROLE_ADMIN');
  $('#admin-create-account').hidden = !state.user.roles.includes('ROLE_ADMIN');
  $('#detail-edit-project').hidden = !canManage();
  $('#detail-delete-project').hidden = !canManage();
  $('#new-project').classList.toggle('hide', !canManage());
  $('#new-task').classList.toggle('hide', !canManage());
  renderProjects();
  renderTasks();
  renderApprovals();
  renderNotifications();
  renderActivity();
  renderProjectDetail();
  renderProjectOptions();
  $('#sync-label').innerHTML = '<i></i> 已同步 ' + new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  showPage(state.page, state.projectId, false);
}

function projectCardMarkup(project) {
  return `<article class="project-card" data-project="${project.id}">
    <span class="project-symbol">▧</span>
    <div class="project-info"><div class="project-title">${escapeHtml(project.name)}</div><div class="project-desc">${escapeHtml(project.description || '暂无项目简介')}</div></div>
    <div class="project-progress"><div class="progress-caption"><span>进度</span><strong>${project.progress}%</strong></div><div class="progress-track"><span style="width:${Math.min(100, project.progress)}%"></span></div></div>
    <div class="project-count">${project.taskCount} 项任务</div>
  </article>`;
}

function renderProjects() {
  const empty = '<div class="empty-state">还没有项目，创建一个项目开始协作。</div>';
  $('#project-list').innerHTML = state.projects.length ? state.projects.slice(0, 4).map(projectCardMarkup).join('') : empty;
  $('#all-projects-list').innerHTML = state.projects.length ? state.projects.map(projectCardMarkup).join('') : empty;
}

function renderProjectDetail() {
  const project = state.projects.find((item) => String(item.id) === String(state.projectId));
  if (!project) return;
  const tasks = state.tasks.filter((task) => String(task.projectId) === String(project.id));
  $('#detail-project-name').textContent = project.name;
  $('#detail-project-description').textContent = project.description || '该项目暂未填写简介。';
  $('#detail-project-owner').textContent = project.owner;
  $('#detail-project-status').textContent = project.status === 'ACTIVE' ? '进行中' : '已归档';
  $('#detail-project-progress').textContent = `${project.progress}%`;
  $('#detail-project-count').textContent = tasks.length;
  $('#detail-task-summary').textContent = `${tasks.filter((task) => task.status === 'DONE').length} / ${tasks.length} 项已完成`;
  const rows = $('#detail-task-rows');
  if (!tasks.length) {
    rows.innerHTML = '<tr><td colspan="6"><div class="empty-state">这个项目还没有任务，添加第一项行动吧。</div></td></tr>';
    return;
  }
  rows.innerHTML = tasks.map((task) => `<tr>
    <td><span class="task-name">${escapeHtml(task.title)}</span></td><td>${escapeHtml(task.assignee || '未分配')}</td>
    <td><span class="priority ${escapeHtml(task.priority)}">${priorityNames[task.priority] || '普通'}</span></td>
    <td><span class="status-pill ${escapeHtml(task.status)}">${statusNames[task.status] || task.status}</span></td>
    <td>${dateLabel(task.dueDate)}</td><td>${taskActions(task)}</td></tr>`).join('');
}

function taskActions(task) {
  let delivery = '';
  if (isAssignee(task) && task.status === 'TODO') delivery = `<button class="row-action" data-start-task="${task.id}">开始</button>`;
  if (isAssignee(task) && task.status === 'IN_PROGRESS') delivery = `<button class="row-action" data-deliver-task="${task.id}">提交交付</button>`;
  if (isAssignee(task) && task.status === 'REVIEW') delivery = '<span class="muted small-copy">等待验收</span>';
  if (isAssignee(task) && task.status === 'DONE') delivery = '<span class="muted small-copy">已验收</span>';
  const management = canManage() ? `<button class="row-action" data-edit-task="${task.id}" ${task.status === 'REVIEW' ? 'disabled' : ''}>编辑</button><button class="row-action danger-action" data-delete-task="${task.id}" ${task.status === 'REVIEW' ? 'disabled' : ''}>删除</button>` : '';
  return `${delivery}${management}` || '<span class="muted small-copy">仅负责人可操作</span>';
}

function showPage(page, projectId = null, updateHash = true) {
  const previousPage = state.page;
  const previousProjectId = state.projectId;
  const validPages = ['overview', 'projects', 'project', 'tasks', 'approvals'];
  if (!validPages.includes(page)) page = 'overview';
  if (page === 'approvals' && !canManage()) page = 'overview';
  if (page === 'project') {
    const exists = state.projects.some((item) => String(item.id) === String(projectId));
    if (!exists) page = 'projects';
  }
  state.page = page;
  state.projectId = page === 'project' ? projectId : null;
  if (page === 'project') renderProjectDetail();
  const home = page === 'overview';
  $('#overview').hidden = !home;
  document.querySelector('.metrics-grid').hidden = !home;
  document.querySelector('.content-grid').hidden = !home;
  $('#projects-view').hidden = page !== 'projects';
  $('#project-view').hidden = page !== 'project';
  $('#tasks').hidden = page !== 'tasks';
  $('#approvals').hidden = page !== 'approvals';
  const titles = { overview: '工作台', projects: '项目空间', project: state.projects.find((item) => String(item.id) === String(projectId))?.name || '项目详情', tasks: '任务列表', approvals: '审批中心' };
  $('#page-title').textContent = titles[page];
  document.querySelectorAll('.nav-item').forEach((item) => item.classList.toggle('active', item.dataset.view === (page === 'project' ? 'projects' : page)));
  if (updateHash) window.location.hash = page === 'project' ? `project/${projectId}` : page;
  if (previousPage !== page || String(previousProjectId) !== String(state.projectId)) window.scrollTo({ top: 0, behavior: 'smooth' });
}

function renderTasks() {
  const rows = $('#task-rows');
  const filtered = state.filter === 'ALL' ? state.tasks : state.tasks.filter((task) => task.status === state.filter);
  if (!filtered.length) {
    rows.innerHTML = '<tr><td colspan="7"><div class="empty-state">没有符合条件的任务</div></td></tr>';
    return;
  }
  rows.innerHTML = filtered.map((task) => {
    const action = taskActions(task);
    return `<tr>
      <td><span class="task-name">${escapeHtml(task.title)}</span></td>
      <td><span class="task-project">${escapeHtml(task.projectName)}</span></td>
      <td>${escapeHtml(task.assignee || '未分配')}</td>
      <td><span class="priority ${escapeHtml(task.priority)}">${priorityNames[task.priority] || '普通'}</span></td>
      <td><span class="status-pill ${escapeHtml(task.status)}">${statusNames[task.status] || task.status}</span></td>
      <td>${dateLabel(task.dueDate)}</td><td>${action}</td></tr>`;
  }).join('');
}

function renderApprovals() {
  const target = $('#approval-list');
  target.innerHTML = state.approvals.length ? state.approvals.map((approval) => `<article class="approval-card">
    <span class="approval-icon">◷</span>
    <div class="approval-main"><strong>${escapeHtml(approval.taskTitle)}</strong><small>${escapeHtml(approval.projectName)} · ${escapeHtml(approval.requester)} · ${timeLabel(approval.createdAt)}</small>${approval.deliveryNote ? `<p class="small-copy">交付说明：${escapeHtml(approval.deliveryNote)}</p>` : ''}${approval.deliveryUrl ? `<p class="small-copy">交付链接：${escapeHtml(approval.deliveryUrl)}</p>` : ''}</div>
    <div class="approval-actions">${canManage() ? `<button class="approve" data-decision="${approval.id}" data-approved="true">通过</button><button data-decision="${approval.id}" data-approved="false">驳回</button>` : '<span class="muted small-copy">等待审批</span>'}</div>
  </article>`).join('') : '<div class="empty-state">当前没有待处理任务审批</div>';
  const projectTarget = $('#project-approval-list');
  const actionNames = { CREATE: '创建项目', UPDATE: '修改项目', DELETE: '删除项目' };
  projectTarget.innerHTML = state.projectApprovals.length ? state.projectApprovals.map((request) => `<article class="approval-card"><span class="approval-icon">▧</span><div class="approval-main"><strong>${actionNames[request.action] || '项目变更'}：${escapeHtml(request.projectName)}</strong><small>${escapeHtml(request.requester)} · ${timeLabel(request.createdAt)}${request.requestedName && request.requestedName !== request.projectName ? ` · 新名称：${escapeHtml(request.requestedName)}` : ''}${request.requestedDescription ? ` · ${escapeHtml(request.requestedDescription)}` : ''}</small></div><div class="approval-actions"><button class="approve" data-project-decision="${request.id}" data-approved="true">通过</button><button data-project-decision="${request.id}" data-approved="false">驳回</button></div></article>`).join('') : '<div class="empty-state">当前没有待处理项目申请</div>';
  const managerTarget = $('#manager-registration-list');
  managerTarget.innerHTML = state.managerRegistrations.length ? state.managerRegistrations.map((account) => `<article class="approval-card"><span class="approval-icon">♙</span><div class="approval-main"><strong>项目经理 / 负责人：${escapeHtml(account.username)}</strong><small>注册时间 ${timeLabel(account.createdAt)} · 审批通过后可登录并管理任务</small></div><div class="approval-actions"><button class="approve" data-manager-decision="${account.id}" data-approved="true">通过</button><button data-manager-decision="${account.id}" data-approved="false">驳回</button></div></article>`).join('') : '<div class="empty-state">当前没有项目经理注册申请</div>';
}

function renderActivity() {
  const target = $('#activity-list');
  if (!state.activities.length) {
    target.innerHTML = '<div class="empty-state">项目有新进展时，动态会显示在这里</div>';
    return;
  }
  target.innerHTML = state.activities.slice(0, 5).map((item) => `<div class="activity-item"><span class="activity-marker">✦</span><div class="activity-copy"><strong>${escapeHtml(item.actor)}</strong> ${escapeHtml(item.detail)}<span class="activity-time">${timeLabel(item.createdAt)}</span></div></div>`).join('');
}

function notificationMarkup(icon, title, detail, type, target = '') {
  return `<button type="button" class="notification-item" data-notification-type="${type}" data-notification-target="${escapeHtml(target)}"><span class="notification-icon">${icon}</span><span class="notification-copy"><strong>${escapeHtml(title)}</strong><small>${escapeHtml(detail)}</small></span></button>`;
}

function renderNotifications() {
  const admin = state.user.roles.includes('ROLE_ADMIN');
  const approver = canManage();
  const pending = [];
  if (approver) state.approvals.forEach((item) => pending.push(notificationMarkup('◷', `待审批任务：${item.taskTitle}`, `${item.projectName} · ${item.requester} · ${timeLabel(item.createdAt)}`, 'approval')));
  if (isEmployee()) state.tasks.filter((task) => task.status === 'TODO' || task.status === 'IN_PROGRESS').forEach((task) => {
    pending.push(notificationMarkup('☷', `我的任务：${task.title}`, `${task.projectName} · ${statusNames[task.status]}`, 'task'));
  });
  if (admin) {
    const actionNames = { CREATE: '创建项目', UPDATE: '修改项目', DELETE: '删除项目' };
    state.projectApprovals.forEach((item) => pending.push(notificationMarkup('▧', `待审批${actionNames[item.action] || '项目变更'}：${item.projectName}`, `${item.requester} · ${timeLabel(item.createdAt)}`, 'project')));
    state.managerRegistrations.forEach((item) => pending.push(notificationMarkup('♙', `项目经理注册申请：${item.username}`, `等待管理员审核 · ${timeLabel(item.createdAt)}`, 'manager')));
  }
  const count = pending.length;
  $('#notification-count').textContent = count > 99 ? '99+' : String(count);
  $('#notification-count').hidden = count === 0;
  $('#notification-dot').hidden = count === 0;
  $('#notification-toggle').setAttribute('aria-label', count ? `通知，${count} 项待处理` : '通知');

  let markup = '';
  if (pending.length) markup += `<p class="notification-section-title">待处理 · ${count}</p>${pending.join('')}`;
  if (state.activities.length) {
    const recent = state.activities.slice(0, 5).map((item) => {
      const project = state.projects.find((candidate) => item.detail && item.detail.includes(candidate.name));
      const detail = `${item.actor} ${item.detail}`;
      return notificationMarkup('✦', detail, timeLabel(item.createdAt), 'activity', project ? project.id : '');
    });
    markup += `<p class="notification-section-title">最近动态</p>${recent.join('')}`;
  }
  $('#notification-list').innerHTML = markup || '<div class="notification-empty">暂时没有新消息</div>';
}

function renderProjectOptions() {
  const select = $('#task-project');
  select.innerHTML = state.projects.map((project) => `<option value="${project.id}">${escapeHtml(project.name)}</option>`).join('');
  const assignee = $('#task-assignee');
  assignee.innerHTML = '<option value="">未分配</option>' + state.members.map((member) => `<option value="${escapeHtml(member.username)}">${escapeHtml(member.username)}</option>`).join('');
  $('#new-task').disabled = !state.projects.length;
}

function renderMemberTasks() {
  const tasks = state.tasks.slice().sort((a, b) => {
    if (!a.dueDate) return 1;
    if (!b.dueDate) return -1;
    return a.dueDate.localeCompare(b.dueDate);
  });
  if (!tasks.length) {
    $('#member-task-list').innerHTML = '<div class="empty-state">暂时没有分配给你的任务。新任务会自动出现在这里，请稍后查看。</div>';
    return;
  }
  $('#member-task-list').innerHTML = tasks.map((task) => `<article class="member-task-card"><div class="member-task-copy"><strong>${escapeHtml(task.title)}</strong><small>${escapeHtml(task.projectName)} · 截止 ${dateLabel(task.dueDate)}</small></div><span class="status-pill ${escapeHtml(task.status)}">${statusNames[task.status] || escapeHtml(task.status)}</span><div class="member-task-action">${taskActions(task)}</div></article>`).join('');
}

$('#login-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const body = new URLSearchParams({ username: form.get('username'), password: form.get('password') });
  try {
    await api('/api/auth/login', { method: 'POST', body });
    await api('/api/auth/csrf');
    const route = pageFromHash();
    state.page = route.page;
    state.projectId = route.projectId;
    await loadWorkspace();
  } catch (error) {
    $('#login-error').textContent = error.message;
  }
});

$('#show-register').addEventListener('click', () => {
  $('#login-form').hidden = true;
  $('#register-form').hidden = false;
  $('#register-result').textContent = '';
});
$('#show-login').addEventListener('click', () => {
  $('#register-form').hidden = true;
  $('#login-form').hidden = false;
  $('#login-error').textContent = '';
});
$('#register-role').addEventListener('change', (event) => {
  $('#register-note').textContent = event.target.value === 'MANAGER'
    ? '项目经理账号需管理员审核；审核通过后即可用注册账号登录。'
    : '成员注册成功后可直接登录。';
});
$('#register-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const formElement = event.currentTarget;
  const submitButton = formElement.querySelector('[type="submit"]');
  const form = new FormData(formElement);
  submitButton.disabled = true;
  $('#register-result').textContent = '';
  $('#register-result').classList.remove('success');
  try {
    const result = await api('/api/auth/register', { method: 'POST', body: JSON.stringify({ username: form.get('username'), password: form.get('password'), role: form.get('role') }) });
    $('#register-result').textContent = result.message;
    $('#register-result').classList.add('success');
    if (result.status === 'ACTIVE') $('#login-form').elements.username.value = result.username;
    if (result.status === 'PENDING') formElement.reset();
  } catch (error) {
    $('#register-result').textContent = error.message;
  } finally { submitButton.disabled = false; }
});

$('#logout-button').addEventListener('click', async () => {
  try { await api('/api/auth/logout', { method: 'POST' }); } catch (error) { /* show login even if the session expired */ }
  showLogin();
});

$('#new-project').addEventListener('click', () => $('#project-dialog').showModal());
$('#new-project-list').addEventListener('click', () => $('#project-dialog').showModal());

function openTaskDialog(projectId = null) {
  if (!state.projects.length) return toast('请先创建一个项目');
  renderProjectOptions();
  state.editingTaskId = null;
  $('#task-form').reset();
  $('#task-dialog-title').textContent = '创建任务';
  $('#task-submit').textContent = '创建任务';
  $('#task-project').disabled = false;
  if (projectId != null) $('#task-project').value = String(projectId);
  $('#task-dialog').showModal();
}

function openDeliveryDialog(taskId) {
  const task = state.tasks.find((item) => String(item.id) === String(taskId));
  if (!task) return;
  const form = $('#delivery-form');
  form.reset();
  form.elements.taskId.value = task.id;
  $('#delivery-task-title').textContent = task.title;
  form.elements.note.value = task.deliveryNote || '';
  form.elements.url.value = task.deliveryUrl || '';
  $('#delivery-dialog').showModal();
}

function editTask(taskId) {
  const task = state.tasks.find((item) => String(item.id) === String(taskId));
  if (!task) return;
  renderProjectOptions();
  state.editingTaskId = task.id;
  const form = $('#task-form');
  form.elements.title.value = task.title;
  form.elements.projectId.value = String(task.projectId);
  const assigneeSelect = form.elements.assignee;
  const hasAssignableUser = state.members.some((member) => member.username.toLowerCase() === String(task.assignee || '').toLowerCase());
  assigneeSelect.value = hasAssignableUser ? task.assignee : '';
  form.elements.priority.value = task.priority || 'NORMAL';
  form.elements.dueDate.value = task.dueDate || '';
  $('#task-project').disabled = true;
  $('#task-dialog-title').textContent = '编辑任务';
  $('#task-submit').textContent = '保存修改';
  if (task.assignee && !hasAssignableUser) toast(`原负责人“${task.assignee}”不是有效成员，请重新选择负责人`);
  $('#task-dialog').showModal();
}

$('#new-task').addEventListener('click', () => openTaskDialog());
$('#detail-new-task').addEventListener('click', () => openTaskDialog(state.projectId));
$('#detail-edit-project').addEventListener('click', () => {
  const project = state.projects.find((item) => String(item.id) === String(state.projectId));
  if (!project) return;
  const form = $('#project-edit-form');
  form.elements.name.value = project.name;
  form.elements.description.value = project.description || '';
  $('#project-edit-dialog').showModal();
});
$('#detail-delete-project').addEventListener('click', async () => {
  const project = state.projects.find((item) => String(item.id) === String(state.projectId));
  if (!project || !window.confirm(`申请删除项目“${project.name}”？提交后需管理员审批。`)) return;
  try { await api(`/api/projects/${project.id}`, { method: 'DELETE' }); toast('删除申请已提交管理员审批'); await loadWorkspace(); showPage('projects'); }
  catch (error) { toast(error.message); }
});

document.querySelectorAll('[data-close]').forEach((button) => button.addEventListener('click', () => document.getElementById(button.dataset.close).close()));

$('#project-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const formElement = event.currentTarget;
  const submitButton = formElement.querySelector('[type="submit"]');
  const form = new FormData(formElement);
  submitButton.disabled = true;
  try {
    const created = await api('/api/projects', { method: 'POST', body: JSON.stringify({ name: form.get('name'), description: form.get('description') }) });
    formElement.reset();
    $('#project-dialog').close();
    toast('项目创建申请已提交，等待管理员审批');
    await loadWorkspace();
  } catch (error) { toast(error.message); }
  finally { submitButton.disabled = false; }
});

$('#project-edit-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const formElement = event.currentTarget;
  const submit = formElement.querySelector('[type="submit"]');
  const form = new FormData(formElement);
  const projectId = state.projectId;
  submit.disabled = true;
  try {
    await api(`/api/projects/${projectId}`, { method: 'PUT', body: JSON.stringify({ name: form.get('name'), description: form.get('description') }) });
    formElement.reset(); $('#project-edit-dialog').close(); toast('项目修改申请已提交管理员审批'); await loadWorkspace();
  } catch (error) { toast(error.message); }
  finally { submit.disabled = false; }
});

$('#task-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const formElement = event.currentTarget;
  const submitButton = formElement.querySelector('[type="submit"]');
  const form = new FormData(formElement);
  const projectId = form.get('projectId');
  const editingTaskId = state.editingTaskId;
  const payload = { title: form.get('title'), assignee: form.get('assignee') || '', priority: form.get('priority'), dueDate: form.get('dueDate') || null };
  submitButton.disabled = true;
  try {
    if (editingTaskId) await api(`/api/tasks/${editingTaskId}`, { method: 'PUT', body: JSON.stringify(payload) });
    else await api(`/api/projects/${projectId}/tasks`, { method: 'POST', body: JSON.stringify(payload) });
    formElement.reset();
    $('#task-dialog').close();
    toast(editingTaskId ? '任务已更新' : '任务已创建');
    state.editingTaskId = null;
    if (!editingTaskId && state.page === 'project') state.projectId = projectId;
    await loadWorkspace();
  } catch (error) { toast(error.message); }
  finally { submitButton.disabled = false; }
});

$('#task-filter').addEventListener('change', (event) => { state.filter = event.target.value; renderTasks(); });
['#task-rows', '#detail-task-rows', '#member-task-list'].forEach((selector) => $(selector).addEventListener('click', async (event) => {
  const startButton = event.target.closest('[data-start-task]');
  if (startButton) {
    try { await api(`/api/tasks/${startButton.dataset.startTask}/start`, { method: 'POST' }); toast('任务已开始'); await loadWorkspace(); }
    catch (error) { toast(error.message); }
    return;
  }
  const deliveryButton = event.target.closest('[data-deliver-task]');
  if (deliveryButton) return openDeliveryDialog(deliveryButton.dataset.deliverTask);
  const editButton = event.target.closest('[data-edit-task]');
  if (editButton) return editTask(editButton.dataset.editTask);
  const deleteButton = event.target.closest('[data-delete-task]');
  if (deleteButton) {
    const task = state.tasks.find((item) => String(item.id) === String(deleteButton.dataset.deleteTask));
    if (!task || !window.confirm(`确定删除任务“${task.title}”？此操作无法撤销。`)) return;
    try { await api(`/api/tasks/${task.id}`, { method: 'DELETE' }); toast('任务已删除'); await loadWorkspace(); }
    catch (error) { toast(error.message); }
  }
}));

$('#delivery-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const form = event.currentTarget;
  const data = new FormData(form);
  const submit = form.querySelector('[type="submit"]');
  submit.disabled = true;
  try {
    await api(`/api/tasks/${data.get('taskId')}/delivery`, { method: 'POST', body: JSON.stringify({ note: data.get('note'), url: data.get('url') }) });
    $('#delivery-dialog').close();
    toast('交付已提交，等待项目负责人验收');
    await loadWorkspace();
  } catch (error) { toast(error.message); }
  finally { submit.disabled = false; }
});

$('#approval-list').addEventListener('click', async (event) => {
  const button = event.target.closest('[data-decision]');
  if (!button) return;
  const approved = button.dataset.approved === 'true';
  const comment = window.prompt(approved ? '审批备注（可留空）' : '驳回原因（可留空）', '') || '';
  try {
    await api(`/api/approvals/${button.dataset.decision}/decision`, { method: 'POST', body: JSON.stringify({ approved, comment }) });
    toast(approved ? '审批已通过，任务已完成' : '已驳回，任务退回处理中');
    await loadWorkspace();
  } catch (error) { toast(error.message); }
});

$('#project-approval-list').addEventListener('click', async (event) => {
  const button = event.target.closest('[data-project-decision]');
  if (!button) return;
  const approved = button.dataset.approved === 'true';
  const comment = window.prompt(approved ? '审批备注（可留空）' : '驳回原因（可留空）', '') || '';
  try {
    await api(`/api/project-requests/${button.dataset.projectDecision}/decision`, { method: 'POST', body: JSON.stringify({ approved, comment }) });
    toast(approved ? '项目申请已通过' : '项目申请已驳回');
    await loadWorkspace();
  } catch (error) { toast(error.message); }
});

$('#manager-registration-list').addEventListener('click', async (event) => {
  const button = event.target.closest('[data-manager-decision]');
  if (!button) return;
  const approved = button.dataset.approved === 'true';
  const comment = window.prompt(approved ? '审核备注（可留空）' : '驳回原因（可留空）', '') || '';
  try {
    await api(`/api/admin/manager-registrations/${button.dataset.managerDecision}/decision`, { method: 'POST', body: JSON.stringify({ approved, comment }) });
    toast(approved ? '项目经理账号已启用' : '注册申请已驳回');
    await loadWorkspace();
  } catch (error) { toast(error.message); }
});

$('#admin-create-account').addEventListener('click', () => $('#admin-account-dialog').showModal());
$('#admin-account-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const formElement = event.currentTarget;
  const submitButton = formElement.querySelector('[type="submit"]');
  const form = new FormData(formElement);
  submitButton.disabled = true;
  try {
    await api('/api/admin/accounts', { method: 'POST', body: JSON.stringify({ username: form.get('username'), password: form.get('password'), role: form.get('role') }) });
    formElement.reset(); $('#admin-account-dialog').close(); toast('账号已创建，可使用新账号登录');
  } catch (error) { toast(error.message); }
  finally { submitButton.disabled = false; }
});

$('#view-projects').addEventListener('click', () => showPage('projects'));
$('#member-view-all-tasks').addEventListener('click', () => showPage('tasks'));
document.querySelectorAll('[data-go-approvals]').forEach((button) => button.addEventListener('click', () => showPage('approvals')));
document.addEventListener('click', (event) => {
  if (!event.target.closest('.notification-wrap')) {
    $('#notification-panel').hidden = true;
    $('#notification-toggle').setAttribute('aria-expanded', 'false');
  }
  const card = event.target.closest('.project-card[data-project]');
  if (card) showPage('project', card.dataset.project);
});

$('#notification-toggle').addEventListener('click', (event) => {
  event.stopPropagation();
  const panel = $('#notification-panel');
  panel.hidden = !panel.hidden;
  $('#notification-toggle').setAttribute('aria-expanded', String(!panel.hidden));
  if (!panel.hidden) renderNotifications();
});

$('#notification-list').addEventListener('click', (event) => {
  const item = event.target.closest('[data-notification-type]');
  if (!item) return;
  $('#notification-panel').hidden = true;
  $('#notification-toggle').setAttribute('aria-expanded', 'false');
  if (item.dataset.notificationType === 'activity' && item.dataset.notificationTarget) {
    showPage('project', item.dataset.notificationTarget);
  } else if (item.dataset.notificationType === 'activity') {
    showPage('overview');
  } else if (item.dataset.notificationType === 'task') {
    showPage('tasks');
  } else {
    showPage('approvals');
  }
});

$('#notification-view-approvals').addEventListener('click', () => {
  $('#notification-panel').hidden = true;
  $('#notification-toggle').setAttribute('aria-expanded', 'false');
  showPage('approvals');
});

document.addEventListener('keydown', (event) => {
  if (event.key === 'Escape' && !$('#notification-panel').hidden) {
    $('#notification-panel').hidden = true;
    $('#notification-toggle').setAttribute('aria-expanded', 'false');
  }
  if (event.key === 'Escape' && !$('#agent-assistant-panel').hidden) setAgentAssistant(false);
});
$('#back-to-projects').addEventListener('click', () => showPage('projects'));
document.querySelectorAll('.nav-item').forEach((item) => item.addEventListener('click', (event) => {
  event.preventDefault();
  if (item.dataset.view === 'agent') {
    setAgentAssistant(true);
    return;
  }
  showPage(item.dataset.view);
}));

function setAgentAssistant(open) {
  const panel = $('#agent-assistant-panel');
  panel.hidden = !open;
  $('#agent-launcher').setAttribute('aria-expanded', String(open));
  if (open) $('#agent-message').focus();
}

$('#agent-launcher').addEventListener('click', () => {
  setAgentAssistant($('#agent-assistant-panel').hidden);
});
$('#agent-close').addEventListener('click', () => setAgentAssistant(false));

$('#agent-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  const input = $('#agent-message');
  const message = input.value.trim();
  if (!message) return;
  const transcript = $('#agent-transcript');
  transcript.querySelector('.agent-empty')?.remove();
  const userLine = document.createElement('p');
  userLine.className = 'agent-message user';
  userLine.textContent = `你：${message}`;
  transcript.append(userLine);
  input.value = '';
  const sendButton = event.currentTarget.querySelector('[type="submit"]');
  sendButton.disabled = true;
  try {
    const result = await api('/api/agent/chat', { method: 'POST', body: JSON.stringify({ message }) });
    const answer = document.createElement('p');
    answer.className = 'agent-message assistant';
    answer.textContent = `FlowWise 助手：${result.answer}`;
    transcript.append(answer);
  } catch (error) {
    const answer = document.createElement('p');
    answer.className = 'agent-message assistant error';
    answer.textContent = error.message;
    transcript.append(answer);
  } finally {
    sendButton.disabled = false;
    transcript.scrollTop = transcript.scrollHeight;
    input.focus();
  }
});

function pageFromHash() {
  const route = window.location.hash.replace(/^#/, '');
  if (route.startsWith('project/')) return { page: 'project', projectId: route.split('/')[1] };
  if (route === 'agent') setAgentAssistant(true);
  return { page: ['projects', 'tasks', 'approvals'].includes(route) ? route : 'overview', projectId: null };
}
window.addEventListener('hashchange', () => {
  const route = pageFromHash();
  showPage(route.page, route.projectId, false);
});

async function boot() {
  $('#today').textContent = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'short' });
  try {
    await api('/api/auth/csrf');
    state.user = await api('/api/auth/me');
    const route = pageFromHash();
    state.page = route.page;
    state.projectId = route.projectId;
    await loadWorkspace();
  } catch (error) { showLogin(); }
}
boot();
setInterval(() => {
  if (state.user && !document.hidden) loadWorkspace(true);
}, 5000);
