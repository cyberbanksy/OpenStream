import { spawn, execFileSync } from 'node:child_process'
import { copyFileSync, existsSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import process from 'node:process'

import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js'
import {
  OPENPENCIL_APP_PATH,
  OPENPENCIL_CODEX_TOOLS,
  assertTrustedOpenPencilInstallation,
  assertTrustedOpenPencilRuntime,
  buildCodexChildEnvironment,
  createPrivateTempWorkspace,
  inspectTrustedOpenPencilListener,
  removePrivateTempWorkspace
} from './openpencil-security.mjs'

const projectRoot = process.cwd()
const outputDir = path.join(projectRoot, 'design', 'make-agent')
const sessionPath = path.join(outputDir, 'session.json')
const reportPath = path.join(outputDir, 'last-run.json')
const latestFigPath = path.join(outputDir, 'latest.fig')
const latestPreviewPath = path.join(outputDir, 'latest.png')
const stagingDir = createPrivateTempWorkspace('openstream-make-')
const stagingFigPath = path.join(stagingDir, 'latest.fig')
const openPencilUrl = 'http://127.0.0.1:7600'
let stagingRemoved = false

mkdirSync(outputDir, { recursive: true })

function cleanupStaging() {
  if (stagingRemoved) return
  stagingRemoved = true
  removePrivateTempWorkspace(stagingDir)
}

process.once('exit', cleanupStaging)

function usage() {
  console.log(`Usage:
  pnpm make -- "Design a music dashboard"
  pnpm make -- "Make the selected card coral"
  pnpm make:new -- "Design a mobile checkout"
  pnpm make -- --image /absolute/reference.png "Use this visual direction"

Options:
  --new           Start a new Codex conversation and OpenPencil page
  --image PATH    Attach a reference image to the prompt
  --help          Show this help`)
}

function parseArguments(argv) {
  const promptParts = []
  const images = []
  let startNew = false

  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index]
    if (argument === '--') continue
    if (argument === '--help' || argument === '-h') return { help: true }
    if (argument === '--new') {
      startNew = true
      continue
    }
    if (argument === '--image' || argument === '-i') {
      const image = argv[index + 1]
      if (!image) throw new Error('--image requires an absolute file path')
      images.push(path.resolve(image))
      index += 1
      continue
    }
    promptParts.push(argument)
  }

  return {
    help: false,
    startNew,
    images,
    prompt: promptParts.join(' ').trim()
  }
}

function delay(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds))
}

async function readHealth() {
  try {
    const response = await fetch(`${openPencilUrl}/health`, {
      signal: AbortSignal.timeout(1000)
    })
    if (!response.ok) return null
    return await response.json()
  } catch {
    return null
  }
}

async function ensureOpenPencil() {
  assertTrustedOpenPencilInstallation()
  const existingListener = inspectTrustedOpenPencilListener()
  let health = existingListener ? await readHealth() : null
  if (health?.status === 'ok') {
    assertTrustedOpenPencilRuntime()
    return health
  }

  console.log('Opening OpenPencil...')
  const child = spawn('open', ['-a', OPENPENCIL_APP_PATH], {
    detached: true,
    stdio: 'ignore'
  })
  child.unref()

  for (let attempt = 0; attempt < 20; attempt += 1) {
    await delay(1000)
    health = await readHealth()
    if (health?.status === 'ok') {
      assertTrustedOpenPencilRuntime()
      return health
    }
  }

  throw new Error('OpenPencil did not start its MCP server at http://127.0.0.1:7600')
}

function parseToolResult(name, result) {
  const text = result.content?.find((item) => item.type === 'text')?.text
  const payload = text ? JSON.parse(text) : null
  if (result.isError || payload?.error) {
    throw new Error(`${name} failed: ${payload?.error ?? 'unknown MCP error'}`)
  }
  return payload
}

async function connectOpenPencil(health) {
  const transport = new StreamableHTTPClientTransport(new URL(`${openPencilUrl}/mcp`), {
    requestInit: health.authRequired
      ? { headers: { Authorization: `Bearer ${health.token}` } }
      : undefined
  })
  const client = new Client({ name: 'openstream-make', version: '0.1.0' })
  await client.connect(transport)
  assertTrustedOpenPencilRuntime()
  return { client, transport }
}

async function callTool(client, name, args = {}) {
  return parseToolResult(name, await client.callTool({ name, arguments: args }))
}

