import {useCallback,useEffect,useState} from 'react';
import type {Credentials} from '../types/domain';
import {request} from '../services/api';

type Participant={username:string;active:boolean};
const endpoint='/api/v1/addons/Sibyl.planning';
const errorText=(e:unknown)=>e instanceof Error?e.message:'Aktion fehlgeschlagen';

export default function PlanningAdminSettings({credentials}:{credentials:Credentials}){
 const [participants,setParticipants]=useState<Participant[]>([]);
 const [username,setUsername]=useState('');
 const [error,setError]=useState('');
 const [notice,setNotice]=useState('');
 const [busy,setBusy]=useState(false);
 const load=useCallback(async()=>{
  setParticipants(await request<Participant[]>(endpoint+'/participants',credentials));
 },[credentials]);
 useEffect(()=>{void load().catch(e=>setError(errorText(e)))},[load]);
 async function execute(op:()=>Promise<unknown>,success:string){
  setBusy(true);setError('');setNotice('');
  try{await op();await load();setNotice(success)}
  catch(e){setError(errorText(e))}
  finally{setBusy(false)}
 }
 return <section className="addon-settings-form">
  <h3>Planung – Teilnehmende verwalten</h3>
  <p>Teilnehmende werden hier einmalig eingetragen oder deaktiviert. Die Planerstellung selbst bleibt im Menüpunkt „Planung“. Auch bei einem deaktivierten Addon ist diese Vorbereitung möglich.</p>
  {error&&<div className="error" role="alert">{error}</div>}
  {notice&&<div className="success-note" role="status">{notice}</div>}
  <form className="sibyl-inline-fields" onSubmit={e=>{
   e.preventDefault();
   void execute(()=>request(endpoint+'/participants',credentials,{
    method:'POST',body:JSON.stringify({username:username.trim()})}),
    'Teilnehmende Person gespeichert.').then(()=>setUsername(''));
  }}>
   <label>Benutzerkonto / Anzeigename<input required maxLength={100}
    value={username} onChange={e=>setUsername(e.target.value)}
    placeholder="AD-Benutzername oder Bezeichnung"/></label>
   <button type="submit" className="primary" disabled={busy||!username.trim()}>Person hinzufügen</button>
  </form>
  <div className="table-panel"><table><thead><tr><th>Teilnehmende</th><th>Status</th><th>Aktion</th></tr></thead>
   <tbody>{participants.map(p=><tr key={p.username}>
    <td>{p.username}</td><td>{p.active?'Aktiv':'Inaktiv'}</td>
    <td>{p.active?<button type="button" className="secondary" disabled={busy} onClick={()=>
     void execute(()=>request(endpoint+'/participants/'+encodeURIComponent(p.username),credentials,
      {method:'DELETE'}),'Person deaktiviert.')}>Deaktivieren</button>:'–'}</td>
   </tr>)}</tbody>
  </table>{participants.length===0&&<p>Noch keine Teilnehmenden eingetragen.</p>}</div>
 </section>;
}
