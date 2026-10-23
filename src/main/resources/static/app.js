'use strict';
const $ = (id) => document.getElementById(id);
const state = { dashboard: null, history: null, page: 0, search: '', historyRequest: 0, loading: false, busy: false, reviewed: false, uncertain: false };
const esc = (value) => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const icon = (name) => `<svg aria-hidden="true"><use href="#i-${name}"/></svg>`;
function cents(value) {
  const [whole, fraction = ''] = String(value).split('.');
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0').slice(0, 2));
}
function moneyFromCents(value) {
  const negative = value < 0n;
  const n = negative ? -value : value;
  return `${negative ? '-' : ''}$${(n / 100n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${(n % 100n).toString().padStart(2, '0')}`;
}
const money = value => moneyFromCents(cents(value));
function accountTitle(number) {
  return ({'ACC-1001':'Operating account','ACC-1002':'Reserve account','ACC-1003':'Expenses account','ACC-1004':'Payroll account'})[number] || 'Connected account';
}
function toast(message, error = false) {
  const element = $('toast'); element.textContent = message; element.classList.toggle('error', error); element.classList.remove('hidden');
  clearTimeout(toast.timer); toast.timer = setTimeout(() => element.classList.add('hidden'), error ? 12000 : 6000);
}
async function api(path, options = {}) {
  if (window.BankSyncDemo) return window.BankSyncDemo.request(path, options);
  const response = await fetch(path, { credentials: 'same-origin', ...options, headers: {'Accept':'application/json', ...(options.headers || {})} });
  if (!response.ok) {
    let body = {}; try { body = await response.json(); } catch (_) {}
    const error = new Error(response.status === 401 ? 'Your session needs authentication. Reload and sign in with your workspace credentials.' : body.message || 'The request could not be completed. Please check your connection.');
    error.status = response.status; throw error;
  }
  return response.status === 204 ? null : response.json();
}
function connection(online) {
  $('connection-status').innerHTML = `<i></i>${online ? (window.BankSyncDemo ? 'Browser demo' : 'Workspace connected') : 'Connection interrupted'}`;
  $('connection-status').classList.toggle('offline', !online);
}
function renderDashboard(data) {
  state.dashboard = data;
  $('total-balance').textContent = money(data.totalBalance);
  $('transfer-volume').textContent = money(data.totalTransferred);
  $('transfer-count').textContent = data.transactionCount.toLocaleString();
  for (const id of ['account-count','balance-account-count','nav-account-count']) $(id).textContent = data.accountCount;
  $('workspace-label').textContent = data.environment;
  $('footer-environment').textContent = data.environment;
  $('account-limit').classList.toggle('hidden', data.accountCount <= data.accounts.length);
  $('account-grid').innerHTML = data.accounts.length ? data.accounts.map(a => `<article class="account-card"><span class="account-icon">${icon('wallet')}</span><div class="account-details"><strong>${esc(accountTitle(a.accountNumber))}</strong><small>${esc(a.accountNumber)} <span aria-hidden="true">·</span> USD</small></div><div class="account-card-right"><strong>${esc(money(a.balance))}</strong><small>Available balance</small></div></article>`).join('') : '<div class="empty-state"><strong>No accounts connected yet</strong>Start the demo profile for sample accounts, or have your administrator provision accounts.</div>';
  drawChart(data.recentTransactions, data.refreshedAt);
}
function drawChart(transactions, now) {
  const end = new Date(now); end.setUTCHours(0,0,0,0);
  const days = Array.from({length:7}, (_, i) => {
    const date = new Date(end); date.setUTCDate(date.getUTCDate() - 6 + i);
    return {key:date.toISOString().slice(0,10), label:date.toLocaleDateString('en-US',{weekday:'short', timeZone:'UTC'}), amount:0n};
  });
  for (const t of transactions) {
    const day = days.find(d => d.key === t.timestamp.slice(0,10));
    if (day && t.status === 'SUCCESS') day.amount += cents(t.amount);
  }
  const sum = days.reduce((n,d) => n+d.amount, 0n);
  $('chart-total').textContent = moneyFromCents(sum);
  const max = days.reduce((n,d) => d.amount > n ? d.amount : n, 10000n);
  const xs = days.map((_,i) => 52 + i*70);
  const ys = days.map(d => 115 - Number(d.amount*10000n/max)/10000*92);
  const points = xs.map((x,i) => `${x},${ys[i]}`).join(' ');
  const path = `M${xs[0]},${ys[0]} `+xs.slice(1).map((x,i) => `L${x},${ys[i+1]}`).join(' ');
  const grid = [0,1,2,3].map(i => {
    const y=115-i*31;
    const amount=Number(max)/100*i/3;
    const label=amount >= 1000 ? `$${(amount/1000).toLocaleString('en-US',{maximumFractionDigits:1})}k` : `$${amount.toLocaleString('en-US',{maximumFractionDigits:0})}`;
    return `<line x1="43" x2="483" y1="${y}" y2="${y}" stroke="#edf0e7" stroke-width="1" stroke-dasharray="3 4"/><text x="0" y="${y+3}" fill="#a3ac97" font-size="8">${esc(label)}</text>`;
  }).join('');
  $('activity-chart').innerHTML = `<svg viewBox="0 0 490 150" preserveAspectRatio="none" aria-hidden="true"><defs><linearGradient id="area-gradient" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stop-color="#b5cd91" stop-opacity=".35"/><stop offset="100%" stop-color="#dae7c8" stop-opacity=".03"/></linearGradient></defs>${grid}<path d="${path} L472,115 L52,115 Z" fill="url(#area-gradient)"/><polyline points="${points}" fill="none" stroke="#729255" stroke-width="2" stroke-linejoin="round"/>${days.map((d,i)=>`<circle cx="${xs[i]}" cy="${ys[i]}" r="3" fill="#fff" stroke="#729255" stroke-width="1.5"><title>${esc(d.label)}: ${esc(moneyFromCents(d.amount))}</title></circle><text x="${xs[i]}" y="140" text-anchor="middle" fill="#9aa68e" font-size="8">${d.label}</text>`).join('')}</svg>`;
  $('activity-chart').setAttribute('aria-label', 'Transferred amounts over the last seven UTC days: '+days.map(d=>d.label+' '+moneyFromCents(d.amount)).join(', '));
}
function renderHistory(history) {
  state.history = history;
  $('history-count').textContent = history.totalElements;
  $('transaction-rows').innerHTML = history.items.length ? history.items.map(t => {
    const date = new Date(t.timestamp);
    return `<tr><td><div class="transfer-label"><span class="row-icon">${icon('arrows')}</span><span>Account transfer<small>BS-${String(t.id).padStart(6,'0')}</small></span></div></td><td class="account-mono">${esc(t.sourceAccountNumber)}</td><td class="account-mono">${esc(t.destinationAccountNumber)}</td><td class="date-cell">${date.toLocaleDateString('en-US',{month:'short',day:'2-digit',year:'numeric'})}<small>${date.toLocaleTimeString('en-US',{hour:'2-digit',minute:'2-digit'})}</small></td><td class="money-cell">${esc(money(t.amount))}</td><td><span class="status-pill ${t.status === 'SUCCESS' ? '' : 'failed'}">${t.status === 'SUCCESS' ? 'Completed' : 'Failed'}</span></td></tr>`;
  }).join('') : `<tr><td colspan="6"><div class="empty-state"><strong>${state.search ? 'No matching transfers' : 'Your next move starts here.'}</strong>${state.search ? 'Try a different account number.' : 'Make your first transfer to see it in your activity.'}</div></td></tr>`;
  $('page-information').textContent = history.totalElements ? `Showing ${history.page*10+1}–${history.page*10+history.items.length} of ${history.totalElements} transfers` : 'No transfers to display';
  $('page-number').textContent = `Page ${history.page+1} of ${Math.max(1,history.totalPages)}`;
  $('previous-page').disabled = history.page === 0;
  $('next-page').disabled = history.page + 1 >= history.totalPages;
  $('export-button').disabled = !history.items.length;
}
async function loadHistory() {
  const request = ++state.historyRequest;
  $('previous-page').disabled = $('next-page').disabled = true;
  $('export-button').disabled = true;
  try {
    const history = await api(`/api/v1/transactions?page=${state.page}&size=10&search=${encodeURIComponent(state.search)}`);
    if (request === state.historyRequest) renderHistory(history);
  } catch(error) {
    if (request === state.historyRequest) {
      state.history = null;
      $('transaction-rows').innerHTML = `<tr><td colspan="6"><div class="empty-state"><strong>Activity could not be loaded</strong>${esc(error.message)} Use the refresh button to try again.</div></td></tr>`;
      $('page-information').textContent = 'Activity unavailable';
      throw error;
    }
  }
}
async function refresh(notify=false) {
  if (state.loading) return;
  state.loading = true; $('refresh-button').classList.add('spinning');
  try {
    const data = await api('/api/v1/dashboard'); renderDashboard(data);
    await loadHistory(); connection(true);
    if (!state.uncertain) $('load-error').classList.add('hidden');
    if (notify) toast('Balances and activity are up to date.');
  } catch(error) {
    connection(false); $('load-error').textContent = error.message+' Previously loaded figures may be out of date.'; $('load-error').classList.remove('hidden');
  } finally { state.loading=false; $('refresh-button').classList.remove('spinning'); }
}
function setView() {
  const view = ['overview','accounts','transfers','activity'].includes(location.hash.slice(1)) ? location.hash.slice(1) : 'overview';
  const titles = {overview:['Your money, in sync','A clear picture of your finances. All in one place.'],accounts:['A home for every balance','Keep an eye on all your connected accounts.'],transfers:['Make your next move','Move money between accounts, with clarity at every step.'],activity:['Every move, accounted for','Explore your transaction history, one transfer at a time.']};
  const name=view[0].toUpperCase()+view.slice(1);
  document.title = `${name} · BankSync`;
  $('breadcrumb-view').textContent=name;
  $('page-title').innerHTML=esc(titles[view][0])+'<span>.</span>';
  $('page-subtitle').textContent=titles[view][1];
  $('summary-section').classList.toggle('hidden',view==='activity');
  $('middle-section').classList.toggle('hidden',view!=='overview');
  $('accounts-section').classList.toggle('hidden',!['overview','accounts','transfers'].includes(view));
  $('accounts-section').classList.toggle('account-view',view==='accounts');
  $('transactions-section').classList.toggle('hidden',view==='accounts');
  $('view-accounts-button').classList.toggle('hidden',view==='accounts');
  $('transactions-title').textContent=view==='overview'?'Recent transactions':'Transaction history';
  document.querySelectorAll('[data-view]').forEach(a=>{a.classList.toggle('active',a.dataset.view===view); if(a.dataset.view===view)a.setAttribute('aria-current','page');else a.removeAttribute('aria-current');});
  $('sidebar').classList.remove('open');$('menu-button').setAttribute('aria-expanded','false');
}
function resetReview() {
  state.reviewed=false; $('transfer-review').classList.add('hidden'); $('submit-transfer').innerHTML='Review transfer <span>→</span>';
}
function updateAccountSelection() {
  const from=$('from-account').value;
  const account=state.dashboard?.accounts.find(a=>a.accountNumber===from);
  $('available-balance').textContent=account?`${money(account.balance)} available`:'Select an account';
  for (const option of $('to-account').options) option.disabled=option.value===from;
  if($('to-account').value===from) $('to-account').value=state.dashboard.accounts.find(a=>a.accountNumber!==from)?.accountNumber||'';
  resetReview();
}
async function openTransfer() {
  if (!state.dashboard || state.dashboard.accounts.length < 2) {toast('At least two connected accounts are needed to make a transfer.',true);return;}
  $('transfer-form').reset(); $('transfer-error').classList.add('hidden'); resetReview();
  for (const id of ['from-account','to-account']) {
    $(id).replaceChildren(...state.dashboard.accounts.map(a=>new Option(`${accountTitle(a.accountNumber)} · ${a.accountNumber}`,a.accountNumber)));
  }
  $('to-account').selectedIndex=1;updateAccountSelection();
  $('submit-transfer').disabled=false;
  $('transfer-dialog').showModal();
}
function setBusy(busy) {
  state.busy=busy;
  for (const element of $('transfer-form').querySelectorAll('button,input,select')) element.disabled=busy;
}
async function submitTransfer(event) {
  event.preventDefault(); if(state.busy)return;
  const from=$('from-account').value,to=$('to-account').value,amount=$('transfer-amount').value.trim();
  const showError = text => {$('transfer-error').textContent=text;$('transfer-error').classList.remove('hidden');};
  $('transfer-error').classList.add('hidden');
  if(!/^\d{1,17}(\.\d{1,2})?$/.test(amount) || cents(amount)<=0n) {showError('Enter a positive amount with at most two decimal places.');return;}
  if(from===to){showError('Choose two different accounts.');return;}
  const source=state.dashboard.accounts.find(a=>a.accountNumber===from);
  if(!source || cents(amount)>cents(source.balance)){showError('This amount exceeds the available balance. Refresh balances if they have changed.');return;}
  if(!state.reviewed){
    state.reviewed=true;$('transfer-review').innerHTML=`<strong>${esc(money(amount))}</strong>From ${esc(from)} to ${esc(to)}<br>Check the details, then confirm your transfer.`;
    $('transfer-review').classList.remove('hidden');$('submit-transfer').textContent='Confirm transfer';return;
  }
  setBusy(true);$('submit-transfer').textContent='Transferring…';
  let ambiguous=false;
  try {
    // A decimal string preserves cents even for amounts beyond JavaScript's safe integer range.
    await api('/api/v1/transfers',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({fromAccountNumber:from,toAccountNumber:to,amount})});
    $('transfer-dialog').close();state.page=0;
    toast(`${money(amount)} transferred successfully. Both accounts are up to date.`);
    await refresh();
  } catch(error) {
    ambiguous=!error.status || error.status>=500;
    if(ambiguous){
      state.uncertain=true;showError('The outcome could not be confirmed. Do not repeat this transfer until you have checked activity and balances.');
      $('load-error').textContent='A transfer outcome could not be confirmed. Check activity and balances before making another transfer; this request will not be automatically retried.';
      $('load-error').classList.remove('hidden');
    }else{showError(error.message);resetReview();}
    await refresh();
  } finally {
    setBusy(false);if(ambiguous)$('submit-transfer').disabled=true;
    else if(!state.reviewed)$('submit-transfer').innerHTML='Review transfer <span>→</span>';
    else $('submit-transfer').textContent='Confirm transfer';
    updateAccountSelection();
  }
}
function exportPage() {
  if(!state.history?.items.length)return;
  const cell=value=>{let s=String(value);if(/^[=+\-@\t\r]/.test(s))s="'"+s;return '"'+s.replace(/"/g,'""')+'"';};
  const rows=[['Transfer ID','From account','To account','Amount (USD)','Timestamp (UTC)','Status'],...state.history.items.map(t=>[t.id,t.sourceAccountNumber,t.destinationAccountNumber,t.amount,t.timestamp,t.status])];
  const blob=new Blob(['\uFEFF'+rows.map(row=>row.map(cell).join(',')).join('\r\n')],{type:'text/csv;charset=utf-8;'});
  const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=`banksync-transfers-page-${state.page+1}.csv`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
  toast('The displayed transaction page has been exported.');
}
$('today-date').textContent=new Date().toLocaleDateString('en-US',{weekday:'long',month:'long',day:'numeric',year:'numeric'});
$('new-transfer-button').addEventListener('click',openTransfer);
$('quick-transfer-button').addEventListener('click',openTransfer);
$('refresh-button').addEventListener('click',()=>refresh(true));
$('about-button').addEventListener('click',()=>$('about-dialog').showModal());
$('view-accounts-button').addEventListener('click',()=>location.hash='accounts');
$('menu-button').addEventListener('click',()=>{const open=$('sidebar').classList.toggle('open');$('menu-button').setAttribute('aria-expanded',String(open));});
document.addEventListener('click',e=>{if(window.innerWidth<=640&&!$('sidebar').contains(e.target)&&!$('menu-button').contains(e.target)){$('sidebar').classList.remove('open');$('menu-button').setAttribute('aria-expanded','false');}});
document.addEventListener('keydown',e=>{if(e.key==='Escape'){$('sidebar').classList.remove('open');$('menu-button').setAttribute('aria-expanded','false');}});
document.querySelectorAll('[data-close]').forEach(button=>button.addEventListener('click',()=>{if(!state.busy)$(button.dataset.close).close();}));
$('transfer-dialog').addEventListener('cancel',event=>{if(state.busy)event.preventDefault();});
$('transfer-form').addEventListener('submit',submitTransfer);
$('from-account').addEventListener('change',updateAccountSelection);
$('to-account').addEventListener('change',resetReview);
$('transfer-amount').addEventListener('input',resetReview);
$('export-button').addEventListener('click',exportPage);
$('previous-page').addEventListener('click',()=>{state.page--;loadHistory().catch(e=>toast(e.message,true));});
$('next-page').addEventListener('click',()=>{state.page++;loadHistory().catch(e=>toast(e.message,true));});
$('transaction-search').addEventListener('input',()=>{
  clearTimeout(state.searchTimer);state.searchTimer=setTimeout(()=>{
    const search=$('transaction-search').value.trim();
    if(!/^[A-Za-z0-9-]{0,64}$/.test(search)){toast('Search using account letters, numbers, or hyphens.',true);return;}
    state.search=search;state.page=0;loadHistory().catch(e=>toast(e.message,true));
  },300);
});
window.addEventListener('hashchange',setView);
setView();refresh();
