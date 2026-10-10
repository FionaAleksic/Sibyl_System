'use strict';
const $=id=>document.getElementById(id);
const status=(message)=>{$('auth-message').textContent=message;};
async function csrf(){
  const res=await fetch('/api/v1/auth/csrf',{credentials:'same-origin',cache:'no-store'});
  if(!res.ok)throw new Error('Sitzung konnte nicht vorbereitet werden');
  const data=await res.json();
  if(!data.header||!data.token)throw new Error('CSRF-Schutz fehlt');
  return data;
}
async function me(){
  const res=await fetch('/api/v1/auth/me',{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'}});
  if(res.status===401)return null;
  if(!res.ok)throw new Error('Kontostatus nicht verfügbar');
  return await res.json();
}
function render(account){
  $('sign-in').hidden=!!account;
  $('password-change').hidden=!account;
  if(account){status('');$('current-password').focus();}
  else{$('username').focus();}
}
async function check(){
  try{
    const account=await me();
    if(account&&account.mustChangePassword)render(account);
    else if(account)location.replace('/');
    else render(null);
  }catch(error){status(error.message);}
}
$('login-form').addEventListener('submit',async event=>{
  event.preventDefault();
  if(location.protocol!=='https:'&&location.hostname!=='localhost'){status('HTTPS für die Anmeldung erforderlich');return;}
  const button=$('submit-login');
  button.disabled=true;status('');
  try{
    const token=await csrf();
    const data=new URLSearchParams();
    data.set('username',$('username').value.trim());
    data.set('password',$('password').value);
    const response=await fetch('/login',{
      method:'POST',credentials:'same-origin',
      headers:{'Content-Type':'application/x-www-form-urlencoded',[token.header]:token.token},
      body:data.toString(),redirect:'manual'
    });
    $('password').value='';
    if(response.status!==204)throw new Error('Benutzername oder Passwort ungültig');
    const account=await me();
    if(!account)throw new Error('Anmeldesitzung nicht gefunden');
    if(account.mustChangePassword)render(account);
    else location.replace('/');
  }catch(error){status(error.message);}
  finally{button.disabled=false;}
});
$('change-form').addEventListener('submit',async event=>{
  event.preventDefault();
  const old=$('current-password').value, newPass=$('new-password').value;
  if(newPass!==$('confirm-password').value){status('Die Passwörter stimmen nicht überein');return;}
  if(newPass.length<12||newPass==='friend'||old===newPass){status('Bitte wählen Sie ein neues Passwort mit mindestens zwölf Zeichen');return;}
  try{
    const token=await csrf();
    const res=await fetch('/api/v1/auth/password',{
      method:'POST',credentials:'same-origin',
      headers:{'Content-Type':'application/json',[token.header]:token.token},
      body:JSON.stringify({currentPassword:old,newPassword:newPass})
    });
    if(!res.ok)throw new Error('Passwort konnte nicht geändert werden');
    $('current-password').value='';$('new-password').value='';$('confirm-password').value='';
    // End the session so the new password must be used immediately.
    const logoutToken=await csrf();
    await fetch('/logout',{method:'POST',credentials:'same-origin',
      headers:{[logoutToken.header]:logoutToken.token}});
    render(null);status('Passwort gespeichert. Bitte mit dem neuen Passwort anmelden.');
  }catch(error){status(error.message);}
});
if(location.protocol!=='https:'&&location.hostname!=='localhost')
  status('Sichere HTTPS-Verbindung erforderlich.');
else check();
