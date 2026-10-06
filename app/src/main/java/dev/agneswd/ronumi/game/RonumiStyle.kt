package dev.agneswd.ronumi.game

import dev.agneswd.ronumi.R
import androidx.annotation.StringRes

/** One item can be worn in each slot. An empty slot keeps Ronumi's original look. */
enum class RonumiSlot { COLOR, OUTFIT, HAT, ACCESSORY }

data class RonumiItem(val id: String, @param:StringRes val nameRes: Int, val slot: RonumiSlot, val level: Int, val minimumTaps: Int = 0)

/** Stable IDs are saved in settings and backups. Level requirements never consume XP. */
object RonumiStyles {
    const val SECRET_PET_TAPS = 1000
    val items: List<RonumiItem> = listOf(
        RonumiItem("color_mint", R.string.wardrobe_item_color_mint, RonumiSlot.COLOR, 2),
        RonumiItem("color_peach", R.string.wardrobe_item_color_peach, RonumiSlot.COLOR, 3),
        RonumiItem("color_sky", R.string.wardrobe_item_color_sky, RonumiSlot.COLOR, 5),
        RonumiItem("color_rose", R.string.wardrobe_item_color_rose, RonumiSlot.COLOR, 7),
        RonumiItem("color_sand", R.string.wardrobe_item_color_sand, RonumiSlot.COLOR, 9),
        RonumiItem("color_slate", R.string.wardrobe_item_color_slate, RonumiSlot.COLOR, 12),
        RonumiItem("color_lilac", R.string.wardrobe_item_color_lilac, RonumiSlot.COLOR, 15),
        RonumiItem("color_moon", R.string.wardrobe_item_color_moon, RonumiSlot.COLOR, 20),
        RonumiItem("outfit_tee", R.string.wardrobe_item_outfit_tee, RonumiSlot.OUTFIT, 1),
        RonumiItem("outfit_stripes", R.string.wardrobe_item_outfit_stripes, RonumiSlot.OUTFIT, 3),
        RonumiItem("outfit_overalls", R.string.wardrobe_item_outfit_overalls, RonumiSlot.OUTFIT, 5),
        RonumiItem("outfit_sweater", R.string.wardrobe_item_outfit_sweater, RonumiSlot.OUTFIT, 6),
        RonumiItem("outfit_raincoat", R.string.wardrobe_item_outfit_raincoat, RonumiSlot.OUTFIT, 8),
        RonumiItem("outfit_vest", R.string.wardrobe_item_outfit_vest, RonumiSlot.OUTFIT, 10),
        RonumiItem("outfit_apron", R.string.wardrobe_item_outfit_apron, RonumiSlot.OUTFIT, 11),
        RonumiItem("outfit_stars", R.string.wardrobe_item_outfit_stars, RonumiSlot.OUTFIT, 13),
        RonumiItem("outfit_suit", R.string.wardrobe_item_outfit_suit, RonumiSlot.OUTFIT, 16),
        RonumiItem("outfit_cape", R.string.wardrobe_item_outfit_cape, RonumiSlot.OUTFIT, 19),
        RonumiItem("outfit_star_guardian", R.string.wardrobe_item_outfit_star_guardian, RonumiSlot.OUTFIT, 1, minimumTaps = SECRET_PET_TAPS),
        RonumiItem("hat_beanie", R.string.wardrobe_item_hat_beanie, RonumiSlot.HAT, 2),
        RonumiItem("hat_bucket", R.string.wardrobe_item_hat_bucket, RonumiSlot.HAT, 4),
        RonumiItem("hat_flower", R.string.wardrobe_item_hat_flower, RonumiSlot.HAT, 6),
        RonumiItem("hat_beret", R.string.wardrobe_item_hat_beret, RonumiSlot.HAT, 8),
        RonumiItem("hat_sun", R.string.wardrobe_item_hat_sun, RonumiSlot.HAT, 10),
        RonumiItem("hat_sleep", R.string.wardrobe_item_hat_sleep, RonumiSlot.HAT, 12),
        RonumiItem("hat_bow", R.string.wardrobe_item_hat_bow, RonumiSlot.HAT, 14),
        RonumiItem("hat_captain", R.string.wardrobe_item_hat_captain, RonumiSlot.HAT, 16),
        RonumiItem("hat_wizard", R.string.wardrobe_item_hat_wizard, RonumiSlot.HAT, 18),
        RonumiItem("hat_crown", R.string.wardrobe_item_hat_crown, RonumiSlot.HAT, 20),
        RonumiItem("accessory_scarf", R.string.wardrobe_item_accessory_scarf, RonumiSlot.ACCESSORY, 2),
        RonumiItem("accessory_glasses", R.string.wardrobe_item_accessory_glasses, RonumiSlot.ACCESSORY, 4),
        RonumiItem("accessory_bowtie", R.string.wardrobe_item_accessory_bowtie, RonumiSlot.ACCESSORY, 7),
        RonumiItem("accessory_satchel", R.string.wardrobe_item_accessory_satchel, RonumiSlot.ACCESSORY, 9),
        RonumiItem("accessory_headphones", R.string.wardrobe_item_accessory_headphones, RonumiSlot.ACCESSORY, 11),
        RonumiItem("accessory_neckerchief", R.string.wardrobe_item_accessory_neckerchief, RonumiSlot.ACCESSORY, 14),
        RonumiItem("accessory_medal", R.string.wardrobe_item_accessory_medal, RonumiSlot.ACCESSORY, 17),
        RonumiItem("accessory_star", R.string.wardrobe_item_accessory_star, RonumiSlot.ACCESSORY, 19),
    )

    fun isUnlocked(item: RonumiItem, level: Int, petTapCount: Int): Boolean =
        item.level <= level && petTapCount >= item.minimumTaps

    fun visibleItems(petTapCount: Int): List<RonumiItem> = items.filter {
        it.minimumTaps == 0 || petTapCount >= it.minimumTaps
    }

    /** Catalog order breaks invalid duplicate slots in a stable way. */
    fun resolve(ids: Set<String>, level: Int, petTapCount: Int = 0): Set<String> = items
        .filter { it.id in ids && isUnlocked(it, level, petTapCount) }
        .distinctBy { it.slot }
        .mapTo(linkedSetOf()) { it.id }
}