function pageNameFor(prompt) {
  const words = prompt
    .replace(/[^a-zA-Z0-9 ]/g, ' ')
    .trim()
    .split(/\s+/)
    .slice(0, 5)
    .join(' ')
  return `Make - ${words || 'Untitled'}`
}

function readSession() {
  if (!existsSync(sessionPath)) return null
  try {
    return JSON.parse(readFileSync(sessionPath, 'utf8'))
  } catch {
    return null
  }
}

async function prepareCanvas(client, prompt, forceNew) {
  let session = forceNew ? null : readSession()
  const pages = await callTool(client, 'list_pages')

  if (session) {
    const existingPage = pages.pages.find(
      (page) => page.id === session.pageId || page.name === session.pageName
    )
    if (existingPage) {
      await callTool(client, 'switch_page', { page: existingPage.id })
      session = { ...session, pageId: existingPage.id, pageName: existingPage.name }
      return { mode: 'iterate', session }
    }
  }

  const baseName = pageNameFor(prompt)
  const existingNames = new Set(pages.pages.map((page) => page.name))
  let pageName = baseName
  let suffix = 2
  while (existingNames.has(pageName)) {
    pageName = `${baseName} ${suffix}`
    suffix += 1
  }

  const page = await callTool(client, 'create_page', { name: pageName })
  await callTool(client, 'switch_page', { page: page.id })

  if (forceNew && existsSync(sessionPath)) rmSync(sessionPath)
  return {
    mode: 'new',
    session: {
      threadId: null,
      pageId: page.id,
      pageName: page.name,
      createdAt: new Date().toISOString()
    }
  }
}

function buildAgentPrompt(userPrompt, mode, pageName) {
  return `You are OpenStream Make, a conversational product-design agent controlling a live OpenPencil canvas.

The active OpenPencil page is named "${pageName}". This is a ${mode === 'new' ? 'new design' : 'continuation of the existing design conversation'}.

USER REQUEST
${userPrompt}

REQUIRED BEHAVIOR
1. Use the openpencil MCP tools to make the requested design on the active page. Do not merely describe a design or return code as a substitute for changing the canvas.
2. Start by calling get_current_page and get_page_tree. Stay on "${pageName}". Do not call new_document, create_page, switch_page, open_file, or save_file.
3. ${mode === 'new' ? 'Create one polished, editable root frame from scratch.' : 'Inspect the existing design and modify it in place unless the user clearly asks for an additional screen.'}
4. Use render for substantial structures and targeted update/layout/style tools for revisions. Use editable text, shapes, named layers, and auto-layout.
5. Honor explicit platform and dimensions. When unspecified, infer sensible dimensions: TV 1920x1080, desktop product UI 1440x900, website 1440x1024, mobile 390x844.
6. Match the domain. Operational products should be compact and work-focused; media and entertainment products may be cinematic. Avoid generic gradient-heavy marketing layouts.
7. Make focus, selected, loading, empty, error, and disabled states when the requested workflow naturally needs them.
8. Before finishing, call analyze_overlaps and describe on the root frame, fix unintended overlap or readability problems, then call page_bounds and get_page_tree to verify the result.
9. Do not ask a follow-up question. Use sound product-design judgment for missing details.

Finish with a concise summary of what you changed after the canvas work is complete.`
}

function codexConfigArgs() {
  return [
    '-c',
    'approval_policy="never"',
    '-c',
    'sandbox_mode="read-only"',
    '-c',
    'mcp_servers.openpencil.url="http://127.0.0.1:7600/mcp"',
    '-c',
    'mcp_servers.openpencil.bearer_token_env_var="OPENPENCIL_MCP_TOKEN"',
    '-c',
    'mcp_servers.openpencil.required=true',
    '-c',
    `mcp_servers.openpencil.enabled_tools=${JSON.stringify(OPENPENCIL_CODEX_TOOLS)}`,
    '-c',
    'mcp_servers.openpencil.default_tools_approval_mode="approve"',
    '-c',
    'mcp_servers.openpencil.tool_timeout_sec=180'
  ]
}

