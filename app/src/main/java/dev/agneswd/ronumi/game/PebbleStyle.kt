package dev.agneswd.ronumi.game

import dev.agneswd.ronumi.R
import androidx.annotation.StringRes

/** One item can be worn in each slot. An empty slot keeps Pebble's original look. */
enum class PebbleSlot { COLOR, OUTFIT, HAT, ACCESSORY }

data class PebbleItem(val id: String, @param:StringRes val nameRes: Int, val slot: PebbleSlot, val level: Int, val minimumTaps: Int = 0)

/** Stable IDs are saved in settings and backups. Level requirements never consume XP. */
object PebbleStyles {
    const val SECRET_PET_TAPS = 1000
    val items: List<PebbleItem> = listOf(
        PebbleItem("color_mint", R.string.wardrobe_item_color_mint, PebbleSlot.COLOR, 2),
        PebbleItem("color_peach", R.string.wardrobe_item_color_peach, PebbleSlot.COLOR, 3),
        PebbleItem("color_sky", R.string.wardrobe_item_color_sky, PebbleSlot.COLOR, 5),
        PebbleItem("color_rose", R.string.wardrobe_item_color_rose, PebbleSlot.COLOR, 7),
        PebbleItem("color_sand", R.string.wardrobe_item_color_sand, PebbleSlot.COLOR, 9),
        PebbleItem("color_slate", R.string.wardrobe_item_color_slate, PebbleSlot.COLOR, 12),
        PebbleItem("color_lilac", R.string.wardrobe_item_color_lilac, PebbleSlot.COLOR, 15),
        PebbleItem("color_moon", R.string.wardrobe_item_color_moon, PebbleSlot.COLOR, 20),
        PebbleItem("outfit_tee", R.string.wardrobe_item_outfit_tee, PebbleSlot.OUTFIT, 1),
        PebbleItem("outfit_stripes", R.string.wardrobe_item_outfit_stripes, PebbleSlot.OUTFIT, 3),
        PebbleItem("outfit_overalls", R.string.wardrobe_item_outfit_overalls, PebbleSlot.OUTFIT, 5),
        PebbleItem("outfit_sweater", R.string.wardrobe_item_outfit_sweater, PebbleSlot.OUTFIT, 6),
        PebbleItem("outfit_raincoat", R.string.wardrobe_item_outfit_raincoat, PebbleSlot.OUTFIT, 8),
        PebbleItem("outfit_vest", R.string.wardrobe_item_outfit_vest, PebbleSlot.OUTFIT, 10),
        PebbleItem("outfit_apron", R.string.wardrobe_item_outfit_apron, PebbleSlot.OUTFIT, 11),
        PebbleItem("outfit_stars", R.string.wardrobe_item_outfit_stars, PebbleSlot.OUTFIT, 13),
        PebbleItem("outfit_suit", R.string.wardrobe_item_outfit_suit, PebbleSlot.OUTFIT, 16),
        PebbleItem("outfit_cape", R.string.wardrobe_item_outfit_cape, PebbleSlot.OUTFIT, 19),
        PebbleItem("outfit_star_guardian", R.string.wardrobe_item_outfit_star_guardian, PebbleSlot.OUTFIT, 1, minimumTaps = SECRET_PET_TAPS),
        PebbleItem("hat_beanie", R.string.wardrobe_item_hat_beanie, PebbleSlot.HAT, 2),
        PebbleItem("hat_bucket", R.string.wardrobe_item_hat_bucket, PebbleSlot.HAT, 4),
        PebbleItem("hat_flower", R.string.wardrobe_item_hat_flower, PebbleSlot.HAT, 6),
        PebbleItem("hat_beret", R.string.wardrobe_item_hat_beret, PebbleSlot.HAT, 8),
        PebbleItem("hat_sun", R.string.wardrobe_item_hat_sun, PebbleSlot.HAT, 10),
        PebbleItem("hat_sleep", R.string.wardrobe_item_hat_sleep, PebbleSlot.HAT, 12),
        PebbleItem("hat_bow", R.string.wardrobe_item_hat_bow, PebbleSlot.HAT, 14),
        PebbleItem("hat_captain", R.string.wardrobe_item_hat_captain, PebbleSlot.HAT, 16),
        PebbleItem("hat_wizard", R.string.wardrobe_item_hat_wizard, PebbleSlot.HAT, 18),
        PebbleItem("hat_crown", R.string.wardrobe_item_hat_crown, PebbleSlot.HAT, 20),
        PebbleItem("accessory_scarf", R.string.wardrobe_item_accessory_scarf, PebbleSlot.ACCESSORY, 2),
        PebbleItem("accessory_glasses", R.string.wardrobe_item_accessory_glasses, PebbleSlot.ACCESSORY, 4),
        PebbleItem("accessory_bowtie", R.string.wardrobe_item_accessory_bowtie, PebbleSlot.ACCESSORY, 7),
        PebbleItem("accessory_satchel", R.string.wardrobe_item_accessory_satchel, PebbleSlot.ACCESSORY, 9),
        PebbleItem("accessory_headphones", R.string.wardrobe_item_accessory_headphones, PebbleSlot.ACCESSORY, 11),
        PebbleItem("accessory_neckerchief", R.string.wardrobe_item_accessory_neckerchief, PebbleSlot.ACCESSORY, 14),
        PebbleItem("accessory_medal", R.string.wardrobe_item_accessory_medal, PebbleSlot.ACCESSORY, 17),
        PebbleItem("accessory_star", R.string.wardrobe_item_accessory_star, PebbleSlot.ACCESSORY, 19),
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
