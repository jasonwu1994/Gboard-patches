package dev.jason.gboardpatches.extension.lanftp.runtime;

import org.apache.ftpserver.command.CommandFactory;
import org.junit.Assert;
import org.junit.Test;

public final class LanFtpCommandSurfaceTest {
    @Test
    public void commandFactoryExposesOnlyTheReviewedPassiveFtpSurface() {
        LanFtpTransferCoordinator transfers =
                new LanFtpTransferCoordinator(LanFtpActivityObserver.NONE, 2);
        try {
            CommandFactory commands = LanFtpEngine.buildCommandFactory(transfers);
            for (String allowed : new String[]{
                    "USER", "PASS", "QUIT", "PWD", "CWD", "CDUP",
                    "PASV", "EPSV", "LIST", "NLST", "MLST", "MLSD",
                    "SIZE", "MDTM", "MFMT", "REST", "STOR", "RETR",
                    "DELE", "MKD", "RMD", "RNFR", "RNTO", "NOOP",
                    "SYST", "TYPE", "MODE", "STRU", "FEAT", "OPTS", "ABOR"}) {
                Assert.assertNotNull(allowed, commands.getCommand(allowed));
            }
            for (String rejected : new String[]{
                    "AUTH", "PBSZ", "PROT", "MD5", "MMD5",
                    "SITE", "STAT", "HELP", "REIN", "ACCT"}) {
                Assert.assertNull(rejected, commands.getCommand(rejected));
            }
            for (String explicitRejection : new String[]{"APPE", "STOU", "PORT", "EPRT"}) {
                Assert.assertNotNull(explicitRejection, commands.getCommand(explicitRejection));
            }
        } finally {
            transfers.close();
        }
    }
}
