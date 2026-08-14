package com.orgista.openstream.media;

import android.media.MediaCodecInfo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DolbyVisionCodecStringTest {
    @Test
    public void parsesDolbyVisionProfileAndLevel() {
        DolbyVisionCodecString.Descriptor descriptor = DolbyVisionCodecString
                .parse("dvh1.08.09")
                .orElseThrow();

        assertEquals("dvh1", descriptor.sampleEntry());
        assertEquals(8, descriptor.profile());
        assertEquals(9, descriptor.level());
    }

    @Test
    public void findsDolbyVisionAmongMultipleCodecs() {
        assertTrue(DolbyVisionCodecString.parse("hvc1.2.4.L153.B0, dvhe.05.06").isPresent());
    }

    @Test
    public void rejectsMalformedOrNonDolbyCodecs() {
        assertFalse(DolbyVisionCodecString.parse("hvc1.2.4.L153.B0").isPresent());
        assertFalse(DolbyVisionCodecString.parse("dvhe.bad.06").isPresent());
        assertFalse(DolbyVisionCodecString.parse(null).isPresent());
    }

    @Test
    public void mapsAndroidDecoderProfilesToDolbyBitstreamProfiles() {
        assertEquals(4, DolbyVisionCodecString.bitstreamProfile(
                MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheDtr).orElseThrow());
        assertEquals(5, DolbyVisionCodecString.bitstreamProfile(
                MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheStn).orElseThrow());
        assertEquals(8, DolbyVisionCodecString.bitstreamProfile(
                MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvheSt).orElseThrow());
        assertEquals(9, DolbyVisionCodecString.bitstreamProfile(
                MediaCodecInfo.CodecProfileLevel.DolbyVisionProfileDvavSe).orElseThrow());
    }
}
