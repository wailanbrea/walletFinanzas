const { spawn } = require('node:child_process');

const worker = spawn('C:\\xampp\\php\\php.exe', [
  'artisan',
  'queue:work',
  'database',
  '--queue=default',
  '--sleep=3',
  '--tries=3',
  '--timeout=90',
], {
  cwd: 'C:\\xampp\\htdocs\\walletFinanzas',
  stdio: 'inherit',
  windowsHide: true,
});

worker.on('exit', (code, signal) => {
  if (signal) process.kill(process.pid, signal);
  process.exit(code ?? 1);
});
