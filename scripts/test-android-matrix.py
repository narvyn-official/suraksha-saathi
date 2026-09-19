#!/usr/bin/env python3
"""Run Android QA with explicit emulator-only font, permission and thermal fixtures.
Build first. Requires the local account server on :5173 for AdminFlowTest.
"""
import argparse, os, pathlib, re, shutil, subprocess, sys
parser=argparse.ArgumentParser()
parser.add_argument('--serial',required=True)
parser.add_argument('--out',required=True,type=pathlib.Path)
parser.add_argument('--classes',help='Optional comma-separated ordinary test classes for a focused run')
args=parser.parse_args()
root=pathlib.Path(__file__).resolve().parents[1]
adb=shutil.which('adb') or str(pathlib.Path(os.environ.get('ANDROID_HOME',str(pathlib.Path.home()/'Library/Android/sdk')))/'platform-tools/adb')
def command(*parts,check=True):
 return subprocess.run([adb,'-s',args.serial,*parts],text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,check=check).stdout.strip()
if not args.serial.startswith('emulator-') or command('shell','getprop','ro.hardware') not in ['ranchu','goldfish']:
 sys.exit('This fixture runner is restricted to Android emulators.')
args.out.mkdir(parents=True,exist_ok=True)
package='com.narvyn.suraksha'
settings=[('system','font_scale'),('global','window_animation_scale'),('global','transition_animation_scale'),('global','animator_duration_scale')]
previous={(scope,key):command('shell','settings','get',scope,key) for scope,key in settings}
results=[]
def run(name,classes,extra=()):
 command('shell','am','force-stop',package)
 command('shell','pm','grant' if name=='thermal' else 'revoke',package,'android.permission.CAMERA',check=False)
 out=command('shell','am','instrument','-w','-e','class',','.join(package+'.'+c for c in classes),*extra,package+'.test/androidx.test.runner.AndroidJUnitRunner',check=False)
 (args.out/(name+'.log')).write_text(out+'\n')
 match=re.search(r'OK \((\d+) tests?\)',out);ok=match is not None
 results.append((name,ok,int(match[1]) if match else 0))
 print(f'{name}: '+(f'PASS ({match[1]} tests)' if ok else 'FAIL — inspect log'),flush=True)
try:
 for apk in ['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
  command('install','-r',str(root/'apps/android'/apk))
 command('reverse','tcp:5173','tcp:5173')
 for scope,key in settings:command('shell','settings','put',scope,key,'1.0' if key=='font_scale' else '0')
 command('shell','cmd','thermalservice','reset')
 command('shell','input','keyevent','KEYCODE_WAKEUP')
 command('shell','wm','dismiss-keyguard')
 special={'AccessibilityFlowTest','RoomMissionWorkspaceTest','PlacementFlowTest','TrainingBayAssetTest'}
 ordinary=args.classes.split(',') if args.classes else sorted(p.stem for p in (root/'apps/android/app/src/androidTest/java/com/narvyn/suraksha').glob('*Test.kt') if p.stem not in special)
 run('ordinary',ordinary)
 if not args.classes:
  run('placement',['PlacementFlowTest'])
  run('workspace',['RoomMissionWorkspaceTest#permissionOffCannotCompleteCameraActions','RoomMissionWorkspaceTest#hindiWorkspaceKeepsSceneAndControlsInPortraitAndLandscape'])
  command('shell','settings','put','system','font_scale','2.0')
  run('large-text',['AccessibilityFlowTest'])
  command('shell','settings','put','system','font_scale','1.0')
  command('shell','cmd','thermalservice','override-status','4')
  run('thermal',['RoomMissionWorkspaceTest#criticalHeatPausesCameraAndOffersRecovery','RoomMissionWorkspaceTest#interruptedDischargeDoesNotSwallowRetryTouch'])
  command('shell','cmd','thermalservice','reset')
  if command('shell','pm','path','com.google.ar.core',check=False).startswith('package:'):
   run('ar-asset',['TrainingBayAssetTest'])
  else:print('AR reference database: NOT TESTED — Google Play Services for AR absent. No physical tracking claim.',flush=True)
finally:
 command('shell','cmd','thermalservice','reset',check=False)
 for (scope,key),value in previous.items():
  command('shell','settings','delete' if value=='null' else 'put',scope,key,*(() if value=='null' else (value,)),check=False)
print(f'Total passed: {sum(n for _,ok,n in results if ok)}; failed groups: {sum(not ok for _,ok,_ in results)}',flush=True)
sys.exit(0 if all(ok for _,ok,_ in results) else 1)
