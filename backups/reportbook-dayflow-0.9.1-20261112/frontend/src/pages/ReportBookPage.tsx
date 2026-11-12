import {useCallback,useEffect,useMemo,useRef,useState} from 'react';
import {Bold,Check,ChevronLeft,ChevronRight,Clock3,Download,
 FileDown,Italic,List,MessageSquare,MoreVertical,Paperclip,Plus,Save,Trash2,X} from 'lucide-react';
import type {Credentials} from '../types/domain';
import {request} from '../services/api';

type Status='DRAFT'|'SUBMITTED'|'RETURNED'|'APPROVED';
type Mode='DAILY'|'WEEKLY';
type DayItem={kind:'TEXT'|'BULLET';minutes:number;body:string};
type Day={date:string;attendance:string;location:string;entries:DayItem[]};
type Detail={id:number;owner:string;weekStart:string;mode:Mode;weeklySummary:string|null;
 status:Status;reviewer:string|null;feedback:string|null;revision:number;
 submittedAt:string|null;reviewedAt:string|null;days:Day[]};
type WeekSummary=Omit<Detail,'days'>;
type Draft={mode:Mode;weeklySummary:string;days:Day[]};
type Me={username:string;trainee:boolean;trainer:boolean;assignedTrainer:string|null;admin:boolean};
type Comment={id:number;date:string;author:string;body:string;at:string};
type Attachment={id:number;date:string;name:string;type:string;size:number;uploadedBy:string;at:string};
const v2='/api/v1/addons/Sibyl.report-book/v2';
const v3='/api/v1/addons/Sibyl.report-book/v3';
const weekdays=['Montag','Dienstag','Mittwoch','Donnerstag','Freitag','Samstag','Sonntag'];
const attendanceOptions=[
 ['ANWESEND','anwesend'],['URLAUB','Urlaub'],['FEIERTAG','Feiertag'],
 ['ZEITAUSGLEICH','Zeitausgleich'],['SONSTIGE_ABWESENHEIT','sonstige Abwesenheit'],
 ['KRANK','Krank'],['ABWESEND','abwesend']
];
const locationOptions=[
 ['BETRIEB','Betrieb'],['SCHULE','Schule'],['UNTERWEISUNG','Unterweisung'],
 ['SCHULE_BETRIEB','Schule/Betrieb'],['SONSTIGES','Sonstiges']
];
const statuses:Record<Status,string>={
 DRAFT:'Noch nicht freigegeben',SUBMITTED:'In Prüfung durch Ausbilder',
 RETURNED:'Vom Ausbilder zurückgewiesen',APPROVED:'Vom Ausbilder freigegeben'
};
const errorText=(err:unknown)=>err instanceof Error?err.message:'Die Aktion ist fehlgeschlagen.';
const pad=(n:number)=>String(n).padStart(2,'0');
const asIso=(d:Date)=>[d.getFullYear(),pad(d.getMonth()+1),pad(d.getDate())].join('-');
const asDate=(s:string)=>new Date(s+'T12:00:00');
const weekStartOf=(d:Date)=>{const x=new Date(d);x.setDate(x.getDate()-(x.getDay()+6)%7);return asIso(x)};
const todayWeek=()=>weekStartOf(new Date());
const dayOf=(start:string,n:number)=>{const d=asDate(start);d.setDate(d.getDate()+n);return asIso(d)};
const dayLabel=(iso:string)=>asDate(iso).toLocaleDateString('de-DE',{day:'2-digit',month:'long'});
const dateLabel=(iso:string)=>asDate(iso).toLocaleDateString('de-DE',{day:'2-digit',month:'2-digit',year:'numeric'});
const weekNo=(iso:string)=>{
 const d=asDate(iso);
 const utc=new Date(Date.UTC(d.getFullYear(),d.getMonth(),d.getDate()));
 utc.setUTCDate(utc.getUTCDate()+4-(utc.getUTCDay()||7));
 const first=new Date(Date.UTC(utc.getUTCFullYear(),0,1));
 return Math.ceil((((utc.getTime()-first.getTime())/86400000)+1)/7);
};
const duration=(n:number)=>pad(Math.floor(n/60))+':'+pad(n%60);
const blankDay=(date:string):Day=>({date,attendance:'',location:'',entries:[]});
const initialDraft=(start:string):Draft=>({
 mode:'DAILY',weeklySummary:'',days:weekdays.map((_,i)=>blankDay(dayOf(start,i)))
});
const draftOf=(week:Detail):Draft=>({
 mode:week.mode,weeklySummary:week.weeklySummary??'',
 days:weekdays.map((_,i)=>week.days.find(d=>d.date===dayOf(week.weekStart,i))??blankDay(dayOf(week.weekStart,i)))
});
const sumWeek=(draft:Draft)=>draft.days.reduce((sum,d)=>sum+d.entries.reduce((s,e)=>s+e.minutes,0),0);
const empty=(kind:DayItem['kind']='TEXT'):DayItem=>({kind,minutes:0,body:''});

