sub init()
    m.browse = m.top.FindNode("browse")
    m.detailsPanel = m.top.FindNode("detailsPanel")
    m.video = m.top.FindNode("video")
    m.focusRing = m.top.FindNode("focusRing")
    m.formatPill = m.top.FindNode("formatPill")
    m.formatTimer = m.top.FindNode("formatTimer")
    m.network = m.top.FindNode("network")
    m.focusIndex = 0
    m.inDetails = false
    m.inPlayer = false
    m.duration = 0.0

    m.video.ObserveField("position", "onPlaybackPosition")
    m.video.ObserveField("duration", "onPlaybackDuration")
    m.video.ObserveField("state", "onPlaybackState")
    m.formatTimer.ObserveField("fire", "hideFormatPill")
    updateNetwork()
    updateFocus()
    m.top.SetFocus(true)
end sub
sub onLaunchArgs()
    args = m.top.launchArgs
    if args <> invalid and args.DoesExist("contentId") then playNorthstar()
end sub

sub updateNetwork()
    deviceInfo = CreateObject("roDeviceInfo")
    connection = deviceInfo.GetConnectionType()
    if connection = invalid or connection = ""
        m.network.text = "OFFLINE"
        m.network.color = "0xf4c576ff"
    else
        m.network.text = "ONLINE"
        m.network.color = "0x62e4b2ff"
    end if
end sub

function onKeyEvent(key as string, press as boolean) as boolean
    if not press then return false

    if m.inPlayer
        if key = "back"
            closePlayer()
            return true
        else if key = "play" or key = "OK"
            if m.video.state = "playing"
                m.video.control = "pause"
            else
                m.video.control = "resume"
            end if
            return true
        end if
        return false
    end if

    if m.inDetails
        if key = "back" or key = "OK"
            m.detailsPanel.visible = false
            m.inDetails = false
            return true
        end if
        return true
    end if

    if key = "back" then return false
    if key = "up"
        if m.focusIndex >= 2 then m.focusIndex = 0
    else if key = "down"
        if m.focusIndex < 2 then m.focusIndex = 2
    else if key = "left"
        if m.focusIndex > 0 then m.focusIndex = m.focusIndex - 1
    else if key = "right"
        if m.focusIndex < 6 then m.focusIndex = m.focusIndex + 1
    else if key = "OK"
        if m.focusIndex = 1
            m.detailsPanel.visible = true
            m.inDetails = true
        else
            playNorthstar()
        end if
        return true
    else
        return false
    end if

    updateFocus()
    return true
end function

sub updateFocus()
    positions = [[78, 590], [330, 590], [78, 808], [412, 808], [746, 808], [1080, 808], [1414, 808]]
    sizes = [[250, 88], [250, 88], [330, 186], [330, 186], [330, 186], [330, 186], [330, 186]]
    m.focusRing.translation = positions[m.focusIndex]
    m.focusRing.width = sizes[m.focusIndex][0]
    m.focusRing.height = sizes[m.focusIndex][1]
end sub

sub playNorthstar()
    content = CreateObject("roSGNode", "ContentNode")
    content.url = "pkg:/media/openstream_demo.mp4"
    content.streamFormat = "mp4"
    content.title = "Northstar"
    m.video.content = content
    m.video.visible = true
    m.browse.visible = false
    m.inPlayer = true

    section = CreateObject("roRegistrySection", "OpenStreamPlayback")
    saved = section.Read("northstar.position")
    m.video.control = "play"
    if saved <> invalid and saved <> ""
        position = Val(saved)
        if position >= 15 then m.video.seek = position
    end if

    m.formatPill.visible = true
    m.formatTimer.control = "start"
end sub

sub closePlayer()
    saveProgress()
    m.video.control = "stop"
    m.video.visible = false
    m.browse.visible = true
    m.formatPill.visible = false
    m.inPlayer = false
    m.top.SetFocus(true)
end sub

sub onPlaybackPosition()
    if m.inPlayer and Int(m.video.position) mod 5 = 0 then saveProgress()
end sub

sub onPlaybackDuration()
    m.duration = m.video.duration
end sub

sub onPlaybackState()
    if m.video.state = "finished" then closePlayer()
end sub

sub saveProgress()
    if m.video.position = invalid or m.video.position < 0 then return
    section = CreateObject("roRegistrySection", "OpenStreamPlayback")
    position = m.video.position
    if m.duration > 0 and (position >= m.duration * 0.95 or m.duration - position <= 30)
        position = 0
    end if
    section.Write("northstar.position", position.ToStr())
    section.Flush()
end sub

sub hideFormatPill()
    m.formatPill.visible = false
end sub
