import {createServer} from '/Users/sobean/IdeaProjects/BILLIM/frontend/node_modules/vite/dist/node/index.js';
import fs from 'node:fs';
const root='/Users/sobean/IdeaProjects/BILLIM';
const fixture=JSON.parse(fs.readFileSync('/tmp/billim-live-sessions-20261107.json','utf8'));
const entry=`
import React from 'react';
import {createRoot} from 'react-dom/client';
import {BrowserRouter} from 'react-router-dom';
import {UiProvider} from '/src/components/overlay/UiProvider.tsx';
import {RentalsPage} from '/src/pages/rentals/RentalsPage.tsx';
import '/src/styles/tokens.css';import '/src/styles/global.css';
let active=0,requests=[],started=0,firstCardMs,done=false;
const nativeFetch=window.fetch.bind(window);
window.fetch=async(...args)=>{active++;const row={path:String(args[0]),startMs:performance.now()-started};requests.push(row);try{const r=await nativeFetch(...args);row.status=r.status;return r;}finally{active--;row.endMs=performance.now()-started;document.getElementById("measurement").textContent=JSON.stringify({active,requests});}};
createRoot(document.getElementById('root')).render(<UiProvider><BrowserRouter><RentalsPage/></BrowserRouter></UiProvider>);started=performance.now();
new MutationObserver(()=>{if(!firstCardMs&&document.querySelector('article'))firstCardMs=performance.now()-started;if(done||!firstCardMs||active)return;requestAnimationFrame(()=>requestAnimationFrame(()=>{if(done||active)return;done=true;const result={firstCardMs,readyMs:performance.now()-started,cards:document.querySelectorAll('article').length,requests};document.getElementById('measurement').textContent=JSON.stringify(result);nativeFetch('/__result',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({...result,url:location.pathname+location.search})});}));}).observe(document.getElementById('root'),{childList:true,subtree:true});
`;
const virtual=root+'/frontend/src/__verification.tsx';
const server=await createServer({root:root+'/frontend',configFile:root+'/frontend/vite.config.ts',server:{host:'127.0.0.1',port:5179,strictPort:true},plugins:[{name:'live-verification',resolveId(id){if(id==='/src/__verification.tsx'||id===virtual)return virtual;},load(id){if(id===virtual)return entry;},configureServer(s){s.middlewares.use(async(req,res,next)=>{
if(req.url==='/__result'){let body='';for await(const chunk of req)body+=chunk;fs.appendFileSync(root+'/docs/performance/rental/raw/verification-20261107/live-screen.jsonl',JSON.stringify(JSON.parse(body))+'\n');res.end('ok');return;}
if(!req.url?.startsWith('/__live'))return next();const u=new URL(req.url,'http://localhost');const token=fixture[u.searchParams.get('account')==='owner'?'owner':'borrower'];res.setHeader('Set-Cookie','BILLIM_SESSION='+token+'; HttpOnly; SameSite=Lax; Path=/');res.setHeader('Content-Type','text/html');res.end(await s.transformIndexHtml(req.url,'<!doctype html><html lang="ko"><meta charset="utf-8"><title>실제 서버 · 내 대여 검증</title><output id="measurement">측정 중</output><div id="root"></div><script type="module" src="/src/__verification.tsx"></script></html>'));
});}}]});
await server.listen();console.log('실제 서버 화면 검증 준비 완료: http://127.0.0.1:5179/__live');
