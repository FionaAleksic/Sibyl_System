'use strict';
const id=new URLSearchParams(location.search).get('id');
const content=document.querySelector('#module-content');
const status=document.querySelector('#module-status');
function node(tag,text){const n=document.createElement(tag); if(text!==undefined)n.textContent=String(text);return n;}
function setStatus(s){status.textContent=s;}
function field(label,type='text'){const div=node('div');div.className='field';const l=node('label',label);const f=node(type==='textarea'?'textarea':'input');if(type!=='textarea')f.type=type;l.append(f);div.append(l);return {div,f};}
async function api(path,options={}){
 const csrf=options.method&&options.method!=='GET'?
   await fetch('/api/v1/auth/csrf',{credentials:'same-origin'}).then(r=>r.json()):null;
 const response=await fetch(path,{credentials:'same-origin',cache:'no-store',
   ...options,headers:{...options.headers,...(csrf?{[csrf.header]:csrf.token}:{})}});
 if(!response.ok)throw Error('HTTP '+response.status);
 return response.json();
}
function fail(error){setStatus('Fehler: '+error.message);}
async function init(){
 const listing=await api('/api/v1/modules');
 const module=listing.modules.find(m=>m.id===id && m.type==='feature');
 if(!module){setStatus('Dieses Modul wurde noch nicht installiert oder der Release ist nicht freigegeben.');return;}
 document.title=module.name+' · Sibyl';
 document.querySelector('#module-title').textContent=module.name;
 document.querySelector('#module-desc').textContent=module.id+' · v'+module.version;
 setStatus('Freigegebene lokale Modul-Schnittstelle verfügbar. Weitere Originalfunktionen folgen schrittweise.');
 if(id==='Sibyl.inventory') inventory();
 else if(id==='Sibyl.planning') planning();
 else if(id==='Sibyl.doc') wiki();
 else if(id==='Sibyl.report-book') content.append(node('p','Das Berichtsheft hat noch keinen aktivierten Datenbank-/UI-Adapter.'));
}
function inventory(){
 content.append(node('h2','IPv4-Adressübersicht'));
 content.append(node('p','Alle nutzbaren Adressen eines Teilnetzes anzeigen. DHCP/Reservierungen optional als JSON eintragen; kein aktiver Netzwerkscan.'));
 const subnet=field('IPv4-Netzwerk (z. B. 192.168.10.0/24)');subnet.f.value='192.168.10.0/29';
 const leases=field('DHCP-Leases als JSON (IP → Gerätename)','textarea');leases.f.value='{}';
 const reserved=field('Reservierte IPs (kommagetrennt)');reserved.f.value='';
 const btn=node('button','Adressen anzeigen');btn.className='button';btn.type='button';
 const summary=node('p');const result=node('div');result.style.maxHeight='420px';result.style.overflow='auto';
 content.append(subnet.div,leases.div,reserved.div,btn,summary,result);
 btn.onclick=async()=>{try{
  const response=await api('/api/v1/modules/inventory/ipam',{method:'POST',headers:{'Content-Type':'application/json'},
   body:JSON.stringify({cidr:subnet.f.value.trim(),leases:JSON.parse(leases.f.value),
    reserved:reserved.f.value.split(',').map(x=>x.trim()).filter(Boolean)})});
  summary.textContent=response.addresses.length+' nutzbare Adressen';
  const table=node('table');const head=node('tr');['Adresse','Status','Gerät'].forEach(x=>head.append(node('th',x)));table.append(head);
  for(const a of response.addresses){const tr=node('tr');[a.ipv4,a.state,a.owner].forEach(x=>tr.append(node('td',x)));table.append(tr);}
  result.replaceChildren(table);setStatus(response.notice);
 }catch(e){fail(e);}};
}
function planning(){
 content.append(node('h2','Faire Slotrotation – Vorschau'));
 content.append(node('p','Alle drei Slotarten besitzen eigene Auslastungszähler. Jede Person erhält pro Runde höchstens einen Slot.'));
 const people=field('Personen (eine pro Zeile)','textarea');people.f.value='Alex\nKim\nSam\nTaylor';
 const turns=field('Anzahl der Runden','number');turns.f.value='8';turns.f.min='1';turns.f.max='100';
 const btn=node('button','Plan berechnen');btn.className='button';btn.type='button';const result=node('div');result.style.overflow='auto';
 content.append(people.div,turns.div,btn,result);
 btn.onclick=async()=>{try{
  const response=await api('/api/v1/modules/planning/preview',{method:'POST',
   headers:{'Content-Type':'application/json'},
   body:JSON.stringify({participants:people.f.value.split(/\r?\n/).map(x=>x.trim()).filter(Boolean),
    turns:Number(turns.f.value)})});
  const table=node('table');const head=node('tr');['Runde','Primär','Sekundär','Weiterer'].forEach(x=>head.append(node('th',x)));table.append(head);
  response.assignments.forEach((a,i)=>{const tr=node('tr');[i+1,a.primary,a.secondary,a.tertiary].forEach(x=>tr.append(node('td',x)));table.append(tr);});
  result.replaceChildren(table);setStatus(response.notice);
 }catch(e){fail(e);}};
}
function wiki(){
 content.append(node('h2','Dokumentation'));
 content.append(node('p','Markdown-Dokumente in MySQL speichern und bearbeiten. Änderungen verwenden Versionskontrolle.'));
 const list=node('div'); const form=node('form');
 const title=field('Titel');title.f.maxLength=240;title.f.required=true;
 const body=field('Inhalt (Markdown)','textarea');body.f.maxLength=100000;body.f.rows=9;
 const save=node('button','Dokument speichern');save.className='button';save.type='submit';
 let selected=null,version=null;
 form.append(title.div,body.div,save);content.append(list,node('h3','Dokument bearbeiten'),form);
 const reload=async()=>{const pages=await api('/api/v1/modules/wiki/pages');list.replaceChildren();
  for(const p of pages){const b=node('button',p.title+' · '+p.owner+' · v'+p.version);b.className='button secondary';b.style.margin='3px';b.type='button';
   b.onclick=()=>{selected=p.id;version=p.version;title.f.value=p.title;body.f.value=p.body;save.textContent='Änderung speichern';};
   list.append(b);}
  const fresh=node('button','Neues Dokument');fresh.className='button secondary';fresh.type='button';
  fresh.onclick=()=>{selected=null;version=null;title.f.value='';body.f.value='';save.textContent='Dokument speichern';};list.append(fresh);
 };
 form.onsubmit=async e=>{e.preventDefault();try{
  const path=selected?'/api/v1/modules/wiki/pages/'+selected:'/api/v1/modules/wiki/pages';
  await api(path,{method:selected?'PUT':'POST',headers:{'Content-Type':'application/json'},
    body:JSON.stringify({title:title.f.value,body:body.f.value,version})});
  selected=null;version=null;form.reset();save.textContent='Dokument speichern';
  await reload();setStatus('Dokument in MySQL gespeichert.');
 }catch(error){fail(error);}};
 reload().catch(fail);
}
init().catch(fail);
