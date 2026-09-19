import { test } from "node:test";
import assert from "node:assert/strict";
import { recoveryReady, sendRecovery } from "../lib/recovery-mail";
const config = { BETTER_AUTH_URL: "https://training.example", RECOVERY_MAIL_URL: "https://mail.example/send", RECOVERY_MAIL_KEY: "x".repeat(40) };
test("recovery mail requires secure configured delivery", () => {
  assert.equal(recoveryReady({}), false);assert.equal(recoveryReady(config),true);
  assert.equal(recoveryReady({...config,RECOVERY_MAIL_URL:'http://mail.example/send'}),false);
  assert.equal(recoveryReady({...config,RECOVERY_MAIL_URL:'https://user:pass@mail.example/send'}),false);
  assert.equal(recoveryReady({...config,RECOVERY_MAIL_KEY:'short'}),false);
});
test("reset delivery uses trusted base and fragment, and never follows redirects", async () => {
  let called=false;
  await sendRecovery(config,'synthetic@example.test','test-token',async (_url,options)=>{
    called=true; assert.equal(options?.redirect,'manual');
    const body=JSON.parse(String(options?.body));assert.equal(body.to,'synthetic@example.test');
    assert.match(body.text,/https:\/\/training.example\/reset-password#token=test-token/);
    return new Response('{}');
  });assert.equal(called,true);
  await assert.rejects(sendRecovery(config,'synthetic@example.test','test-token',async()=>new Response('',{status:503})),/failed/);
});