function TimeInput({value,disabled,onChange}:{value:number;disabled:boolean;onChange:(minutes:number)=>void}){
 const hours=Math.floor(value/60),mins=value%60;
 return <div className="ihk-time" aria-label="Stunden und Minuten">
  <label>Stunden<input aria-label="Stunden" type="number" min="0" max="24" value={hours} disabled={disabled}
   onChange={e=>onChange(Math.max(0,Math.min(24,Number(e.target.value)||0))*60+mins)}/></label>
  <span>:</span>
  <label>Minuten<input aria-label="Minuten" type="number" min="0" max="59" value={pad(mins)}
   disabled={disabled} onChange={e=>onChange(hours*60+Math.max(0,Math.min(59,Number(e.target.value)||0)))}/></label>
 </div>;
}
function NoteEditor({value,onChange,kind,disabled}:{value:string;onChange:(v:string)=>void;kind:DayItem['kind'];disabled:boolean}){
 const ref=useRef<HTMLTextAreaElement|null>(null);
 const format=(before:string,after=before)=>{
  const el=ref.current;if(!el||disabled)return;
  const a=el.selectionStart,b=el.selectionEnd;
  const selected=value.slice(a,b)||'Text';
  const updated=value.slice(0,a)+before+selected+after+value.slice(b);
  onChange(updated);el.focus();
  requestAnimationFrame(()=>el.setSelectionRange(a+before.length,a+before.length+selected.length));
 };
 return <div className="ihk-note">
  {kind==='TEXT'&&<div className="ihk-toolbar" aria-label="Textformatierung">
   <button type="button" title="Fett" aria-label="Fett" disabled={disabled}
    onClick={()=>format('**')}><Bold size={14}/></button>
   <button type="button" title="Kursiv" aria-label="Kursiv" disabled={disabled}
    onClick={()=>format('*')}><Italic size={14}/></button>
   <button type="button" title="Aufzählung" aria-label="Aufzählung" disabled={disabled}
    onClick={()=>format('- ','')}><List size={14}/></button>
   <span>Freitext-Eintrag</span>
  </div>}
  <textarea ref={ref} rows={kind==='BULLET'?2:3} maxLength={12000} disabled={disabled}
   placeholder={kind==='BULLET'?'Neuer Stichpunkt*':'Tätigkeit oder Unterricht eintragen …'}
   value={value} onChange={e=>onChange(e.target.value)}/>
 </div>;
}

