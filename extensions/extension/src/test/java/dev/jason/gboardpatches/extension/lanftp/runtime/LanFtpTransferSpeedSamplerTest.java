package dev.jason.gboardpatches.extension.lanftp.runtime;

import org.junit.Assert;
import org.junit.Test;

public final class LanFtpTransferSpeedSamplerTest {
    private static final long MIB = 1024L * 1024L;

    @Test
    public void socketBufferBurstIsNotReportedAsWifiThroughput() {
        LanFtpTransferSpeedSampler sampler = new LanFtpTransferSpeedSampler(0L);

        Assert.assertEquals(LanFtpTransferSpeedSampler.NO_SAMPLE,
                sampler.sample(1L * MIB, 3_000_000L));
        Assert.assertEquals(LanFtpTransferSpeedSampler.NO_SAMPLE,
                sampler.sample(8L * MIB, 500_000_000L));
        Assert.assertEquals(12L * MIB,
                sampler.sample(12L * MIB, 1_000_000_000L));
    }

    @Test
    public void laterSamplesAlsoRequireAFullWindow() {
        LanFtpTransferSpeedSampler sampler = new LanFtpTransferSpeedSampler(0L);
        Assert.assertEquals(10L * MIB,
                sampler.sample(10L * MIB, 1_000_000_000L));
        Assert.assertEquals(LanFtpTransferSpeedSampler.NO_SAMPLE,
                sampler.sample(16L * MIB, 1_500_000_000L));
        Assert.assertEquals(12L * MIB,
                sampler.sample(22L * MIB, 2_000_000_000L));
    }
}
