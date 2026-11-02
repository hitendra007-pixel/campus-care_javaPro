// CampusCare browser UI. All login and complaint data requests are handled by CampusComplaintSystem.java.
const $=id=>document.getElementById(id);
const depts=['Transport','Hostel','IT Support','Finance','General Administration'];
let me=null,allTickets=[],selectedDept=null;
const students={AP25110071330:'Hitendra',AP25110071331:'Sasank',AP25110071332:'Lokesh',AP25110071333:'Srikanta',AP25110071334:'Abhinav',AP25110071335:'Prabhas'};
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const deptFor=c=>({'Bus Problem':'Transport','Hostel Problem':'Hostel','Wi-Fi / Computer':'IT Support','Fee Problem':'Finance','Classroom / Academic':'General Administration','Other':'General Administration'}[c]||'General Administration');

// Uses relative API base so it works on any domain, localhost, or production hosting URL.
// When opened via file:// protocol directly in a browser, falls back to http://localhost:8090/api/
const API_BASE = (typeof window !== 'undefined' && window.location.protocol === 'file:')
  ? 'http://localhost:8090/api/'
  : '/api/';

async function api(path,data){
  const url=API_BASE+path;
  const opt=data?{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams(data)}:{};
  let r;
  try { r=await fetch(url,opt); }
  catch(e) { throw new Error('Cannot connect to CampusCare server. Please check your network connection.'); }
  let j;
  try { j=await r.json(); } catch(e) { throw new Error('CampusCare server returned an invalid response. Please reload.'); }
  if(!r.ok) throw new Error(j.error||'Request failed');
  return j;
}

function notify(msg){
  let n=$('toast');
  if(!n){
    n=document.createElement('div');
    n.id='toast';
    n.style.cssText='position:fixed;top:18px;right:22px;background:#263b32;color:white;padding:13px 20px;border-radius:8px;z-index:9999;box-shadow:0 5px 20px #0002;font-size:13px;max-width:90vw';
    document.body.appendChild(n);
  }
  n.textContent=msg;
  n.style.display='block';
  setTimeout(()=>n.style.display='none',2600);
}

function studentName(id){return students[id]||id;}
function duration(start){
  let mins=Math.max(0,Math.floor((Date.now()-new Date(start).getTime())/60000)),
      d=Math.floor(mins/1440),h=Math.floor(mins%1440/60),m=mins%60;
  return d?`${d}d ${h}h ${m}m`:h?`${h}h ${m}m`:`${m} min`;
}

function mine(){return allTickets.filter(t=>t.student===me.id);}
function deptItems(d){return allTickets.filter(t=>t.dept===d);}
function pill(s){
  return `<span class="pill ${s==='Resolved'?'resolved':s==='In Progress'?'progress':s==='Closed by Admin'?'closed':''}">${esc(s)}</span>`;
}

async function refresh(){
  allTickets=await api('tickets?'+new URLSearchParams({username:me.id,role:me.role,department:me.dept||''}));
}

function login(){
  $('loginForm').onsubmit=async e=>{
    e.preventDefault();
    try{
      let u=await api('login',{username:$('username').value.trim(),password:$('password').value});
      me=u;
      $('error').classList.add('hidden');
      $('login').classList.add('hidden');
      $('app').classList.remove('hidden');
      $('logout').classList.remove('hidden');
      $('who').textContent=u.name;
      $('roleLabel').textContent=u.role==='staff'?u.dept:u.role;
      $('nav').innerHTML=(u.role==='student'?[['studentHome','Overview'],['new','Raise a ticket'],['history','My tickets']]:u.role==='staff'?[['staffHome','Department tickets']]:[['adminHome','Overview'],['departments','Departments'],['allComplaints','All tickets']]).map(([p,t])=>`<button class="navbtn" data-p="${p}" onclick="page('${p}')">${t}</button>`).join('');
      await refresh();
      page(u.role==='student'?'studentHome':u.role==='staff'?'staffHome':'adminHome');
    }catch(err){
      $('error').classList.remove('hidden');
      $('error').textContent=err.message;
    }
  };
}

function logout(){
  me=null;
  allTickets=[];
  $('app').classList.add('hidden');
  $('login').classList.remove('hidden');
  $('logout').classList.add('hidden');
  $('username').value='';
  $('password').value='';
}

