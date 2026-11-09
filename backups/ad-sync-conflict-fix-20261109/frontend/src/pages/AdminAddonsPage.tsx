import {useCallback,useEffect,useState} from 'react';
import {Settings2,RefreshCw,Plus,Save,Trash2,ShieldCheck} from 'lucide-react';
import type {Addon,Credentials} from '../types/domain';
import {api} from '../services/api';
import ReportBookAdminSettings from '../components/ReportBookAdminSettings';
import PlanningAdminSettings from '../components/PlanningAdminSettings';

type Field={key:string;label:string;type:'number'|'boolean'|'text';description:string;defaultValue:string;min:number;max:number;options:string[]};
type Config={addonId:string;fields:Field[];values:Record<string,string>};
type Directory={id:number;label:string;domain:string;userBaseDn:string;discoveryMode:'dns'|'static';
 serverHostname:string|null;servicePrincipal:string|null;hasSecret:boolean;adminGroupDns:string;
 userGroupDns:string;enabled:boolean};
type DirectoryInput=Omit<Directory,'id'|'hasSecret'> & {servicePassword:string};
const blankDirectory:DirectoryInput={label:'',domain:'',userBaseDn:'',discoveryMode:'dns',
 serverHostname:'',servicePrincipal:'',servicePassword:'',adminGroupDns:'',userGroupDns:'',enabled:false};
const message=(e:unknown)=>e instanceof Error?e.message:'Die Anfrage ist fehlgeschlagen.';

