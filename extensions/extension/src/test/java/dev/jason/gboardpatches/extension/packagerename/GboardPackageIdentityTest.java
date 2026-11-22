package dev.jason.gboardpatches.extension.packagerename;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public final class GboardPackageIdentityTest {
    private static final String RENAMED = "dev.jason.com.google.android.inputmethod.latin";
    private static final String CUSTOM = "com.example.keyboard";

    @After
    public void resetCache() {
        GboardPackageIdentity.resetCacheForTest();
    }

    @Test
    public void identityIsAPrefixedLowercaseSha256() {
        Assert.assertEquals("sha256:".length() + 64,
                GboardPackageIdentity.identityOf(CUSTOM).length());
        Assert.assertTrue(GboardPackageIdentity.identityOf(CUSTOM).startsWith("sha256:"));
        Assert.assertEquals(GboardPackageIdentity.identityOf(CUSTOM),
                GboardPackageIdentity.identityOf(CUSTOM).toLowerCase(java.util.Locale.ROOT));
        Assert.assertNotEquals(GboardPackageIdentity.identityOf(CUSTOM),
                GboardPackageIdentity.identityOf(RENAMED));
        Assert.assertNull(GboardPackageIdentity.identityOf(null));
        Assert.assertNull(GboardPackageIdentity.identityOf(""));
    }

    @Test
    public void identityMatchesTheValueThePackageRenamePatchWrites() {
        // The same vectors are asserted by the patch-side test, so both sides agree.
        Assert.assertEquals(
                "sha256:f7be8452dc6a8d37e01288672407a2ec20b33d0b559b110d1d231d84c405f467",
                GboardPackageIdentity.identityOf(RENAMED));
        Assert.assertEquals(
                "sha256:376535488ad8c71fc204404de019921ca7cda644bb8d249ff939b77917db0c2a",
                GboardPackageIdentity.identityOf(CUSTOM));
        Assert.assertEquals("dev.jason.gboardpatches.package_identity",
                GboardPackageIdentity.IDENTITY_META_DATA_NAME);
    }

    @Test
    public void officialPackageIsSupportedWithoutAnIdentity() {
        Assert.assertTrue(GboardPackageIdentity.isSupportedPackage(
                GboardPackageIdentity.OFFICIAL_PACKAGE, null));
    }

    @Test
    public void renamedPackageNeedsItsOwnMatchingIdentity() {
        Assert.assertTrue(GboardPackageIdentity.isSupportedPackage(
                CUSTOM, GboardPackageIdentity.identityOf(CUSTOM)));
        Assert.assertTrue(GboardPackageIdentity.isSupportedPackage(
                RENAMED, " " + GboardPackageIdentity.identityOf(RENAMED).toUpperCase() + " "));

        // Renamed by another tool: no identity at all.
        Assert.assertFalse(GboardPackageIdentity.isSupportedPackage(RENAMED, null));
        Assert.assertFalse(GboardPackageIdentity.isSupportedPackage(RENAMED, ""));
        // Renamed again after this bundle's rename: the identity names a different package.
        Assert.assertFalse(GboardPackageIdentity.isSupportedPackage(
                CUSTOM, GboardPackageIdentity.identityOf(RENAMED)));
        // A plain package name is never accepted as an identity.
        Assert.assertFalse(GboardPackageIdentity.isSupportedPackage(CUSTOM, CUSTOM));
        Assert.assertFalse(GboardPackageIdentity.isSupportedPackage(null, null));
    }

    @Test
    public void contextFailuresReportUnsupportedInsteadOfThrowing() {
        Context base = RuntimeEnvironment.getApplication();
        Context throwingPackageName = new ContextWrapper(base) {
            @Override
            public String getPackageName() {
                throw new IllegalStateException("package name unavailable");
            }
        };
        Context throwingPackageManager = new ContextWrapper(base) {
            @Override
            public String getPackageName() {
                return CUSTOM;
            }

            @Override
            public PackageManager getPackageManager() {
                throw new IllegalStateException("package manager unavailable");
            }
        };

        Assert.assertFalse(GboardPackageIdentity.isSupported(null));
        Assert.assertFalse(GboardPackageIdentity.isSupported(throwingPackageName));
        Assert.assertFalse(GboardPackageIdentity.isSupported(throwingPackageManager));
    }

    @Test
    public void officialContextIsSupportedWithoutReadingMetaData() {
        Context official = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override
            public String getPackageName() {
                return GboardPackageIdentity.OFFICIAL_PACKAGE;
            }

            @Override
            public PackageManager getPackageManager() {
                throw new AssertionError("official package must not read meta-data");
            }
        };

        Assert.assertTrue(GboardPackageIdentity.isSupported(official));
    }
}
