import "jsr:@supabase/functions-js/edge-runtime.d.ts";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const OFFICIAL_PDF = "https://files.floridalottery.com/exptkt/pick3_winning-numbers-history.pdf";
const READER_URL = "https://r.jina.ai/http://files.floridalottery.com/exptkt/pick3_winning-numbers-history.pdf";
const cors = {"Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"authorization, x-client-info, apikey, content-type","Access-Control-Allow-Methods":"POST, OPTIONS","Content-Type":"application/json"};

function isoDate(value: string): string | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!m) return null;
  const d = new Date(Date.UTC(Number(m[1]), Number(m[2])-1, Number(m[3])));
  return d.toISOString().slice(0,10) === value ? value : null;
}
function floridaToday(): string {
  return new Intl.DateTimeFormat("en-CA",{timeZone:"America/New_York",year:"numeric",month:"2-digit",day:"2-digit"}).format(new Date());
}
function subtractDays(value: string, days: number): string {
  const d = new Date(value + "T00:00:00Z"); d.setUTCDate(d.getUTCDate()-days); return d.toISOString().slice(0,10);
}
async function dbRequest(url: string, init: RequestInit = {}) {
  return await fetch(url,{...init,headers:{apikey:SERVICE_KEY,Authorization:"Bearer "+SERVICE_KEY,"Content-Type":"application/json",...(init.headers??{})}});
}
function parseOfficialPick3(text: string,target: string|null=null,cutoff: string|null=null) {
  const normalized=text.replace(/<[^>]*>/g," ").replace(/&nbsp;|&#160;/gi," ").replace(/\u00a0/g," ").replace(/\s+/g," ");
  const matches:{draw_date:string;period:string;winning_number:string;fireball:string}[]=[];
  const pattern=/(\d{1,2})\/(\d{1,2})\/(\d{4})\s+(\d)\s*-\s*(\d)\s*-\s*(\d)\s+FB\s*(\d)\s+(\d)\s*-\s*(\d)\s*-\s*(\d)\s+FB\s*(\d)/g;
  for(const m of normalized.matchAll(pattern)){
    const date=new Date(Date.UTC(Number(m[3]),Number(m[1])-1,Number(m[2]))).toISOString().slice(0,10);
    if(target&&date!==target) continue; if(cutoff&&date<cutoff) continue;
    matches.push({draw_date:date,period:"MIDDAY",winning_number:m[4]+m[5]+m[6],fireball:m[7]});
    matches.push({draw_date:date,period:"EVENING",winning_number:m[8]+m[9]+m[10],fireball:m[11]});
  }
  return matches;
}
async function fetchOfficialText():Promise<string>{
  const controller=new AbortController(); const timeout=setTimeout(()=>controller.abort(),55000);
  try{
    const response=await fetch(READER_URL,{headers:{"Accept":"text/plain","X-Return-Format":"markdown","X-No-Cache":"true"},signal:controller.signal});
    if(!response.ok) throw new Error("Official history fetch returned HTTP "+response.status);
    return await response.text();
  }finally{clearTimeout(timeout);}
}
async function saveRows(rows:ReturnType<typeof parseOfficialPick3>){
  const payload=rows.map(r=>({draw_date:r.draw_date,game:"PICK_3",period:r.period,winning_number:r.winning_number,fireball:r.fireball,source:"Florida Lottery official PICK 3 winning numbers history PDF",source_url:OFFICIAL_PDF,published_at:null,verified:true}));
  const saved=await dbRequest(SUPABASE_URL+"/rest/v1/lottery_draws?on_conflict=draw_date,game,period",{method:"POST",headers:{"Prefer":"resolution=merge-duplicates,return=representation"},body:JSON.stringify(payload)});
  if(!saved.ok) throw new Error("Database save failed: "+saved.status+" "+(await saved.text()).slice(0,300));
  return await saved.json();
}
Deno.serve(async(req:Request)=>{
  if(req.method==="OPTIONS") return new Response("ok",{headers:cors});
  if(req.method!=="POST") return Response.json({error:"POST required"},{status:405,headers:cors});
  try{
    if(!req.headers.get("apikey")) return Response.json({error:"Missing API key"},{status:401,headers:cors});
    const body=await req.json(); const today=floridaToday();
    if(body.recent_days!==undefined){
      const days=Number(body.recent_days);
      if(!Number.isInteger(days)||days<1||days>10) return Response.json({error:"recent_days debe estar entre 1 y 10."},{status:400,headers:cors});
      const cutoff=subtractDays(today,days-1);
      const cached=await dbRequest(SUPABASE_URL+"/rest/v1/lottery_draws?select=draw_date,period,winning_number,fireball&game=eq.PICK_3&verified=eq.true&draw_date=gte."+cutoff+"&order=draw_date.desc,period.asc");
      if(!cached.ok) throw new Error("Database read failed: "+cached.status);
      const cachedRows=await cached.json(); const cachedDays=new Set((cachedRows as any[]).map(r=>r.draw_date));
      const todayRows=(cachedRows as any[]).filter(r=>r.draw_date===today);
      const todayComplete=todayRows.length>=2;
      if(todayComplete&&cachedDays.size>=days-1&&(cachedRows as any[]).length>=(days-1)*2) return Response.json({source:"cache",official_source:OFFICIAL_PDF,results:cachedRows},{headers:cors});
      const text=await fetchOfficialText(); const found=parseOfficialPick3(text,null,cutoff).filter(r=>r.draw_date<=today);
      if(found.length===0) return Response.json({error:"La fuente oficial no devolvió resultados para los últimos días.",results:[]},{status:502,headers:cors});
      const saved=await saveRows(found);
      return Response.json({source:"official_florida_lottery",official_source:OFFICIAL_PDF,results:saved},{headers:cors});
    }
    const date=isoDate(String(body.date??""));
    if(!date||date<"2025-01-01") return Response.json({error:"Fecha inválida. Usa una fecha desde 2025-01-01."},{status:400,headers:cors});
    if(date>today) return Response.json({error:"La fecha todavía no ha ocurrido."},{status:400,headers:cors});
    const cached=await dbRequest(SUPABASE_URL+"/rest/v1/lottery_draws?select=draw_date,period,winning_number,fireball&game=eq.PICK_3&verified=eq.true&draw_date=eq."+date+"&order=period.asc");
    if(!cached.ok) throw new Error("Database read failed: "+cached.status);
    const cachedRows=await cached.json();
    if(Array.isArray(cachedRows)&&cachedRows.length===2) return Response.json({source:"cache",official_source:OFFICIAL_PDF,results:cachedRows},{headers:cors});
    const text=await fetchOfficialText(); const found=parseOfficialPick3(text,date);
    if(found.length!==2) return Response.json({status:"not_found",error:"La fuente oficial no devolvió los dos sorteos para esa fecha. No se guardó ningún número.",results:[],source:OFFICIAL_PDF},{status:404,headers:cors});
    const saved=await saveRows(found);
    return Response.json({source:"official_florida_lottery",official_source:OFFICIAL_PDF,results:saved},{headers:cors});
  }catch(error){
    const message=error instanceof Error?error.message:String(error); console.error("lookup-pick3-date failed",message);
    return Response.json({error:"No se pudo verificar esa fecha en la fuente oficial. Inténtalo más tarde."},{status:502,headers:cors});
  }
});