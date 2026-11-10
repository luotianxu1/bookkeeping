import { spawn } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { delimiter, join } from 'node:path'

const backend = fileURLToPath(new URL('../../life-hub/', import.meta.url))
let child
let stopping = false

function terminate() {
  stopping = true
  if (!child?.pid) return
  try {
    if (process.platform === 'win32') child.kill('SIGTERM')
    else process.kill(-child.pid, 'SIGTERM')
  } catch (error) {
    if (error.code !== 'ESRCH') throw error
  }
}
process.on('SIGTERM', terminate)
process.on('SIGINT', terminate)

function run(command, args) {
  return new Promise((resolve, reject) => {
    child = spawn(command, args, {
      cwd: backend,
      stdio: 'inherit',
      detached: process.platform !== 'win32',
    })
    child.on('error', reject)
    child.on('exit', (code) => {
      child = undefined
      if (stopping || code === 0) resolve()
      else reject(new Error(`${command} exited with ${code}`))
    })
  })
}

try {
  await run('mvn', [
    '-B',
    '-ntp',
    '-Plive-e2e',
    '-pl',
    'live-tests',
    '-am',
    'clean',
    'package',
    '-DskipTests',
  ])
  if (!stopping) {
    const java = process.env.JAVA_HOME
      ? join(process.env.JAVA_HOME, 'bin', 'java')
      : 'java'
    const classpath = [
      'live-tests/target/classes',
      'live-tests/target/dependency/*',
    ].join(delimiter)
    await run(java, ['-cp', classpath, 'com.example.livetest.PcTestServer'])
  }
} catch (error) {
  console.error(error)
  process.exitCode = 1
}
