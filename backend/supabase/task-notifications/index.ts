const json=(body:unknown,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json','Cache-Control':'no-store'}});
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const enc=new TextEncoder();
const b64=(bytes:Uint8Array)=>btoa(String.fromCharCode(...bytes)).replaceAll('+','-').replaceAll('/','_').replaceAll('=','');
async function accessToken(sa:{client_email:string;private_key:string}){const now=Math.floor(Date.now()/1000);const head=b64(enc.encode(JSON.stringify({alg:'RS256',typ:'JWT'})));const body=b64(enc.encode(JSON.stringify({iss:sa.client_email,scope:'https://www.googleapis.com/auth/firebase.messaging',aud:'https://oauth2.googleapis.com/token',iat:now,exp:now+3600})));const pem=sa.private_key.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g,'');const key=await crypto.subtle.importKey('pkcs8',Uint8Array.from(atob(pem),c=>c.charCodeAt(0)),{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['sign']);const sig=await crypto.subtle.sign('RSASSA-PKCS1-v1_5',key,enc.encode(head+'.'+body));const r=await fetch('https://oauth2.googleapis.com/token',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion:head+'.'+body+'.'+b64(new Uint8Array(sig))}),signal:AbortSignal.timeout(10000)});if(!r.ok)throw new Error('sender_auth_failed');const t=await r.json();if(typeof t.access_token!=='string')throw new Error('sender_auth_failed');return t.access_token;}

// Cron-only worker. Deploy with Verify JWT OFF; this endpoint checks its own secret.
Deno.serve(async req => {
 if(req.method !== 'POST') return json({status:'method_not_allowed'},405);
 const supplied=req.headers.get('X-Task-Secret') || '';
 if(supplied.length<32 || supplied.length>256) return json({status:'unauthorized'},401);
 const url = Deno.env.get('SUPABASE_URL');
 const admin = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
 const raw = Deno.env.get('FCM_SERVICE_ACCOUNT_JSON');
 if(!url || !admin) return json({status:'sender_not_configured'},503);
 const rpc = async(name:string, payload:unknown) => {
  const r=await fetch(url+'/rest/v1/rpc/'+name,{method:'POST',headers:{apikey:admin,Authorization:'Bearer '+admin,'Content-Type':'application/json'},body:JSON.stringify(payload),signal:AbortSignal.timeout(10000)});
  if(!r.ok) throw new Error('database_unavailable');return r.json();
 };
 try {
  const secret=Deno.env.get('TASK_NOTIFICATION_SECRET');
  const authorized=secret && secret.length>=32 ? supplied===secret : await rpc('task_worker_authorized',{p_secret:supplied})===true;
  if(!authorized)return json({status:'unauthorized'},401);
 let sa: {project_id:string;client_email:string;private_key:string;type:string};
 try {sa=JSON.parse(raw || '');if(sa.type !== 'service_account' || !/^[a-z0-9-]+$/.test(sa.project_id) || !sa.client_email || !sa.private_key) throw new Error();}
 catch {return json({status:'sender_not_configured'},503);}
  // Health checks authenticate and validate configuration, but never claim/send events.
  if(req.headers.get('X-Pipo-Health')==='1')return json({status:'ready',sender_configured:true,delivery_verified:false});
  const google=await accessToken(sa);
  type Job={event_id:string;recipient_id:string;kind:string;tokens:string[]};
  const jobs:Job[]=await rpc('claim_task_push_batch',{});
  let accepted=0,failed=0;
  const worker=async()=>{
   while(jobs.length) {
    const job=jobs.shift()!;
    if(!uuid.test(job.event_id) || !uuid.test(job.recipient_id) || !Array.isArray(job.tokens)) {failed++;continue;}
    const results=await Promise.all(job.tokens.slice(0,5).map(async token=>{
     try {
      const r=await fetch('https://fcm.googleapis.com/v1/projects/'+sa.project_id+'/messages:send',{
       method:'POST',headers:{Authorization:'Bearer '+google,'Content-Type':'application/json'},
       body:JSON.stringify({message:{token,data:{type:'task_event',event_id:job.event_id},android:{priority:job.kind==='sync'?'NORMAL':'HIGH',ttl:'86400s',collapse_key:'task_'+job.event_id}}}),signal:AbortSignal.timeout(10000)});
      if(!r.ok) {
       const reply=await r.json().catch(()=>null);
       if(reply?.error?.details?.some((d:{errorCode?:string})=>d.errorCode==='UNREGISTERED')) await rpc('remove_invalid_push_token',{p_token:token}).catch(()=>{});
      }
      return r.ok;
     } catch {return false;}
    }));
    const ok=results.some(Boolean);
    await rpc('finish_task_push',{p_event:job.event_id,p_accepted:ok});
    if(ok) accepted++;else failed++;
   }
  };
  await Promise.all([worker(),worker(),worker()]);
  return json({status:'processed',accepted,failed,delivery_verified:false});
 } catch {return json({status:'worker_unavailable'},503);}
});
