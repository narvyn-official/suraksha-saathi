import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { projectRoot } from './sites-env.mjs';

const secrets = join(projectRoot, '.dev.vars');
if (!existsSync(secrets)) throw new Error('Configure the local account service first: node ../../scripts/create-app-auth.mjs');
// Wrangler resolves env files beside the generated config. An absolute path
// keeps secrets in the ignored project file, outside distributable build output.
const child = spawn(process.execPath, [join(projectRoot, 'node_modules/wrangler/bin/wrangler.js'),
  'dev', '--config', 'dist/server/wrangler.json', '--local', '--env-file', secrets,
  '--persist-to', '.wrangler/state', '--ip', '127.0.0.1', '--inspector-port', '0',
  '--port', process.env.PORT || '5173'], { cwd: projectRoot, stdio: 'inherit', env: process.env });
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => child.kill(signal));
child.on('error', error => { console.error(error.message);process.exitCode=1; });
child.on('exit', code => { process.exitCode = code ?? 1; });