function DayCard({day,index,readOnly,change,onOpenComments,onOpenFiles,attachmentCount,weekStatus}:{
 day:Day;index:number;readOnly:boolean;change:(next:Day)=>void;
 onOpenComments:()=>void;onOpenFiles:()=>void;attachmentCount:number;weekStatus:Status
}){
 const [menu,setMenu]=useState(false);
 const weekend=index>4;
 const total=day.entries.reduce((sum,e)=>sum+e.minutes,0);
 const showEntries=day.entries.length>0;
 const hasAttendance=day.attendance!=='';
 const needsLocation=day.attendance==='ANWESEND';
 const hasLocation=day.location!=='';
 const mayAdd=needsLocation&&hasLocation;
 const clearDay=()=>{change(blankDay(day.date));setMenu(false);};
 const setAttendance=(value:string)=>{
  // Re-selecting attendance intentionally starts the day selection afresh.
  change({...day,attendance:value,location:'',entries:[]});
  setMenu(false);
 };
 const setLocation=(value:string)=>{
  change({...day,location:value});
  setMenu(false);
 };
 const changeItem=(idx:number,patch:Partial<DayItem>)=>change({
  ...day,entries:day.entries.map((e,i)=>i===idx?{...e,...patch}:e)
 });
 const add=(kind:DayItem['kind'])=>{
  if(readOnly||!mayAdd)return;
  change({...day,entries:[...day.entries,empty(kind)]});setMenu(false);
 };
 return <article className={'ihk-day'+(weekend?' weekend':'')+(weekStatus==='APPROVED'?' approved':'')+(!showEntries?' ihk-day-empty':'')}>
  <div className="ihk-day-head">
   <div className="ihk-date"><strong>{pad(asDate(day.date).getDate())}</strong>
    <div><b>{dayLabel(day.date).split(' ')[1]}</b><span>{weekdays[index]}</span></div></div>
   <div className="ihk-day-selects">
    <label className={!hasAttendance?'ihk-unset':''}>
     <span>Anwesenheit</span>
     {readOnly?<strong>{hasAttendance?(attendanceOptions.find(a=>a[0]===day.attendance)?.[1]??day.attendance):'Nicht ausgewählt'}</strong>
     :<select aria-label={'Anwesenheit '+day.date} value={day.attendance}
       onChange={e=>setAttendance(e.target.value)}>
       <option value="">Anwesenheit</option>
       {attendanceOptions.map(a=><option key={a[0]} value={a[0]}>{a[1]}</option>)}
      </select>}</label>
    {needsLocation&&<label className={!hasLocation?'ihk-unset':''}>
     <span>Ort{!readOnly?'*':''}</span>
     {readOnly?<strong>{hasLocation?(locationOptions.find(a=>a[0]===day.location)?.[1]??day.location):'Nicht ausgewählt'}</strong>
     :<select aria-label={'Ort '+day.date} value={day.location}
       onChange={e=>setLocation(e.target.value)}>
       <option value="">Ort*</option>
       {locationOptions.map(a=><option key={a[0]} value={a[0]}>{a[1]}</option>)}
      </select>}</label>}
   </div>
   <div className="ihk-day-actions">
    <button type="button" title="Anhänge" aria-label={'Anhänge '+day.date}
      onClick={onOpenFiles}><Paperclip size={16}/>{attachmentCount>0&&<small>{attachmentCount}</small>}</button>
    <button type="button" title="Kommentare" aria-label={'Kommentare '+day.date}
      onClick={onOpenComments}><MessageSquare size={16}/></button>
   </div>
  </div>
  {hasLocation&&(mayAdd||showEntries)&&<div className="ihk-location-heading">{locationOptions.find(a=>a[0]===day.location)?.[1].toUpperCase()??day.location}</div>}
  {!readOnly&&mayAdd&&!showEntries&&<button type="button" className="ihk-first-add" onClick={()=>add('TEXT')}>
   <Plus size={15}/> Eintrag hinzufügen *
  </button>}
  {day.entries.map((entry,i)=><div key={day.date+'-'+i} className="ihk-entry">
   <div className="ihk-entry-title">{day.location==='SCHULE'?'SCHULE':day.location==='UNTERWEISUNG'?'UNTERWEISUNG':'BETRIEB'} · {entry.kind==='BULLET'?'Stichpunkt':'Freitext'}</div>
   <div className="ihk-entry-row">
    <div className="ihk-entry-main"><NoteEditor value={entry.body} kind={entry.kind} disabled={readOnly}
      onChange={body=>changeItem(i,{body})}/></div>
    <div className="ihk-entry-time">
     <TimeInput value={entry.minutes} disabled={readOnly} onChange={minutes=>changeItem(i,{minutes})}/>
     {!readOnly&&<button className="ihk-icon-delete" title="Eintrag löschen" type="button"
      onClick={()=>change({...day,entries:day.entries.filter((_,j)=>j!==i)})}><Trash2 size={14}/></button>}
    </div>
   </div>
  </div>)}
  {!readOnly&&<div className="ihk-day-bottom">
   {mayAdd&&showEntries&&<div className="ihk-insert">
    <button type="button" className="ihk-add" onClick={()=>add('TEXT')}><Plus size={14}/> Weiteren Eintrag hinzufügen</button>
    <button type="button" aria-label="Eintragsart auswählen" title="Eintragsart wählen"
     onClick={()=>setMenu(!menu)}><MoreVertical size={15}/></button>
    {menu&&<div className="ihk-insert-menu" role="menu">
     <button role="menuitem" type="button" onClick={()=>add('BULLET')}>Stichpunkt-Eintrag</button>
     <button role="menuitem" type="button" onClick={()=>add('TEXT')}>Freitext-Eintrag</button>
    </div>}
   </div>}
   <button type="button" className="ihk-clear" onClick={clearDay}>Eintrag leeren</button>
   <span className="ihk-day-total">{total>0?duration(total)+' Std.':''}</span>
  </div>}
  {readOnly&&total>0&&<div className="ihk-readonly-total">{duration(total)} Std.</div>}
 </article>;
}

function YearCalendar({year,weekStatuses,onChoose}:{year:number;weekStatuses:Map<string,Status>;onChoose:(week:string)=>void}){
 const months=Array.from({length:12},(_,month)=>{
  const first=new Date(year,month,1,12);
  const last=new Date(year,month+1,0,12).getDate();
  const blanks=(first.getDay()+6)%7;
  const cells=[...Array.from({length:blanks},()=>null),
   ...Array.from({length:last},(_,i)=>i+1)];
  return {month,cells,name:first.toLocaleDateString('de-DE',{month:'long'})};
 });
 return <div className="ihk-year-grid">{months.map(m=><section className="ihk-month" key={m.month}>
  <h3>{m.name}</h3><div className="ihk-month-days">
   {['Mo','Di','Mi','Do','Fr','Sa','So'].map((w,i)=><span className="ihk-month-head" key={i}>{w}</span>)}
   {m.cells.map((day,i)=>{
    if(day===null)return <span key={i}/>;
    const dt=new Date(year,m.month,day,12),d=asIso(dt),wk=weekStartOf(dt),status=weekStatuses.get(wk);
    const today=d===asIso(new Date());
    return <button key={i} className={'ihk-calendar-day '+(status?.toLowerCase()??'empty')+(today?' today':'')}
      type="button" title={dateLabel(d)+(status?' – '+statuses[status]:'')}
      onClick={()=>onChoose(wk)}>{day}</button>;
   })}
  </div></section>)}</div>;
}

