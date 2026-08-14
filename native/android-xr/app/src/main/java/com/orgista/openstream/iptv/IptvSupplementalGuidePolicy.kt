package com.orgista.openstream.iptv

/** Nightly national/local XMLTV fallbacks maintained by the open EPGTalk project. */
object IptvSupplementalGuidePolicy {
    const val SOURCE_ID = "epgtalk-us-guide"
    const val NATIONAL_GUIDE_URL =
        "https://raw.githubusercontent.com/acidjesuz/EPGTalk/master/US_guide.xml.gz"
    const val LOCAL_GUIDE_URL =
        "https://raw.githubusercontent.com/acidjesuz/EPGTalk/master/US_local_guide.xml.gz"

    fun catalog(channels: List<IptvChannel>): IptvSourceCatalog = IptvSourceCatalog(
        source = IptvSource.m3u(
            id = SOURCE_ID,
            name = "US television guide",
            playlistUrl = NATIONAL_GUIDE_URL,
        ),
        channels = channels,
        guideUrls = listOf(NATIONAL_GUIDE_URL, LOCAL_GUIDE_URL),
    )
}
