import './styles/addons.css';
import './styles/inventory.css';
import './styles/workflow.css';
import {useEffect,useState} from 'react';
import {Menu} from 'lucide-react';
import type {Credentials,ResourceKey,Identity,Addon} from './types/domain';
import {api} from './services/api';
import Navigation from './components/Navigation';
import type {ViewKey} from './components/Navigation';
import Login from './components/Login';
import DashboardPage from './pages/DashboardPage';
import ResourcePage from './pages/ResourcePage';
import UserDashboard from './pages/UserDashboard';
import DocumentsPage from './pages/DocumentsPage';
import AdminAddonsPage from './pages/AdminAddonsPage';
import AdminUsersPage from './pages/AdminUsersPage';
import AdminServicesPage from './pages/AdminServicesPage';
import InventoryDiscoveryPage from './pages/InventoryDiscoveryPage';
import PlanningPage from './pages/PlanningPage';
import ReportBookPage from './pages/ReportBookPage';
const resourceKeys=['sites','racks','devices','vlans','prefixes'];
export default function App(){
 const [credentials,setCredentials]=useState<Credentials|null>(null);
 const [identity,setIdentity]=useState<Identity|null>(null);
 const [active,setActive]=useState<ViewKey>('dashboard');
 const [addons,setAddons]=useState<Addon[]>([]);
 const [error,setError]=useState('');
 useEffect(()=>{if(!credentials)return;
  let cancelled=false;
  api.addons(credentials).then(list=>{if(!cancelled)setAddons(list)}).catch(()=>{if(!cancelled)setError('Addons konnten nicht geladen werden.')});
  return()=>{cancelled=true};
 },[credentials]);
 function logout(){setCredentials(null);setIdentity(null);setAddons([]);setActive('dashboard');setError('');}
 if(!credentials||!identity)return <Login onSuccess={(c,i)=>{setCredentials(c);setIdentity(i)}}/>;
 const currentCredentials:Credentials=credentials;
 const currentIdentity:Identity=identity;
 const admin=currentIdentity.roles.includes('ADMIN');
 const currentAddon=active.startsWith('addon:')?addons.find(a=>a.addonId===active.substring(6)):undefined;
 const changePage=(view:ViewKey)=>{
  if(view.startsWith('addon:')&&!addons.some(a=>a.addonId===view.substring(6)))return;
  if(!admin && view!=='dashboard'&&!view.startsWith('addon:'))return;
  setActive(view);
 };
 function page(){
  if(active==='dashboard')return admin?<DashboardPage credentials={currentCredentials} onNavigate={changePage}/>:<UserDashboard addons={addons} onNavigate={changePage}/>;
  if(currentAddon){
   if(currentAddon.moduleType==='documents')return <DocumentsPage key={currentAddon.addonId} addon={currentAddon} credentials={currentCredentials} admin={admin}/>;
   if(currentAddon.moduleType==='planning')return <PlanningPage credentials={currentCredentials} admin={admin}/>;
   if(currentAddon.moduleType==='report-book')return <ReportBookPage credentials={currentCredentials} admin={admin}/>;
   return <div className="info-panel"><h2>{currentAddon.name}</h2><p>Für diesen Provider steht keine eigene Benutzeroberfläche zur Verfügung.</p></div>;
  }
  if(!admin)return <UserDashboard addons={addons} onNavigate={changePage}/>;
  if(active==='admin-addons')return <AdminAddonsPage credentials={currentCredentials} onChanged={()=>api.addons(currentCredentials).then(setAddons).catch(()=>setError('Addon-Menü konnte nicht aktualisiert werden.'))}/>;
  if(active==='admin-users')return <AdminUsersPage credentials={currentCredentials} identity={currentIdentity}/>;
  if(active==='admin-inventory')return <InventoryDiscoveryPage credentials={currentCredentials}/>;
  if(active==='admin-services')return <AdminServicesPage credentials={currentCredentials}/>;
  if(resourceKeys.includes(active))return <ResourcePage key={active} resource={active as ResourceKey} credentials={currentCredentials}/>;
  return <DashboardPage credentials={currentCredentials} onNavigate={changePage}/>;
 }
 return <div className="shell"><Navigation active={active} identity={currentIdentity} addons={addons} onSelect={changePage} onLogout={logout}/>
  <main className="main"><header className="topbar"><span><Menu size={16}/> SIBYL / {admin?'ADMINISTRATION':'ARBEITSBEREICH'}</span><span className="topbar-right">ADDON CORE 0.7.0 <i className="live-indicator"/></span></header>
  <div className="content">{error&&<div className="error" role="alert">{error}</div>}{page()}</div>
  <footer className="footer">SIBYL SYSTEM / INTERNAL <span>ROLE-BASED ACCESS CONTROL</span></footer>
 </main></div>;
}
