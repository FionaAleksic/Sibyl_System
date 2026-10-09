/* Addon Browser: read-only on static first-run frontend.
 * No GitHub secrets, branch downloads, or unverified code execution in browser.
 * Backend release installation requires a future authenticated HTTPS Core API.
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
let mode='snapshot';

function text(tag,value,css=''){
  const el=document.createElement(tag);
  el.textContent=String(value??'');
  if(css)el.className=css;
  return el;
}
function render(){
  const q=search.value.trim().toLocaleLowerCase('de');
  const selected=channel.value;
  const list=catalog.filter(a=>(a.name+' '+a.id+' '+a.summary).toLocaleLowerCase('de').includes(q));
  body.replaceChildren();
  counter.textContent=list.length+' Addons';
  for(const addon of list){
    const versions=Array.isArray(addon.releases)?addon.releases:[];
    const releases=versions.filter(r=>r&&!r.draft&&(selected==='stable'?!r.prerelease:!!r.prerelease));
    const newest=releases[0]||null;
    const card=text('article',undefined,'card');
    const head=text('div',undefined,'card-header');
    head.append(text('div',addon.icon||'MOD','icon-tile'));
    const title=text('div');
    title.append(text('h3',addon.name),text('div',addon.id,'id'));
    head.append(title);
    card.append(head,text('p',addon.summary||'Erweiterung für Sibyl System.','desc'));
    const chips=text('div',undefined,'chips');
    chips.append(text('span',addon.kind||'Addon','chip'));
    chips.append(text('span',newest?newest.tag_name:'Kein Release','chip '+(newest?'ready':'missing')));
    card.append(chips);
    const foot=text('div',undefined,'card-footer');
    const repo=document.createElement('a');
    repo.href='https://github.com/'+addon.repo;
    repo.textContent='Repository ansehen ↗';
    repo.target='_blank';
    repo.rel='noopener noreferrer';
    repo.className='small';
    foot.append(repo);
    const btn=text('button','Installieren','button');
    btn.type='button';
    btn.disabled=true;
    btn.title=newest?'Core-Releaseverwaltung ist noch nicht aktiv':'Für dieses Addon ist kein Release veröffentlicht';
    foot.append(btn);
    card.append(foot);
    body.append(card);
  }
  if(!list.length)body.append(text('div','Keine Addons gefunden. Versuchen Sie einen anderen Suchbegriff.','empty'));
  statusText.textContent=(mode==='snapshot'?'Letzte geprüfte GitHub-Releases: ':'Live-Katalog: ')+
    (sourceDate?new Date(sourceDate).toLocaleString('de-DE'):'–')+
    ' · Quelle: '+(mode==='snapshot'?'Momentaufnahme':'Sibyl Core API')+
    '. Private Repositories benötigen für spätere Downloads eine serverseitige GitHub-Berechtigung.';
}
async function load(){
  statusText.textContent='Addon-Katalog wird geladen …';
  refresh.disabled=true;
  try{
    // Once a protected Core API is deployed this can report live metadata.
    // The standalone HTTP staging version has no authenticated Core.
    const res=await fetch('./catalog.json',{cache:'no-store',credentials:'same-origin'});
    if(!res.ok)throw new Error('HTTP '+res.status);
    const value=await res.json();
    if(value.schema!==1||value.releaseOnly!==true||!Array.isArray(value.addons))
      throw new Error('Nicht unterstütztes Katalogformat');
    catalog=value.addons.filter(a=>typeof a.id==='string'&&typeof a.repo==='string'&&
      /^[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+$/.test(a.repo));
    sourceDate=value.snapshotUtc;
    mode='snapshot';
    notice.querySelector('strong').textContent='Addon-Katalog – noch keine installierbaren Releases';
    notice.querySelector('p').textContent='Die Repositories wurden geprüft, aber aktuell sind keine Releases veröffentlicht. Der Browser zeigt den nachprüfbaren Katalog; Download und Installation bleiben bis zur sicheren Core-Anbindung deaktiviert.';
    render();
  }catch(e){
    catalog=[];
    body.replaceChildren(text('p','Katalog konnte nicht geladen werden: '+e.message,'empty'));
    statusText.textContent='Bitte später erneut versuchen.';
  }finally{refresh.disabled=false;}
}
search.addEventListener('input',render);
channel.addEventListener('change',render);
refresh.addEventListener('click',load);
load();