async function page(id){
  document.querySelectorAll('.page').forEach(x=>x.classList.add('hidden'));
  $(id).classList.remove('hidden');
  document.querySelectorAll('.navbtn').forEach(x=>x.classList.toggle('active',x.dataset.p===id));
  await refresh();
  ({studentHome,history,staffHome,adminHome,departments,allComplaints}[id]||(()=>{}))();
}

$('complaintForm').onsubmit=async e=>{
  e.preventDefault();
  let category=$('category').value,description=$('description').value.trim();
  if(!category||!description)return notify('Complete all fields.');
  try{
    let result=await api('tickets',{username:me.id,password:$('password').value,category,description});
    $('category').value='';
    $('description').value='';
    notify('Complaint submitted. Ticket ID: '+result.id);
    await page('history');
  }catch(err){
    notify(err.message);
  }
};

function ticketTable(items,mode='student'){
  if(!items.length)return '<div class="empty">No tickets found.</div>';
  let admin=mode==='admin',staff=mode==='staff';
  return `<div class="tablewrap"><table><tr><th>Ticket ID</th>${admin||staff?'<th>Student</th>':''}<th>Problem</th><th>Department</th><th>Raised</th><th>Time elapsed</th><th>Status</th><th>Latest message</th>${staff||admin?'<th>Actions</th>':''}</tr>${items.map(c=>`<tr><td><b>#${esc(c.id)}</b></td>${admin||staff?`<td>${esc(c.studentName)}<br><small>${esc(c.student)}</small></td>`:''}<td>${esc(c.category)}<br><small>${esc(c.description)}</small></td><td>${esc(c.dept)}</td><td>${new Date(c.raisedAt).toLocaleDateString()}</td><td>${duration(c.raisedAt)}</td><td>${pill(c.status)}</td><td>${esc(c.reply||'—')}</td>${staff||admin?`<td><select class="table-select" onchange="setStatus('${esc(c.id)}',this.value)"><option ${c.status==='Pending'?'selected':''}>Pending</option><option ${c.status==='In Progress'?'selected':''}>In Progress</option><option ${c.status==='Resolved'?'selected':''}>Resolved</option>${admin?`<option ${c.status==='Closed by Admin'?'selected':''}>Closed by Admin</option>`:''}</select>${admin?`<button class="outline" style="margin-top:8px;display:block;width:100%" onclick="openAdminTicket('${esc(c.id)}')">Open ticket, message & close</button>`:`<input class="msg" placeholder="Message to student" onchange="setReply('${esc(c.id)}',this.value)">`}</td>`:''}</tr>`).join('')}</table></div>`;
}

function openAdminTicket(id){
  const t=allTickets.find(x=>String(x.id)===String(id));
  if(!t)return notify('Ticket not found. Refresh and try again.');
  const activePage=document.querySelector('.page:not(.hidden)');
  const host=($('deptDetail') && !$('deptDetail').classList.contains('hidden'))
    ? $('deptDetail')
    : (activePage || $('allTable') || $('adminHome'));
  let panel=$('adminTicketDetail');
  if(panel && panel.parentElement!==host){panel.remove();panel=null;}
  if(!panel){
    panel=document.createElement('section');
    panel.id='adminTicketDetail';
    panel.className='card';
    host.appendChild(panel);
  }
  panel.innerHTML=`<div class="between"><h2>Ticket #${esc(t.id)} — Admin actions</h2><button class="outline" onclick="this.closest('#adminTicketDetail').remove()">Close panel</button></div>
  <p><b>Student:</b> ${esc(t.studentName)} (${esc(t.student)})</p>
  <p><b>Department:</b> ${esc(t.dept)} &nbsp; <b>Status:</b> ${pill(t.status)}</p>
  <p><b>Complaint:</b> ${esc(t.description)}</p>
  <p><b>Latest message:</b> ${esc(t.reply||'No message yet')}</p>
  <label for="adminReply">Message to student</label>
  <textarea id="adminReply" rows="3" style="width:100%;margin:8px 0;padding:12px;border:1px solid #d8d8ca;border-radius:8px;box-sizing:border-box;font:inherit" placeholder="Write an update for this student..."></textarea>
  <div style="display:flex;gap:10px;flex-wrap:wrap;margin-top:8px">
    <button class="primary" onclick="sendAdminReply('${esc(t.id)}')">Send message to student</button>
    <button class="outline" onclick="adminCloseTicket('${esc(t.id)}')" ${t.status==='Closed by Admin'?'disabled':''}>${t.status==='Closed by Admin'?'Ticket closed by admin':'Close ticket as admin'}</button>
  </div>`;
  panel.scrollIntoView({behavior:'smooth',block:'center'});
}