export default function ReportBookPage({credentials}:{credentials:Credentials}){
 const [view,setView]=useState<'week'|'year'|'review'>('week');
 const [weekStart,setWeekStart]=useState(todayWeek());
 const [year,setYear]=useState(new Date().getFullYear());
 const [mine,setMine]=useState<WeekSummary[]>([]);
 const [reviewQueue,setReviewQueue]=useState<WeekSummary[]>([]);
 const [me,setMe]=useState<Me|null>(null);
 const [week,setWeek]=useState<Detail|null>(null);
 const [draft,setDraft]=useState<Draft>(initialDraft(todayWeek()));
 const [selectedReview,setSelectedReview]=useState(false);
 const [saving,setSaving]=useState<'idle'|'editing'|'saving'|'saved'|'error'>('idle');
 const [working,setWorking]=useState(false);
 const [error,setError]=useState('');
 const [notice,setNotice]=useState('');
 const [weekMenu,setWeekMenu]=useState(false);
 const [inspector,setInspector]=useState<{date:string;type:'comments'|'files'}|null>(null);
 const [comments,setComments]=useState<Comment[]>([]);
 const [files,setFiles]=useState<Attachment[]>([]);
 const [commentBody,setCommentBody]=useState('');
 const [fileUploading,setFileUploading]=useState(false);
 const [reviewMessage,setReviewMessage]=useState('');
 const [loaded,setLoaded]=useState(false);
 const formRef=useRef(draft),weekRef=useRef<Detail|null>(null),weekStartRef=useRef(weekStart);
 const dirty=useRef(false),seq=useRef(0),inflight=useRef<Promise<void>|null>(null);
 const [editSerial,setEditSerial]=useState(0);
 const own=me!==null&&!selectedReview;
 const canEdit=own&&(!week||week.status==='DRAFT'||week.status==='RETURNED');
 const canReview=selectedReview&&week?.status==='SUBMITTED';

 const findMine=useCallback((start:string,available:WeekSummary[])=>available.find(w=>w.weekStart===start),[]);
 const openWeek=useCallback(async(start:string,id?:number,forReview=false)=>{
  setWorking(true);setError('');setNotice('');setInspector(null);setWeekMenu(false);
  try{
   const target=id??(forReview?undefined:findMine(start,mine)?.id);
   const full=target?await request<Detail>(v3+'/weeks/'+target+'/detail',credentials):null;
   const attachments=target?await request<Attachment[]>(v3+'/weeks/'+target+'/files',credentials):[];
   setFiles(attachments);
   weekStartRef.current=start;weekRef.current=full;
   setWeekStart(start);setWeek(full);setDraft(full?draftOf(full):initialDraft(start));
   formRef.current=full?draftOf(full):initialDraft(start);
   dirty.current=false;setEditSerial(0);setSaving('idle');
   setSelectedReview(forReview);setView('week');setYear(asDate(start).getFullYear());
  }catch(e){setError(errorText(e))}
  finally{setWorking(false)}
 },[credentials,findMine,mine]);

 useEffect(()=>{
  let cancelled=false;
  async function init(){
   try{
    const [identity,weeks]=await Promise.all([
     request<Me>(v2+'/me',credentials),request<WeekSummary[]>(v2+'/weeks',credentials)]);
    if(cancelled)return;
    setMe(identity);setMine(weeks);
    if(identity.trainer){
     const assigned=await request<WeekSummary[]>(v2+'/review-queue',credentials);
     if(!cancelled)setReviewQueue(assigned);
    }
    const existing=weeks.find(w=>w.weekStart===weekStartRef.current);
    const w=existing?await request<Detail>(v3+'/weeks/'+existing.id+'/detail',credentials):null;
    const attachments=existing?await request<Attachment[]>(v3+'/weeks/'+existing.id+'/files',credentials):[];
    if(cancelled)return;
    setFiles(attachments);
    weekRef.current=w;setWeek(w);
    formRef.current=w?draftOf(w):initialDraft(weekStartRef.current);
    setDraft(formRef.current);setLoaded(true);
   }catch(e){if(!cancelled)setError(errorText(e))}
  }
  void init();
  return()=>{cancelled=true};
 },[credentials]);

 const modify=(next:Draft)=>{
  if(!canEdit)return;
  formRef.current=next;setDraft(next);dirty.current=true;seq.current++;
  setSaving('editing');setEditSerial(v=>v+1);
 };
 const persist=async()=>{
  if(inflight.current)await inflight.current;
  if(!dirty.current||!canEdit)return;
  const snapshot=formRef.current,start=weekStartRef.current,version=seq.current;
  dirty.current=false;setSaving('saving');
  const job=(async()=>{
   let current=weekRef.current;
   if(!current){
    const created=await request<WeekSummary>(v2+'/weeks',credentials,{
     method:'POST',body:JSON.stringify({weekStart:start,mode:snapshot.mode})});
    current=await request<Detail>(v3+'/weeks/'+created.id+'/detail',credentials);
    weekRef.current=current;setWeek(current);
   }
   const updated=await request<Detail>(v3+'/weeks/'+current.id+'/detail',credentials,{
    method:'PUT',body:JSON.stringify({...snapshot,revision:current.revision})});
   weekRef.current=updated;setWeek(updated);
   setMine(items=>[{
    id:updated.id,owner:updated.owner,weekStart:updated.weekStart,mode:updated.mode,
    weeklySummary:updated.weeklySummary,status:updated.status,reviewer:updated.reviewer,
    feedback:updated.feedback,revision:updated.revision,submittedAt:updated.submittedAt,
    reviewedAt:updated.reviewedAt
   },...items.filter(w=>w.id!==updated.id)]);
   if(seq.current===version){setSaving('saved')}
   else{dirty.current=true;setSaving('editing')}
  })();
  inflight.current=job;
  try{await job}
  catch(e){dirty.current=true;setSaving('error');setError(errorText(e));throw e}
  finally{if(inflight.current===job)inflight.current=null}
 };
 useEffect(()=>{
  if(!editSerial||!dirty.current)return;
  const timer=window.setTimeout(()=>{void persist().catch(()=>{})},1250);
  return()=>window.clearTimeout(timer);
 // eslint-disable-next-line react-hooks/exhaustive-deps
 },[editSerial]);

 const navigate=async(start:string,reviewId?:number)=>{
  if(working)return;
  try{await persist();await openWeek(start,reviewId,reviewId!==undefined)}
  catch(e){setError(errorText(e))}
 };
 const shift=(dir:number)=>{
  const d=asDate(weekStartRef.current);d.setDate(d.getDate()+dir*7);
  void navigate(asIso(d));
 };
 const changeDay=(i:number,next:Day)=>{
  const current=formRef.current;
  modify({...current,days:current.days.map((d,j)=>j===i?next:d)});
 };
 const changeMode=(mode:Mode)=>modify({...formRef.current,mode});
 const ensureSave=async()=>{
  await persist();
  // A user may have typed while the previous request was in flight.
  if(dirty.current)await persist();
 };
 const updateSummary=(status:Status,updated:Detail)=>{
  weekRef.current=updated;setWeek(updated);
  setMine(items=>items.map(w=>w.id===updated.id?{...w,status,revision:updated.revision,
   feedback:updated.feedback,reviewer:updated.reviewer,reviewedAt:updated.reviewedAt}:w));
 };
 const submit=async()=>{
  if(!me?.trainee){setError('Dein Konto ist noch keiner Azubi-Gruppe zugeordnet.');return}
  if(!me.assignedTrainer){setError('Ein Ausbilder muss deinem Konto erst unter Addons → Berichtsheft zugeordnet werden.');return}
  setWorking(true);setError('');
  try{
   await ensureSave();
   if(!weekRef.current){setError('Bitte zuerst einen Eintrag anlegen.');return}
   const result=await request<Detail>(v2+'/weeks/'+weekRef.current.id+'/submit',credentials,{method:'POST'});
   const full=await request<Detail>(v3+'/weeks/'+result.id+'/detail',credentials);
   updateSummary('SUBMITTED',full);setNotice('Die Berichtswoche ist zur Prüfung eingereicht.');
  }catch(e){setError(errorText(e))}
  finally{setWorking(false)}
 };
 const withdraw=async()=>{
  if(!weekRef.current||!window.confirm('Möchtest du diese eingereichte Berichtsheftwoche zur Bearbeitung zurückziehen?'))return;
  setWorking(true);setError('');
  try{
   const full=await request<Detail>(v3+'/weeks/'+weekRef.current.id+'/withdraw',credentials,{method:'POST'});
   updateSummary('DRAFT',full);setNotice('Die Woche kann wieder bearbeitet werden.');
  }catch(e){setError(errorText(e))}
  finally{setWorking(false)}
 };
 const deleteWeek=async()=>{
  if(!weekRef.current||!canEdit||!window.confirm('Berichtswoche und alle Einträge dauerhaft löschen?'))return;
  const id=weekRef.current.id;setWorking(true);setError('');
  try{
   await request<void>(v3+'/weeks/'+id,credentials,{method:'DELETE'});
   setMine(items=>items.filter(w=>w.id!==id));
   weekRef.current=null;setWeek(null);
   formRef.current=initialDraft(weekStartRef.current);setDraft(formRef.current);
   dirty.current=false;setSaving('idle');setNotice('Berichtswoche gelöscht.');
  }catch(e){setError(errorText(e))}
  finally{setWorking(false);setWeekMenu(false)}
 };
 const review=async(decision:'APPROVED'|'RETURNED')=>{
  if(!week||!canReview)return;
  if(decision==='RETURNED'&&!reviewMessage.trim()){setError('Bitte eine Begründung für die Rückgabe angeben.');return}
  setWorking(true);setError('');
  try{
   await request(v2+'/weeks/'+week.id+'/review',credentials,{
    method:'POST',body:JSON.stringify({decision,feedback:reviewMessage})});
   const full=await request<Detail>(v3+'/weeks/'+week.id+'/detail',credentials);
   weekRef.current=full;setWeek(full);
   setReviewQueue(items=>items.map(w=>w.id===full.id?{...w,status:full.status}:w));
   setNotice(decision==='APPROVED'?'Woche freigegeben.':'Woche zur Überarbeitung zurückgegeben.');
   setReviewMessage('');
  }catch(e){setError(errorText(e))}
  finally{setWorking(false)}
 };
 const openInspector=async(date:string,type:'comments'|'files')=>{
  setInspector({date,type});setCommentBody('');setError('');
  if(!weekRef.current){setComments([]);setFiles([]);return}
  try{
   const id=weekRef.current.id;
   if(type==='comments')setComments(await request<Comment[]>(
    v3+'/weeks/'+id+'/days/'+date+'/comments',credentials));
   else setFiles(await request<Attachment[]>(v3+'/weeks/'+id+'/files',credentials));
  }catch(e){setError(errorText(e))}
 };
 const sendComment=async()=>{
  if(!weekRef.current||!inspector||!commentBody.trim())return;
  try{
   await request(v3+'/weeks/'+weekRef.current.id+'/days/'+inspector.date+'/comments',
    credentials,{method:'POST',body:JSON.stringify({body:commentBody.trim()})});
   setComments(await request<Comment[]>(v3+'/weeks/'+weekRef.current.id+
    '/days/'+inspector.date+'/comments',credentials));
   setCommentBody('');
  }catch(e){setError(errorText(e))}
 };
 const uploadFile=async(file:File)=>{
  if(!weekRef.current||!inspector)return;
  if(file.size>3*1024*1024){setError('Datei maximal 3 MB.');return}
  setFileUploading(true);setError('');
  try{
   const base64=await new Promise<string>((resolve,reject)=>{
    const reader=new FileReader();
    reader.onload=()=>resolve(String(reader.result).split(',')[1]??'');
    reader.onerror=()=>reject(new Error('Datei konnte nicht gelesen werden.'));
    reader.readAsDataURL(file);
   });
   await request(v3+'/weeks/'+weekRef.current.id+'/files',credentials,{
    method:'POST',body:JSON.stringify({date:inspector.date,name:file.name,type:file.type,base64})});
   setFiles(await request<Attachment[]>(v3+'/weeks/'+weekRef.current.id+'/files',credentials));
  }catch(e){setError(errorText(e))}
  finally{setFileUploading(false)}
 };
 const downloadFile=async(file:Attachment)=>{
  if(!weekRef.current)return;
  try{
   // Binary responses need the same authenticated user as the JSON endpoints.
   const raw=new TextEncoder().encode(credentials.username+':'+credentials.password);
   const header='Basic '+btoa(Array.from(raw,b=>String.fromCharCode(b)).join(''));
   const response=await fetch(v3+'/weeks/'+weekRef.current.id+'/files/'+file.id,{
    headers:{Authorization:header},cache:'no-store'});
   if(!response.ok)throw new Error('Anhang konnte nicht heruntergeladen werden.');
   const blob=await response.blob(),link=document.createElement('a');
   const href=URL.createObjectURL(blob);link.href=href;link.download=file.name;link.click();
   window.setTimeout(()=>URL.revokeObjectURL(href),5000);
  }catch(e){setError(errorText(e))}
 };
 const deleteFile=async(fileId:number)=>{
  if(!weekRef.current||!window.confirm('Anhang entfernen?'))return;
  try{
   await request<void>(v3+'/weeks/'+weekRef.current.id+'/files/'+fileId,credentials,{method:'DELETE'});
   setFiles(await request<Attachment[]>(v3+'/weeks/'+weekRef.current.id+'/files',credentials));
  }catch(e){setError(errorText(e))}
 };
 const weekStatuses=useMemo(()=>new Map(mine.map(w=>[w.weekStart,w.status])),[mine]);
 const title='KW '+weekNo(weekStart)+', '+dateLabel(weekStart)+' - '+dateLabel(dayOf(weekStart,6));
 const readonly=!canEdit||working;
 const visibleFiles=inspector?files.filter(f=>f.date===inspector.date):[];
 const printBook=async()=>{
  try{await ensureSave();setNotice('Wähle im Druckdialog „Als PDF speichern“.');window.print()}
  catch(e){setError(errorText(e))}
 };
 return <section className="resource-page sibyl-reportbook ihk-report">
  <header className="ihk-title page-heading">
   <div><div className="eyebrow">SIBYL / AUSBILDUNG</div><h1>Berichtsheft</h1>
    <p>Ausbildungsnachweise – Wochenansicht und Jahresübersicht</p></div>
   <button type="button" className="secondary ihk-print-button" onClick={()=>void printBook()}>
    <FileDown size={15}/> Berichtsheft als PDF drucken</button>
  </header>
  {error&&<div className="error" role="alert">{error}</div>}
  {notice&&<div className="success-note" role="status">{notice}</div>}
  <div className="ihk-nav">
   <div className="ihk-nav-tabs">
    <button className={view==='week'?'active':''} onClick={()=>setView('week')} type="button">Wochenansicht</button>
    <button className={view==='year'?'active':''} onClick={()=>{void ensureSave().then(()=>setView('year')).catch(e=>setError(errorText(e)))}} type="button">Jahresansicht</button>
    {me?.trainer&&<button className={view==='review'?'active':''} type="button"
     onClick={()=>{void ensureSave().then(()=>setView('review')).catch(e=>setError(errorText(e)))}}>
     Ausbilder: Prüfungen ({reviewQueue.filter(w=>w.status==='SUBMITTED').length})</button>}
   </div>
  </div>
  {view==='review'?<div className="ihk-review-list">
   <h2>Berichtshefte meiner Azubis</h2>
   {reviewQueue.length===0?<p>Keine zugewiesenen Berichtswochen vorhanden.</p>:
    <table><thead><tr><th>Azubi</th><th>Berichtswoche</th><th>Status</th><th></th></tr></thead><tbody>
     {reviewQueue.map(w=><tr key={w.id}><td>{w.owner}</td><td>KW {weekNo(w.weekStart)} · {dateLabel(w.weekStart)}</td>
      <td>{statuses[w.status]}</td><td><button className="secondary" type="button"
       onClick={()=>void navigate(w.weekStart,w.id)}>Öffnen</button></td></tr>)}
    </tbody></table>}
  </div>:view==='year'?<>
   <div className="ihk-week-toolbar"><div className="ihk-year-nav">
    <button type="button" aria-label="Vorjahr" onClick={()=>setYear(v=>v-1)}><ChevronLeft size={18}/></button>
    <strong>{year}</strong>
    <button type="button" aria-label="Folgejahr" onClick={()=>setYear(v=>v+1)}><ChevronRight size={18}/></button>
   </div></div>
   <YearCalendar year={year} weekStatuses={weekStatuses} onChoose={start=>void navigate(start)}/>
   <div className="ihk-legend"><span className="draft">Entwurf</span><span className="submitted">Eingereicht</span>
    <span className="approved">Freigegeben</span><span className="returned">Zurückgewiesen</span></div>
  </>:<>
   <div className="ihk-week-toolbar">
    <div className="ihk-week-status">
     {canEdit?<button type="button" className="primary" disabled={working||!loaded}
      onClick={()=>void submit()}><Check size={15}/> Zur Abnahme freigeben</button>:
      <strong className={'ihk-status '+(week?.status.toLowerCase()??'draft')}>
       {week?statuses[week.status]:'Noch nicht freigegeben'}</strong>}
     {week?.status==='SUBMITTED'&&own&&<button className="secondary" type="button"
      onClick={()=>void withdraw()}>Woche zurückziehen</button>}
     <span className="ihk-save-indicator" aria-live="polite">
      <Save size={14}/>{saving==='saving'?'Speichert …':saving==='editing'?'Änderungen noch nicht gespeichert'
       :saving==='saved'?'Automatisch gespeichert':saving==='error'?'Speichern fehlgeschlagen':'Automatische Speicherung aktiv'}
     </span>
     {week&&<div className="ihk-week-menu"><button type="button" title="Wochenaktionen"
      aria-label="Wochenaktionen" onClick={()=>setWeekMenu(!weekMenu)}><MoreVertical size={17}/></button>
      {weekMenu&&<div className="ihk-dropdown">
       <button type="button" onClick={()=>{setWeekMenu(false);if(!canEdit){setError('Diese Woche ist gesperrt.')}}}>
        Woche bearbeiten</button>
       {canEdit&&<button type="button" onClick={()=>void deleteWeek()}>Woche löschen</button>}
      </div>}
     </div>}
    </div>
    <div className="ihk-week-nav">
     <span>{title}</span>
     <button aria-label="Vorherige Woche" title="Vorherige Woche" type="button"
      disabled={working} onClick={()=>shift(-1)}><ChevronLeft size={17}/></button>
     <button aria-label="Nächste Woche" title="Nächste Woche" type="button"
      disabled={working} onClick={()=>shift(1)}><ChevronRight size={17}/></button>
    </div>
   </div>
   {week?.feedback&&<div className="ihk-feedback">
    <strong>Rückmeldung des Ausbilders:</strong> {week.feedback}</div>}
   {!me?.trainee&&!selectedReview&&<p className="ihk-setup-warning">
    Du kannst Entwürfe schreiben. Für die Einreichung muss dein Konto unter Addons → Berichtsheft als Azubi zugeordnet sein.</p>}
   {me?.trainee&&!me.assignedTrainer&&!selectedReview&&<p className="ihk-setup-warning">
    Für die Einreichung fehlt noch ein zugewiesener Ausbilder (Administration → Addons → Berichtsheft).</p>}
   {canReview&&<div className="ihk-trainer-review">
    <label>Rückmeldung an den Azubi<textarea rows={2} maxLength={1500} value={reviewMessage}
     onChange={e=>setReviewMessage(e.target.value)} placeholder="Was soll bei einer Rückgabe geändert werden?"/></label>
    <button className="primary" type="button" disabled={working} onClick={()=>void review('APPROVED')}>Freigeben</button>
    <button className="secondary" type="button" disabled={working||!reviewMessage.trim()}
     onClick={()=>void review('RETURNED')}>Zurückweisen</button>
   </div>}
   {!loaded?<p className="loading">Berichtsheft wird geladen …</p>:<div className="ihk-week-content">
    <div className="ihk-mode-tabs">
     <button type="button" disabled={readonly} className={draft.mode==='DAILY'?'active':''}
      onClick={()=>changeMode('DAILY')}>Einträge auf Tagesbasis anlegen</button>
     <button type="button" disabled={readonly} className={draft.mode==='WEEKLY'?'active':''}
      onClick={()=>changeMode('WEEKLY')}>Einträge auf Wochenbasis anlegen</button>
    </div>
    {draft.mode==='WEEKLY'?<div className="ihk-weekly-editor">
     <h2>Berichtswoche {title}</h2>
     <NoteEditor kind="TEXT" disabled={readonly} value={draft.weeklySummary}
      onChange={v=>modify({...formRef.current,weeklySummary:v})}/>
    </div>:<div className="ihk-days">
     {draft.days.map((day,i)=><DayCard key={day.date} day={day} index={i}
      readOnly={readonly} weekStatus={week?.status??'DRAFT'}
      change={next=>changeDay(i,next)}
      onOpenComments={()=>void openInspector(day.date,'comments')}
      onOpenFiles={()=>void openInspector(day.date,'files')}
      attachmentCount={files.filter(f=>f.date===day.date).length}/>)}
    </div>}
    <div className="ihk-week-total"><Clock3 size={16}/> Gesamtdauer: <b>{duration(sumWeek(draft))}</b></div>
   </div>}
  </>}
  {inspector&&<div className="ihk-modal-backdrop" onClick={()=>setInspector(null)}>
   <section className="ihk-modal" role="dialog" aria-modal="true" aria-label="Tagesdetails"
    onClick={e=>e.stopPropagation()}>
    <div className="ihk-modal-heading"><h2>{inspector.type==='comments'?'Kommentare':'Anhänge'} · {dateLabel(inspector.date)}</h2>
     <button title="Schließen" type="button" onClick={()=>setInspector(null)}><X size={17}/></button></div>
    {inspector.type==='comments'?<>
     {comments.length===0?<p>Noch keine Kommentare.</p>:comments.map(c=><div className="ihk-comment" key={c.id}>
      <strong>{c.author}</strong><small>{new Date(c.at).toLocaleString('de-DE')}</small><p>{c.body}</p>
     </div>)}
     {weekRef.current&&<div className="ihk-modal-form"><label>Kommentar hinzufügen
      <textarea maxLength={1500} rows={3} value={commentBody} onChange={e=>setCommentBody(e.target.value)}/></label>
      <button type="button" className="primary" disabled={!commentBody.trim()} onClick={()=>void sendComment()}>
       Kommentar speichern</button></div>}
    </>:<>
     {visibleFiles.length===0?<p>Keine Anhänge für diesen Tag.</p>:
      visibleFiles.map(f=><div className="ihk-file-row" key={f.id}>
       <div><strong>{f.name}</strong><small>{Math.ceil(f.size/1024)} KB</small></div>
       <button type="button" title="Herunterladen" onClick={()=>void downloadFile(f)}><Download size={15}/></button>
       {canEdit&&<button type="button" title="Löschen" onClick={()=>void deleteFile(f.id)}><Trash2 size={15}/></button>}
      </div>)}
     {canEdit&&<label className="ihk-file-upload">Datei anhängen (max. 3 MB; PDF, PNG, JPG, DOCX, TXT)
      <input type="file" disabled={fileUploading||!week} accept=".pdf,.png,.jpg,.jpeg,.docx,.txt"
       onChange={e=>{const file=e.target.files?.[0];if(file)void uploadFile(file);e.target.value=''}}/>
     </label>}
     {!week&&<p>Lege zuerst einen Eintrag an; anschließend können Dateien angehängt werden.</p>}
    </>}
   </section>
  </div>}
 </section>;
}
