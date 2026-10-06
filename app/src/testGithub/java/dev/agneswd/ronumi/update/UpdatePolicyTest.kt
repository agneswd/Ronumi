package dev.agneswd.ronumi.update

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The release API can answer before any APK exists.
 * A missing repository must not look like a connection error.
 */
class UpdatePolicyTest {
    @Test fun missingRepositoryIsTheCurrentRelease() {
        assertEquals(ReleaseLookup.UpToDate, UpdatePolicy.releaseLookup(404))
    }

    @Test fun successStillReadsTheReleaseBody() {
        assertEquals(ReleaseLookup.ReadBody, UpdatePolicy.releaseLookup(200))
    }

    @Test fun rateLimitsStayVisible() {
        listOf(403, 429).forEach { status ->
            assertEquals(ReleaseLookup.RateLimited, UpdatePolicy.releaseLookup(status))
        }
    }

    @Test fun outagesAndRedirectsAreNotAQuietSuccess() {
        listOf(0, -1, 204, 301, 302, 500, 503).forEach { status ->
            assertEquals(ReleaseLookup.Unavailable, UpdatePolicy.releaseLookup(status))
        }
    }

    @Test fun oldPackageIsNotInstalled() {
        assertEquals(
            ApkPackageDecision.LegacyApp,
            UpdatePolicy.apkPackageDecision("dev.agneswd.stillpoint", "dev.agneswd.ronumi"),
        )
    }

    @Test fun oldPackageStaysLegacyEvenWhenTheIdsMatch() {
        assertEquals(
            ApkPackageDecision.LegacyApp,
            UpdatePolicy.apkPackageDecision(UpdatePolicy.LEGACY_PACKAGE, UpdatePolicy.LEGACY_PACKAGE),
        )
    }

    @Test fun currentPackageCanBeInstalled() {
        assertEquals(
            ApkPackageDecision.CurrentApp,
            UpdatePolicy.apkPackageDecision("dev.agneswd.ronumi", "dev.agneswd.ronumi"),
        )
    }

    @Test fun otherPackagesStayForeign() {
        listOf("", "com.example.other", "dev.agneswd.Stillpoint", "dev.agneswd.ronumi.debug").forEach { candidate ->
            assertEquals(ApkPackageDecision.OtherApp, UpdatePolicy.apkPackageDecision(candidate, "dev.agneswd.ronumi"))
        }
    }
}
