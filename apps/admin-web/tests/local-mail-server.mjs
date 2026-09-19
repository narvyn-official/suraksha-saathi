import http from 'node:http';
import fs from 'node:fs';
const vars=fs.readFileSync('.dev.vars','utf8');
// Synthetic recipients only. Run from apps/admin-web with local QA .dev.vars.
const key=vars.match(/^RECOVERY_MAIL_KEY=(.*)$/m)[1].trim();
const messages=[];
http.createServer(async(req,res)=>{
 if(req.headers.authorization!==`Bearer ${key}`){res.writeHead(403);res.end();return;}
 if(req.method==='POST'&&req.url==='/send'){
  let body='';for await(const chunk of req){body+=chunk;if(body.length>10000){res.writeHead(413);res.end();return;}}
  const data=JSON.parse(body);
  if(!data.to.endsWith('@example.test')){res.writeHead(400);res.end();return;}
  messages.push(data);res.setHeader('Content-Type','application/json');res.end('{}');
 }else {res.setHeader('Content-Type','application/json');res.end(JSON.stringify(messages));}
}).listen(5188,'127.0.0.1',()=>console.log('Synthetic mail transport ready; no external email is sent.'));
