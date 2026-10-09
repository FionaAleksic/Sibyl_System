/* Sibyl add-on catalog: source discovery != released, installable version.
 * This static first-run UI cannot download or execute add-on code.
 * Private GitHub repository access is delegated to the future secure Core backend.
 */
'use strict';
const body=document.querySelector('#addons');
const notice=document.querySelector('#catalog-notice');
const statusText=document.querySelector('#catalog-status');
const counter=document.querySelector('#count');
const search=document.querySelector('#addon-search');
const channel=document.querySelector('#release-channel');
const refresh=document.querySelector('#refresh');
let catalog=[];
let sourceDate='';

function element(tag,value,css=''){
  const el=document.createElement(tag);
  if(value!==undefined)el.textContent=String(value??'');
  if(css)el.className=css;
  return el;
}
function render(){
  const q=search.value.trim().toLocaleLowerCase('de');
  const selected=channel.value;
  // Addons must remain discoverable even when no release exists for the selected channel.
  const list=catalog.filter(a=>
    [a.name,a.id,a.summary,a.repo,a.kind,...(Array.isArray(a.searchTerms)?a.searchTerms:[])]
      .join(' ').toLocaleLowerCase('de').includes(q)
  );
  body.replaceChildren();
  counter.textContent=list.length+' von '+catalog.length+' Addons';
  for(const addon of list){
    const all=Array.isArray(addon.releases)?addon.releases:[];
    const released=all.filter(r=>r&&!r.draft&&(selected==='stable'?!r.prerelease:!!r.prerelease));
    const latest=released[0]||null;
    const card=element('article',undefined,'card');
    if(addon.id==='Sibyl.ad')card.id='sibyl-ad';
    const head=element('div',undefined,'card-header');
    head.append(element('div',addon.icon||'MOD','icon-tile'));
    const title=element('div');
    title.append(element('h3',addon.name),element('div',addon.id,'id'));
    head.append(title);
    card.append(head,element('p',addon.summary||'Erweiterung für Sibyl System.','desc'));
    const chips=element('div',undefined,'chips');
    chips.append(element('span',addon.kind||'Addon','chip'));
    chips.append(element('span',latest?('Auf GitHub veröffentlicht: '+latest.tag_name):
      'Kein GitHub-Release','chip '+(latest?'ready':'missing')));
    card.append(chips);
    if(!latest){
      const info=element('p','Dieses Repository ist bekannt, aber für den gewählten Kanal ist auf GitHub noch kein Release veröffentlicht.','small muted');
      info.style.margin='0';
      card.append(info);
    }
    const foot=element('div',undefined,'card-footer');
    const repo=document.createElement('a');
    repo.href='https://github.com/'+addon.repo;
    repo.textContent='Repository ↗';
    repo.target='_blank';
    repo.rel='noopener noreferrer';
    repo.className='small';
    foot.append(repo);
    const button=element('button','Noch nicht installierbar','button');
    button.type='button';
    button.disabled=true;
    button.title=latest?'Für die Installation des veröffentlichten GitHub-Releases fehlt noch die geschützte Sibyl-Core-Runtime':
      'Für diesen Kanal wurde noch kein GitHub-Release veröffentlicht';
    foot.append(button);
    card.append(foot);
    body.append(card);
  }
  if(!list.length){
    body.append(element('div','Kein passendes Addon im Katalog. Suche nach „AD“, „Sibyl.ad“ oder „Active Directory“.','empty'));
  }
  const ad=catalog.find(a=>a.id==='Sibyl.ad');
  if(ad){
    statusText.textContent='Sibyl.ad: Repository im Katalog gefunden, aktuell kein auf GitHub veröffentlichter Release.'+
      ' · GitHub-Status zuletzt geprüft: '+(sourceDate?new Date(sourceDate).toLocaleString('de-DE'):'–')+
      ' · Dies ist ein datierter Snapshot, keine Live-Abfrage.';
  }else{
    statusText.textContent='Der Katalog enthält aktuell keinen Eintrag für Sibyl.ad.';
  }
}
async function load(){
  statusText.textContent='Addon-Katalog wird geladen …';
  refresh.disabled=true;
  try{
    const res=await fetch('./catalog.json',{cache:'no-store',credentials:'same-origin'});
    if(!res.ok)throw new Error('HTTP '+res.status);
    const payload=await res.json();
    if(payload.schema!==1||payload.releaseOnly!==true||!Array.isArray(payload.addons))
      throw new Error('Nicht unterstütztes Katalogformat');
    catalog=payload.addons.filter(a=>typeof a.id==='string'&&typeof a.name==='string'&&
      typeof a.repo==='string'&&/^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(a.repo));
    sourceDate=payload.snapshotUtc;
    notice.querySelector('strong').textContent='Quellen vorhanden – Installation noch nicht freigeschaltet';
    notice.querySelector('p').textContent='Addons werden auf dieser Sibyl-Installation ausschließlich aus echten GitHub-Releases oder Pre-Releases heruntergeladen. Für Sibyl.ad gibt es auf GitHub derzeit noch keinen Release. Der Addon-Installer wird nach Einrichtung des sicheren Sibyl Core aktiviert.';
    render();
  }catch(error){
    catalog=[];
    counter.textContent='0 Addons';
    body.replaceChildren(element('p','Addon-Katalog konnte nicht geladen werden: '+error.message,'empty'));
    statusText.textContent='Bitte die Verbindung prüfen und erneut aktualisieren.';
  }finally{
    refresh.disabled=false;
  }
}
search.addEventListener('input',render);
channel.addEventListener('change',render);
refresh.addEventListener('click',load);
load();
