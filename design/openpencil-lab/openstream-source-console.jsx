<Frame name="OpenStream Source Console" flex="col" h={1080} w={1920} bg="#070A0D" overflow="hidden">
  <Frame name="Navigation" flex="row" h={104} w="fill" justify="between" items="center" py={0} px={72} bg="#0B1015" stroke="#1B2830">
    <Frame name="Brand and sections" flex="row" gap={54} items="center">
      <Frame name="OpenStream wordmark" flex="row" gap={14} items="center">
        <Frame name="Brand mark" w={36} h={36} bg="#05D9C6" rounded={8} cornerSmoothing={0.699999988079071} />
        <Text name="Text" w={156} size={26} weight="bold" lineHeight={32} color="#F6F8F9">OpenStream</Text>
      </Frame>
      <Text name="Text" w={87} size={18} weight="medium" lineHeight={22} color="#7D8A92">Discover</Text>
      <Text name="Text" w={76} size={18} weight="medium" lineHeight={22} color="#7D8A92">Library</Text>
      <Text name="Text" w={44} size={18} weight="medium" lineHeight={22} color="#7D8A92">Live</Text>
      <Frame name="Sources active" flex="col" gap={10}>
        <Text name="Text" w={76} size={18} weight="bold" lineHeight={22} color="#F6F8F9">Sources</Text>
        <Frame name="Active section underline" w={68} h={3} bg="#05D9C6" rounded={2} />
      </Frame>
    </Frame>
    <Frame name="Status controls" flex="row" gap={18} items="center">
      <Frame name="Search" flex="row" w={230} h={48} gap={12} items="center" py={0} px={18} bg="#11181E" stroke="#26343C" rounded={8}>
        <Ellipse name="Ellipse" w={16} h={16} stroke="#86949C" strokeWidth={2} />
        <Text name="Text" w={135} size={16} lineHeight={20} color="#86949C">Search sources</Text>
      </Frame>
      <Frame name="Profile" w={48} h={48} bg="#FF6A5C" rounded={24} />
    </Frame>
  </Frame>
  <Frame name="Content" flex="col" grow={1} w="fill" gap={28} pt={38} pr={72} pb={44} pl={72}>
    <Frame name="Page heading" flex="row" w="fill" justify="between" items="end">
      <Frame name="Frame" flex="col" gap={8}>
        <Text name="Text" w={152} size={18} weight="medium" lineHeight={22} color="#05D9C6">SOURCE CONTROL</Text>
        <Text name="Text" w={1008} size={42} weight="bold" lineHeight={51} color="#F6F8F9">Everything connected. One place to play.</Text>
      </Frame>
      <Frame name="Health summary" flex="row" gap={10} items="center" py={12} px={16} bg="#10251F" rounded={8}>
        <Ellipse name="Ellipse" w={12} h={12} bg="#54D694" rounded={6} />
        <Text name="Text" w={164} size={16} weight="medium" lineHeight={20} color="#C9F7DF">8 sources healthy</Text>
      </Frame>
    </Frame>
    <Frame name="Primary source row" flex="row" h={420} w="fill" gap={24}>
      <Frame name="Focused NAS source" flex="col" w={1090} h="fill" justify="between" p={30} bg="#102025" stroke="#A6FFF3" strokeWidth={4} rounded={8} shadow="0 12 32 #00000066">
        <Frame name="Frame" flex="row" w="fill" justify="between">
          <Frame name="Frame" flex="row" gap={18} items="center">
            <Frame name="NAS glyph" flex="col" h={74} w={74} gap={8} justify="center" items="center" bg="#05D9C6" rounded={8}>
              <Frame name="NAS drive 1" w={40} h={9} bg="#062E2A" rounded={3} />
              <Frame name="NAS drive 2" w={40} h={9} bg="#062E2A" rounded={3} />
              <Frame name="NAS drive 3" w={40} h={9} bg="#062E2A" rounded={3} />
            </Frame>
            <Frame name="Frame" flex="col" gap={7}>
              <Text name="Text" w={141} size={18} weight="medium" lineHeight={22} color="#91A1AA">LOCAL LIBRARY</Text>
              <Text name="Text" w={266} size={34} weight="bold" lineHeight={41} color="#FFFFFF">Northstar NAS</Text>
              <Text name="Text" w={260} size={16} lineHeight={20} color="#A7B4BA">smb://northstar.local/media</Text>
            </Frame>
          </Frame>
          <Frame name="Frame" flex="row" gap={9} items="center" py={10} px={14} bg="#15392F" rounded={8}>
            <Ellipse name="Ellipse" w={10} h={10} bg="#54D694" rounded={5} />
            <Text name="Text" w={81} size={15} weight="bold" lineHeight={18} color="#B8F7D4">CONNECTED</Text>
          </Frame>
        </Frame>
        <Frame name="NAS metrics" flex="row" w="fill" gap={16}>
          <Frame name="Frame" flex="col" w={240} gap={6} p={18} bg="#0B1519" rounded={8}>
            <Text name="Text" w={59} lineHeight={17} color="#7E9099">LIBRARY</Text>
            <Text name="Text" w={188} size={26} weight="bold" lineHeight={32} color="#F6F8F9">1,842 titles</Text>
          </Frame>
          <Frame name="Frame" flex="col" w={240} gap={6} p={18} bg="#0B1519" rounded={8}>
            <Text name="Text" w={76} lineHeight={17} color="#7E9099">LAST SCAN</Text>
            <Text name="Text" w={141} size={26} weight="bold" lineHeight={32} color="#F6F8F9">4 min ago</Text>
          </Frame>
          <Frame name="Frame" flex="col" w={240} gap={6} p={18} bg="#0B1519" rounded={8}>
            <Text name="Text" w={68} lineHeight={17} color="#7E9099">PLAYBACK</Text>
            <Text name="Text" w={172} size={26} weight="bold" lineHeight={32} color="#54D694">Direct play</Text>
          </Frame>
        </Frame>
        <Frame name="NAS actions" flex="row" gap={14}>
          <Frame name="Browse focused action" flex="row" h={58} gap={12} items="center" py={0} px={24} bg="#F5FFFF" rounded={8}>
            <Text name="Text" w={152} size={18} weight="bold" lineHeight={22} color="#061514">Browse library</Text>
          </Frame>
          <Frame name="Frame" flex="row" h={58} items="center" py={0} px={24} bg="#17242A" stroke="#33444C" rounded={8}>
            <Text name="Text" w={87} size={18} weight="medium" lineHeight={22} color="#E2E8EA">Scan now</Text>
          </Frame>
          <Frame name="Frame" flex="row" h={58} items="center" py={0} px={24} bg="#17242A" stroke="#33444C" rounded={8}>
            <Text name="Text" w={65} size={18} weight="medium" lineHeight={22} color="#E2E8EA">Manage</Text>
          </Frame>
        </Frame>
      </Frame>
      <Frame name="Source side stack" flex="col" grow={1} h="fill" gap={24}>
        <Frame name="Add-ons" flex="col" grow={1} w="fill" justify="between" p={26} bg="#15161B" stroke="#2D3038" rounded={8}>
          <Frame name="Frame" flex="row" w="fill" justify="between" items="center">
            <Frame name="Frame" flex="col" gap={7}>
              <Text name="Text" w={153} size={15} weight="medium" lineHeight={18} color="#FF8C80">COMMUNITY ADD-ONS</Text>
              <Text name="Text" w={135} size={28} weight="bold" lineHeight={34} color="#F6F8F9">5 active</Text>
            </Frame>
            <Frame name="Frame" w={52} h={52} bg="#FF6A5C" rounded={8} />
          </Frame>
          <Frame name="Frame" flex="row" gap={8}>
            <Frame name="Frame" flex="col" py={8} px={12} bg="#272128" rounded={7}>
              <Text name="Text" w={68} lineHeight={17} color="#F4D6D2">Cinemeta</Text>
            </Frame>
            <Frame name="Frame" flex="col" py={8} px={12} bg="#272128" rounded={7}>
              <Text name="Text" w={110} lineHeight={17} color="#F4D6D2">OpenSubtitles</Text>
            </Frame>
            <Frame name="Frame" flex="col" py={8} px={12} bg="#272128" rounded={7}>
              <Text name="Text" w={17} lineHeight={17} color="#F4D6D2">+3</Text>
            </Frame>
          </Frame>
        </Frame>
        <Frame name="Remote shares" flex="col" grow={1} w="fill" justify="between" p={26} bg="#111B18" stroke="#283C35" rounded={8}>
          <Frame name="Frame" flex="row" w="fill" justify="between" items="center">
            <Frame name="Frame" flex="col" gap={7}>
              <Text name="Text" w={117} size={15} weight="medium" lineHeight={18} color="#67DDA0">REMOTE SHARES</Text>
              <Text name="Text" w={135} size={28} weight="bold" lineHeight={34} color="#F6F8F9">2 online</Text>
            </Frame>
            <Frame name="Frame" flex="row" gap={7}>
              <Ellipse name="Ellipse" w={14} h={14} bg="#54D694" rounded={7} />
              <Ellipse name="Ellipse" w={14} h={14} bg="#54D694" rounded={7} />
            </Frame>
          </Frame>
          <Text name="Text" w={260} size={16} lineHeight={20} color="#8FA49B">Home Plex  /  Studio WebDAV</Text>
        </Frame>
      </Frame>
    </Frame>
    <Frame name="Available sources heading" flex="row" w="fill" justify="between" items="center">
      <Text name="Text" w={245} size={24} weight="bold" lineHeight={29} color="#F6F8F9">Available sources</Text>
      <Text name="Text" w={269} size={16} weight="medium" lineHeight={20} color="#83939B">Sorted by quality and health</Text>
    </Frame>
    <Frame name="Available source cards" flex="row" grow={1} w="fill" gap={18}>
      <Frame name="Real-Debrid source" flex="col" grow={1} h="fill" justify="between" p={22} bg="#11171C" stroke="#2B3940" rounded={8}>
        <Frame name="Frame" flex="row" justify="between" items="center">
          <Text name="Text" w={106} size={16} weight="bold" lineHeight={20} color="#FFFFFF">Real-Debrid</Text>
          <Ellipse name="Ellipse" w={10} h={10} bg="#54D694" rounded={5} />
        </Frame>
        <Text name="Text" w={101} size={28} weight="bold" lineHeight={34} color="#A6FFF3">4K HDR</Text>
        <Text name="Text" w={168} lineHeight={17} color="#809099">68.2 Mbps  /  cached</Text>
      </Frame>
      <Frame name="Torrentio source" flex="col" grow={1} h="fill" justify="between" p={22} bg="#11171C" stroke="#2B3940" rounded={8}>
        <Frame name="Frame" flex="row" justify="between" items="center">
          <Text name="Text" w={87} size={16} weight="bold" lineHeight={20} color="#FFFFFF">Torrentio</Text>
          <Ellipse name="Ellipse" w={10} h={10} bg="#54D694" rounded={5} />
        </Frame>
        <Text name="Text" w={84} size={28} weight="bold" lineHeight={34} color="#F6F8F9">1080p</Text>
        <Text name="Text" w={185} lineHeight={17} color="#809099">12.4 Mbps  /  83 peers</Text>
      </Frame>
      <Frame name="Easynews source" flex="col" grow={1} h="fill" justify="between" p={22} bg="#11171C" stroke="#2B3940" rounded={8}>
        <Frame name="Frame" flex="row" justify="between" items="center">
          <Text name="Text" w={77} size={16} weight="bold" lineHeight={20} color="#FFFFFF">Easynews</Text>
          <Ellipse name="Ellipse" w={10} h={10} bg="#F3C969" rounded={5} />
        </Frame>
        <Text name="Text" w={84} size={28} weight="bold" lineHeight={34} color="#F6F8F9">1080p</Text>
        <Text name="Text" w={160} lineHeight={17} color="#809099">8.1 Mbps  /  remote</Text>
      </Frame>
      <Frame name="WebDAV source" flex="col" grow={1} h="fill" justify="between" p={22} bg="#11171C" stroke="#2B3940" rounded={8}>
        <Frame name="Frame" flex="row" justify="between" items="center">
          <Text name="Text" w={125} size={16} weight="bold" lineHeight={20} color="#FFFFFF">Studio WebDAV</Text>
          <Ellipse name="Ellipse" w={10} h={10} bg="#54D694" rounded={5} />
        </Frame>
        <Text name="Text" w={135} size={28} weight="bold" lineHeight={34} color="#F6F8F9">Original</Text>
        <Text name="Text" w={177} lineHeight={17} color="#809099">Direct play  /  31 ms</Text>
      </Frame>
    </Frame>
  </Frame>
</Frame>