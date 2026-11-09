import {useCallback,useEffect,useState} from 'react';
import {request} from '../services/api';
import type {Credentials} from '../types/domain';

type Status='DRAFT'|'SUBMITTED'|'RETURNED'|'APPROVED';
type Location='BETRIEB'|'SCHULE'|'UNTERWEISUNG'|'URLAUB'|'KRANK'|'FEIERTAG'|'SONSTIGES';
type Attendance='ANWESEND'|'ABWESEND';
type Day={date:string;location:Location;attendance:Attendance;minutes:number;activity:string};
type Week={id:number;owner:string;weekStart:string;mode:'DAILY'|'WEEKLY';weeklySummary:string|null;
 status:Status;reviewer:string|null;feedback:string|null;revision:number;
 submittedAt:string|null;reviewedAt:string|null;updatedAt:string;days:Day[]};
type Draft={revision:number;mode:'DAILY'|'WEEKLY';weeklySummary:string;days:Day[]};
type Me={username:string;trainee:boolean;trainer:boolean;assignedTrainer:string|null;admin:boolean};
type Event={actor:string;type:string;note:string|null;at:string};
const base='/api/v1/addons/Sibyl.report-book/v2';
const labels:Record<Status,string>={DRAFT:'Entwurf',SUBMITTED:'Zur Prüfung eingereicht',RETURNED:'Zurückgewiesen',APPROVED:'Freigegeben'};
const places:Location[]=['BETRIEB','SCHULE','UNTERWEISUNG','URLAUB','KRANK','FEIERTAG','SONSTIGES'];
const weekdays=['Montag','Dienstag','Mittwoch','Donnerstag','Freitag','Samstag','Sonntag'];
const message=(e:unknown)=>e instanceof Error?e.message:'Unbekannter Fehler';
function iso(d:Date){return [d.getFullYear(),String(d.getMonth()+1).padStart(2,'0'),String(d.getDate()).padStart(2,'0')].join('-')}
function monday(){const d=new Date();d.setDate(d.getDate()-((d.getDay()+6)%7));return iso(d)}
function dates(start:string){const d=new Date(start+'T12:00:00');return weekdays.map((_,i)=>{const x=new Date(d);x.setDate(d.getDate()+i);return iso(x)})}
function toDraft(w:Week):Draft{
 return {revision:w.revision,mode:w.mode,weeklySummary:w.weeklySummary??'',
  days:dates(w.weekStart).map(date=>w.days.find(d=>d.date===date)??{
   date,location:'BETRIEB',attendance:'ANWESEND',minutes:0,activity:''})};
}
export default function ReportBookPage({credentials}:{credentials:Credentials}){
 const [tab,setTab]=useState<'own'|'review'>('own');
 const [me,setMe]=useState<Me|null>(null);
 const [own,setOwn]=useState<Week[]>([]);
 const [assigned,setAssigned]=useState<Week[]>([]);
 const [selected,setSelected]=useState<Week|null>(null);
 const [form,setForm]=useState<Draft|null>(null);
 const [history,setHistory]=useState<Event[]>([]);
 const [weekDate,setWeekDate]=useState(monday());
 const [createMode,setCreateMode]=useState<'DAILY'|'WEEKLY'>('DAILY');
 const [reviewMessage,setReviewMessage]=useState('');
 const [busy,setBusy]=useState(false);
 const [error,setError]=useState('');
 const [notice,setNotice]=useState('');
 const load=useCallback(async()=>{
  const self=await request<Me>(base+'/me',credentials);
  setMe(self);
  setOwn(await request<Week[]>(base+'/weeks',credentials));
  if(self.trainer)setAssigned(await request<Week[]>(base+'/review-queue',credentials));
 },[credentials]);
 useEffect(()=>{void load().catch(e=>setError(message(e)))},[load]);
 async function action(job:()=>Promise<unknown>,success:string){
  setBusy(true);setError('');setNotice('');
  try{await job();await load();setNotice(success)}catch(e){setError(message(e))}
  finally{setBusy(false)}
 }
 async function openWeek(id:number){
  setBusy(true);setError('');setReviewMessage('');
  try{
   const [week,events]=await Promise.all([
    request<Week>(base+'/weeks/'+id,credentials),
    request<Event[]>(base+'/weeks/'+id+'/history',credentials)]);
   setSelected(week);setForm(toDraft(week));setHistory(events);
  }catch(e){setError(message(e))}
  finally{setBusy(false)}
 }
 async function newWeek(e:React.FormEvent){
  e.preventDefault();
  await action(async()=>{const w=await request<Week>(base+'/weeks',credentials,{method:'POST',
   body:JSON.stringify({weekStart:weekDate,mode:createMode})});await openWeek(w.id)},'Berichtswoche angelegt.');
 }
 async function save(e:React.FormEvent){
  e.preventDefault();if(!selected||!form)return;
  await action(async()=>{const w=await request<Week>(base+'/weeks/'+selected.id,credentials,{
   method:'PUT',body:JSON.stringify(form)});setSelected(w);setForm(toDraft(w))},'Änderungen gespeichert.');
 }
 async function submit(){
  if(!selected||!form)return;
  const current=selected;
  const draft=form;
  await action(async()=>{
   if(JSON.stringify(draft)!==JSON.stringify(toDraft(current))){
    await request<Week>(base+'/weeks/'+current.id,credentials,{
     method:'PUT',body:JSON.stringify(draft)});
   }
   const w=await request<Week>(base+'/weeks/'+current.id+'/submit',credentials,{method:'POST'});
   setSelected(w);setForm(toDraft(w));
  },'Woche gespeichert und zur Prüfung eingereicht.');
 }
 async function review(decision:'APPROVED'|'RETURNED'){
  if(!selected)return;
  await action(async()=>{const w=await request<Week>(base+'/weeks/'+selected.id+'/review',
   credentials,{method:'POST',body:JSON.stringify({decision,feedback:reviewMessage})});
   setSelected(w);setForm(toDraft(w));setHistory(await request<Event[]>(base+'/weeks/'+w.id+'/history',credentials))},
   decision==='APPROVED'?'Bericht freigegeben.':'Bericht zur Überarbeitung zurückgewiesen.');
 }
 const editable=selected!==null&&selected.owner===me?.username&&(selected.status==='DRAFT'||selected.status==='RETURNED');
 const selectedTrainer=me?.trainer&&selected?.owner!==me?.username
  &&assigned.some(w=>w.id===selected?.id)&&selected?.status==='SUBMITTED';
 const allWeeks=tab==='own'?own:assigned;
 function changeDay(index:number,patch:Partial<Day>){
  setForm(old=>old?{...old,days:old.days.map((d,i)=>i===index?{...d,...patch}:d)}:old);
 }
 return <section className="resource-page sibyl-reportbook">
  <div className="page-heading"><div><div className="eyebrow">SIBYL / AUSBILDUNG</div>
   <h1>Berichtsheft</h1><p>Deine Ausbildungsnachweise: schreiben, einreichen und prüfen.</p></div></div>
  {error&&<div className="error" role="alert">{error}</div>}
  {notice&&<div className="success-note" role="status">{notice}</div>}
  <div className="sibyl-inline-fields sibyl-report-tabs">
   <button type="button" className={tab==='own'?'primary':'secondary'} onClick={()=>{setTab('own');setSelected(null)}}>Mein Berichtsheft</button>
   {me?.trainer&&<button type="button" className={tab==='review'?'primary':'secondary'} onClick={()=>{setTab('review');setSelected(null)}}>Ausbilder – Prüfungen ({assigned.filter(w=>w.status==='SUBMITTED').length})</button>}
  </div>
  <>
   {tab==='own'&&<div className="sibyl-addon-section">
    <h2>Neue Berichtswoche</h2>
    <form className="sibyl-inline-fields" onSubmit={e=>void newWeek(e)}>
     <label>Montag der Berichtswoche<input type="date" required value={weekDate}
       onChange={e=>{const v=e.target.value;const d=new Date(v+'T12:00:00');if(!isNaN(d.getTime())){d.setDate(d.getDate()-((d.getDay()+6)%7));setWeekDate(iso(d))}}}/></label>
     <label>Berichtsform<select value={createMode} onChange={e=>setCreateMode(e.target.value as 'DAILY'|'WEEKLY')}>
      <option value="DAILY">Tagesberichte</option><option value="WEEKLY">Wochenbericht</option></select></label>
     <button className="primary" disabled={busy}>Woche anlegen</button>
    </form>
    {me&&!me.trainee&&<p>Du kannst Entwürfe führen. Zum Einreichen muss dein Konto einer konfigurierten Azubi-Gruppe zugeordnet sein.</p>}
    {me?.trainee&&!me.assignedTrainer&&<p>Ein Ausbilder muss dir vor der ersten Einreichung zugewiesen werden.</p>}
   </div>}
   <div className="sibyl-report-layout">
    <section className="sibyl-addon-section sibyl-report-list">
     <h2>{tab==='own'?'Meine Berichtswoche':'Zugewiesene Azubis'}</h2>
     <button className="secondary" disabled={busy} onClick={()=>void load().catch(e=>setError(message(e)))}>Aktualisieren</button>
     {allWeeks.length===0?<p>Noch keine Berichtswoche vorhanden.</p>:
      allWeeks.map(w=><button key={w.id} type="button" onClick={()=>void openWeek(w.id)}
       className={'sibyl-report-week '+(selected?.id===w.id?'selected':'')}>
       <strong>{w.weekStart} – {dates(w.weekStart)[6]}</strong>
       {tab==='review'&&<span>{w.owner}</span>}<small>{labels[w.status]}</small></button>)}
    </section>
    <section className="sibyl-addon-section sibyl-report-edit">
     {!selected||!form?<p>Berichtswoche auswählen, um Einträge und Prüfstatus zu sehen.</p>:<>
      <div className="sibyl-inline-fields"><div><div className="eyebrow">AUSBILDUNGSNACHWEIS</div>
       <h2>{selected.weekStart} – {dates(selected.weekStart)[6]}</h2></div>
       <span className={'sibyl-report-status '+selected.status.toLowerCase()}>{labels[selected.status]}</span></div>
      <p>Azubi: {selected.owner}{selected.reviewer?' · geprüft von '+selected.reviewer:''}</p>
      {selected.feedback&&<div className="sibyl-report-feedback"><strong>Rückmeldung:</strong> {selected.feedback}</div>}
      <form onSubmit={e=>void save(e)}>
       <label>Darstellung<select disabled={!editable} value={form.mode}
        onChange={e=>setForm(x=>x?{...x,mode:e.target.value as 'DAILY'|'WEEKLY'}:x)}>
        <option value="DAILY">Tagesbasis</option><option value="WEEKLY">Wochenbasis</option></select></label>
       {form.mode==='WEEKLY'?<label>Tätigkeiten in dieser Woche<textarea rows={12} maxLength={16000}
        disabled={!editable} value={form.weeklySummary} onChange={e=>setForm(x=>x?{...x,weeklySummary:e.target.value}:x)}/></label>
        :<div className="sibyl-report-days">{form.days.map((day,i)=><fieldset key={day.date}>
         <legend>{weekdays[i]} · {day.date}</legend>
         <div className="sibyl-inline-fields">
          <label>Ort<select disabled={!editable} value={day.location} onChange={e=>changeDay(i,{location:e.target.value as Location})}>
           {places.map(p=><option value={p} key={p}>{p.charAt(0)+p.slice(1).toLowerCase()}</option>)}</select></label>
          <label>Anwesenheit<select disabled={!editable} value={day.attendance}
           onChange={e=>changeDay(i,{attendance:e.target.value as Attendance})}>
           <option value="ANWESEND">Anwesend</option><option value="ABWESEND">Abwesend</option></select></label>
          <label>Dauer (Minuten)<input type="number" min={0} max={1440} disabled={!editable} value={day.minutes}
           onChange={e=>changeDay(i,{minutes:Number(e.target.value)})}/></label>
         </div>
         <label>Tätigkeiten / Unterricht<textarea rows={3} maxLength={16000} disabled={!editable}
          value={day.activity} onChange={e=>changeDay(i,{activity:e.target.value})}/></label>
        </fieldset>)}</div>}
       <div className="sibyl-inline-fields">
        {editable&&<button className="primary" disabled={busy}>Entwurf speichern</button>}
        {editable&&<button type="button" className="secondary" disabled={busy||!me?.trainee||!me.assignedTrainer}
         onClick={()=>void submit()}>Speichern und einreichen</button>}
        <button type="button" className="secondary" onClick={()=>window.print()}>Drucken / als PDF speichern</button>
       </div>
      </form>
      {selectedTrainer&&<div className="sibyl-report-feedback">
       <h3>Prüfung durch Ausbilder</h3><label>Begründung bei Rückgabe
        <textarea rows={3} maxLength={1500} value={reviewMessage}
         onChange={e=>setReviewMessage(e.target.value)} placeholder="Was soll geändert werden?"/></label>
       <div className="sibyl-inline-fields">
        <button className="primary" disabled={busy} onClick={()=>void review('APPROVED')}>Approved / Freigeben</button>
        <button className="secondary" disabled={busy||!reviewMessage.trim()} onClick={()=>void review('RETURNED')}>Zurückweisen</button>
       </div>
      </div>}
      {history.length>0&&<details className="sibyl-report-history"><summary>Verlauf und Freigaben</summary>
       {history.map((h,i)=><p key={i}>{new Date(h.at).toLocaleString('de-DE')} · {h.actor} · {h.type}{h.note?' – '+h.note:''}</p>)}
      </details>}
     </>}
    </section>
   </div>
  </>
 </section>;
}