export default function AdminAddonsPage({credentials,onChanged}:{credentials:Credentials;onChanged:()=>void}){
 const [addons,setAddons]=useState<Addon[]>([]);
 const [selected,setSelected]=useState('Sibyl.ad');
 const [config,setConfig]=useState<Config|null>(null);
 const [values,setValues]=useState<Record<string,string>>({});
 const [directories,setDirectories]=useState<Directory[]>([]);
 const [activeDirectory,setActiveDirectory]=useState<number|null>(null);
 const [form,setForm]=useState<DirectoryInput>(blankDirectory);
 const [busy,setBusy]=useState(false);
 const [error,setError]=useState('');
 const [notice,setNotice]=useState('');
 const [testMessage,setTestMessage]=useState('');
 const refresh=useCallback(async()=>{
  try{
   const data=await api.adminAddons(credentials);
   setAddons(data);
   setSelected(old=>data.some(a=>a.addonId===old)?old:data[0]?.addonId??'');
   setError('');
  }catch(e){setError(message(e))}
 },[credentials]);
 useEffect(()=>{void refresh()},[refresh]);
 useEffect(()=>{
  if(!selected)return;
  let active=true;
  setConfig(null);setError('');setNotice('');
  api.getAddonConfig(credentials,selected).then(c=>{if(active){setConfig(c);setValues(c.values)}})
   .catch(e=>{if(active)setError(message(e))});
  if(selected==='Sibyl.ad'){
   api.adConnections(credentials).then(list=>{if(active)setDirectories(list)})
    .catch(e=>{if(active)setError(message(e))});
  }
  return()=>{active=false};
 },[credentials,selected]);
 async function toggle(addon:Addon){
  setBusy(true);setError('');setNotice('');
  try{
   await api.setAddonEnabled(credentials,addon.addonId,!addon.enabled);
   await refresh();onChanged();setNotice('Addon-Status aktualisiert.');
  }catch(e){setError(message(e))}finally{setBusy(false)}
 }
 async function saveSettings(e:React.FormEvent){
  e.preventDefault();setBusy(true);setError('');
  try{const updated=await api.saveAddonConfig(credentials,selected,values);setConfig(updated);
   setValues(updated.values);setNotice('Einstellungen gespeichert und übernommen.')}
  catch(e){setError(message(e))}finally{setBusy(false)}
 }
 function editDirectory(dir:Directory){
  setActiveDirectory(dir.id);
  setForm({...dir,serverHostname:dir.serverHostname??'',servicePrincipal:dir.servicePrincipal??'',servicePassword:''});
  setTestMessage('');setError('');setNotice('');
 }
 function addDirectory(){
  setActiveDirectory(null);setForm({...blankDirectory});setTestMessage('');setError('');setNotice('');
 }
 function patch<K extends keyof DirectoryInput>(key:K,value:DirectoryInput[K]){
  setForm(f=>({...f,[key]:value}));
 }
 async function saveDirectory(e:React.FormEvent){
  e.preventDefault();setBusy(true);setError('');
  try{
   if(activeDirectory===null) await api.addAdConnection(credentials,form);
   else await api.updateAdConnection(credentials,activeDirectory,form);
   const list=await api.adConnections(credentials);setDirectories(list);
   setActiveDirectory(null);setForm({...blankDirectory});
   setNotice('AD-Verbindung gespeichert. Zum Prüfen „Verbindung testen“ auswählen.');
  }catch(e){setError(message(e))}finally{setBusy(false)}
 }
 async function removeDirectory(id:number){
  if(!window.confirm('Diese AD-Verbindung und das gespeicherte Servicekonto entfernen?'))return;
  setBusy(true);setError('');
  try{await api.removeAdConnection(credentials,id);setDirectories(await api.adConnections(credentials));
   if(activeDirectory===id)addDirectory();setNotice('AD-Verbindung entfernt.')}
  catch(e){setError(message(e))}finally{setBusy(false)}
 }
 async function testDirectory(id:number){
  setBusy(true);setTestMessage('');setError('');
  try{const response=await api.testAdConnection(credentials,id);
   setTestMessage(response.message+' – '+response.endpoint)}
  catch(e){setError(message(e))}finally{setBusy(false)}
 }
 const addon=addons.find(a=>a.addonId===selected);
 return <div className="addon-manager">
  <div className="page-heading"><div><div className="eyebrow">SIBYL / ADMINISTRATION</div>
   <h1>Addons</h1>
   <p>Alle Addons sind fest in Sibyl integriert. Hier verwaltest du ausschließlich die Aktivierung und sämtliche Addon-Einstellungen; es gibt keine externe Installation.</p></div></div>
  {error&&<div className="error" role="alert">{error}</div>}
  {notice&&<div className="success-note" role="status">{notice}</div>}
  <div className="addon-manager-layout">
   <section className="addon-manager-list" aria-label="Installierte Addons">
    <div className="eyebrow">IM CORE ENTHALTENE ADDONS</div>
    {addons.map(a=><button type="button" key={a.addonId}
     onClick={()=>setSelected(a.addonId)}
     className={'addon-selection '+(selected===a.addonId?'selected':'')}>
     <strong>{a.name}</strong><span>{a.addonId} · {a.version}</span>
     <small>{a.enabled?'Aktiv':'Deaktiviert'}</small>
    </button>)}
   </section>
   <section className="addon-manager-details">
    {addon&&<><div className="addon-panel-heading"><div><div className="eyebrow">KONFIGURATION / {addon.addonId}</div>
      <h2>{addon.name}</h2><p>{addon.description}</p></div>
     <button className="secondary" disabled={busy} onClick={()=>void toggle(addon)}>
      {addon.enabled?'Deaktivieren':'Aktivieren'}</button></div>
     <div className="addon-module-meta"><span>Im Sibyl-Core enthalten · Version {addon.version}</span><span>{addon.enabled?'Modul aktiv':'Modul deaktiviert'}</span></div>
     {config&&config.fields.length>0&&<form className="addon-settings-form" onSubmit={e=>void saveSettings(e)}>
      <h3><Settings2 size={18}/> Einstellungen</h3>
      {config.fields.map(field=><label key={field.key}>
       <strong>{field.label}</strong><small>{field.description}</small>
       {field.type==='boolean'?
        <select value={values[field.key]??field.defaultValue} onChange={e=>setValues(x=>({...x,[field.key]:e.target.value}))}>
         <option value="true">Aktiviert</option><option value="false">Deaktiviert</option></select>
        :field.type==='text'?<textarea rows={3} maxLength={1024}
          value={values[field.key]??field.defaultValue}
          onChange={e=>setValues(x=>({...x,[field.key]:e.target.value}))}/>
        :<input type="number" required min={field.min} max={field.max}
          value={values[field.key]??field.defaultValue}
          onChange={e=>setValues(x=>({...x,[field.key]:e.target.value}))}/>}
      </label>)}
      <button type="submit" className="primary" disabled={busy}><Save size={16}/> Einstellungen speichern</button>
     </form>}
     {addon.addonId==='Sibyl.report-book'&&<ReportBookAdminSettings credentials={credentials}
      groupsReady={Boolean(config?.values.traineeGroupDns?.trim())&&Boolean(config?.values.trainerGroupDns?.trim())}
      onConfigureAd={()=>{setSelected('Sibyl.ad');window.scrollTo({top:0,behavior:'smooth'})}}/>}
     {addon.addonId==='Sibyl.planning'&&<PlanningAdminSettings credentials={credentials}/>} 
     {addon.addonId==='Sibyl.ad'&&<>
      <div className="addon-directory-heading"><div><h3>Active-Directory-Verbindungen</h3>
       <p>Mehrere Domänen mit eigenen Anmelde-, Gruppen- und Servicekontoeinstellungen.</p></div>
       <button className="secondary" onClick={addDirectory} disabled={busy}><Plus size={16}/> Verbindung hinzufügen</button></div>
      <div className="discovery-note">Ohne eigene Konfiguration bleibt die bisherige, automatisch über DNS erkannte Hauptdomäne aktiv.
       Zusätzliche ADs können über ihren UPN (z. B. benutzer@firma.example) genutzt werden.
       Servicekonten sind für spätere lesende AD-Funktionen vorgesehen, nicht für die Passwortprüfung beim Login.</div>
      {directories.map(dir=><div className="addon-directory-row" key={dir.id}>
       <div><strong>{dir.label}</strong><small>{dir.domain} · {dir.discoveryMode==='dns'?'DNS-SRV':'Fester LDAPS-Server'}
        · {dir.enabled?' Aktiv':' Deaktiviert'}{dir.hasSecret?' · Zugangsdaten hinterlegt':''}</small></div>
       <div className="addon-directory-actions">
        <button className="secondary" disabled={busy} onClick={()=>void testDirectory(dir.id)}><RefreshCw size={14}/> Testen</button>
        <button className="secondary" disabled={busy} onClick={()=>editDirectory(dir)}>Bearbeiten</button>
        <button className="secondary" disabled={busy} aria-label={dir.label+' entfernen'}
         onClick={()=>void removeDirectory(dir.id)}><Trash2 size={15}/></button>
       </div></div>)}
      {testMessage&&<div className="success-note" role="status"><ShieldCheck size={16}/> {testMessage}</div>}
      <form className="addon-ad-form" onSubmit={e=>void saveDirectory(e)} autoComplete="off">
       <h3>{activeDirectory===null?'AD-Verbindung hinzufügen':'AD-Verbindung bearbeiten'}</h3>
       <div className="admin-fields">
        <label>Name der Verbindung<input required maxLength={100} value={form.label}
         onChange={e=>patch('label',e.target.value)} placeholder="Firmenverzeichnis" /></label>
        <label>Domäne (DNS)<input required value={form.domain}
         onChange={e=>patch('domain',e.target.value)} placeholder="firma.example"/></label>
        <label className="wide">Benutzersuchbasis (Base DN)<input required value={form.userBaseDn}
         onChange={e=>patch('userBaseDn',e.target.value)} placeholder="OU=Users,DC=firma,DC=example"/></label>
        <label>Servererkennung<select value={form.discoveryMode} onChange={e=>patch('discoveryMode',e.target.value as 'dns'|'static')}>
         <option value="dns">DNS-SRV automatisch</option><option value="static">Festen LDAPS-Server verwenden</option></select></label>
        {form.discoveryMode==='static'&&<label>LDAPS-Hostname<input required value={form.serverHostname??''}
         onChange={e=>patch('serverHostname',e.target.value)} placeholder="dc01.firma.example"/></label>}
        <label className="wide">Servicekonto (UPN oder DN)<input value={form.servicePrincipal??''}
         onChange={e=>patch('servicePrincipal',e.target.value)} placeholder="svc-sibyl@firma.example"/></label>
        <label className="wide">Servicekonto-Passwort (verschlüsselt gespeichert)<input type="password" autoComplete="new-password"
         value={form.servicePassword} onChange={e=>patch('servicePassword',e.target.value)}
         placeholder={activeDirectory!==null?'Leer lassen, um bestehendes Passwort beizubehalten':'Optional: Passwort eingeben'}/></label>
        <label className="wide">Administratorgruppen (ein vollständiger Gruppen-DN pro Zeile)<textarea rows={3}
         value={form.adminGroupDns} onChange={e=>patch('adminGroupDns',e.target.value)}
         placeholder="CN=IT-Admins,OU=Groups,DC=firma,DC=example"/></label>
        <label className="wide">Benutzergruppen (haben Vorrang vor Administratorgruppen)<textarea rows={3}
         value={form.userGroupDns} onChange={e=>patch('userGroupDns',e.target.value)}
         placeholder="CN=Employees,OU=Groups,DC=firma,DC=example"/></label>
        <label className="checkline"><input type="checkbox" checked={form.enabled}
         onChange={e=>patch('enabled',e.target.checked)}/> Verbindung für die Anmeldung aktivieren</label>
       </div>
       <div className="addon-directory-actions">
        <button className="primary" type="submit" disabled={busy}><Save size={16}/> Verbindung speichern</button>
        {activeDirectory!==null&&<button className="secondary" type="button" onClick={addDirectory}>Abbrechen</button>}
       </div>
      </form>
     </>}
    </>}
   </section>
  </div>
 </div>;
}
