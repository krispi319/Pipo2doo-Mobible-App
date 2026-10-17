// Unit/integration-style handler checks with synthetic keys and mocked HTTP. No real push.
const fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const {stripTypeScriptTypes}=require('node:module'),{webcrypto}=require('node:crypto');
(async()=>{
 const keys=await webcrypto.subtle.generateKey({name:'RSASSA-PKCS1-v1_5',modulusLength:2048,publicExponent:new Uint8Array([1,0,1]),hash:'SHA-256'},true,['sign','verify']);
 const der=await webcrypto.subtle.exportKey('pkcs8',keys.privateKey);
 const pem='-----BEGIN PRIVATE KEY-----\n'+Buffer.from(der).toString('base64')+'\n-----END PRIVATE KEY-----';
 const sender='11111111-1111-4111-8111-111111111111',record='22222222-2222-4222-8222-222222222222',recipient='33333333-3333-4333-8333-333333333333';
 let handler,mode='ok',oauth=0,fcm=0,removed=0,checks=0;
 const env={SUPABASE_URL:'https://mock.invalid',SUPABASE_ANON_KEY:'anon',SUPABASE_SERVICE_ROLE_KEY:'admin',FCM_SERVICE_ACCOUNT_JSON:JSON.stringify({type:'service_account',project_id:'pipo2doo-bfc83',client_email:'test@invalid.test',private_key:pem})};
 const response=(body,status=200)=>new Response(JSON.stringify(body),{status});
 const ctx=vm.createContext({crypto:webcrypto,TextEncoder,Uint8Array,btoa,atob,URLSearchParams,AbortSignal,Response,Request,Deno:{env:{get:k=>env[k]},serve:f=>handler=f},fetch:async(url,options={})=>{
  if(url.endsWith('/auth/v1/user'))return mode==='bad-auth'?response({},401):response({id:sender});
  if(url.includes('/shared_records?'))return response([{created_by:mode==='outsider'?recipient:sender,payload:{kind:mode==='photo'?'Locket':'Message'}}]);
  if(url==='https://oauth2.googleapis.com/token'){
   oauth++;if(mode==='bad-signature')return response({error:'invalid_grant',error_description:'Invalid JWT Signature.'},400);
   const [head,claims,signature]=new URLSearchParams(options.body).get('assertion').split('.');
   if(!await webcrypto.subtle.verify('RSASSA-PKCS1-v1_5',keys.publicKey,Buffer.from(signature,'base64url'),new TextEncoder().encode(head+'.'+claims)))throw Error('Bad generated signature');
   return response({access_token:'synthetic-google-token'});
  }
  if(url.endsWith('/claim_note_notification'))return response(mode==='duplicate'?{status:'already_sent'}:mode==='opt-out'?{status:'recipient_unavailable'}:mode==='rate'?{status:'rate_limited'}:{status:'claimed',record_id:record,recipient_id:recipient,tokens:['synthetic-device-token']});
  if(url.endsWith('/remove_invalid_push_token')){removed++;return response({});}
  if(url.startsWith('https://fcm.googleapis.com/')){
   fcm++;const msg=JSON.parse(options.body).message;
   if(msg.notification || msg.data.type!=='partner_note' || msg.data.record_id!==record || 'message' in msg.data || 'photo' in msg.data)throw Error('Push must contain IDs only');
   return mode==='unregistered'?response({error:{details:[{errorCode:'UNREGISTERED'}]}},404):response({name:'mock-message'});
  }
  throw Error('Unexpected URL '+url);
 }});
 vm.runInContext(stripTypeScriptTypes(fs.readFileSync(path.join(__dirname,'index.ts'),'utf8')),ctx);
 const request=(body={record_id:record},auth=true)=>handler(new Request('https://mock.invalid',{method:'POST',headers:auth?{Authorization:'Bearer synthetic-user'}:{},body:JSON.stringify(body)}));
 const check=(ok,label)=>{if(!ok)throw Error(label);checks++;};
 check((await request({},false)).status===401,'missing authorization');
 mode='bad-auth';check((await request()).status===401,'invalid session');check(oauth===0,'invalid caller cannot request Google token');
 mode='ok';check((await request({record_id:'bad'})).status===400,'invalid record ID');
 for(const m of ['outsider','photo']){mode=m;check((await request()).status===403,'unowned or photo record rejected');}check(oauth===0,'unauthorized record cannot request OAuth');
 mode='ok';let result=await (await request()).json();check(result.status==='provider_accepted'&&result.delivery_verified===false,'provider acceptance not phone delivery');check(fcm===1,'one ID-only push sent');check(!JSON.stringify(result).includes('synthetic-device-token'),'tokens not returned');
 for(const [m,status] of [['duplicate','already_sent'],['opt-out','recipient_unavailable'],['rate','rate_limited']]){mode=m;result=await (await request()).json();check(result.status===status&&fcm===1,m+' does not send push');}
 mode='unregistered';result=await (await request()).json();check(result.status==='failed'&&removed===1,'expired token removed');
 mode='bad-signature';const failure=await request();result=await failure.json();check(failure.status===503&&result.reason==='invalid_jwt_signature','OAuth errors are classified');
 console.log('PASS: '+checks+' handler checks; mocked HTTP only, no real notifications.');
})().catch(e=>{console.error(e);process.exit(1)});
