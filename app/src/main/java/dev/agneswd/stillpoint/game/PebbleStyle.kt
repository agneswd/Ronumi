package dev.agneswd.stillpoint.game

/** One item can be worn in each slot. An empty slot keeps Pebble's original look. */
enum class PebbleSlot { COLOR, OUTFIT, HAT, ACCESSORY }

data class PebbleItem(val id: String, val name: String, val slot: PebbleSlot, val level: Int, val minimumTaps: Int = 0)

/** Stable IDs are saved in settings and backups. Level requirements never consume XP. */
object PebbleStyles {
    const val SECRET_PET_TAPS = 1000
    val items: List<PebbleItem> = listOf(
        PebbleItem("color_mint", "Mint", PebbleSlot.COLOR, 2),
        PebbleItem("color_peach", "Peach", PebbleSlot.COLOR, 3),
        PebbleItem("color_sky", "Sky", PebbleSlot.COLOR, 5),
        PebbleItem("color_rose", "Rose quartz", PebbleSlot.COLOR, 7),
        PebbleItem("color_sand", "Sandstone", PebbleSlot.COLOR, 9),
        PebbleItem("color_slate", "Slate", PebbleSlot.COLOR, 12),
        PebbleItem("color_lilac", "Lilac", PebbleSlot.COLOR, 15),
        PebbleItem("color_moon", "Moonstone", PebbleSlot.COLOR, 20),
        PebbleItem("outfit_tee", "Everyday tee", PebbleSlot.OUTFIT, 1),
        PebbleItem("outfit_stripes", "Sailor stripes", PebbleSlot.OUTFIT, 3),
        PebbleItem("outfit_overalls", "Garden overalls", PebbleSlot.OUTFIT, 5),
        PebbleItem("outfit_sweater", "Cozy sweater", PebbleSlot.OUTFIT, 6),
        PebbleItem("outfit_raincoat", "Raincoat", PebbleSlot.OUTFIT, 8),
        PebbleItem("outfit_vest", "Explorer vest", PebbleSlot.OUTFIT, 10),
        PebbleItem("outfit_apron", "Art apron", PebbleSlot.OUTFIT, 11),
        PebbleItem("outfit_stars", "Star pajamas", PebbleSlot.OUTFIT, 13),
        PebbleItem("outfit_suit", "Little waistcoat", PebbleSlot.OUTFIT, 16),
        PebbleItem("outfit_cape", "Focus cape", PebbleSlot.OUTFIT, 19),
        PebbleItem("outfit_star_guardian", "Star guardian", PebbleSlot.OUTFIT, 1, minimumTaps = SECRET_PET_TAPS),
        PebbleItem("hat_beanie", "Soft beanie", PebbleSlot.HAT, 2),
        PebbleItem("hat_bucket", "Bucket hat", PebbleSlot.HAT, 4),
        PebbleItem("hat_flower", "Daisy", PebbleSlot.HAT, 6),
        PebbleItem("hat_beret", "Artist beret", PebbleSlot.HAT, 8),
        PebbleItem("hat_sun", "Sun hat", PebbleSlot.HAT, 10),
        PebbleItem("hat_sleep", "Sleep cap", PebbleSlot.HAT, 12),
        PebbleItem("hat_bow", "Big bow", PebbleSlot.HAT, 14),
        PebbleItem("hat_captain", "Captain's cap", PebbleSlot.HAT, 16),
        PebbleItem("hat_wizard", "Stargazer hat", PebbleSlot.HAT, 18),
        PebbleItem("hat_crown", "Little crown", PebbleSlot.HAT, 20),
        PebbleItem("accessory_scarf", "Warm scarf", PebbleSlot.ACCESSORY, 2),
        PebbleItem("accessory_glasses", "Round glasses", PebbleSlot.ACCESSORY, 4),
        PebbleItem("accessory_bowtie", "Bow tie", PebbleSlot.ACCESSORY, 7),
        PebbleItem("accessory_satchel", "Field satchel", PebbleSlot.ACCESSORY, 9),
        PebbleItem("accessory_headphones", "Quiet headphones", PebbleSlot.ACCESSORY, 11),
        PebbleItem("accessory_neckerchief", "Trail neckerchief", PebbleSlot.ACCESSORY, 14),
        PebbleItem("accessory_medal", "Focus medal", PebbleSlot.ACCESSORY, 17),
        PebbleItem("accessory_star", "Pocket star", PebbleSlot.ACCESSORY, 19),
    )

    fun isUnlocked(item: PebbleItem, level: Int, petTapCount: Int): Boolean =
        item.level <= level && petTapCount >= item.minimumTaps

    fun visibleItems(petTapCount: Int): List<PebbleItem> = items.filter {
        it.minimumTaps == 0 || petTapCount >= it.minimumTaps
    }

    /** Catalog order breaks invalid duplicate slots in a stable way. */
    fun resolve(ids: Set<String>, level: Int, petTapCount: Int = 0): Set<String> = items
        .filter { it.id in ids && isUnlocked(it, level, petTapCount) }
        .distinctBy { it.slot }
        .mapTo(linkedSetOf()) { it.id }
}