async function runCodex({ prompt, images, session, mode, token }) {
  const agentPrompt = buildAgentPrompt(prompt, mode, session.pageName)
  const shared = [
    '--json',
    '--ignore-user-config',
    '--skip-git-repo-check',
    ...codexConfigArgs()
  ]

  let args
  if (mode === 'iterate' && session.threadId) {
    args = ['exec', 'resume', ...shared]
    for (const image of images.slice(0, 1)) args.push('--image', image)
    args.push(session.threadId, agentPrompt)
  } else {
    args = ['exec', ...shared, '--cd', projectRoot]
    for (const image of images) args.push('--image', image)
    args.push(agentPrompt)
  }

  console.log(`${mode === 'new' ? 'Creating' : 'Updating'} "${session.pageName}" with Codex...`)

  const child = spawn('codex', args, {
    cwd: projectRoot,
    env: buildCodexChildEnvironment(token),
    stdio: ['ignore', 'pipe', 'pipe']
  })

  let buffer = ''
  let threadId = session.threadId
  let finalMessage = ''
  let failure = null

  child.stderr.on('data', (chunk) => process.stderr.write(chunk))
  child.stdout.on('data', (chunk) => {
    buffer += chunk.toString()
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''

    for (const line of lines) {
      if (!line.trim()) continue
      try {
        const event = JSON.parse(line)
        if (event.type === 'thread.started') threadId = event.thread_id
        if (event.type === 'item.started' && event.item?.type === 'mcp_tool_call') {
          const toolName = event.item.tool ?? event.item.name ?? 'design tool'
          console.log(`  OpenPencil: ${toolName}`)
        }
        if (event.type === 'item.completed' && event.item?.type === 'agent_message') {
          finalMessage = event.item.text ?? finalMessage
        }
        if (event.type === 'turn.failed' || event.type === 'error') failure = event
      } catch {
        process.stderr.write(`${line}\n`)
      }
    }
  })

  const exitCode = await new Promise((resolve, reject) => {
    child.once('error', reject)
    child.once('close', resolve)
  })

  if (exitCode !== 0 || failure) {
    throw new Error(`Codex design run failed${failure?.message ? `: ${failure.message}` : ''}`)
  }
  if (!threadId) throw new Error('Codex did not return a reusable conversation ID')

  if (finalMessage) console.log(`\n${finalMessage}\n`)
  return { threadId, finalMessage }
}

async function snapshotCanvas(client, prompt, session, finalMessage) {
  const currentPage = await callTool(client, 'get_current_page')
  const bounds = await callTool(client, 'page_bounds')
  const tree = await callTool(client, 'get_page_tree')
  await callTool(client, 'save_file', { path: stagingFigPath })
  copyFileSync(stagingFigPath, latestFigPath)

  const openPencilCli = execFileSync('which', ['openpencil'], { encoding: 'utf8' }).trim()
  execFileSync(
    'bun',
    [
      openPencilCli,
      'export',
      stagingFigPath,
      '--format',
      'png',
      '--page',
      currentPage.name,
      '--output',
      latestPreviewPath
    ],
    { stdio: 'inherit' }
  )

  const report = {
    generatedAt: new Date().toISOString(),
    prompt,
    threadId: session.threadId,
    page: currentPage,
    bounds,
    topLevelNodes: tree?.children?.length ?? null,
    finalMessage,
    artifacts: {
      fig: latestFigPath,
      preview: latestPreviewPath
    }
  }
  writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`)
  return report
}

const options = parseArguments(process.argv.slice(2))
if (options.help) {
  usage()
  process.exit(0)
}
if (!options.prompt) {
  usage()
  process.exitCode = 1
} else {
  for (const image of options.images) {
    if (!existsSync(image)) throw new Error(`Reference image not found: ${image}`)
  }

  const health = await ensureOpenPencil()
  const { client } = await connectOpenPencil(health)

  try {
    const prepared = await prepareCanvas(client, options.prompt, options.startNew)
    const result = await runCodex({
      prompt: options.prompt,
      images: options.images,
      session: prepared.session,
      mode: prepared.mode,
      token: health.token
    })
    const session = {
      ...prepared.session,
      threadId: result.threadId,
      updatedAt: new Date().toISOString()
    }
    writeFileSync(sessionPath, `${JSON.stringify(session, null, 2)}\n`)

    const report = await snapshotCanvas(client, options.prompt, session, result.finalMessage)
    console.log(`Editable canvas saved:\n- ${report.artifacts.fig}\n- ${report.artifacts.preview}`)
  } finally {
    await client.close()
    cleanupStaging()
  }
}
