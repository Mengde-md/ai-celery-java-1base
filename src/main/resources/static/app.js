'use strict';

(() => {
  const $ = (id) => document.getElementById(id);
  const STORAGE_KEY = 'aicelery-task-ids';
  const ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
  const TERMINAL = new Set(['SUCCEEDED', 'FAILED', 'CANCELLED']);
  const STATUSES = {
    PENDING: ['等待执行', 'pending'], RUNNING: ['执行中', 'running'],
    SUCCEEDED: ['已完成', 'success'], FAILED: ['执行失败', 'failed'],
    CANCELLED: ['已取消', 'cancelled']
  };
  const state = {
    authorized: false, submitting: false, activeId: null, pollVersion: 0,
    pollTimer: null, elapsedTimer: null, toastTimer: null,
    tasks: new Map(), ids: readIds()
  };

  function readIds() {
    try {
      const stored = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]');
      return Array.isArray(stored) ? [...new Set(stored.filter((id) => typeof id === 'string' && ID_PATTERN.test(id)))].slice(0, 20) : [];
    } catch { return []; }
  }

  function rememberId(id) {
    if (!ID_PATTERN.test(id)) throw new Error('服务器返回的任务标识无效，请稍后重试');
    state.ids = [id, ...state.ids.filter((item) => item !== id)].slice(0, 20);
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(state.ids)); }
    catch { toast('浏览器未允许保存本机记录，请保留任务 ID'); }
  }

  async function api(path, { method = 'GET', body, business = true } = {}) {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 15000);
    try {
      const response = await fetch(path, {
        method, credentials: 'same-origin', cache: 'no-store', signal: controller.signal,
        headers: body === undefined ? { Accept: 'application/json' } : { Accept: 'application/json', 'Content-Type': 'application/json' },
        body: body === undefined ? undefined : JSON.stringify(body)
      });
      let result;
      try { result = await response.json(); }
      catch { throw new Error('服务暂时不可用，请稍后重试'); }
      if (response.status === 401 && result?.code === 'SITE_ACCESS_REQUIRED') {
        lockSite();
        const error = new Error('请输入访问码');
        error.accessRequired = true;
        throw error;
      }
      if (!response.ok) throw new Error(result?.message || result?.msg || '请求未能完成，请稍后重试');
      if (!business) return result;
      if (result?.code !== 0) {
        const error = new Error(result?.msg || '请求未能完成，请稍后重试');
        error.businessCode = result?.code;
        throw error;
      }
      return result.data;
    } catch (error) {
      if (error.name === 'AbortError') throw new Error('连接超时，请稍后重试');
      if (error instanceof TypeError) throw new Error('暂时无法连接，请检查网络后重试');
      throw error;
    } finally { clearTimeout(timer); }
  }

  function stopPolling() {
    state.pollVersion += 1;
    clearTimeout(state.pollTimer);
    state.pollTimer = null;
  }

  function lockSite() {
    state.authorized = false;
    stopPolling();
    clearInterval(state.elapsedTimer);
    $('application').hidden = true;
    $('access-panel').hidden = false;
    $('access-code').value = '';
    $('access-error').textContent = '';
    $('toast').hidden = true;
    $('access-code').focus({ preventScroll: true });
  }

  async function unlockSite() {
    state.authorized = true;
    $('access-code').value = '';
    $('access-error').textContent = '';
    $('access-panel').hidden = true;
    $('application').hidden = false;
    clearInterval(state.elapsedTimer);
    state.elapsedTimer = setInterval(updateDuration, 1000);
    renderRecent();
    const selected = state.activeId || state.ids[0];
    if (selected) selectTask(selected);
    await refreshRecent();
  }

  function setStatus(element, status) {
    const [label, className] = STATUSES[status] || ['未获取', 'neutral'];
    element.textContent = label;
    element.className = `status ${className}`;
  }

  function formatTime(value) {
    if (!value) return '—';
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return '—';
    return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false }).format(date);
  }

  function formatNumber(value) {
    return Number.isFinite(Number(value)) ? new Intl.NumberFormat('zh-CN').format(Number(value)) : '—';
  }

  function updateDuration() {
    const task = state.tasks.get(state.activeId);
    if (!task?.createdAt) { $('task-duration').textContent = '—'; return; }
    const start = Date.parse(task.createdAt);
    const end = task.finishedAt ? Date.parse(task.finishedAt) : Date.now();
    if (!Number.isFinite(start) || !Number.isFinite(end)) { $('task-duration').textContent = '—'; return; }
    const seconds = Math.max(0, Math.floor((end - start) / 1000));
    $('task-duration').textContent = seconds < 60 ? `${seconds} 秒` : `${Math.floor(seconds / 60)} 分 ${seconds % 60} 秒`;
  }

  function renderRecent() {
    const container = $('recent-tasks');
    container.replaceChildren();
    $('recent-count').textContent = String(state.ids.length);
    if (!state.ids.length) {
      const empty = document.createElement('p');
      empty.className = 'recent-empty';
      empty.textContent = '还没有任务记录';
      container.append(empty);
      return;
    }
    for (const id of state.ids) {
      const task = state.tasks.get(id);
      const row = document.createElement('button');
      row.type = 'button';
      row.className = `recent-row${state.activeId === id ? ' active' : ''}`;
      row.title = id;
      row.setAttribute('aria-pressed', state.activeId === id ? 'true' : 'false');
      const top = document.createElement('span');
      top.className = 'recent-row-top';
      const title = document.createElement('span');
      title.className = 'recent-title';
      title.textContent = `任务 ${id.slice(0, 8)}`;
      const status = document.createElement('span');
      setStatus(status, task?.status);
      top.append(title, status);
      const meta = document.createElement('span');
      meta.className = 'recent-row-meta';
      const model = document.createElement('span');
      model.textContent = task?.model || '等待获取';
      const time = document.createElement('span');
      time.textContent = formatTime(task?.createdAt);
      meta.append(model, time);
      row.append(top, meta);
      row.addEventListener('click', () => selectTask(id));
      container.append(row);
    }
  }

  function renderTask(task) {
    if (!task || task.taskId !== state.activeId) return;
    setStatus($('task-status'), task.status);
    $('task-id').textContent = task.taskId;
    $('task-model').textContent = task.model || '—';
    $('task-total').textContent = task.usage ? formatNumber(task.usage.totalTokens) : '—';
    $('input-tokens').textContent = formatNumber(task.usage?.inputTokens ?? 0);
    $('output-tokens').textContent = formatNumber(task.usage?.outputTokens ?? 0);
    $('task-attempts').textContent = Number.isInteger(task.attemptCount) ? `执行次数 ${task.attemptCount}${task.maxAttempts ? ` / ${task.maxAttempts}` : ''}` : '';
    $('task-time').textContent = `创建于 ${formatTime(task.createdAt)}`;
    $('task-details').hidden = false;
    $('task-error').hidden = !task.error;
    $('task-error-message').textContent = task.error?.message || '任务执行未完成，请稍后重试';
    const completed = task.status === 'SUCCEEDED';
    $('result-placeholder').hidden = completed;
    $('result-text').hidden = !completed;
    $('result-text').textContent = completed ? (task.result?.text || '本次任务没有返回文本内容。') : '';
    $('copy-result').disabled = !completed || !task.result?.text;
    $('cancel-task').hidden = TERMINAL.has(task.status);
    $('cancel-note').hidden = TERMINAL.has(task.status);
    const placeholders = {
      PENDING: ['任务已进入队列', '后台会自动开始执行，进度将在这里更新。'],
      RUNNING: ['正在生成内容', '模型正在处理你的任务，请稍候。'],
      FAILED: ['本次任务未完成', '请查看执行提示，调整需求后重新提交。'],
      CANCELLED: ['任务已取消', '你可以继续创建新的任务。']
    };
    const [heading, description] = placeholders[task.status] || ['正在获取任务', '请稍候。'];
    $('placeholder-heading').textContent = heading;
    $('placeholder-description').textContent = description;
    updateDuration();
  }

  async function fetchTask(id) {
    const task = await api(`/api/v1/tasks/${encodeURIComponent(id)}`);
    if (!task || task.taskId !== id) throw new Error('任务信息暂时不可用');
    state.tasks.set(id, task);
    return task;
  }

  async function pollTask(id, version) {
    if (!state.authorized || state.activeId !== id || state.pollVersion !== version) return;
    try {
      const task = await fetchTask(id);
      if (!state.authorized || state.activeId !== id || state.pollVersion !== version) return;
      $('poll-warning').hidden = true;
      renderTask(task);
      renderRecent();
      if (TERMINAL.has(task.status)) return;
    } catch (error) {
      if (error.accessRequired || !state.authorized || state.pollVersion !== version) return;
      if (error.businessCode === 20006) {
        $('poll-warning').textContent = '该任务已不存在，可提交新任务。';
        $('poll-warning').hidden = false;
        $('cancel-task').hidden = true;
        $('cancel-note').hidden = true;
        return;
      }
      $('poll-warning').textContent = '连接暂时中断，正在重新获取进度……';
      $('poll-warning').hidden = false;
    }
    if (state.authorized && state.activeId === id && state.pollVersion === version) {
      state.pollTimer = setTimeout(() => pollTask(id, version), 2000);
    }
  }

  function selectTask(id) {
    if (!state.authorized || !ID_PATTERN.test(id)) return;
    stopPolling();
    state.activeId = id;
    $('poll-warning').hidden = true;
    const cached = state.tasks.get(id);
    if (cached) renderTask(cached);
    else {
      $('task-id').textContent = id;
      $('task-status').textContent = '正在获取';
      $('task-status').className = 'status neutral';
      $('task-model').textContent = '—';
      $('task-total').textContent = '—';
      $('task-duration').textContent = '—';
      $('task-time').textContent = '';
      $('task-details').hidden = true;
      $('result-text').hidden = true;
      $('result-text').textContent = '';
      $('result-placeholder').hidden = false;
      $('placeholder-heading').textContent = '正在获取任务';
      $('placeholder-description').textContent = '请稍候。';
      $('task-error').hidden = true;
      $('copy-result').disabled = true;
      $('cancel-task').hidden = true;
      $('cancel-note').hidden = true;
    }
    renderRecent();
    pollTask(id, state.pollVersion);
  }

  async function refreshRecent() {
    if (!state.authorized) return;
    $('refresh-recent').disabled = true;
    const ids = [...state.ids];
    try {
      for (let offset = 0; offset < ids.length && state.authorized; offset += 3) {
        await Promise.allSettled(ids.slice(offset, offset + 3).map((id) => fetchTask(id)));
      }
      if (state.authorized) {
        renderRecent();
        renderTask(state.tasks.get(state.activeId));
      }
    } finally { $('refresh-recent').disabled = false; }
  }

  async function submitTask(event) {
    event.preventDefault();
    if (state.submitting || !state.authorized) return;
    const prompt = $('prompt').value.trim();
    if (!prompt) { $('submit-error').textContent = '请先填写任务内容'; $('prompt').focus(); return; }
    const maxOutputTokens = Number($('max-tokens').value);
    if (!Number.isInteger(maxOutputTokens) || maxOutputTokens < 1 || maxOutputTokens > 8000) {
      $('submit-error').textContent = '最大输出 Token 请填写 1～8000 的整数';
      return;
    }
    state.submitting = true;
    $('submit-task').disabled = true;
    $('submit-task').firstElementChild.textContent = '正在创建…';
    $('submit-error').textContent = '';
    try {
      const created = await api('/api/v1/tasks', {
        method: 'POST',
        body: {
          type: 'ai.generate', provider: 'deepseek', model: $('model').value,
          input: { systemPrompt: $('system-prompt').value.trim() || null, prompt },
          options: { maxOutputTokens }
        }
      });
      rememberId(created.taskId);
      state.tasks.set(created.taskId, created);
      selectTask(created.taskId);
      toast('任务已创建');
    } catch (error) {
      if (!error.accessRequired) $('submit-error').textContent = error.message;
    } finally {
      state.submitting = false;
      $('submit-task').disabled = false;
      $('submit-task').firstElementChild.textContent = '创建任务';
    }
  }

  async function cancelTask() {
    const id = state.activeId;
    const task = state.tasks.get(id);
    if (!state.authorized || !id || !task || TERMINAL.has(task.status)) return;
    $('cancel-task').disabled = true;
    try {
      await api(`/api/v1/tasks/${encodeURIComponent(id)}/cancel`, { method: 'POST' });
      const updated = await fetchTask(id);
      renderRecent();
      if (state.activeId === id) { renderTask(updated); if (TERMINAL.has(updated.status)) stopPolling(); }
      toast(updated.status === 'CANCELLED' ? '任务已取消' : '任务状态已更新');
    } catch (error) { if (!error.accessRequired) toast(error.message); }
    finally { $('cancel-task').disabled = false; }
  }

  function toast(message) {
    if (!state.authorized) return;
    $('toast').textContent = message;
    $('toast').hidden = false;
    clearTimeout(state.toastTimer);
    state.toastTimer = setTimeout(() => { $('toast').hidden = true; }, 3500);
  }

  $('access-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const button = $('access-submit');
    if (button.disabled) return;
    const code = $('access-code').value;
    button.disabled = true;
    button.textContent = '验证中…';
    $('access-error').textContent = '';
    try {
      const result = await api('/api/site-access', { method: 'POST', body: { code }, business: false });
      if (result.granted !== true) throw new Error('访问码不正确');
      await unlockSite();
    } catch (error) {
      $('access-error').textContent = error.message;
      $('access-code').value = '';
      $('access-code').focus({ preventScroll: true });
    } finally { button.disabled = false; button.textContent = '进入'; }
  });
  $('task-form').addEventListener('submit', submitTask);
  $('cancel-task').addEventListener('click', cancelTask);
  $('refresh-recent').addEventListener('click', refreshRecent);
  $('prompt').addEventListener('input', () => { $('prompt-count').textContent = `${$('prompt').value.length} / 60000`; });
  $('copy-result').addEventListener('click', async () => {
    const text = state.tasks.get(state.activeId)?.result?.text;
    if (!text) return;
    try { await navigator.clipboard.writeText(text); toast('结果已复制'); }
    catch { toast('未能复制，请手动选择结果文本'); }
  });
  window.addEventListener('pagehide', () => { stopPolling(); clearInterval(state.elapsedTimer); });
  window.addEventListener('pageshow', (event) => { if (event.persisted) checkAccess(); });

  async function checkAccess() {
    try {
      const status = await api('/api/site-access', { business: false });
      if (status.granted === true) await unlockSite();
      else lockSite();
    } catch (error) {
      lockSite();
      $('access-error').textContent = error.message;
    }
  }

  checkAccess();
})();
