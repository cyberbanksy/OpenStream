import { execFileSync } from 'node:child_process'
import { copyFileSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import process from 'node:process'

import { Client } from '@modelcontextprotocol/sdk/client/index.js'
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js'
import {
  assertTrustedOpenPencilRuntime,
  copyFileExclusive,
  createPrivateTempWorkspace,
  removePrivateTempWorkspace
} from './openpencil-security.mjs'

const projectRoot = process.cwd()
const labDir = path.join(projectRoot, 'design', 'openpencil-lab')
const stagingDir = createPrivateTempWorkspace('openstream-openpencil-lab-')
const figPath = path.join(labDir, 'openstream-source-console.fig')
const previewPath = path.join(labDir, 'openstream-source-console.png')
const jsxPath = path.join(labDir, 'openstream-source-console.jsx')
const reportPath = path.join(labDir, 'smoke-test-report.json')
const stagingFigPath = path.join(stagingDir, 'openstream-source-console.fig')
const stagingPreviewPath = path.join(stagingDir, 'openstream-source-console.png')
const roundtripFigPath = path.join(stagingDir, 'openstream-source-console-roundtrip.fig')
const openPencilCli = execFileSync('which', ['openpencil'], { encoding: 'utf8' }).trim()
let stagingRemoved = false

mkdirSync(labDir, { recursive: true })

function cleanupStaging() {
  if (stagingRemoved) return
  stagingRemoved = true
  removePrivateTempWorkspace(stagingDir)
}

process.once('exit', cleanupStaging)

const jsx = String.raw`
<Frame name="OpenStream Source Console" w={1920} h={1080} flex="col" bg="#070A0D" overflow="hidden">
  <Frame name="Navigation" w="fill" h={104} flex="row" items="center" justify="between" px={72} bg="#0B1015" stroke="#1B2830" strokeWidth={1}>
    <Frame name="Brand and sections" flex="row" items="center" gap={54}>
      <Frame name="OpenStream wordmark" flex="row" items="center" gap={14}>
        <Frame name="Brand mark" w={36} h={36} bg="#05D9C6" rounded={8} cornerSmoothing={0.7} />
        <Text size={26} weight="bold" color="#F6F8F9">OpenStream</Text>
      </Frame>
      <Text size={18} weight="medium" color="#7D8A92">Discover</Text>
      <Text size={18} weight="medium" color="#7D8A92">Library</Text>
      <Text size={18} weight="medium" color="#7D8A92">Live</Text>
      <Frame name="Sources active" flex="col" gap={10}>
        <Text size={18} weight="bold" color="#F6F8F9">Sources</Text>
        <Frame name="Active section underline" w={68} h={3} bg="#05D9C6" rounded={2} />
      </Frame>
    </Frame>
    <Frame name="Status controls" flex="row" items="center" gap={18}>
      <Frame name="Search" w={230} h={48} flex="row" items="center" gap={12} px={18} bg="#11181E" stroke="#26343C" rounded={8}>
        <Ellipse w={16} h={16} stroke="#86949C" strokeWidth={2} />
        <Text size={16} color="#86949C">Search sources</Text>
      </Frame>
      <Frame name="Profile" w={48} h={48} bg="#FF6A5C" rounded={24} />
    </Frame>
  </Frame>

  <Frame name="Content" w="fill" h="fill" flex="col" gap={28} px={72} pt={38} pb={44}>
    <Frame name="Page heading" w="fill" flex="row" items="end" justify="between">
      <Frame flex="col" gap={8}>
        <Text size={18} weight="medium" color="#05D9C6">SOURCE CONTROL</Text>
        <Text size={42} weight="bold" color="#F6F8F9">Everything connected. One place to play.</Text>
      </Frame>
      <Frame name="Health summary" flex="row" items="center" gap={10} py={12} px={16} bg="#10251F" rounded={8}>
        <Ellipse w={12} h={12} bg="#54D694" rounded={6} />
        <Text size={16} weight="medium" color="#C9F7DF">8 sources healthy</Text>
      </Frame>
    </Frame>

    <Frame name="Primary source row" w="fill" h={420} flex="row" gap={24}>
      <Frame name="Focused NAS source" w={1090} h="fill" flex="col" justify="between" p={30} bg="#102025" stroke="#A6FFF3" strokeWidth={4} rounded={8} shadow="0 12 32 #00000066">
        <Frame w="fill" flex="row" justify="between" items="start">
          <Frame flex="row" items="center" gap={18}>
            <Frame name="NAS glyph" w={74} h={74} flex="col" justify="center" items="center" gap={8} bg="#05D9C6" rounded={8}>
              <Frame name="NAS drive 1" w={40} h={9} bg="#062E2A" rounded={3} />
              <Frame name="NAS drive 2" w={40} h={9} bg="#062E2A" rounded={3} />
              <Frame name="NAS drive 3" w={40} h={9} bg="#062E2A" rounded={3} />
            </Frame>
            <Frame flex="col" gap={7}>
              <Text size={18} weight="medium" color="#91A1AA">LOCAL LIBRARY</Text>
              <Text size={34} weight="bold" color="#FFFFFF">Northstar NAS</Text>
              <Text size={16} color="#A7B4BA">smb://northstar.local/media</Text>
            </Frame>
          </Frame>
          <Frame flex="row" items="center" gap={9} py={10} px={14} bg="#15392F" rounded={8}>
            <Ellipse w={10} h={10} bg="#54D694" rounded={5} />
            <Text size={15} weight="bold" color="#B8F7D4">CONNECTED</Text>
          </Frame>
        </Frame>

        <Frame name="NAS metrics" w="fill" flex="row" gap={16}>
          <Frame w={240} flex="col" gap={6} p={18} bg="#0B1519" rounded={8}>
            <Text size={14} color="#7E9099">LIBRARY</Text>
            <Text size={26} weight="bold" color="#F6F8F9">1,842 titles</Text>
          </Frame>
          <Frame w={240} flex="col" gap={6} p={18} bg="#0B1519" rounded={8}>
            <Text size={14} color="#7E9099">LAST SCAN</Text>
            <Text size={26} weight="bold" color="#F6F8F9">4 min ago</Text>
          </Frame>
          <Frame w={240} flex="col" gap={6} p={18} bg="#0B1519" rounded={8}>
            <Text size={14} color="#7E9099">PLAYBACK</Text>
            <Text size={26} weight="bold" color="#54D694">Direct play</Text>
          </Frame>
        </Frame>

        <Frame name="NAS actions" flex="row" gap={14}>
          <Frame name="Browse focused action" h={58} flex="row" items="center" gap={12} px={24} bg="#F5FFFF" rounded={8}>
            <Text size={18} weight="bold" color="#061514">Browse library</Text>
          </Frame>
          <Frame h={58} flex="row" items="center" px={24} bg="#17242A" stroke="#33444C" rounded={8}>
            <Text size={18} weight="medium" color="#E2E8EA">Scan now</Text>
          </Frame>
          <Frame h={58} flex="row" items="center" px={24} bg="#17242A" stroke="#33444C" rounded={8}>
            <Text size={18} weight="medium" color="#E2E8EA">Manage</Text>
          </Frame>
        </Frame>
      </Frame>

      <Frame name="Source side stack" w="fill" h="fill" flex="col" gap={24}>
        <Frame name="Add-ons" w="fill" h="fill" flex="col" justify="between" p={26} bg="#15161B" stroke="#2D3038" rounded={8}>
          <Frame w="fill" flex="row" justify="between" items="center">
            <Frame flex="col" gap={7}>
              <Text size={15} weight="medium" color="#FF8C80">COMMUNITY ADD-ONS</Text>
              <Text size={28} weight="bold" color="#F6F8F9">5 active</Text>
            </Frame>
            <Frame w={52} h={52} bg="#FF6A5C" rounded={8} />
          </Frame>
          <Frame flex="row" gap={8}>
            <Frame py={8} px={12} bg="#272128" rounded={7}><Text size={14} color="#F4D6D2">Cinemeta</Text></Frame>
            <Frame py={8} px={12} bg="#272128" rounded={7}><Text size={14} color="#F4D6D2">OpenSubtitles</Text></Frame>
            <Frame py={8} px={12} bg="#272128" rounded={7}><Text size={14} color="#F4D6D2">+3</Text></Frame>
          </Frame>
        </Frame>
        <Frame name="Remote shares" w="fill" h="fill" flex="col" justify="between" p={26} bg="#111B18" stroke="#283C35" rounded={8}>
          <Frame w="fill" flex="row" justify="between" items="center">
            <Frame flex="col" gap={7}>
              <Text size={15} weight="medium" color="#67DDA0">REMOTE SHARES</Text>
              <Text size={28} weight="bold" color="#F6F8F9">2 online</Text>
            </Frame>
            <Frame flex="row" gap={7}>
              <Ellipse w={14} h={14} bg="#54D694" rounded={7} />
              <Ellipse w={14} h={14} bg="#54D694" rounded={7} />
            </Frame>
          </Frame>
          <Text size={16} color="#8FA49B">Home Plex  /  Studio WebDAV</Text>
        </Frame>
      </Frame>
    </Frame>

    <Frame name="Available sources heading" w="fill" flex="row" items="center" justify="between">
      <Text size={24} weight="bold" color="#F6F8F9">Available sources</Text>
      <Text size={16} weight="medium" color="#83939B">Sorted by quality and health</Text>
    </Frame>

    <Frame name="Available source cards" w="fill" h="fill" flex="row" gap={18}>
      <Frame name="Real-Debrid source" w="fill" h="fill" flex="col" justify="between" p={22} bg="#11171C" stroke="#2B3940" rounded={8}>
        <Frame flex="row" justify="between" items="center"><Text size={16} weight="bold" color="#FFFFFF">Real-Debrid</Text><Ellipse w={10} h={10} bg="#54D694" rounded={5} /></Frame>
        <Text size={28} weight="bold" color="#A6FFF3">4K HDR</Text>
        <Text size={14} color="#809099">68.2 Mbps  /  cached</Text>
      </Frame>
      <Frame name="Torrentio source" w="fill" h="fill" flex="col" justify="between" p={22} bg="#11171C" stroke="#2B3940" rounded={8}>
        <Frame flex="row" justify="between" items="center"><Text size={16} weight="bold" color="#FFFFFF">Torrentio</Text><Ellipse w={10} h={10} bg="#54D694" rounded={5} /></Frame>
        <Text size={28} weight="bold" color="#F6F8F9">1080p</Text>
        <Text size={14} color="#809099">12.4 Mbps  /  83 peers</Text>
      </Frame>
      <Frame name="Easynews source" w="fill" h="fill" flex="col" justify="between" p={22} bg="#11171C" stroke="#2B3940" rounded={8}>
        <Frame flex="row" justify="between" items="center"><Text size={16} weight="bold" color="#FFFFFF">Easynews</Text><Ellipse w={10} h={10} bg="#F3C969" rounded={5} /></Frame>
        <Text size={28} weight="bold" color="#F6F8F9">1080p</Text>
        <Text size={14} color="#809099">8.1 Mbps  /  remote</Text>
      </Frame>
      <Frame name="WebDAV source" w="fill" h="fill" flex="col" justify="between" p={22} bg="#11171C" stroke="#2B3940" rounded={8}>
        <Frame flex="row" justify="between" items="center"><Text size={16} weight="bold" color="#FFFFFF">Studio WebDAV</Text><Ellipse w={10} h={10} bg="#54D694" rounded={5} /></Frame>
        <Text size={28} weight="bold" color="#F6F8F9">Original</Text>
        <Text size={14} color="#809099">Direct play  /  31 ms</Text>
      </Frame>
    </Frame>
  </Frame>
</Frame>`

function textPayload(result) {
  const text = result.content?.find((item) => item.type === 'text')?.text
  return text ? JSON.parse(text) : null
}

function assertToolResult(name, result) {
  if (result.isError) {
    const message = result.content?.map((item) => item.text).filter(Boolean).join('\n')
    throw new Error(`${name} failed: ${message || 'unknown MCP error'}`)
  }
  return textPayload(result)
}

assertTrustedOpenPencilRuntime()
const healthResponse = await fetch('http://127.0.0.1:7600/health')
if (!healthResponse.ok) {
  throw new Error('OpenPencil MCP health endpoint is unavailable; restart the desktop app')
}

const health = await healthResponse.json()
if (health.status !== 'ok') {
  throw new Error('OpenPencil desktop app is not connected to its MCP server')
}

const transport = new StreamableHTTPClientTransport(new URL('http://127.0.0.1:7600/mcp'), {
  requestInit: health.authRequired
    ? { headers: { 'x-mcp-token': health.token } }
    : undefined
})

const client = new Client({ name: 'openstream-openpencil-smoke', version: '1.0.0' })

try {
  await client.connect(transport)
  assertTrustedOpenPencilRuntime()

  const { tools } = await client.listTools()
  const toolNames = new Set(tools.map((tool) => tool.name))
  for (const required of [
    'render',
    'save_file',
    'page_bounds',
    'get_page_tree'
  ]) {
    if (!toolNames.has(required)) throw new Error(`Required OpenPencil tool missing: ${required}`)
  }

  const renderResult = assertToolResult(
    'render',
    await client.callTool({ name: 'render', arguments: { x: 0, y: 0, jsx } })
  )

  const bounds = assertToolResult(
    'page_bounds',
    await client.callTool({ name: 'page_bounds', arguments: {} })
  )
  const tree = assertToolResult(
    'get_page_tree',
    await client.callTool({ name: 'get_page_tree', arguments: {} })
  )

  let imageExport = null
  if (toolNames.has('export_image')) {
    imageExport = assertToolResult(
      'export_image',
      await client.callTool({
        name: 'export_image',
        arguments: { ids: [renderResult.id], format: 'PNG', scale: 1, path: stagingPreviewPath }
      })
    )
  }

  assertToolResult(
    'save_file',
    await client.callTool({ name: 'save_file', arguments: { path: stagingFigPath } })
  )

  writeFileSync(
    reportPath,
    `${JSON.stringify(
      {
        openPencilMcpTools: tools.length,
        renderResult,
        bounds,
        topLevelNodes: tree?.children?.length ?? null,
        imageExport,
        artifacts: { figPath, previewPath, jsxPath }
      },
      null,
      2
    )}\n`
  )
} finally {
  await client.close()
}

copyFileSync(stagingFigPath, figPath)

const savedNodes = JSON.parse(
  execFileSync(
    'bun',
    [
      openPencilCli,
      'find',
      stagingFigPath,
      '--name',
      'OpenStream Source Console',
      '--json'
    ],
    { encoding: 'utf8' }
  )
)
const savedFrame = savedNodes.find((node) => node.name === 'OpenStream Source Console')
if (!savedFrame) throw new Error('Saved OpenPencil file is missing the rendered root frame')
const fileInfo = JSON.parse(
  execFileSync('bun', [openPencilCli, 'info', stagingFigPath, '--json'], {
    encoding: 'utf8'
  })
)

execFileSync(
  'bun',
  [openPencilCli, 'export', stagingFigPath, '--format', 'png', '--output', previewPath],
  { stdio: 'inherit' }
)
execFileSync(
  'bun',
  [
    openPencilCli,
    'export',
    stagingFigPath,
    '--format',
    'jsx',
    '--node',
    savedFrame.id,
    '--output',
    jsxPath
  ],
  { stdio: 'inherit' }
)

const accessibilityLint = JSON.parse(
  execFileSync(
    'bun',
    [
      openPencilCli,
      'lint',
      stagingFigPath,
      '--preset',
      'accessibility',
      '--json'
    ],
    { encoding: 'utf8' }
  )
)
if (accessibilityLint.messages.length > 0) {
  throw new Error(`OpenPencil accessibility lint found ${accessibilityLint.messages.length} issue(s)`)
}

copyFileExclusive(stagingFigPath, roundtripFigPath)
execFileSync(
  'bun',
  [
    openPencilCli,
    'eval',
    roundtripFigPath,
    '--code',
    'const n = figma.currentPage.children[0]; n.name = "Roundtrip Verified"; return n.name;',
    '--write',
    '--json'
  ],
  { stdio: 'pipe' }
)
const roundtripNodes = JSON.parse(
  execFileSync(
    'bun',
    [openPencilCli, 'find', roundtripFigPath, '--name', 'Roundtrip Verified', '--json'],
    { encoding: 'utf8' }
  )
)
if (roundtripNodes.length !== 1) {
  throw new Error('OpenPencil .fig mutation did not survive the save/read round trip')
}

const report = JSON.parse(readFileSync(reportPath, 'utf8'))
report.savedFileNodeId = savedFrame.id
report.cliVerification = {
  nodeCount: fileInfo.totalNodes,
  nodeTypes: fileInfo.types,
  preview: { width: 1920, height: 1080 },
  accessibilityFindings: accessibilityLint.messages.length,
  roundtripMutation: roundtripNodes[0].name
}
writeFileSync(reportPath, `${JSON.stringify(report, null, 2)}\n`)

console.log(`OpenPencil smoke test complete:\n- ${figPath}\n- ${previewPath}\n- ${jsxPath}\n- ${reportPath}`)
cleanupStaging()
