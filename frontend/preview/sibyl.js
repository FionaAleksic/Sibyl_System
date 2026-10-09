/* Shared, browser-local UI preferences. Does not grant administration privileges. */
(function(){
  'use strict';
  const modes=['system','light','dark'];
  const labels={system:'System',light:'Hell',dark:'Dunkel'};
  const KEY_THEME='sibyl-theme';
  const KEY_PREVIEW='sibyl-admin-local-preview-v1';
  function readPreview(){
    try {
      const value=JSON.parse(localStorage.getItem(KEY_PREVIEW)||'{}');
      if(!value||typeof value!=='object'||Array.isArray(value)) return {};
      return value;
    }catch{return {};}
  }
  function readTheme(){
    try {const v=localStorage.getItem(KEY_THEME);return modes.includes(v)?v:'system';}
    catch{return 'system';}
  }
  function setTheme(next){
    if(!modes.includes(next))return;
    if(next==='system')delete document.documentElement.dataset.theme;
    else document.documentElement.dataset.theme=next;
    try {localStorage.setItem(KEY_THEME,next)}catch{}
    for(const btn of document.querySelectorAll('[data-theme-toggle]')){
      btn.textContent='Darstellung: '+labels[next];
      btn.setAttribute('aria-label','Darstellung: '+labels[next]+'. Klicken zum Wechseln.');
    }
    document.dispatchEvent(new CustomEvent('sibyl-theme-changed',{detail:next}));
  }
  function brand(){
    const cfg=readPreview();
    const name=typeof cfg.organization==='string'&&cfg.organization.trim()?cfg.organization.trim().slice(0,80):'Sibyl System';
    const display= name;
    for(const e of document.querySelectorAll('[data-sibyl-brand]'))e.textContent=display;
    for(const e of document.querySelectorAll('[data-org-preview]'))e.textContent=display;
    for(const e of document.querySelectorAll('[data-org-initial]'))e.textContent=display.trim().slice(0,1).toUpperCase()||'S';
  }
  document.addEventListener('DOMContentLoaded',()=>{
    setTheme(readTheme());
    for(const btn of document.querySelectorAll('[data-theme-toggle]')){
      btn.addEventListener('click',()=>{const old=readTheme();setTheme(modes[(modes.indexOf(old)+1)%modes.length]);});
    }
    brand();
  });
  window.addEventListener('storage',event=>{
    if(event.key===KEY_THEME)setTheme(readTheme());
    if(event.key===KEY_PREVIEW)brand();
  });
  window.SibylPreview=Object.freeze({
    read:readPreview,
    save(value){
      if(!value||typeof value!=='object')throw new TypeError('Invalid preview');
      localStorage.setItem(KEY_PREVIEW,JSON.stringify(value));
      brand();
    },
    reset(){localStorage.removeItem(KEY_PREVIEW);brand();},
    refresh:brand,
    key:KEY_PREVIEW
  });
})();
