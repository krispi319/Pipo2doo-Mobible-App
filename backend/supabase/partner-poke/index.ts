const json=(body:unknown,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const enc=new TextEncoder();
const b64=(bytes:Uint8Array)=>btoa(String.fromCharCode(...bytes)).replaceAll('+','-').replaceAll('/','_').replaceAll('=','');
class SenderAuthError extends Error {readonly reason:string;constructor(reason:string){super(reason);this.reason=reason;}}
async function accessToken(sa:{client_email:string;private_key:string}) {
 const now=Math.floor(Date.now()/1000);
 const head=b64(enc.encode(JSON.stringify({alg:'RS256',typ:'JWT'})));
 const body=b64(enc.encode(JSON.stringify({iss:sa.client_email,scope:'https://www.googleapis.com/auth/firebase.messaging',aud:'https://oauth2.googleapis.com/token',iat:now,exp:now+3600})));
 let key:CryptoKey;
 try {
  const pem=sa.private_key.replaceAll('\\n','\n').replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g,'');
  key=await crypto.subtle.importKey('pkcs8',Uint8Array.from(atob(pem),c=>c.charCodeAt(0)),{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['sign']);
 }catch{throw new SenderAuthError('invalid_private_key');}
 const signature=await crypto.subtle.sign('RSASSA-PKCS1-v1_5',key,enc.encode(head+'.'+body));
 let r:Response;
 try {r=await fetch('https://oauth2.googleapis.com/token',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion:head+'.'+body+'.'+b64(new Uint8Array(signature))}),signal:AbortSignal.timeout(10000)});}
 catch{throw new SenderAuthError('oauth_unreachable');}
 const result=await r.json().catch(()=>({}));
 if(!r.ok) {
  const detail=String(result.error_description??'').toLowerCase();
  const reason=detail.includes('signature')?'invalid_jwt_signature':detail.includes('account not found')||detail.includes('disabled')?'service_account_unavailable':detail.includes('iat')||detail.includes('short-lived')?'server_clock':String(result.error??'oauth_rejected');
  throw new SenderAuthError(reason);
 }
 if(typeof result.access_token!=='string')throw new SenderAuthError('oauth_bad_response');
 return result.access_token as string;
}
Deno.serve(async req=>{
 if(req.method!=='POST')return json({status:'method_not_allowed'},405);
 const url=Deno.env.get('SUPABASE_URL'),anon=Deno.env.get('SUPABASE_ANON_KEY'),admin=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');if(!url||!anon||!admin)return json({status:'server_unavailable'},503);
 const authorization=req.headers.get('Authorization')??'';if(!authorization.startsWith('Bearer '))return json({status:'unauthorized'},401);
 let sender:string;try{const r=await fetch(url+'/auth/v1/user',{headers:{apikey:anon,Authorization:authorization},signal:AbortSignal.timeout(10000)});if(!r.ok)return json({status:'unauthorized'},401);const u=await r.json();if(!uuid.test(u.id??''))return json({status:'unauthorized'},401);sender=u.id;}catch{return json({status:'auth_unavailable'},503);}
 const secret=Deno.env.get('FCM_SERVICE_ACCOUNT_JSON');if(!secret)return json({status:'sender_not_configured',message:'Save the complete Firebase service-account JSON as FCM_SERVICE_ACCOUNT_JSON in Supabase Edge Function secrets.'},503);
 let input:{task_id:string;request_id:string};let sa:{project_id:string;client_email:string;private_key:string;type:string};try{const text=await req.text();if(text.length>4096)return json({status:'invalid_request'},400);input=JSON.parse(text);if(!uuid.test(input.task_id??'')||!uuid.test(input.request_id??''))return json({status:'invalid_request'},400);sa=JSON.parse(secret);if(sa.type!=='service_account'||sa.project_id!=='pipo2doo-bfc83'||!sa.client_email||!sa.private_key)return json({status:'sender_not_configured',message:'Save the complete Firebase service-account JSON as FCM_SERVICE_ACCOUNT_JSON in Supabase Edge Function secrets.'},503);}catch{return json({status:'invalid_request_or_config'},400);}
 const headers={apikey:admin,Authorization:'Bearer '+admin,'Content-Type':'application/json'};
 const rpc=async(name:string,payload:unknown,user=false)=>{const r=await fetch(url+'/rest/v1/rpc/'+name,{method:'POST',headers:user?{apikey:anon,Authorization:authorization,'Content-Type':'application/json'}:headers,body:JSON.stringify(payload),signal:AbortSignal.timeout(10000)});if(!r.ok)throw new Error('rpc_failed');return await r.json();};
 let googleToken:string;try{googleToken=await accessToken(sa);}catch(e){return json({status:'sender_auth_failed',reason:e instanceof SenderAuthError?e.reason:'oauth_rejected',message:'Firebase sending credential could not be authorized. Check the current service-account key in Supabase.'},503);}
 let event:{id?:string;status:string};try{event=await rpc('request_partner_poke',{p_task:input.task_id,p_request:input.request_id},true);}catch{return json({status:'request_denied_or_unavailable'},403);}if(event.status!=='queued')return json(event);
 let claim:{status:string;event:{id:string;space_id:string;recipient_id:string;expires_at:string};tokens:string[]};try{claim=await rpc('claim_partner_poke',{p_event:input.request_id,p_sender:sender});}catch{return json({status:'pending',request_id:input.request_id},503);}if(claim.status!=='claimed')return json({status:claim.status,request_id:input.request_id});
 let accepted=0,uncertain=false;for(const token of claim.tokens){try{const r=await fetch('https://fcm.googleapis.com/v1/projects/'+sa.project_id+'/messages:send',{method:'POST',headers:{Authorization:'Bearer '+googleToken,'Content-Type':'application/json'},body:JSON.stringify({message:{token,data:{type:'partner_poke',event_id:claim.event.id,space_id:claim.event.space_id,recipient_id:claim.event.recipient_id,expires_at:claim.event.expires_at},android:{priority:'HIGH',ttl:'300s',collapse_key:claim.event.id}}}),signal:AbortSignal.timeout(10000)});if(r.ok)accepted++;else{const body=await r.json().catch(()=>null);if(body?.error?.details?.some((d:{errorCode?:string})=>d.errorCode==='UNREGISTERED'))await rpc('remove_invalid_push_token',{p_token:token}).catch(()=>{});}}catch{uncertain=true;}}
 const state=accepted>0?'provider_accepted':uncertain?'unknown':'failed';try{const r=await fetch(url+'/rest/v1/poke_events?id=eq.'+input.request_id+'&status=eq.sending',{method:'PATCH',headers:{...headers,Prefer:'return=representation'},body:JSON.stringify({status:state,updated_at:new Date().toISOString()}),signal:AbortSignal.timeout(10000)});if(!r.ok)return json({status:'unknown',request_id:input.request_id},503);const rows=await r.json();if(!Array.isArray(rows)||rows.length===0)return json({status:'canceled_or_changed',request_id:input.request_id,delivery_verified:false});}catch{return json({status:'unknown',request_id:input.request_id},503);}
 return json({status:state,request_id:input.request_id,delivery_verified:false});
});
