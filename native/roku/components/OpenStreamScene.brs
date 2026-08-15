sub init()
    m.network = m.top.FindNode("network")
    updateNetwork()
    m.top.SetFocus(true)
end sub

sub onLaunchArgs()
    ' Content is supplied by configured OpenStream sources. Launch arguments
    ' never select a title bundled into the channel package.
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
    if key = "back" then return false
    return true
end function
