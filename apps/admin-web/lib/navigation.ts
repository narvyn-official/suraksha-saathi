/** Only allow destinations within this application, including workspace hashes. */
export function returnPath(raw: string | null | undefined, fallback='/') {
 if(!raw || !raw.startsWith('/') || raw.startsWith('//') || /[\\\r\n]/.test(raw))return fallback;
 try { const parsed=new URL(raw,'https://suraksha.invalid');
  if(parsed.origin!=='https://suraksha.invalid'||/^\/(login|forgot-password|reset-password)(\/|$)/.test(parsed.pathname))return fallback;
  return parsed.pathname+parsed.search+parsed.hash;
 }catch{return fallback}
}
