'use strict';
// Backend-backed administration: only HTTPS, credentials remain in JS memory.
const $=id=>document.getElementById(id);
const buttons=[...document.querySelectorAll('.tab-nav button[data-section]')];
const sections=[...document.querySelectorAll('.tab-panel')];
let authorization=null;
let account=null;
let csrfToken=null;
let settings=null;
const validPages=['overview','organization','modules','integrations','security'];
function show(page){
  if(!validPages.includes(page))page='overview';
  buttons.forEach(btn=>{
    const active=btn.dataset.section===page;
    btn.classList.toggle('active',active);
    if(active)btn.setAttribute('aria-current','page');else btn.removeAttribute('aria-current');
  });
  sections.forEach(p=>p.hidden=p.id!=='panel-'+page);
}
function status(id,message,error=false){
  $(id).textContent=message;
  $(id).style.color=error?'var(--danger)':'var(--accent)';
}
function hideAll(){
  $('admin-area').hidden=true;
  $('password-change-box').hidden=true;
  $('admin-login-box').hidden=true;
}
function logout(){
  authorization=null;account=null;csrfToken=null;settings=null;
  $('login-password').value='';
  $('old-password').value='';$('new-password').value='';$('confirm-password').value='';
  hideAll();$('admin-login-box').hidden=false;
}
function authHeaders(){return {Authorization:authorization,Accept:'application/json'};}
async function request(path,options={}){
  const headers={...authHeaders(),...(options.headers||{})};
  const res=await fetch('/api/v1/admin'+path,{...options,credentials:'same-origin',headers,cache:'no-store'});
  if(!res.ok){
    if(res.status===401)logout();
    let reason='HTTP '+res.status;
    try{const error=await res.json();if(error.error)reason+=': '+error.error}catch{}
    throw new Error(reason);
  }
  return await res.json();
}
async function refreshCsrf(){
  const response=await request('/csrf');
  if(!response.header||!response.token)throw new Error('CSRF-Token fehlt');
  csrfToken=response;
}
function populate(s){
  settings=s;
  $('organization-name').value=s.organization||'Sibyl System';
  $('organization-language').value=s.language||'de';
  $('organization-timezone').value=s.timezone||'Europe/Berlin';
  $('organization-accent').value=s.accent||'classic';
  document.querySelectorAll('[data-org-preview]').forEach(x=>x.textContent=s.organization);
  document.querySelectorAll('[data-org-initial]').forEach(x=>x.textContent=(s.organization||'S').charAt(0).toUpperCase());
  document.querySelectorAll('[data-sibyl-brand]').forEach(x=>x.textContent=s.organization||'Sibyl System');
}
async function loadAccount(){
  account=await request('/account');
  hideAll();
  if(account.mustChangePassword){
    $('password-change-box').hidden=false;
    status('password-change-status','Das Startpasswort muss vor der Administration geändert werden.');
  }else{
    $('admin-area').hidden=false;
    $('signed-in-as').textContent='Angemeldet als '+account.username;
    populate(await request('/settings'));
    await listAddonSettings();
  }
}
$('login-form').addEventListener('submit',async event=>{
  event.preventDefault();
  if(location.protocol!=='https:'){
    status('login-status','Zur Anmeldung ist HTTPS erforderlich.',true);return;
  }
  const name=$('login-name').value.trim(), pass=$('login-password').value;
  if(!name||!pass)return;
  // encode Unicode credentials without storing them in localStorage or a cookie
  authorization='Basic '+btoa(String.fromCharCode(...new TextEncoder().encode(name+':'+pass)));
  $('login-password').value='';
  try{
    await refreshCsrf();
    await loadAccount();
  }catch(e){
    authorization=null;
    hideAll();$('admin-login-box').hidden=false;
    status('login-status','Anmeldung fehlgeschlagen: '+e.message,true);
  }
});
$('password-change-form').addEventListener('submit',async event=>{
  event.preventDefault();
  const old=$('old-password').value,next=$('new-password').value,confirm=$('confirm-password').value;
  if(next!==confirm){status('password-change-status','Die neuen Passwörter stimmen nicht überein.',true);return;}
  if(next.length<12||next==='friend'||next===old){status('password-change-status','Ein neues Passwort mit mindestens zwölf Zeichen ist erforderlich.',true);return;}
  try{
    await refreshCsrf();
    await request('/account/password',{method:'POST',
      headers:{'Content-Type':'application/json',[csrfToken.header]:csrfToken.token},
      body:JSON.stringify({currentPassword:old,newPassword:next})});
    logout();
    status('login-status','Passwort geändert. Bitte mit dem neuen Passwort erneut anmelden.');
  }catch(e){
    status('password-change-status','Passwortänderung fehlgeschlagen: '+e.message,true);
  }finally{
    $('old-password').value='';$('new-password').value='';$('confirm-password').value='';
  }
});
$('organization-form').addEventListener('submit',async event=>{
  event.preventDefault();if(!authorization)return;
  const next={
    organization:$('organization-name').value.trim(),
    language:$('organization-language').value,
    timezone:$('organization-timezone').value,
    accent:$('organization-accent').value
  };
  try{
    await refreshCsrf();
    const saved=await request('/settings',{
      method:'PUT',
      headers:{'Content-Type':'application/json',[csrfToken.header]:csrfToken.token},
      body:JSON.stringify(next)});
    populate(saved);
    status('save-status','Einstellungen zentral in PostgreSQL gespeichert.');
  }catch(e){status('save-status','Speichern fehlgeschlagen: '+e.message,true);}
});
$('export-config').addEventListener('click',()=>{
  if(!settings)return;
  const blob=new Blob([JSON.stringify({schema:1,...settings},null,2)+'\n'],{type:'application/json'});
  const uri=URL.createObjectURL(blob),a=document.createElement('a');
  a.href=uri;a.download='sibyl-organisation.json';document.body.appendChild(a);a.click();a.remove();
  setTimeout(()=>URL.revokeObjectURL(uri),2000);
  status('save-status','Aktuelle Servereinstellungen als JSON exportiert.');
});
$('import-file').addEventListener('change',async()=>{
  const file=$('import-file').files?.[0];if(!file)return;
  try{
    if(file.size>64*1024)throw new Error('Datei zu groß');
    const data=JSON.parse(await file.text());
    if(typeof data.organization!=='string'||data.organization.length>80)throw new Error('Ungültige Organisation');
    $('organization-name').value=data.organization;
    if(data.language)$('organization-language').value=data.language;
    if(data.timezone)$('organization-timezone').value=data.timezone;
    if(data.accent)$('organization-accent').value=data.accent;
    status('save-status','Entwurf geladen. Klicken Sie auf Einstellungen speichern, um ihn in die Datenbank zu übernehmen.');
  }catch(e){status('save-status','Import fehlgeschlagen: '+e.message,true)}
  finally{$('import-file').value='';}
});
$('reset-config').addEventListener('click',()=>{
  if(!settings)return;populate(settings);status('save-status','Nicht gespeicherte Änderungen verworfen.');
});
async function listAddonSettings(){
  const select=$('addon-settings-id');
  select.replaceChildren(new Option('Bitte Addon auswählen',''));
  try{
    const response=await fetch('/api/v1/addons/catalog?channel=prerelease',
      {credentials:'same-origin',headers:{Accept:'application/json'},cache:'no-store'});
    if(!response.ok)throw new Error('Katalog nicht erreichbar');
    const payload=await response.json();
    for(const item of payload.addons||[]){
      select.add(new Option((item.name||item.id)+' ('+item.id+')',item.id));
    }
  }catch(error){status('addon-settings-status',error.message,true);}
}
$('addon-settings-id').addEventListener('change',async()=>{
  const id=$('addon-settings-id').value;
  if(!authorization||!id){$('addon-settings-json').value='{}';return;}
  try{
    const data=await request('/addon-settings/'+encodeURIComponent(id));
    $('addon-settings-json').value=JSON.stringify(data.settings||{},null,2);
    status('addon-settings-status','Einstellungen aus der Sibyl-Datenbank geladen.');
  }catch(error){status('addon-settings-status','Laden fehlgeschlagen: '+error.message,true);}
});
$('addon-settings-form').addEventListener('submit',async event=>{
  event.preventDefault();
  const id=$('addon-settings-id').value;
  if(!authorization||!id){status('addon-settings-status','Bitte ein Addon auswählen.',true);return;}
  try{
    const json=JSON.parse($('addon-settings-json').value);
    if(!json||typeof json!=='object'||Array.isArray(json))throw new Error('JSON-Objekt erforderlich');
    await refreshCsrf();
    const result=await request('/addon-settings/'+encodeURIComponent(id),{
      method:'PUT',
      headers:{'Content-Type':'application/json',[csrfToken.header]:csrfToken.token},
      body:JSON.stringify(json)
    });
    $('addon-settings-json').value=JSON.stringify(result.settings,null,2);
    status('addon-settings-status','Addon-Einstellungen zentral gespeichert.');
  }catch(error){status('addon-settings-status','Speichern fehlgeschlagen: '+error.message,true);}
});

$('logout-button').addEventListener('click',logout);
buttons.forEach(b=>b.addEventListener('click',()=>show(b.dataset.section)));
document.querySelectorAll('button[data-go]').forEach(b=>b.addEventListener('click',()=>show(b.dataset.go)));
logout();show(validPages.includes(location.hash.slice(1))?location.hash.slice(1):'overview');
if(location.protocol!=='https:'){
  status('login-status','Administratoranmeldung nur über HTTPS möglich.',true);
  $('login-form').querySelector('button[type=submit]').disabled=true;
}
