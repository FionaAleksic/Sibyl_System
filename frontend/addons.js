'use strict';
/* Browser never contacts GitHub: the Sibyl Core on this server checks releases,
 * downloads ZIP assets from GitHub and verifies digest + manifest.
 * Password is held only in this page's memory; use HTTPS only.
 */
const body=document.querySelector('#addons');
const counter=document.querySelector('#count');
const statusText=document.querySelector('#catalog-status');
const notice=document.querySelector('#catalog-notice');
const search=document.querySelector('#addon-search');
const channel=document.querySelector('#release-channel');
const refresh=document.querySelector('#refresh');
const form=document.querySelector('#admin-login');
const nameField=document.querySelector('#admin-name');
const passwordField=document.querySelector('#admin-password');
const authStatus=document.querySelector('#admin-auth-status');
const logoutButton=document.querySelector('#admin-logout');
const connectButton=document.querySelector('#admin-connect');
let entries=[];
let authHeader=null;
let live=false;
let busy=false;
const secure=location.protocol==='https:'||location.hostname==='localhost'||location.hostname==='127.0.0.1';
function el(tag,value,css=''){
  const x=document.createElement(tag);if(value!==undefined)x.textContent=String(value??'');
  if(css)x.className=css;return x;
}
function banner(title,message,warning=true){
  notice.classList.toggle('warning',warning);
  notice.querySelector('strong').textContent=title;
  notice.querySelector('p').textContent=message;
}
function render(){
  body.replaceChildren();
  const query=search.value.trim().toLocaleLowerCase('de');
  const view=entries.filter(a=>[a.name,a.id,a.description,a.repo].join(' ').toLocaleLowerCase('de').includes(query));
  counter.textContent=view.length+' von '+entries.length+' Addons';
  for(const a of view){
    const card=el('article',undefined,'card');
    if(a.id==='Sibyl.ad')card.id='sibyl-ad';
    const head=el('div',undefined,'card-header');
    head.append(el('div',a.id.split('.').at(-1).slice(0,2).toUpperCase(),'icon-tile'));
    const title=el('div');
    title.append(el('h3',a.name),el('div',a.id,'id'));
    head.append(title);card.append(head,el('p',a.description||'GitHub Addon','desc'));
    const chips=el('div',undefined,'chips');
    const statuses={available:'GitHub-Release verfügbar',downloaded:'Von GitHub heruntergeladen',no_release:'Kein GitHub-Release',repository_unavailable:'Repository nicht erreichbar',error:'GitHub-API-Fehler'};
    chips.append(el('span',statuses[a.status]||'Unbekannt','chip '+(a.status==='available'?'ready':'missing')));
    if(a.version)chips.append(el('span',a.version,'chip'));
    card.append(chips);
    if(a.error)card.append(el('p',a.error,'small muted'));
    const foot=el('div',undefined,'card-footer');
    const link=document.createElement('a');
    link.href='https://github.com/'+(a.repo||'FionaAleksic/'+a.id);
    link.target='_blank';link.rel='noopener noreferrer';
    link.textContent='GitHub ↗';link.className='small';
    foot.append(link);
    const button=el('button',a.status==='downloaded'?'Heruntergeladen':'Von GitHub laden','button');
    button.type='button';
    button.disabled=busy||!secure||!authHeader||a.status!=='available';
    button.title=!secure?'HTTPS erforderlich':!authHeader?'Bitte als Administrator anmelden':
      a.status!=='available'?'Kein installierbarer Release für den Kanal':'Veröffentlichtes GitHub ZIP abrufen und prüfen';
    button.addEventListener('click',()=>download(a,button));
    foot.append(button);card.append(foot);body.append(card);
  }
  if(!view.length)body.append(el('div','Keine passenden Addons gefunden.','empty'));
}
function apply(items,fromLive){
  entries=items;
  live=fromLive;
  render();
}
async function load(){
  refresh.disabled=true;
  statusText.textContent='GitHub-Releases werden durch Sibyl Core abgefragt …';
  try{
    const res=await fetch('/api/v1/addons/catalog?channel='+encodeURIComponent(channel.value),
      {credentials:'same-origin',headers:{Accept:'application/json'},cache:'no-store'});
    if(!res.ok)throw new Error('Core HTTP '+res.status);
    const data=await res.json();
    if(!Array.isArray(data.addons))throw new Error('Invalid Core catalog');
    apply(data.addons,true);
    banner('GitHub-Releases werden live geprüft','Der Sibyl-Server fragt veröffentlichte GitHub Releases ab. Private Repositories benötigen gegebenenfalls eine serverseitige Freigabe.',false);
    statusText.textContent='Live-Abfrage über Sibyl Core. Download ausschließlich aus veröffentlichten GitHub-Releases.';
  }catch(error){
    // Static preview is only for discovery; never claim this is a live release result.
    try{
      const response=await fetch('./catalog.json',{cache:'no-store'});
      if(!response.ok)throw new Error('HTTP '+response.status);
      const snapshot=await response.json();
      if(!snapshot.releaseOnly||!Array.isArray(snapshot.addons))throw new Error('Invalid local catalog');
      apply(snapshot.addons.map(a=>({id:a.id,name:a.name,description:a.summary,repo:a.repo,status:'no_release'})),false);
      banner('Vorschau ohne Sibyl-Core-Verbindung','Der Browser zeigt nur registrierte GitHub-Repositories, keine Live-Releases. Installation bleibt gesperrt.');
      statusText.textContent='Core-Verbindung fehlgeschlagen: '+error.message;
    }catch(inner){
      apply([],false);statusText.textContent='Katalog nicht erreichbar: '+inner.message;
    }
  }finally{refresh.disabled=false;}
}
form.addEventListener('submit',async event=>{
  event.preventDefault();
  if(!secure){authStatus.textContent='Anmeldung nur über HTTPS möglich.';return;}
  const username=nameField.value.trim(), password=passwordField.value;
  if(!username||!password)return;
  connectButton.disabled=true;
  try{
    const value='Basic '+btoa(unescape(encodeURIComponent(username+':'+password)));
    const res=await fetch('/api/v1/admin/csrf',{credentials:'same-origin',headers:{Authorization:value,Accept:'application/json'},cache:'no-store'});
    if(!res.ok)throw new Error('HTTP '+res.status);
    const json=await res.json();
    if(!json.header||!json.token)throw new Error('CSRF-Handshake fehlgeschlagen');
    authHeader=value;
    passwordField.value='';
    passwordField.disabled=true;nameField.disabled=true;
    connectButton.hidden=true;logoutButton.hidden=false;
    authStatus.textContent='Administrator angemeldet. Verfügbare GitHub-Releases können jetzt auf diesen Server heruntergeladen werden.';
    render();
  }catch(error){
    authHeader=null;
    passwordField.value='';
    authStatus.textContent='Anmeldung fehlgeschlagen: '+error.message;
    render();
  }finally{connectButton.disabled=false;}
});
logoutButton.addEventListener('click',()=>{
  authHeader=null;nameField.disabled=false;passwordField.disabled=false;
  connectButton.hidden=false;logoutButton.hidden=true;
  authStatus.textContent='Administrativer Download abgemeldet.';
  render();
});
async function download(addon,button){
  if(!secure||!authHeader||!live||addon.status!=='available')return;
  if(!confirm(addon.name+' '+addon.tag+' wirklich von GitHub herunterladen und auf dem Sibyl-Server prüfen?'))return;
  busy=true;render();
  statusText.textContent='Sibyl-Server lädt '+addon.id+' von GitHub …';
  try{
    const csrfResponse=await fetch('/api/v1/admin/csrf',{headers:{Authorization:authHeader,Accept:'application/json'},credentials:'same-origin'});
    if(!csrfResponse.ok)throw new Error('CSRF HTTP '+csrfResponse.status);
    const csrf=await csrfResponse.json();
    const response=await fetch('/api/v1/admin/addons/'+encodeURIComponent(addon.id)+'/download',{
      method:'POST',credentials:'same-origin',
      headers:{Authorization:authHeader,'Content-Type':'application/json',[csrf.header]:csrf.token},
      body:JSON.stringify({channel:channel.value,releaseTag:addon.tag})
    });
    if(!response.ok){
      let info='HTTP '+response.status;
      try{const data=await response.json();info+=' '+(data.error||data.message||'')}catch{}
      throw new Error(info);
    }
    const result=await response.json();
    statusText.textContent=addon.id+' '+result.version+': GitHub-Paket erfolgreich heruntergeladen und geprüft. Aktivierung des Addons ist noch nicht implementiert.';
    await load();
  }catch(error){
    statusText.textContent='Download fehlgeschlagen: '+error.message;
  }finally{busy=false;render();}
}
if(!secure){
  form.hidden=true;
  authStatus.textContent='HTTPS für Administratorfunktionen erforderlich. Über HTTP ist nur die Addon-Suche verfügbar.';
}
search.addEventListener('input',render);
channel.addEventListener('change',load);
refresh.addEventListener('click',load);
load();
