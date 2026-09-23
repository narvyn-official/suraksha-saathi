from pathlib import Path
import json,re
extras=json.loads(Path("content/scenario-additions-v2.json").read_text())
q=lambda v:json.dumps(v,ensure_ascii=False)
lines=['package com.narvyn.suraksha','', '/** Generated from content/scenario-additions-v2.json by scripts/generate-scenarios.py. */','object ScenarioAdditions {',' val modules = mapOf(']
for mi,(module,steps) in enumerate(extras.items()):
 lines.append('  '+q(module)+' to listOf(')
 for i,st in enumerate(steps):
  parts=[q(st[k]) for k in ['id','title','hindi','explanation','hindiExplanation']]
  actions=[]
  for a in st['actions']:
   actions.append('ProcedureCatalog.Action('+','.join([q(a['id']),q(a['label']),q(a['hindi']),str(a['correct']).lower(),'floatArrayOf('+','.join(str(x)+'f' for x in a['point'])+')'])+')')
  lines.append('   ProcedureCatalog.Step('+','.join(parts)+',listOf('+','.join(actions)+'))'+(',' if i<len(steps)-1 else ''))
 lines.append('  )'+(',' if mi<len(extras)-1 else ''))
lines+=[' )','}']
Path('apps/android/app/src/main/java/com/narvyn/suraksha/ScenarioAdditions.kt').write_text('\n'.join(lines)+'\n')

text=Path("apps/android/app/src/main/java/com/narvyn/suraksha/ProcedureLearning.kt").read_text()
legacy={m:re.findall(r'task\("('+m+r'-[^" ]+)"',text) for m in ['fire','gas']}
current={}
f=legacy['fire'];x=[s['id'] for s in extras['fire']];current['fire']=f[:3]+x[:3]+f[3:]+x[-1:]
g=legacy['gas'];x=[s['id'] for s in extras['gas']];current['gas']=g[:1]+x[:2]+g[1:7]+x[2:3]+x[-1:]+g[7:]
for m in ['machinery','ppe','emergency']:current[m]=[s['id'] for s in extras[m]]
Path('apps/admin-web/lib/scenario-catalog.json').write_text(json.dumps({'1':legacy,'2':current},indent=2)+'\n')
