import {useCallback,useEffect,useMemo,useState} from 'react';
import type {Credentials} from '../types/domain';
import {api,request} from '../services/api';

type Profile={
 username:string;trainee:boolean;trainer:boolean;
 learningYear:number|null;groups:string[];assignedTrainers:string[];
};
type BulkResult={matched:number;assignmentsAdded:number;alreadyAssignedOrSelf:number};
const base='/api/v1/addons/Sibyl.report-book/v2';
const admin=base+'/admin';
const err=(e:unknown)=>e instanceof Error?e.message:'Aktion fehlgeschlagen';
const yearOptions=[1,2,3,4];
const lower=(s:string)=>s.toLocaleLowerCase('de-DE').trim();

export default function ReportBookAdminSettings({credentials,onConfigureAd,groupsReady}:{
 credentials:Credentials;onConfigureAd:()=>void;groupsReady:boolean
}){
 const [roster,setRoster]=useState<Profile[]>([]);
 const [adReady,setAdReady]=useState<boolean|null>(null);
 const [adCount,setAdCount]=useState(0);
 const [search,setSearch]=useState('');
 const [filterYear,setFilterYear]=useState('');
 const [filterGroup,setFilterGroup]=useState('');
 const [username,setUsername]=useState('');
 const [trainee,setTrainee]=useState(true);
 const [trainer,setTrainer]=useState(false);
 const [bulkYear,setBulkYear]=useState('');
 const [bulkGroup,setBulkGroup]=useState('');
 const [bulkTrainers,setBulkTrainers]=useState<string[]>([]);
 const [bulkSetYear,setBulkSetYear]=useState('');
 const [busy,setBusy]=useState(false);
 const [error,setError]=useState('');
 const [notice,setNotice]=useState('');
 const refresh=useCallback(async()=>{
  const [profiles,dirs]=await Promise.all([
   request<Profile[]>(admin+'/roster-v3',credentials),
   api.adConnections(credentials)
  ]);
  setRoster(profiles.filter(p=>p.trainee||p.trainer));
  setAdCount(dirs.length);
  setAdReady(dirs.some(d=>d.enabled&&d.hasSecret&&Boolean(d.servicePrincipal?.trim())));
 },[credentials]);
 useEffect(()=>{void refresh().catch(e=>setError(err(e)))},[refresh]);
 const trainers=useMemo(()=>roster.filter(p=>p.trainer),[roster]);
 const groups=useMemo(()=>[...new Set(roster.flatMap(p=>p.trainee?p.groups:[]))]
   .sort((a,b)=>a.localeCompare(b,'de')),[roster]);
 const filtered=useMemo(()=>{
  const q=lower(search);
  return roster.filter(p=>
   (!q||[p.username,...p.groups,p.trainee?'azubi':'',p.trainer?'ausbilder':'',
     p.learningYear?String(p.learningYear)+'. lehrjahr':''].some(x=>lower(x).includes(q)))
   &&(!filterYear||(p.trainee&&p.learningYear===Number(filterYear)))
   &&(!filterGroup||(p.trainee&&p.groups.includes(filterGroup))));
 },[roster,search,filterYear,filterGroup]);
 const bulkMatches=useMemo(()=>roster.filter(p=>p.trainee
  &&(bulkYear!==''||bulkGroup!=='')
  &&(!bulkYear||p.learningYear===Number(bulkYear))
  &&(!bulkGroup||p.groups.includes(bulkGroup))),[roster,bulkYear,bulkGroup]);
 async function run(fn:()=>Promise<unknown>,success:(result:unknown)=>string){
  setBusy(true);setError('');setNotice('');
  try{
   const value=await fn();
   await refresh();
   setNotice(success(value));
   return true;
  }catch(e){
   setError(err(e));
   return false;
  }finally{setBusy(false)}
 }
 async function saveProfile(e:React.FormEvent){
  e.preventDefault();
  const ok=await run(()=>request(admin+'/roster/'+encodeURIComponent(username.trim()),credentials,{
   method:'PUT',body:JSON.stringify({trainee,trainer,active:true})}),
   ()=>'Konto gespeichert.');
  if(ok)setUsername('');
 }
 async function changeYear(p:Profile,value:string){
  await run(()=>request(admin+'/trainees/'+encodeURIComponent(p.username)+'/year',credentials,
   {method:'PUT',body:JSON.stringify({year:value===''?null:Number(value)})}),
   ()=>'Lehrjahr aktualisiert.');
 }
 async function addTrainer(p:Profile,trainerName:string){
  if(!trainerName)return;
  await run(()=>request(admin+'/assignments/'+encodeURIComponent(p.username)+'/'+
   encodeURIComponent(trainerName),credentials,{method:'PUT'}),
   ()=>'Ausbilder hinzugefügt.');
 }
 async function removeTrainer(p:Profile,trainerName:string){
  await run(()=>request(admin+'/assignments/'+encodeURIComponent(p.username)+'/'+
   encodeURIComponent(trainerName),credentials,{method:'DELETE'}),
   ()=>'Ausbilderzuordnung entfernt.');
 }
 async function assignBulk(){
  await run(()=>request<BulkResult>(admin+'/assignments/bulk',credentials,{
   method:'POST',body:JSON.stringify({
    year:bulkYear?Number(bulkYear):null,group:bulkGroup||null,trainers:bulkTrainers
   })
  }),result=>{
   const data=result as BulkResult;
   return `${data.assignmentsAdded} neue Zuordnungen für ${data.matched} Azubis gespeichert.`;
  });
 }
 async function setYearForGroup(){
  await run(()=>request<number>(admin+'/trainees/bulk-year',credentials,{
    method:'POST',body:JSON.stringify({group:bulkGroup,year:Number(bulkSetYear)})
  }),result=>`Lehrjahr bei ${result as number} Azubi-Konten aktualisiert.`);
 }
 const toggleBulkTrainer=(name:string,checked:boolean)=>{
  setBulkTrainers(previous=>checked?
   [...previous.filter(x=>x!==name),name]:previous.filter(x=>x!==name));
 };

 return <div className="addon-settings-form">
  <h3>Berichtsheft: Benutzer &amp; Ausbilder</h3>
  <p>Hier verwaltest du die Lehrjahre und weist einem Azubi einen oder mehrere Ausbilder zu.
   Gruppen stammen aus der AD-Synchronisierung; inaktive Konten werden nicht angezeigt.</p>
  {!groupsReady&&<div className="error" role="alert">
   Zuerst die Azubi- und Ausbildergruppen oben konfigurieren und Einstellungen speichern.
  </div>}
  {adReady===false&&<div className="error" role="alert">
   {adCount===0?'Keine AD-Verbindung eingerichtet.':'Keine aktive AD-Verbindung mit gespeichertem Servicekonto vorhanden.'}
   <button type="button" className="secondary" onClick={onConfigureAd}>Active Directory konfigurieren</button>
  </div>}
  {error&&<div className="error" role="alert">{error}</div>}
  {notice&&<div className="success-note" role="status">{notice}</div>}
  <div className="sibyl-inline-fields">
   <button type="button" className="secondary" disabled={busy||!groupsReady||adReady!==true}
    onClick={()=>void run(()=>request(base+'/admin/sync',credentials,{method:'POST'}),
      ()=>'AD-Konten und Gruppenzugehörigkeiten erfolgreich synchronisiert.')}>
      Azubis und Ausbilder aus AD laden
   </button>
   <button type="button" className="secondary" disabled={busy}
    onClick={()=>void refresh().catch(e=>setError(err(e)))}>Liste aktualisieren</button>
  </div>
  <form className="sibyl-report-profile-form" onSubmit={e=>void saveProfile(e)}>
   <label>Einzelnes Konto ergänzen oder bearbeiten
    <input required maxLength={100} value={username} onChange={e=>setUsername(e.target.value)}
     placeholder="AD- oder Sibyl-Benutzername"/></label>
   <label className="rb-inline-check"><input type="checkbox" checked={trainee}
    onChange={e=>setTrainee(e.target.checked)}/> Azubi</label>
   <label className="rb-inline-check"><input type="checkbox" checked={trainer}
    onChange={e=>setTrainer(e.target.checked)}/> Ausbilder</label>
   <button type="submit" className="primary" disabled={busy||!username.trim()||(!trainee&&!trainer)}>
    Konto speichern</button>
  </form>

  <section className="rb-bulk-panel" aria-labelledby="rb-bulk-title">
   <h4 id="rb-bulk-title">Mehrere Azubis zuweisen</h4>
   <p>Wähle ein Lehrjahr, eine Gruppe oder beides. Neue Ausbilder werden hinzugefügt;
    bestehende Zuordnungen bleiben erhalten.</p>
   <div className="rb-bulk-fields">
    <label>Lehrjahr
     <select aria-label="Lehrjahr für Sammelzuweisung" value={bulkYear} onChange={e=>setBulkYear(e.target.value)}>
      <option value="">Alle Lehrjahre</option>
      {yearOptions.map(y=><option key={y} value={y}>{y}. LJ</option>)}
     </select>
    </label>
    <label>Gruppe
     <select aria-label="Gruppe für Sammelzuweisung" value={bulkGroup} onChange={e=>setBulkGroup(e.target.value)}>
      <option value="">Alle Gruppen</option>
      {groups.map(group=><option key={group} value={group}>{group}</option>)}
     </select>
    </label>
   </div>
   <div className="rb-bulk-trainers">
    <strong>Ausbilder auswählen (mehrere möglich)</strong>
    <div className="rb-trainer-checkboxes">
     {trainers.map(t=><label className="rb-inline-check" key={t.username}>
      <input type="checkbox" disabled={busy} checked={bulkTrainers.includes(t.username)}
       onChange={e=>toggleBulkTrainer(t.username,e.target.checked)}/>
      {t.username}
     </label>)}
    </div>
    {trainers.length===0&&<p>Es sind noch keine aktiven Ausbilder vorhanden.</p>}
   </div>
   <p role="status" className="rb-match-count">{bulkYear||bulkGroup
    ?`${bulkMatches.length} Azubi-Konten entsprechen der Auswahl.`
    :'Bitte zuerst ein Lehrjahr oder eine Gruppe auswählen.'}</p>
   <button type="button" className="primary" disabled={busy||bulkTrainers.length===0||bulkMatches.length===0}
    onClick={()=>void assignBulk()}>Ausbilder gesammelt zuweisen</button>
   <div className="rb-bulk-year">
    <label>Lehrjahr für gesamte ausgewählte Gruppe setzen
     <select value={bulkSetYear} onChange={e=>setBulkSetYear(e.target.value)}>
      <option value="">Lehrjahr wählen</option>
      {yearOptions.map(y=><option key={y} value={y}>{y}. LJ</option>)}
     </select>
    </label>
    <button type="button" className="secondary" disabled={busy||!bulkGroup||!bulkSetYear}
     onClick={()=>void setYearForGroup()}>Lehrjahr für Gruppe speichern</button>
   </div>
  </section>

  <div className="rb-roster-filters">
   <label>Konten durchsuchen
    <input value={search} onChange={e=>setSearch(e.target.value)} placeholder="Benutzername oder Gruppe filtern"/>
   </label>
   <label>Nach Lehrjahr filtern
    <select value={filterYear} onChange={e=>setFilterYear(e.target.value)}>
     <option value="">Alle Lehrjahre</option>
     {yearOptions.map(y=><option key={y} value={y}>{y}. LJ</option>)}
    </select>
   </label>
   <label>Nach Gruppe filtern
    <select value={filterGroup} onChange={e=>setFilterGroup(e.target.value)}>
     <option value="">Alle Gruppen</option>
     {groups.map(g=><option key={g} value={g}>{g}</option>)}
    </select>
   </label>
  </div>
  <p className="rb-match-count" role="status">{filtered.length} von {roster.length} aktiven Konten angezeigt.</p>
  <div className="table-panel rb-roster-table">
   <table>
    <thead><tr><th>Benutzername</th><th>Azubi</th><th>Ausbilder</th><th>LJ</th><th>Gruppe</th>
     <th>Zugewiesene Ausbilder</th></tr></thead>
    <tbody>{filtered.map(p=><tr key={p.username}>
     <td>{p.username}</td>
     <td>{p.trainee?'Ja':'—'}</td>
     <td>{p.trainer?'Ja':'—'}</td>
     <td>{p.trainee?
      <select aria-label={'Lehrjahr für '+p.username} disabled={busy}
       value={p.learningYear??''} onChange={e=>void changeYear(p,e.target.value)}>
       <option value="">—</option>
       {yearOptions.map(y=><option key={y} value={y}>{y}. LJ</option>)}
      </select>:'—'}</td>
     <td>{p.trainee?(p.groups.length?p.groups.join(', '):'—'):'—'}</td>
     <td>{p.trainee?<div className="rb-trainer-cell">
      <div className="rb-trainer-tags">
       {p.assignedTrainers.map(name=><button type="button" disabled={busy}
        aria-label={`Ausbilder ${name} von ${p.username} entfernen`} title="Zuordnung entfernen"
        className="rb-trainer-tag" key={name} onClick={()=>void removeTrainer(p,name)}>
        {name} <span aria-hidden="true">×</span></button>)}
      </div>
      <select disabled={busy} aria-label={'Ausbilder für '+p.username+' hinzufügen'} value=""
       onChange={e=>void addTrainer(p,e.target.value)}>
       <option value="">+ Ausbilder hinzufügen</option>
       {trainers.filter(t=>t.username!==p.username&&!p.assignedTrainers.includes(t.username))
        .map(t=><option key={t.username} value={t.username}>{t.username}</option>)}
      </select>
     </div>:'—'}</td>
    </tr>)}</tbody>
   </table>
   {!filtered.length&&<p>Keine passenden aktiven Konten gefunden. Bitte Filter anpassen oder AD synchronisieren.</p>}
  </div>
 </div>;
}
