import {useCallback,useEffect,useMemo,useState} from 'react';
import type {Credentials} from '../types/domain';
import {api,request} from '../services/api';

type Profile={username:string;trainee:boolean;trainer:boolean;active:boolean;source:string;assignedTrainer:string|null};
const base='/api/v1/addons/Sibyl.report-book/v2';
const err=(e:unknown)=>e instanceof Error?e.message:'Aktion fehlgeschlagen';

export default function ReportBookAdminSettings({credentials,onConfigureAd,groupsReady}:{credentials:Credentials;onConfigureAd:()=>void;groupsReady:boolean}){
 const [roster,setRoster]=useState<Profile[]>([]);
 const [adReady,setAdReady]=useState<boolean|null>(null);
 const [adCount,setAdCount]=useState(0);
 const [search,setSearch]=useState('');
 const [username,setUsername]=useState('');
 const [trainee,setTrainee]=useState(true);
 const [trainer,setTrainer]=useState(false);
 const [active,setActive]=useState(true);
 const [busy,setBusy]=useState(false);
 const [error,setError]=useState('');
 const [notice,setNotice]=useState('');
 const refresh=useCallback(async()=>{
  const [profiles,dirs]=await Promise.all([
   request<Profile[]>(base+'/admin/roster',credentials),
   api.adConnections(credentials)
  ]);
  setRoster(profiles);
  setAdCount(dirs.length);
  setAdReady(dirs.some(d=>d.enabled&&d.hasSecret&&Boolean(d.servicePrincipal?.trim())));
 },[credentials]);
 useEffect(()=>{void refresh().catch(e=>setError(err(e)))},[refresh]);
 const trainers=useMemo(()=>roster.filter(x=>x.trainer&&x.active),[roster]);
 const filtered=useMemo(()=>roster.filter(x=>x.username.toLowerCase().includes(search.toLowerCase())),[roster,search]);
 async function run(fn:()=>Promise<unknown>,done:string){
  setBusy(true);setError('');setNotice('');
  try{await fn();await refresh();setNotice(done)}
  catch(e){setError(err(e))}finally{setBusy(false)}
 }
 async function saveProfile(e:React.FormEvent){
  e.preventDefault();
  await run(()=>request(base+'/admin/roster/'+encodeURIComponent(username.trim()),credentials,{
   method:'PUT',body:JSON.stringify({trainee,trainer,active})}), 'Konto gespeichert.');
  setUsername('');
 }
 return <div className="addon-settings-form">
  <h3>Berichtsheft: Benutzer &amp; Ausbilder</h3>
  <p>Azubis und Ausbilder werden anhand der oben konfigurierten AD-Gruppen synchronisiert. Danach kannst du jeden Azubi einem Ausbilder zuordnen.</p>
  {!groupsReady&&<div className="error" role="alert">
   Zuerst die Azubi- und Ausbildergruppen oben eintragen und auf „Einstellungen speichern“ klicken.
  </div>}
  {adReady===false&&<div className="error" role="alert">
   {adCount===0
    ?'Keine AD-Verbindung eingerichtet. Die automatische Anmeldung per AD reicht für die Synchronisierung nicht aus.'
    :'Keine aktive AD-Verbindung mit hinterlegtem Servicekonto und Passwort vorhanden.'}
   {' '}Öffne das Addon „Active Directory“, richte dort eine aktive Verbindung mit einem leseberechtigten Servicekonto ein und teste sie.
   <div className="sibyl-inline-fields">
    <button type="button" className="secondary" onClick={onConfigureAd}>Active Directory konfigurieren</button>
   </div>
  </div>}
  {error&&<div className="error" role="alert">{error}</div>}
  {notice&&<div className="success-note" role="status">{notice}</div>}
  <div className="sibyl-inline-fields">
   <button type="button" className="secondary" disabled={busy||!groupsReady||adReady!==true}
    title={adReady===false?'AD-Verbindung mit Servicekonto konfigurieren':!groupsReady?'AD-Gruppen speichern':undefined}
    onClick={()=>void run(()=>request(base+'/admin/sync',credentials,{method:'POST'}),
     'AD-Konten erfolgreich synchronisiert.')}>Azubis und Ausbilder aus AD laden</button>
   <button type="button" className="secondary" disabled={busy}
    onClick={()=>void refresh().catch(e=>setError(err(e)))}>Liste aktualisieren</button>
  </div>
  <form className="sibyl-report-profile-form" onSubmit={e=>void saveProfile(e)}>
   <label>Einzelnes Konto ergänzen oder bearbeiten<input required maxLength={100}
    value={username} onChange={e=>setUsername(e.target.value)} placeholder="AD- oder Sibyl-Benutzername"/></label>
   <label><input type="checkbox" checked={trainee} onChange={e=>setTrainee(e.target.checked)}/> Azubi</label>
   <label><input type="checkbox" checked={trainer} onChange={e=>setTrainer(e.target.checked)}/> Ausbilder</label>
   <label><input type="checkbox" checked={active} onChange={e=>setActive(e.target.checked)}/> Aktiv</label>
   <button type="submit" className="primary" disabled={busy||!username.trim()}>Konto speichern</button>
  </form>
  <label>Konten durchsuchen<input value={search} onChange={e=>setSearch(e.target.value)}
    placeholder="Benutzernamen filtern"/></label>
  <div className="table-panel">
   <table><thead><tr><th>Benutzername</th><th>Azubi</th><th>Ausbilder</th><th>Quelle</th><th>Zugewiesene/r Ausbilder/in</th></tr></thead>
    <tbody>{filtered.map(p=><tr key={p.username}>
     <td>{p.username}{!p.active?' (inaktiv)':''}</td>
     <td>{p.trainee?'Ja':'–'}</td><td>{p.trainer?'Ja':'–'}</td><td>{p.source}</td>
     <td>{p.trainee?<select disabled={busy} value={p.assignedTrainer??''}
      aria-label={'Ausbilder für '+p.username} onChange={e=>void run(()=>
       request(base+'/admin/assignment/'+encodeURIComponent(p.username),credentials,
        e.target.value?{method:'PUT',body:JSON.stringify({trainerUsername:e.target.value})}:{method:'DELETE'}),
       'Ausbilderzuordnung aktualisiert.')}>
      <option value="">Nicht zugewiesen</option>
      {trainers.filter(t=>t.username!==p.username).map(t=><option value={t.username} key={t.username}>{t.username}</option>)}
     </select>:'–'}</td>
    </tr>)}</tbody>
   </table>
   {!filtered.length&&<p>Keine Konten gefunden. Bitte Gruppen konfigurieren und die AD-Synchronisierung ausführen.</p>}
  </div>
 </div>;
}
