// Built-in add-on browser, designed to use the authenticated Sibyl Core API.
// All GitHub repository access and private tokens remain on the backend.
// Never download executable add-ons directly from the user's browser.
const statusEl=document.querySelector('#status');
const catalogEl=document.querySelector('#catalog');
const queryEl=document.querySelector('#addon-search');
const channelEl=document.querySelector('#release-channel');
const refreshEl=document.querySelector('#refresh');
let entries=[];

function updateStatus(message,error=false){
  statusEl.textContent=message;
  statusEl.classList.toggle('error',error);
  statusEl.hidden=!message;
}
function el(tag,text,css){
  const node=document.createElement(tag);
  if(text!==undefined)node.textContent=String(text);
  if(css)node.className=css;
  return node;
}
function decorate(card,label,text){
  card.append(el('span',text,label));
}
function render(){
  catalogEl.replaceChildren();
  const q=queryEl.value.trim().toLocaleLowerCase();
  const view=entries.filter(a=>(a.id+' '+a.name+' '+(a.description||'')).toLocaleLowerCase().includes(q));
  for(const addon of view){
    const card=el('article',undefined,'addon-card');
    const title=el('div',undefined,'addon-title');
    const names=el('div');
    names.append(el('div',addon.name,'addon-name'),el('div',addon.id,'addon-id'));
    title.append(names);
    const meta=el('div',undefined,'addon-meta');
    decorate(meta,'chip',addon.status==='available'?'Release verfügbar':addon.status==='downloaded'?'Bereits heruntergeladen':addon.status==='no_release'?'Kein Release':'Nicht verfügbar');
    if(addon.version)decorate(meta,'chip ok',addon.version);
    card.append(title,el('div',addon.description||'Kein Beschreibungstext verfügbar.','addon-desc'),meta);
    const actions=el('div',undefined,'addon-actions');
    const button=el('button','Release herunterladen');
    button.type='button';
    button.disabled=addon.status!=='available';
    button.addEventListener('click',()=>downloadAddon(addon,button));
    actions.append(button);
    card.append(actions);
    catalogEl.append(card);
  }
  if(!view.length)updateStatus('Keine passenden Addons gefunden.');
  else if(entries.length)updateStatus('');
}
async function load(){
  updateStatus('GitHub-Releases werden über Sibyl Core geprüft …');
  try{
    const res=await fetch('/api/v1/admin/addons/catalog?channel='+encodeURIComponent(channelEl.value),{credentials:'same-origin',headers:{'Accept':'application/json'}});
    if(!res.ok)throw new Error('HTTP '+res.status+' – bitte Admin-Anmeldung und GitHub-Konfiguration prüfen');
    const body=await res.json();
    if(!Array.isArray(body.addons))throw new Error('Unerwartetes Katalogformat');
    entries=body.addons;
    render();
    if(!entries.length)updateStatus('Der Katalog ist noch leer. Repositories werden im Adminbereich konfiguriert.');
  }catch(err){entries=[];catalogEl.replaceChildren();updateStatus('Addon-Katalog nicht erreichbar: '+err.message,true);}
}
async function downloadAddon(addon,button){
  button.disabled=true;
  button.textContent='Wird geprüft …';
  try{
    const csrfResponse=await fetch('/api/v1/admin/csrf',{credentials:'same-origin',headers:{'Accept':'application/json'}});
    if(!csrfResponse.ok)throw new Error('CSRF-Token nicht verfügbar (HTTP '+csrfResponse.status+')');
    const csrf=await csrfResponse.json();
    const res=await fetch('/api/v1/admin/addons/'+encodeURIComponent(addon.id)+'/download',{
      method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/json',[csrf.header]:csrf.token},
      body:JSON.stringify({channel:channelEl.value,releaseTag:addon.tag})
    });
    if(!res.ok){
      let reason='HTTP '+res.status;
      try{const detail=await res.json();if(detail.error)reason+=' – '+String(detail.error);}catch{}
      throw new Error(reason);
    }
    updateStatus(addon.name+': Release heruntergeladen und geprüft. Aktivierung erfolgt nach Freigabe.');
    await load();
  }catch(err){updateStatus('Download fehlgeschlagen: '+err.message,true);}
  finally{button.textContent='Release herunterladen';button.disabled=addon.status!=='available';}
}
document.querySelector('#theme-toggle').addEventListener('click',()=>{
  const next=document.documentElement.dataset.theme==='dark'?'light':'dark';
  document.documentElement.dataset.theme=next;
  localStorage.setItem('sibyl-theme',next);
});
const theme=localStorage.getItem('sibyl-theme');
if(theme==='dark'||theme==='light')document.documentElement.dataset.theme=theme;
refreshEl.addEventListener('click',load);
queryEl.addEventListener('input',render);
channelEl.addEventListener('change',load);
load();
