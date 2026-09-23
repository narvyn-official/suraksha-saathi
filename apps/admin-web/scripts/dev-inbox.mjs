import http from 'node:http';
import fs from 'node:fs';
const config=fs.readFileSync('.dev.vars','utf8'),key=config.match(/^RECOVERY_MAIL_KEY=(.*)$/m)?.[1]?.trim();
if(!key||key.length<32)throw new Error('Run the local development launcher first.');
const messages=[];
const escape=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
http.createServer(async(req,res)=>{
 res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');res.setHeader('Referrer-Policy','no-referrer');res.setHeader('Content-Security-Policy',"default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'; base-uri 'none'");
 if(!['127.0.0.1:5188','localhost:5188'].includes(req.headers.host)||req.headers['sec-fetch-site']==='cross-site'){res.writeHead(403);res.end();return}
 if(req.method==='POST'&&req.url==='/send'){
  if(req.headers.authorization!==`Bearer ${key}`){res.writeHead(403);res.end();return}
  try{let raw='';for await(const chunk of req){raw+=chunk;if(Buffer.byteLength(raw)>20_000)throw new Error('Too large')}
   const m=JSON.parse(raw);if(typeof m.to!=='string'||typeof m.text!=='string'||typeof m.subject!=='string')throw new Error('Invalid mail');
   messages.push({...m,at:new Date().toISOString()});if(messages.length>100)messages.shift();res.setHeader('Content-Type','application/json');res.end('{}');
  }catch{res.writeHead(400);res.end('{}')}return;
 }
 if(req.method==='GET'&&req.url==='/messages'&&req.headers.authorization===`Bearer ${key}`){res.setHeader('Content-Type','application/json');res.end(JSON.stringify(messages));return}
 if(req.method!=='GET'||req.url!=='/'){res.writeHead(404);res.end();return}
 res.setHeader('Content-Type','text/html; charset=utf-8');res.end(`<!doctype html><html lang="en"><meta name="viewport" content="width=device-width"><title>SurakshaAr local inbox</title><style>body{font:16px system-ui;max-width:850px;margin:30px auto;padding:20px;background:#f4f7f6;color:#173b34}article{background:white;padding:20px;border:1px solid #ccd9d4;margin:20px 0}pre{white-space:pre-wrap;overflow-wrap:anywhere}a{color:#005c51}</style><h1>Local development inbox</h1><p>No external email is sent. Messages exist only in this process and disappear when it stops. Anyone using this computer can view this development inbox; use local test accounts.</p><a href="/">Refresh inbox</a>${[...messages].reverse().map(m=>{const links=(m.text.match(/https?:\/\/[^\s]+/g)||[]).filter(u=>{try{return ['localhost','127.0.0.1'].includes(new URL(u).hostname)}catch{return false}});return `<article><h2>${escape(m.subject)}</h2><p>${escape(m.to)} · ${escape(m.at)}</p><pre>${escape(m.text)}</pre>${links.map(u=>`<p><a href="${escape(u)}">Open local account link</a></p>`).join('')}</article>`}).join('')||'<p>No messages yet. Request a password reset in SurakshaAr, then refresh.</p>'}</html>`);
}).listen(5188,'127.0.0.1',()=>console.log('Local development inbox listening on loopback port 5188. External delivery is disabled.'));
