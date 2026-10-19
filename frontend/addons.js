'use strict';
/* GitHub access and package execution stay on Sibyl Core. Explicit admin action
 * triggers download -> verification -> dependency setup -> activation.
 * No page-load installs, no unsanitized HTML, no browser GitHub tokens.
 */
const body=document.querySelector('#addons');
const counter=document.querySelector('#count');
const statusText=document.querySelector('#catalog-status');
const notice=document.querySelector('#catalog-notice');
const search=document.querySelector('#addon-search');
const channel=document.querySelector('#release-channel');
const refresh=document.querySelector('#refresh');
let entries=[];
let activeModules=[];
let busy=false;
const secure=location.protocol==='https:'||location.hostname==='localhost';
const el=(tag,text,css='')=>{
  const node=document.createElement(tag);
  if(text!==undefined)node.textContent=String(text??'');
  if(css)node.className=css;
  return node;
};
function status(message,error=false) {
  statusText.textContent=message;
  statusText.style.color=error?'var(--danger)':'';
}
function banner(title,message,warning=false){
  notice.classList.toggle('warning',warning);
  notice.querySelector('strong').textContent=title;
  notice.querySelector('p').textContent=message;
}
async function request(url,options={}){
  const res=await fetch(url,{credentials:'same-origin',cache:'no-store',...options});
  if(!res.ok){
    let reason='';
    try{
      const body=await res.json();
      reason=body.message||body.error||body.detail||'';
    }catch{}
    throw new Error('HTTP '+res.status+(reason?' – '+String(reason).slice(0,240):''));
  }
  if(res.status===204)return {};
  return res.json();
}
async function write(url,object){
  const token=await request('/api/v1/auth/csrf');
  return request(url,{method:'POST',headers:{'Content-Type':'application/json',[token.header]:token.token},body:JSON.stringify(object)});
}
async function saveSettings(id,data){
  const token=await request('/api/v1/auth/csrf');
  return request('/api/v1/admin/addon-settings/'+encodeURIComponent(id),{
    method:'PUT',headers:{'Content-Type':'application/json',[token.header]:token.token},
    body:JSON.stringify(data)
  });
}
const installed=(id,version)=>activeModules.some(x=>x.id===id&&(!version||x.version===version));
function render(){
  body.replaceChildren();
  const term=search.value.trim().toLocaleLowerCase('de');
  const shown=entries.filter(x=>(x.name+' '+x.id+' '+x.description+' '+x.repo).toLocaleLowerCase('de').includes(term));
  counter.textContent=shown.length+' von '+entries.length+' Addons';
  for(const addon of shown){
    const card=el('article',undefined,'card');
    const top=el('div',undefined,'card-header');
    top.append(el('div',addon.id.split('.').at(-1).slice(0,2).toUpperCase(),'icon-tile'));
    const label=el('div');
    label.append(el('h3',addon.name),el('div',addon.id,'id'));
    top.append(label);
    const chips=el('div',undefined,'chips');
    let state=addon.status;
    if(installed(addon.id))state='installed';
    const states={installed:'Installiert und aktiviert',available:'Release verfügbar',downloaded:'Heruntergeladen – noch nicht installiert',no_release:'Kein Release',repository_unavailable:'Repository nicht erreichbar',error:'GitHub-API-Fehler'};
    chips.append(el('span',states[state]||'Unbekannt','chip '+(state==='installed'?'ready':state==='available'?'ready':'missing')));
    if(addon.version)chips.append(el('span',addon.version,'chip'));
    card.append(top,el('p',addon.description||'Versioniertes GitHub-Addon','desc'),chips);
    if(addon.error)card.append(el('p',addon.error,'small muted'));
    const footer=el('div',undefined,'card-footer');
    const repo=el('a','Repository ↗','small');
    repo.href='https://github.com/'+(addon.repo||'FionaAleksic/'+addon.id);
    repo.rel='noopener noreferrer';repo.target='_blank';
    footer.append(repo);
    if(state==='available'||state==='downloaded'){
      const button=el('button',state==='available'?'Herunterladen & installieren':'Jetzt installieren','button');
      button.type='button';button.disabled=busy||!secure;
      button.addEventListener('click',()=>start(addon));
      footer.append(button);
    }
    if(state==='installed'||state==='downloaded'){
      const configure=el('button','Konfigurieren','button secondary');
      configure.type='button';configure.disabled=busy||!secure;
      configure.addEventListener('click',()=>configureAddon(addon));
      footer.append(configure);
    }
    if(state==='installed'){
      const open=el('a','Öffnen →','button secondary');
      open.href='/extension-host.html?id='+encodeURIComponent(addon.id);
      if(addon.type==='integration')open.hidden=true;
      footer.append(open);
    }
    card.append(footer);body.append(card);
  }
  if(!shown.length)body.append(el('div','Keine passenden Addons gefunden.','empty'));
}
async function load(){
  refresh.disabled=true;
  status('Veröffentlichte GitHub-Releases werden geprüft …');
  try{
    const results=await Promise.all([
      request('/api/v1/addons/catalog?channel='+encodeURIComponent(channel.value)),
      request('/api/v1/extensions')
    ]);
    if(!Array.isArray(results[0].addons))throw new Error('Ungültiges Katalogformat');
    entries=results[0].addons;
    activeModules=Array.isArray(results[1].modules)?results[1].modules:[];
    banner('Release-Katalog','Herunterladen und Installieren erfolgen in einem Ablauf. Fehlende Einstellungen werden vorher abgefragt.');
    status('');
  }catch(error){
    entries=[];activeModules=[];
    banner('Verbindung nicht verfügbar','Der Browser benötigt den authentifizierten Sibyl Core.',true);
    status('Katalog konnte nicht geladen werden: '+error.message,true);
  }finally{refresh.disabled=false;render();}
}
function friendly(name){
  return name.replace(/([a-z])([A-Z])/g,'$1 $2').replace(/^./,x=>x.toUpperCase());
}
function example(name,rule){
  if(Array.isArray(rule.examples)&&rule.examples.length)return String(rule.examples[0]);
  if(typeof rule.example==='string')return rule.example;
  if(name==='serverUrl')return 'ldaps://dc.example.org:636';
  if(name==='baseDn')return 'DC=example,DC=org';
  if(name==='bindDn')return 'CN=svc-sibyl,OU=Service,DC=example,DC=org';
  return rule.description||'';
}
function setupForm(addon,info,initial){
  return new Promise((resolve,reject)=>{
    const dialog=el('dialog',undefined,'setup-dialog');
    dialog.setAttribute('aria-label','Konfiguration für '+addon.name);
    const title=el('h2',addon.name+' einrichten');
    dialog.append(title,el('p','Version '+(info.releaseTag||addon.tag||'')+' · Nur nicht geheime Werte. Änderungen werden im Sibyl Core gespeichert.','small muted'));
    const form=el('form',undefined,'setup-form');form.method='dialog';
    const rules=info.schema&&info.schema.properties||{};
    const required=new Set(info.required||[]);
    const controls=[];
    for(const [name,rule] of Object.entries(rules)){
      if(/(password|passwd|secret|token|privatekey|apikey)/i.test(name))continue;
      const block=el('div',undefined,'setup-field');
      const label=el('label',(rule.title||friendly(name))+(required.has(name)?' *':''),'');
      const type=rule.type||'string';
      const current=initial[name];
      let input;
      if(type==='boolean'){
        input=el('input');input.type='checkbox';input.checked=Boolean(current);
        label.prepend(input);
      }else if(Array.isArray(rule.enum)){
        input=el('select');
        for(const option of rule.enum){
          const item=el('option',String(option));item.value=String(option);input.append(item);
        }
        if(current!==undefined)input.value=String(current);
      }else if(type==='array'){
        input=el('textarea');input.rows=3;
        input.value=JSON.stringify(current??[],null,2);
      }else{
        input=el('input');input.type=type==='integer'?'number':'text';
        input.value=current===undefined?'':String(current);
        input.placeholder=example(name,rule);
        if(type==='integer'){
          if(rule.minimum!==undefined)input.min=String(rule.minimum);
          if(rule.maximum!==undefined)input.max=String(rule.maximum);
          input.step='1';
        }
        input.required=required.has(name);
        input.maxLength=rule.maxLength||2048;
      }
      if(!label.contains(input))label.append(input);
      if(input.id==='')input.id='setting-'+name;
      const help=el('div',rule.description||('JSON-Feld: '+name),'small muted');
      block.append(label,help);
      controls.push({name,type,input,required:required.has(name)});
      form.append(block);
    }
    const infoLine=el('p','','small');infoLine.setAttribute('role','status');
    const buttons=el('div',undefined,'actions');
    const cancel=el('button','Abbrechen','button secondary');cancel.type='button';
    const save=el('button','Einstellungen speichern','button');save.type='submit';
    buttons.append(cancel,save);form.append(infoLine,buttons);dialog.append(form);
    const close=()=>{dialog.close();dialog.remove();};
    cancel.addEventListener('click',()=>{close();reject(new Error('Einrichtung abgebrochen'));});
    dialog.addEventListener('cancel',event=>{event.preventDefault();close();reject(new Error('Einrichtung abgebrochen'));});
    form.addEventListener('submit',async event=>{
      event.preventDefault();
      const data={...initial};
      try{
        for(const control of controls){
          let value;
          if(control.type==='boolean')value=control.input.checked;
          else if(control.type==='integer'){
            if(!control.input.value){if(control.required)throw Error(control.name+' fehlt');delete data[control.name];continue;}
            value=Number(control.input.value);
            if(!Number.isSafeInteger(value))throw Error(control.name+' muss eine Ganzzahl sein');
          }else if(control.type==='array'){
            value=JSON.parse(control.input.value);
            if(!Array.isArray(value))throw Error(control.name+' muss ein JSON-Array sein');
          }else{
            value=control.input.value.trim();
            if(!value&&!control.required){delete data[control.name];continue;}
          }
          data[control.name]=value;
        }
        save.disabled=true;infoLine.textContent='Einstellungen werden gespeichert …';
        await saveSettings(addon.id,data);
        close();resolve(data);
      }catch(error){infoLine.textContent='Bitte prüfen: '+error.message;save.disabled=false;}
    });
    document.body.append(dialog);dialog.showModal();
  });
}
async function configureAddon(addon){
  if(busy)return;
  try{
    const info=await request('/api/v1/admin/addon-settings/'+encodeURIComponent(addon.id)+
      '?releaseTag='+encodeURIComponent(addon.tag||addon.version||''));
    if(!info.schemaAvailable)throw Error('Zuerst das Release-Paket herunterladen, um das Konfigurationsschema zu lesen.');
    await setupForm(addon,info,{...(info.defaults||{}),...(info.settings||{})});
    status('Einstellungen von '+addon.name+' gespeichert. Weitere benötigte Zugangsdaten müssen serverseitig hinterlegt werden.');
  }catch(error){if(!/abgebrochen/.test(error.message))status('Konfiguration: '+error.message,true);}
}
async function setupIfNeeded(addon,tag){
  const info=await request('/api/v1/admin/addon-settings/'+encodeURIComponent(addon.id)+'?releaseTag='+encodeURIComponent(tag));
  if(!info.schemaAvailable)throw new Error('Konfigurationsschema fehlt im Release – keine automatische Aktivierung.');
  const merged={...(info.defaults||{}),...(info.settings||{})};
  const missing=(info.required||[]).filter(x=>merged[x]===undefined||merged[x]===null||merged[x]==='');
  if(missing.length){
    status(addon.name+': Einrichtung erforderlich ('+missing.join(', ')+')');
    await setupForm(addon,info,merged);
  }else if(Object.keys(info.settings||{}).length===0&&Object.keys(merged).length>0){
    await saveSettings(addon.id,merged);
  }
}
async function installTree(addon,tag,chain=new Set()){
  if(installed(addon.id,tag.substring(1)))return;
  if(chain.has(addon.id))throw Error('Zyklische Abhängigkeit: '+addon.id);
  const next=new Set(chain);next.add(addon.id);
  const lookup=entries.find(a=>a.id===addon.id)||addon;
  if(lookup.status!=='downloaded'&&!installed(addon.id,tag.substring(1))){
    status(addon.name+': Release wird heruntergeladen …');
    await write('/api/v1/admin/addons/'+encodeURIComponent(addon.id)+'/download',{
      channel:channel.value,releaseTag:tag
    });
    lookup.status='downloaded';
  }
  const plan=await request('/api/v1/admin/extensions/'+encodeURIComponent(addon.id)+'/plan?'+
    new URLSearchParams({releaseTag:tag,channel:channel.value}));
  for(const dep of plan.dependencies||[]){
    if(installed(dep.id,dep.version))continue;
    const child=entries.find(x=>x.id===dep.id);
    if(!child||child.tag!=='v'+dep.version)
      throw Error('Benötigtes Addon '+dep.id+' '+dep.version+' fehlt im Katalog. Bitte zuerst passenden Release veröffentlichen.');
    await installTree(child,'v'+dep.version,next);
  }
  await setupIfNeeded(addon,tag);
  status(addon.name+': Verifizierte Installation wird ausgeführt …');
  await write('/api/v1/admin/extensions/'+encodeURIComponent(addon.id)+'/install',{
    channel:channel.value,releaseTag:tag
  });
  activeModules.push({id:addon.id,version:tag.substring(1)});
}
async function start(addon){
  if(busy||!secure||!addon.tag)return;
  const action=addon.status==='downloaded'?'installieren und aktivieren':'herunterladen, installieren und aktivieren';
  if(!confirm(addon.name+' '+addon.tag+' '+action+'?\nAbhängigkeiten werden bei Bedarf ebenfalls eingerichtet. Der Core prüft Paket-Hash, Manifest und erlaubte Installationsoperationen.'))return;
  busy=true;render();
  try{
    await installTree(addon,addon.tag);
    status(addon.name+' erfolgreich registriert und aktiviert. Konfigurierte externe Dienste und Datenquellen müssen anschließend separat geprüft werden.');
    await load();
    status(addon.name+' ist installiert. Die Funktionen erscheinen nach dem Neuladen der Startseite.');
  }catch(error){
    status('Installation nicht abgeschlossen: '+error.message+'. Heruntergeladene Pakete bleiben erhalten, bitte Konfiguration und Abhängigkeiten prüfen.',true);
  }finally{busy=false;render();}
}
search.addEventListener('input',render);
channel.addEventListener('change',load);
refresh.addEventListener('click',load);
if(!secure)status('HTTPS ist erforderlich.',true);
else load();
