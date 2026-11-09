import {useState} from 'react';
import {LayoutDashboard,Network,Server,Layers,GitBranch,Building2,LogOut,ShieldCheck,
        Package,Users,BookOpen,AppWindow,Boxes,ChevronDown,ChevronRight,Radar} from 'lucide-react';
import type {ResourceKey, Addon, Identity} from '../types/domain';
export type ViewKey='dashboard'|'admin-addons'|'admin-users'|'admin-services'|'admin-inventory'|ResourceKey|`addon:${string}`;
const infra:{key:ResourceKey;title:string;icon:typeof Server}[]=[
 {key:'sites',title:'Standorte',icon:Building2},
 {key:'racks',title:'Racks',icon:Server},
 {key:'devices',title:'Geräte / Server',icon:Network},
 {key:'vlans',title:'VLANs',icon:Layers},
 {key:'prefixes',title:'IP-Netze',icon:GitBranch}
];
export default function Navigation({active,onSelect,onLogout,identity,addons}:{active:ViewKey;onSelect:(key:ViewKey)=>void;
 onLogout:()=>void;identity:Identity;addons:Addon[]}){
 const admin=identity.roles.includes('ADMIN');
 const [inventoryOpen,setInventoryOpen]=useState(true);
 const nav=(key:ViewKey,title:string,Icon:typeof Server,child=false)=><button key={key}
  className={'nav-item '+(active===key?'selected ':'')+(child?'inventory-child':'')}
  onClick={()=>onSelect(key)}><Icon size={17}/><span>{title}</span></button>;
 const isInventory=active==='admin-inventory'||active==='admin-services'||infra.some(x=>x.key===active);
 return <aside className="sidebar">
  <div className="brand"><div className="brand-mark">S</div><div><strong>SIBYL</strong><small>SYSTEM / CORE</small></div></div>
  <div className="sidebar-group-label">ARBEITSBEREICH</div>
  <nav aria-label="Hauptnavigation">{nav('dashboard','Übersicht',LayoutDashboard)}
   {addons.filter(a=>a.moduleType!=='ad-auth'&&a.moduleType!=='inventory-discovery').map(a=>nav(`addon:${a.addonId}`,a.name,BookOpen))}
  </nav>
  {admin&&<><div className="sidebar-group-label admin-group">ADMINISTRATION</div>
   <nav aria-label="Administrationsnavigation">
    <div className="inventory-heading">
     <button type="button" onClick={()=>onSelect(addons.some(a=>a.addonId==='Sibyl.inventory')?'admin-inventory':'sites')} className={'nav-item inventory-root '+(isInventory?'selected':'')}>
      <Boxes size={17}/><span>Inventar</span>
     </button>
     <button type="button" title={inventoryOpen?'Inventar zuklappen':'Inventar aufklappen'}
      aria-label={inventoryOpen?'Inventar zuklappen':'Inventar aufklappen'} aria-expanded={inventoryOpen}
      className="inventory-expand" onClick={()=>setInventoryOpen(v=>!v)}>
      {inventoryOpen?<ChevronDown size={17}/>:<ChevronRight size={17}/>}
     </button>
    </div>
    {inventoryOpen&&<div className="inventory-children">
      {addons.some(a=>a.addonId==='Sibyl.inventory')&&nav('admin-inventory','Automatische Erkennung',Radar,true)}
      {infra.map(({key,title,icon})=>nav(key,title,icon,true))}
      {nav('admin-services','Programme & Dienste',AppWindow,true)}
    </div>}
    {nav('admin-addons','Addons',Package)}
    {nav('admin-users','Benutzer & Rechte',Users)}
   </nav></>}
  <div className="sidebar-spacer"/>
  <div className="sidebar-meta"><ShieldCheck size={15}/> INTERNES SYSTEM</div>
  <div className="user-line"><div><strong>{identity.username}</strong><small>{admin?'Administrator':'Benutzer'}</small></div>
   <button title="Abmelden" aria-label="Abmelden" onClick={onLogout}><LogOut size={18}/></button></div>
 </aside>;
}
