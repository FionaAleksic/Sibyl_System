'use strict';
/* Browser-local configuration draft. There are deliberately no server mutation calls. */
const buttons=[...document.querySelectorAll('.tab-nav button[data-section]')];
const sections=[...document.querySelectorAll('.tab-panel')];
const form=document.querySelector('#organization-form');
const status=document.querySelector('#save-status');
const org=document.querySelector('#organization-name');
const language=document.querySelector('#organization-language');
const zone=document.querySelector('#organization-timezone');
const accent=document.querySelector('#organization-accent');
const importFile=document.querySelector('#import-file');
const defaults=Object.freeze({schema:1,organization:'',language:'de',timezone:'Europe/Berlin',accent:'classic'});
function show(page){
  if(!['overview','organization','modules','integrations','security'].includes(page)) page='overview';
  for(const btn of buttons){
    const active=btn.dataset.section===page;
    btn.classList.toggle('active',active);
    if(active) btn.setAttribute('aria-current','page');
    else btn.removeAttribute('aria-current');
  }
  for(const panel of sections)panel.hidden=panel.id!=='panel-'+page;
}
function read(){
  const raw=window.SibylPreview.read();
  return {
    schema:1,
    organization:typeof raw.organization==='string'?raw.organization.slice(0,80):'',
    language:raw.language==='en'?'en':'de',
    timezone:['Europe/Berlin','Europe/London','UTC','America/New_York'].includes(raw.timezone)?raw.timezone:'Europe/Berlin',
    accent:['classic','teal','violet'].includes(raw.accent)?raw.accent:'classic'
  };
}
function render(){
  const c=read();
  org.value=c.organization;
  language.value=c.language;
  zone.value=c.timezone;
  accent.value=c.accent;
}
function current(){
  return {
    schema:1,
    organization:org.value.trim().slice(0,80),
    language:language.value,
    timezone:zone.value,
    accent:accent.value
  };
}
function validate(c){
  if(!c||typeof c!=='object'||Array.isArray(c))throw new Error('Ungültige Konfigurationsdatei.');
  if(c.schema!==1)throw new Error('Nicht unterstützte Version.');
  if(typeof c.organization!=='string'||c.organization.length>80)
    throw new Error('Der Organisationsname ist ungültig.');
  if(!['de','en'].includes(c.language))throw new Error('Ungültige Sprache.');
  if(!['Europe/Berlin','Europe/London','UTC','America/New_York'].includes(c.timezone))
    throw new Error('Ungültige Zeitzone.');
  if(!['classic','teal','violet'].includes(c.accent))
    throw new Error('Ungültige Akzentfarbe.');
  return Object.fromEntries(['schema','organization','language','timezone','accent'].map(k=>[k,c[k]]));
}
buttons.forEach(b=>b.addEventListener('click',()=>show(b.dataset.section)));
document.querySelectorAll('button[data-go]').forEach(b=>b.addEventListener('click',()=>show(b.dataset.go)));
form.addEventListener('submit',event=>{
  event.preventDefault();
  try{
    window.SibylPreview.save(validate(current()));
    status.textContent='Vorschau gespeichert – nur in diesem Browser, nicht auf dem Server.';
    status.style.color='var(--accent)';
  }catch(e){
    status.textContent=e.message;
    status.style.color='var(--danger)';
  }
});
document.querySelector('#export-config').addEventListener('click',()=>{
  try{
    const cfg=validate(current());
    const blob=new Blob([JSON.stringify(cfg,null,2)+'\n'],{type:'application/json'});
    const uri=URL.createObjectURL(blob);
    const a=document.createElement('a');
    a.href=uri;a.download='sibyl-konfiguration-entwurf.json';
    document.body.append(a);a.click();a.remove();
    setTimeout(()=>URL.revokeObjectURL(uri),2000);
    status.textContent='Entwurf wurde als JSON exportiert.';
  }catch(e){status.textContent=e.message;}
});
importFile.addEventListener('change',async()=>{
  const file=importFile.files?.[0];if(!file)return;
  if(file.size>64*1024){status.textContent='Die Datei ist zu groß (max. 64 KiB).';return;}
  try{
    const data=validate(JSON.parse(await file.text()));
    window.SibylPreview.save(data);
    render();
    status.textContent='Entwurf importiert und in diesem Browser gespeichert.';
    status.style.color='var(--accent)';
  }catch(e){
    status.textContent='Import fehlgeschlagen: '+e.message;
    status.style.color='var(--danger)';
  }finally{importFile.value='';}
});
document.querySelector('#reset-config').addEventListener('click',()=>{
  if(!window.confirm('Lokalen Konfigurationsentwurf dieses Browsers zurücksetzen?'))return;
  window.SibylPreview.reset();
  render();status.textContent='Der lokale Entwurf wurde zurückgesetzt.';
});
render();
const requested=location.hash.replace(/^#/,'');
show(['overview','organization','modules','integrations','security'].includes(requested)?requested:'overview');
