/* Sibyl global branding comes from the platform MySQL database.
 * Only a browser-local theme preference remains in localStorage.
 */
(function(){
  'use strict';
  const modes=['system','light','dark'];
  const labels={system:'System',light:'Hell',dark:'Dunkel'};
  const themeKey='sibyl-theme';
  function currentTheme(){
    try{const v=localStorage.getItem(themeKey);return modes.includes(v)?v:'system'}
    catch{return 'system'}
  }
  function applyTheme(next){
    if(next==='system')delete document.documentElement.dataset.theme;
    else document.documentElement.dataset.theme=next;
    try{localStorage.setItem(themeKey,next)}catch{}
    for(const button of document.querySelectorAll('[data-theme-toggle]')){
      button.textContent='Darstellung: '+labels[next];
      button.setAttribute('aria-label','Darstellung: '+labels[next]);
    }
  }
  async function loadBranding(){
    try{
      const result=await fetch('/api/v1/settings/public',{cache:'no-store',credentials:'same-origin'});
      if(!result.ok)throw new Error('settings unavailable');
      const value=await result.json();
      const name=typeof value.organization==='string'&&value.organization.trim()
        ?value.organization.slice(0,80):'Sibyl System';
      for(const x of document.querySelectorAll('[data-sibyl-brand]'))x.textContent=name;
      for(const x of document.querySelectorAll('[data-org-preview]'))x.textContent=name;
      for(const x of document.querySelectorAll('[data-org-initial]'))x.textContent=name[0].toUpperCase();
      // Only a fixed palette is accepted from the database (never arbitrary CSS).
      const accents={
        classic:['#1c5d8f','#4d91c8'],
        teal:['#137d80','#53b3ac'],
        violet:['#6751a5','#a591eb']
      };
      if(Object.hasOwn(accents,value.accent)){
        const [light,dark]=accents[value.accent];
        const style=document.createElement('style');
        style.textContent=':root{--accent:'+light+'} html[data-theme=dark]{--accent:'+dark+'}';
        document.head.append(style);
      }
    }catch{
      // No connection: the neutral, generic default design remains available.
    }
  }
  document.addEventListener('DOMContentLoaded',()=>{
    applyTheme(currentTheme());
    document.querySelectorAll('[data-theme-toggle]').forEach(button=>{
      button.addEventListener('click',()=>{
        const next=modes[(modes.indexOf(currentTheme())+1)%modes.length];
        applyTheme(next);
      });
    });
    loadBranding();
    fetch('/api/v1/auth/me',{credentials:'same-origin',cache:'no-store'}).then(async response=>{
      if(!response.ok){location.replace('/login.html');return;}
      const account=await response.json();
      if(account.mustChangePassword){location.replace('/login.html?change=1');return;}
      const nav=document.querySelector('.module-nav');
      if(account.role==='ADMIN'&&nav&&
          !nav.querySelector('a[href*="admin.html"]')){
        for(const [path,label] of [['/addons.html','Addon-Browser'],['/admin.html','Admin']]){
          const a=document.createElement('a');a.href=path;a.textContent=label;nav.append(a);
        }
      }
      const bar=document.querySelector('.topbar');
      if(bar&&!document.querySelector('[data-logout]')){
        const logout=document.createElement('button');
        logout.type='button';logout.className='theme-toggle';
        logout.textContent='Abmelden';logout.dataset.logout='true';
        logout.addEventListener('click',async()=>{
          const response=await fetch('/api/v1/auth/csrf',{credentials:'same-origin',cache:'no-store'});
          if(response.ok){
            const csrf=await response.json();
            await fetch('/logout',{method:'POST',credentials:'same-origin',
              headers:{[csrf.header]:csrf.token}});
          }
          location.replace('/login.html');
        });
        bar.append(logout);
      }
    }).catch(()=>{location.replace('/login.html');});
  });
})();
