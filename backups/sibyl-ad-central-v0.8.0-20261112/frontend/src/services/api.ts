import type {Credentials, Item, ResourceKey, DashboardCounts, Identity, Addon, AddonDocument, UserAccount,ServiceInfo} from '../types/domain';
export class ApiError extends Error {constructor(public status:number, message:string){super(message);}}
function authorization(c:Credentials) {
 const bytes=new TextEncoder().encode(c.username+':'+c.password);
 return 'Basic '+btoa(Array.from(bytes,b=>String.fromCharCode(b)).join(''));
}
export async function request<T>(path:string, credentials:Credentials, options:RequestInit={}):Promise<T> {
 const response=await fetch(path,{...options,headers:{'Authorization':authorization(credentials),'Accept':'application/json',...(options.body?{'Content-Type':'application/json'}:{}),...options.headers},cache:'no-store'});
 if(!response.ok) {
  let message='Anfrage fehlgeschlagen ('+response.status+')';
  try {const payload:unknown=await response.json();if(payload && typeof payload==='object'){
   if('detail' in payload && typeof payload.detail==='string')message=payload.detail;
   else if('error' in payload && typeof payload.error==='string')message=payload.error;
  }}catch { /* no JSON */ }
  throw new ApiError(response.status,message);
 }
 const text=await response.text();
 return text ? JSON.parse(text) as T : undefined as T;
}
export const api={
 login:(c:Credentials)=>request<Identity>('/api/v1/auth/me',c),
 dashboard:(c:Credentials)=>request<DashboardCounts>('/api/v1/dashboard',c),
 list:(resource:ResourceKey,c:Credentials)=>request<Item[]>('/api/v1/'+resource,c),
 create:(resource:ResourceKey,c:Credentials,record:Item)=>request<Item>('/api/v1/'+resource,c,{method:'POST',body:JSON.stringify(record)}),
 update:(resource:ResourceKey,c:Credentials,id:number,record:Item)=>request<Item>('/api/v1/'+resource+'/'+id,c,{method:'PUT',body:JSON.stringify(record)}),
 remove:(resource:ResourceKey,c:Credentials,id:number)=>request<void>('/api/v1/'+resource+'/'+id,c,{method:'DELETE'}),
 addons:(c:Credentials)=>request<Addon[]>('/api/v1/addons',c),
 adminAddons:(c:Credentials)=>request<Addon[]>('/api/v1/admin/addons',c),
 getAddonConfig:(c:Credentials,id:string)=>request<{addonId:string;fields:{key:string;label:string;type:'number'|'boolean'|'text';description:string;defaultValue:string;min:number;max:number;options:string[]}[];values:Record<string,string>}>('/api/v1/admin/addons/'+encodeURIComponent(id)+'/settings',c),
 saveAddonConfig:(c:Credentials,id:string,values:Record<string,string>)=>request<{addonId:string;fields:{key:string;label:string;type:'number'|'boolean'|'text';description:string;defaultValue:string;min:number;max:number;options:string[]}[];values:Record<string,string>}>('/api/v1/admin/addons/'+encodeURIComponent(id)+'/settings',c,{method:'PUT',body:JSON.stringify(values)}),
 adConnections:(c:Credentials)=>request<{id:number;label:string;domain:string;userBaseDn:string;discoveryMode:'dns'|'static';serverHostname:string|null;servicePrincipal:string|null;hasSecret:boolean;adminGroupDns:string;userGroupDns:string;enabled:boolean;loginEnabled:boolean;syncEnabled:boolean;netbiosName:string|null}[]>('/api/v1/admin/ad-connections',c),
 addAdConnection:(c:Credentials,data:unknown)=>request<void>('/api/v1/admin/ad-connections',c,{method:'POST',body:JSON.stringify(data)}),
 updateAdConnection:(c:Credentials,id:number,data:unknown)=>request<void>('/api/v1/admin/ad-connections/'+id,c,{method:'PUT',body:JSON.stringify(data)}),
 removeAdConnection:(c:Credentials,id:number)=>request<void>('/api/v1/admin/ad-connections/'+id,c,{method:'DELETE'}),
 testAdConnection:(c:Credentials,id:number)=>request<{tlsVerified:boolean;serviceBindVerified:boolean;message:string;endpoint:string}>('/api/v1/admin/ad-connections/'+id+'/test',c,{method:'POST'}),
 setAddonEnabled:(c:Credentials,id:string,enabled:boolean)=>
  request<Addon>('/api/v1/admin/addons/'+encodeURIComponent(id)+'/enabled',c,{method:'PATCH',body:JSON.stringify({enabled})}),
 documents:(c:Credentials,id:string)=>request<AddonDocument[]>('/api/v1/addons/'+encodeURIComponent(id)+'/documents',c),
 addDocument:(c:Credentials,id:string,doc:{title:string;body:string})=>
  request<void>('/api/v1/addons/'+encodeURIComponent(id)+'/documents',c,{method:'POST',body:JSON.stringify(doc)}),
 editDocument:(c:Credentials,id:string,docId:number,doc:{title:string;body:string})=>
  request<void>('/api/v1/addons/'+encodeURIComponent(id)+'/documents/'+docId,c,{method:'PUT',body:JSON.stringify(doc)}),
 deleteDocument:(c:Credentials,id:string,docId:number)=>
  request<void>('/api/v1/addons/'+encodeURIComponent(id)+'/documents/'+docId,c,{method:'DELETE'}),
 services:(c:Credentials)=>request<ServiceInfo[]>('/api/v1/admin/services',c),
 registerService:(c:Credentials,service:Omit<ServiceInfo,'id'>)=>request<void>('/api/v1/admin/services',c,{method:'POST',body:JSON.stringify(service)}),
 removeService:(c:Credentials,id:number)=>request<void>('/api/v1/admin/services/'+id,c,{method:'DELETE'}),
 discoveredAssets:(c:Credentials)=>request<{id:number;hostname:string;ipv4:string|null;assetType:string;source:string;details:string;firstSeen:string;lastSeen:string}[]>('/api/v1/admin/inventory/discovered',c),
 discoveryStatus:(c:Credentials)=>request<{domain:string;controllers:number;observations:number;lastSuccess:string;lastError:string;enabled:boolean;scanMode:string}>('/api/v1/admin/inventory/status',c),
 refreshDiscovery:(c:Credentials)=>request<{domain:string;controllers:number;observations:number;lastSuccess:string;lastError:string;enabled:boolean;scanMode:string}>('/api/v1/admin/inventory/refresh',c,{method:'POST'}),
 users:(c:Credentials)=>request<UserAccount[]>('/api/v1/admin/users',c),
 createUser:(c:Credentials,u:{username:string;password:string;role:'USER'|'ADMIN'})=>
  request<UserAccount>('/api/v1/admin/users',c,{method:'POST',body:JSON.stringify(u)}),
 updateUser:(c:Credentials,username:string,u:{role:'USER'|'ADMIN';enabled:boolean})=>
  request<UserAccount>('/api/v1/admin/users/'+encodeURIComponent(username),c,{method:'PATCH',body:JSON.stringify(u)})
};
