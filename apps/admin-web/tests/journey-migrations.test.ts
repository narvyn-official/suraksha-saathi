import {test} from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import {readFileSync,readdirSync} from 'node:fs';
test('learning/MFA migrations preserve existing review decisions and legacy policy',()=>{
 const db=new DatabaseSync(':memory:');
 try{
  const files=readdirSync('drizzle').filter(f=>f.endsWith('.sql')).sort();
  for(const file of files.filter(f=>f<'0009'))db.exec(readFileSync('drizzle/'+file,'utf8'));
  for(const status of ['pending','approved','rejected'])db.prepare('INSERT INTO certification_requests VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)').run(status,'existing-centre',status+'-attempt','original-digest',1900000000000,'requester',1800000000000,'Original request note',status,status==='pending'?null:'reviewer',status==='pending'?null:1800000001000,'Original review note',status==='approved'?'signed-credential':null);
  for(const file of files.filter(f=>f>='0009'))db.exec(readFileSync('drizzle/'+file,'utf8'));
  const rows=db.prepare('SELECT * FROM certification_requests').all();assert.equal(rows.length,3);
  for(const row of rows){assert.equal(row.status,row.id);assert.equal(row.evidence_digest,'original-digest');assert.equal(row.request_note,'Original request note');assert.equal(row.review_reason,'Original review note');assert.equal(row.workflow_version,1);assert.equal(row.learning_digest,null)}
  assert.equal(rows.find(r=>r.id==='approved')?.credential_id,'signed-credential');
  const fields=db.prepare('PRAGMA table_info(auth_two_factor)').all().map(r=>r.name);assert(fields.includes('failed_verification_count'));assert(fields.includes('locked_until'));
 }finally{db.close()}
});
