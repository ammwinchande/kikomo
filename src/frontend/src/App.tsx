import React,{useEffect,useState} from 'react'; import Logo from './logo';
async function api(path:string, opts?:any){ const r=await fetch(path,{headers:{'Content-Type':'application/json'},...opts}); return r.json().catch(()=>({})); }
export default function App(){
  const [health,setHealth]=useState<any>(null); const [calls,setCalls]=useState<any[]>([]); const [approvals,setApprovals]=useState<any[]>([]); const [agents,setAgents]=useState<any[]>([]); const [audit,setAudit]=useState<any>(null);
  const [password,setPassword]=useState(''); const [loggedIn,setLoggedIn]=useState(false); const [policyText,setPolicyText]=useState('Allow dev tools up to $500, approve over $25'); const [draft,setDraft]=useState<any>(null);
  const load=async()=>{ setHealth(await api('/api/health')); if(loggedIn){ setCalls(await api('/api/calls')); setApprovals(await api('/api/approvals')); setAgents(await api('/api/agents')); setAudit(await api('/api/audit/verify')); } };
  useEffect(()=>{ load(); },[loggedIn]);
  const login=async()=>{ const r=await fetch('/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({password})}); if(r.ok) setLoggedIn(true); else alert('Bad password'); };
  const act=async(id:string,kind:string)=>{ await api(`/api/approvals/${id}/${kind}`,{method:'POST'}); load(); };
  const kill=async(id:string,on:boolean)=>{ await api(`/api/agents/${id}/${on?'suspend':'activate'}`,{method:'POST'}); load(); };
  const compile=async()=>{ setDraft(await api('/api/policies/compile',{method:'POST',body:JSON.stringify({text:policyText,allowlist:[]})})); };
  return (<div style={{maxWidth:1100,margin:'0 auto',padding:20}}>
    {health?.mock && <div style={{background:'#f59e0b',color:'#111',padding:10,borderRadius:8,fontWeight:700,textAlign:'center'}}>MOCK PAYPAL — no real money moves</div>}
    <header style={{display:'flex',justifyContent:'space-between',margin:'16px 0'}}><Logo/><button className="btn" onClick={load}>Refresh</button></header>
    {!loggedIn ? (<div className="card"><h2>Admin login</h2><input type="password" value={password} onChange={e=>setPassword(e.target.value)} placeholder="ADMIN_PASSWORD"/><button className="btn" onClick={login} style={{marginTop:10}}>Login</button></div>) : (<>
    <section className="card"><h3>Agents (kill switch)</h3>{agents.map((a:any)=>(<div key={a.id} style={{display:'flex',gap:8,justifyContent:'space-between'}}><span>{a.name} — {a.status}</span><span><button className="btn btn-danger" onClick={()=>kill(a.id,true)}>Suspend</button> <button className="btn" onClick={()=>kill(a.id,false)}>Activate</button></span></div>))}</section>
    <section className="card" style={{marginTop:12}}><h3>Pending approvals</h3>{approvals.filter((a:any)=>a.status==='pending').map((a:any)=>(<div key={a.id}>{a.id} <button className="btn" onClick={()=>act(a.id,'approve')}>Approve</button> <button className="btn btn-danger" onClick={()=>act(a.id,'deny')}>Deny</button></div>))}{approvals.filter((a:any)=>a.status==='pending').length===0&&<p>None</p>}</section>
    <section className="card" style={{marginTop:12}}><h3>Calls</h3>{calls.map((c:any)=>(<div key={c.id}>{c.tool} → {c.decision}/{c.status} {c.counterparty||''}</div>))}</section>
    <section className="card" style={{marginTop:12}}><h3>Audit chain</h3><p>{audit?`valid: ${audit.valid}`:'—'}</p></section>
    <section className="card" style={{marginTop:12}}><h3>Policy compiler (AI, draft only — never enforces)</h3><textarea value={policyText} onChange={e=>setPolicyText(e.target.value)}/><button className="btn" onClick={compile}>Compile draft</button>{draft&&<pre>{JSON.stringify(draft,null,2)}</pre>}</section>
    <section className="card" style={{marginTop:12}}><h3>Demo</h3><p>Small payment (allow) · Large payment (approval) · Injection via /demo/malicious-page + kikomo_fetch_url (deny UNTRUSTED_COUNTERPARTY). Use MCP client with DEMO_AGENT_TOKEN.</p><a href="/demo/malicious-page" style={{color:'#93c5fd'}}>Malicious page</a></section>
    </>)}
  </div>);
}