async function adminCloseTicket(id){
  if(!confirm('Close this ticket as administrator? The student will see that it was closed by Admin.'))return;
  try{
    await api('status',{ticketId:id,status:'Closed by Admin',role:'admin',department:''});
    notify('Ticket closed by Admin. The student can see the updated status.');
    await refresh();
    if($('deptDetail') && !$('deptDetail').classList.contains('hidden')) openDept(selectedDept);
    else if(!$('allComplaints').classList.contains('hidden')) allComplaints();
    else adminHome();
    openAdminTicket(id);
  }catch(e){notify(e.message);}
}

async function sendAdminReply(id){
  const message=$('adminReply')?.value.trim()||'';
  if(!message)return notify('Write a message first.');
  try{
    await api('message',{ticketId:id,message,role:'admin',department:'',username:me.id});
    notify('Admin message sent to the student.');
    await refresh();
    if($('deptDetail') && !$('deptDetail').classList.contains('hidden')) openDept(selectedDept);
    else if(!$('allComplaints').classList.contains('hidden')) allComplaints();
    else adminHome();
    openAdminTicket(id);
  }catch(e){notify(e.message);}
}

function studentHome(){
  let a=mine();
  $('studentName').textContent=studentName(me.id);
  $('sTotal').textContent=a.length;
  $('sPending').textContent=a.filter(x=>x.status==='Pending').length;
  $('sResolved').textContent=a.filter(x=>x.status==='Resolved').length;
  $('recent').innerHTML=ticketTable(a.slice(0,5));
}

function history(){
  $('historyTable').innerHTML=ticketTable(mine());
}

function staffHome(){
  let a=deptItems(me.dept);
  $('staffTitle').textContent=me.dept+' dashboard';
  $('dTotal').textContent=a.length;
  $('dPending').textContent=a.filter(x=>x.status==='Pending').length;
  $('dResolved').textContent=a.filter(x=>x.status==='Resolved').length;
  $('staffTable').innerHTML=ticketTable(a,'staff');
}

async function setStatus(id,status){
  try{
    await api('status',{ticketId:id,status,role:me.role,department:me.dept||''});
    notify('Ticket status updated.');
    await page(me.role==='admin'?'adminHome':'staffHome');
  }catch(e){notify(e.message);}
}

async function setReply(id,message){
  if(!message.trim())return;
  try{
    await api('message',{ticketId:id,message,role:me.role,department:me.dept||''});
    notify('Message sent.');
    await page(me.role==='admin'?'allComplaints':'staffHome');
  }catch(e){notify(e.message);}
}

function adminHome(){
  let a=allTickets,count=s=>a.filter(x=>x.status===s).length;
  $('aTotal').textContent=a.length;
  $('aPending').textContent=count('Pending');
  $('aProgress').textContent=count('In Progress');
  $('aResolved').textContent=count('Resolved');
  if($('aAvgTime')) $('aAvgTime').textContent='—';
  let nums=depts.map(d=>deptItems(d).length),max=Math.max(1,...nums);
  $('chart').innerHTML=depts.map((d,i)=>`<div class="baritem"><b>${nums[i]}</b><div class="bar" style="height:${Math.max(4,nums[i]/max*125)}px"></div><small>${d}</small></div>`).join('');
  $('deptSummary').innerHTML=depts.map(d=>`<button class="dept" onclick="openDept('${d}')"><b>${d}<strong>${deptItems(d).length}</strong></b><span>View department tickets →</span></button>`).join('');
}

function departments(){
  $('deptDetail').classList.add('hidden');
  $('deptCards').innerHTML=depts.map(d=>`<button class="dept" onclick="openDept('${d}')"><b>${d}<strong>${deptItems(d).length}</strong></b><span>${deptItems(d).length} ticket(s) · View details →</span></button>`).join('');
}

function openDept(d){
  selectedDept=d;
  $('deptDetail').classList.remove('hidden');
  $('deptDetail').innerHTML=`<div class="between"><h2>${d} Department</h2><button class="outline" onclick="departments()">Back to departments</button></div>${ticketTable(deptItems(d),'admin')}`;
}

function allComplaints(){
  $('allTable').innerHTML=ticketTable(allTickets,'admin');
}

login();
