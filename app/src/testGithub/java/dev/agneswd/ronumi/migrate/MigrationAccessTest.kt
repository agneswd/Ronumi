package dev.agneswd.ronumi.migrate

import java.io.FileNotFoundException
import org.junit.Assert.assertThrows
import org.junit.Test

class MigrationAccessTest {
    @Test fun callersWithoutPermissionCannotRead() {
        assertThrows(SecurityException::class.java) {
            MigrationAccess.checkRead(MigrationAccess.URI, "r", false)
        }
    }

    @Test fun writeModesAreRejectedEvenWithPermission() {
        listOf("w", "wt", "wa", "rw", "rwt", "", "R").forEach { mode ->
            assertThrows(SecurityException::class.java) {
                MigrationAccess.checkRead(MigrationAccess.URI, mode, true)
            }
        }
    }

    @Test fun onlyTheExactDocumentUriIsAccepted() {
        listOf("content://dev.agneswd.stillpoint.migrate", "${MigrationAccess.URI}/",
            "${MigrationAccess.URI}?version=2", "${MigrationAccess.URI}#part",
            "content://dev.agneswd.stillpoint.migrate/settings",
            "content://other/backup", "file:///backup").forEach { uri ->
            assertThrows(FileNotFoundException::class.java) {
                MigrationAccess.checkRead(uri, "r", true)
            }
        }
    }

    @Test fun permittedReadIsAccepted() {
        MigrationAccess.checkRead(MigrationAccess.URI, "r", true)
    }
}
