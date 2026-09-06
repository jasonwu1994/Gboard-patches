package dev.jason.gboardpatches.extension.lanftp.runtime;

import org.apache.ftpserver.message.MessageResource;
import org.junit.Assert;
import org.junit.Test;

public final class LanFtpMessageResourceTest {
    @Test
    public void embedsDefaultAndCommandSpecificMessages() {
        MessageResource messages = LanFtpMessageResource.create();

        Assert.assertEquals("Service ready for new user.",
                messages.getMessage(220, null, null));
        Assert.assertEquals("Transfer complete.",
                messages.getMessage(226, "STOR", null));
        Assert.assertEquals("Requested action not taken.",
                messages.getMessage(550, "unknown", null));
        Assert.assertTrue(messages.getMessages(null).size() > 100);
    }
}
