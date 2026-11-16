package org.syalosovetskyi.onemoney.ui.accounts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountIconCatalogueTest {
    @Test fun icon_keys_are_unique() {
        // A duplicate key crashes LazyVerticalGrid in IconColorPickerSheet ("sports", #3037).
        val duplicates = ACCOUNT_ICON_ENTRIES.groupBy { it.key }.filterValues { it.size > 1 }.keys
        assertTrue("duplicate account icon keys: $duplicates", duplicates.isEmpty())
        assertEquals(ACCOUNT_ICON_ENTRIES.size, ACCOUNT_ICONS.size)
    }

    @Test fun legacy_sports_key_still_resolves() {
        assertEquals(
            ACCOUNT_ICONS.first { it.key == "sports" }.icon,
            accountIconFromKey("sports"),
        )
    }
}
