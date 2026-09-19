import { test } from "node:test";
import assert from "node:assert/strict";
import { readJson } from "../lib/http";
const make = (body: string, headers = {}) => new Request("https://training.example/api/import", {
  method: "POST", headers: { "Content-Type": "application/json", ...headers }, body,
});
test("JSON accepts objects and same-origin native/browser requests", async () => {
  assert.deepEqual(await readJson(make('{"name":"कर्मचारी"}')), { name: "कर्मचारी" });
  assert.deepEqual(await readJson(make('{}', { Origin: 'https://training.example' })), {});
});
test("rejects cross-origin, malformed JSON, primitives and unsupported media", async () => {
  for (const request of [make('{}',{Origin:'https://other.example'}), make('{}',{'sec-fetch-site':'cross-site'}),
    make('null'), make('[]'), make('"text"'), make('{'), make('{}',{'Content-Type':'text/plain'})])
    await assert.rejects(readJson(request), /Invalid/);
});
test("bounds UTF-8 bytes and cancels a streamed oversized request", async () => {
  await assert.rejects(readJson(make('{"n":"हहह"}'), 12), /too large/);
  let cancelled = false;
  const stream = new ReadableStream({ pull(c) { c.enqueue(new Uint8Array(10)); }, cancel() { cancelled = true; } });
  const request = new Request('https://training.example/api/import', { method: 'POST',
    headers: {'Content-Type':'application/json'}, body:stream, duplex:'half' } as RequestInit);
  await assert.rejects(readJson(request, 16), /too large/); assert.equal(cancelled,true);
  await assert.rejects(readJson(make('{}',{'Content-Length':'1000001'})), /too large/);
});
