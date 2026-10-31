// Runs the actual workflow publication script with mocked Git/GitHub commands.
// No release, tag, upload or network call is made by these checks.
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const assert = require('node:assert/strict');
const {spawnSync} = require('node:child_process');
const workflow = fs.readFileSync('.github/workflows/build-apk.yml', 'utf8');
const step = workflow.split('      - name: Create release or attach APK to selected release')[1].split('      - name:')[0];
const script = step.split('        run: |')[1].split(/\r?\n/).filter(line => line.startsWith('          ')).map(line => line.slice(10)).join('\n');
assert(script.includes('Release changed during the build'));
const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'pipo-release-check-'));
const shell = 'C:/Program Files/Git/bin/bash.exe';
const prolog = `
gh() {
  printf '%s\\n' "$*" >> "$CALL_LOG"
  if [[ "$*" == *"--jq .id" ]]; then printf '%s\\n' "$MOCK_RELEASE_ID";
  elif [[ "$*" == *"--jq .object.sha" ]]; then printf '%s\\n' "$MOCK_TAG_SHA"; fi
}
git() { printf '%s\\n' 'candidate-commit'; }
`;
const file = path.join(dir, 'publish.sh');
fs.writeFileSync(file, prolog + script);
assert.equal(spawnSync(shell, ['-n', file.replaceAll('\\', '/')]).status, 0, 'shell syntax');
function run(mode, id = 'original-id', sha = 'original-sha') {
  const log = path.join(dir, `calls-${mode}-${id}-${sha}.txt`);
  const result = spawnSync(shell, [file.replaceAll('\\', '/')], {cwd: dir, encoding: 'utf8', env: {
    ...process.env, RELEASE_MODE: mode, RELEASE_TAG: 'v1.4.3', GH_REPO: 'test/repository',
    REPLACED_RELEASE_ID: 'original-id', REPLACED_TAG_SHA: 'original-sha',
    MOCK_RELEASE_ID: id, MOCK_TAG_SHA: sha, CALL_LOG: log.replaceAll('\\', '/')
  }});
  return {result, calls: fs.existsSync(log) ? fs.readFileSync(log, 'utf8') : ''};
}
const good = run('replace');
assert.equal(good.result.status, 0, good.result.stderr);
assert(good.calls.indexOf('release delete') < good.calls.indexOf('api --method PATCH'));
assert(good.calls.indexOf('api --method PATCH') < good.calls.indexOf('release create'));
assert(good.calls.includes('--target candidate-commit'));
for (const changed of [run('replace', 'changed-id'), run('replace', 'original-id', 'changed-sha')]) {
  assert.notEqual(changed.result.status, 0);
  assert(!/release delete|--method PATCH|release create/.test(changed.calls), 'changed release must remain untouched');
}
const create = run('create');
assert.equal(create.result.status, 0);
assert(create.calls.includes('release create') && !create.calls.includes('release delete'));
const upload = run('upload');
assert.equal(upload.result.status, 0);
assert(upload.calls.includes('release upload') && !/release delete|--method PATCH|release create/.test(upload.calls));
console.log('PASS: replacement order, changed-release guard, changed-tag guard, ordinary create, ordinary upload; no live mutations.');
