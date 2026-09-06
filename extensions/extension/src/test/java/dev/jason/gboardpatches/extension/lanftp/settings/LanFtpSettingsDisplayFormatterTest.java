package dev.jason.gboardpatches.extension.lanftp.settings;

import org.junit.Assert;
import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

public final class LanFtpSettingsDisplayFormatterTest {
    private static final long KIB = 1024L;
    private static final long MIB = KIB * 1024L;
    private static final long GIB = MIB * 1024L;

    @Test
    public void byteCountsUseWindowsStyleBinaryUnitsAndThreeSignificantDigits() {
        Assert.assertEquals("0B", LanFtpSettingsDisplayFormatter.formatBytes(0L));
        Assert.assertEquals("1023B", LanFtpSettingsDisplayFormatter.formatBytes(1023L));
        Assert.assertEquals("1KB", LanFtpSettingsDisplayFormatter.formatBytes(KIB));
        Assert.assertEquals("2.61MB", LanFtpSettingsDisplayFormatter.formatBytes(
                Math.round(2.612d * MIB)));
        Assert.assertEquals("96.4MB", LanFtpSettingsDisplayFormatter.formatBytes(
                Math.round(96.4375d * MIB)));
        Assert.assertEquals("706MB", LanFtpSettingsDisplayFormatter.formatBytes(
                Math.round(705.6d * MIB)));
        Assert.assertEquals("2.61GB", LanFtpSettingsDisplayFormatter.formatBytes(
                Math.round(2.612d * GIB)));
    }

    @Test
    public void timestampsUseThePhoneTimeZoneAndRequestedCalendarShape() {
        TimeZone losAngeles = TimeZone.getTimeZone("America/Los_Angeles");
        Calendar calendar = new GregorianCalendar(losAngeles);
        calendar.clear();
        calendar.set(2026, Calendar.AUGUST, 28, 18, 0, 15);

        Assert.assertEquals("2026/8/28 18:00:15",
                LanFtpSettingsDisplayFormatter.formatTimestamp(
                        calendar.getTimeInMillis(), losAngeles));
        Assert.assertEquals("2026/8/29 10:00:15",
                LanFtpSettingsDisplayFormatter.formatTimestamp(
                        calendar.getTimeInMillis(), TimeZone.getTimeZone("Asia/Tokyo")));
    }

    @Test
    public void transferRateUsesTheSameWindowsStyleUnits() {
        Assert.assertEquals("12.5MB/s", LanFtpSettingsDisplayFormatter.formatRate(
                Math.round(12.5d * MIB)));
    }
}
