package org.syalosovetskyi.onemoney.ui.accounts

import org.syalosovetskyi.onemoney.util.parseColorHex
import androidx.core.graphics.toColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.syalosovetskyi.onemoney.ui.theme.OneMoneyTheme
import org.syalosovetskyi.onemoney.ui.theme.Spacing
import org.syalosovetskyi.onemoney.ui.theme.FallbackAccountColor
import org.syalosovetskyi.onemoney.R

// ── Icon catalogue ────────────────────────────────────────────────────────────

data class AccountIconInfo(val key: String, val icon: ImageVector)

val ACCOUNT_ICONS: List<AccountIconInfo> = listOf(
    // Category aliases — kept so saved category keys still resolve
    AccountIconInfo("category",     Icons.Outlined.Category),
    AccountIconInfo("shopping",     Icons.Outlined.ShoppingCart),
    AccountIconInfo("car",          Icons.Outlined.DirectionsCar),
    AccountIconInfo("health",       Icons.Outlined.LocalHospital),
    AccountIconInfo("music",        Icons.Outlined.MusicNote),
    AccountIconInfo("coffee",       Icons.Outlined.LocalCafe),
    AccountIconInfo("gift",         Icons.Outlined.CardGiftcard),
    AccountIconInfo("sports",       Icons.Outlined.FitnessCenter),
    AccountIconInfo("phone",        Icons.Outlined.PhoneAndroid),
    AccountIconInfo("cloud",        Icons.Outlined.Cloud),
    AccountIconInfo("ai",           Icons.Outlined.SmartToy),
    AccountIconInfo("aliexpress",   Icons.Outlined.Language),
    // Finance
    AccountIconInfo("account_balance_wallet", Icons.Outlined.AccountBalanceWallet),
    AccountIconInfo("credit_card",            Icons.Outlined.CreditCard),
    AccountIconInfo("savings",                Icons.Outlined.Savings),
    AccountIconInfo("account_balance",        Icons.Outlined.AccountBalance),
    AccountIconInfo("payments",               Icons.Outlined.Payments),
    AccountIconInfo("attach_money",           Icons.Outlined.AttachMoney),
    AccountIconInfo("money_off",              Icons.Outlined.MoneyOff),
    AccountIconInfo("currency_exchange",      Icons.Outlined.CurrencyExchange),
    AccountIconInfo("bar_chart",              Icons.Outlined.BarChart),
    AccountIconInfo("pie_chart",              Icons.Outlined.PieChart),
    AccountIconInfo("local_atm",              Icons.Outlined.LocalAtm),
    AccountIconInfo("paid",                   Icons.Outlined.Paid),
    AccountIconInfo("sell",                   Icons.Outlined.Sell),
    AccountIconInfo("redeem",                 Icons.Outlined.Redeem),
    AccountIconInfo("card_giftcard",          Icons.Outlined.CardGiftcard),
    AccountIconInfo("credit_score",           Icons.Outlined.CreditScore),
    AccountIconInfo("money",                  Icons.Outlined.Money),
    AccountIconInfo("analytics",              Icons.Outlined.Analytics),
    AccountIconInfo("monetization_on",        Icons.Outlined.MonetizationOn),
    AccountIconInfo("autorenew",              Icons.Outlined.Autorenew),
    AccountIconInfo("receipt",                Icons.Outlined.Receipt),
    AccountIconInfo("shopping_cart",          Icons.Outlined.ShoppingCart),
    AccountIconInfo("store",                  Icons.Outlined.Store),
    AccountIconInfo("local_mall",             Icons.Outlined.LocalMall),
    AccountIconInfo("wallet",                 Icons.Outlined.Wallet),
    // Transport
    AccountIconInfo("directions_car",         Icons.Outlined.DirectionsCar),
    AccountIconInfo("train",                  Icons.Outlined.Train),
    AccountIconInfo("flight",                 Icons.Outlined.Flight),
    AccountIconInfo("directions_bus",         Icons.Outlined.DirectionsBus),
    AccountIconInfo("two_wheeler",            Icons.Outlined.TwoWheeler),
    AccountIconInfo("local_gas_station",      Icons.Outlined.LocalGasStation),
    AccountIconInfo("local_parking",          Icons.Outlined.LocalParking),
    AccountIconInfo("directions_bike",        Icons.AutoMirrored.Outlined.DirectionsBike),
    AccountIconInfo("commute",                Icons.Outlined.Commute),
    AccountIconInfo("electric_car",           Icons.Outlined.ElectricCar),
    // Food & Drink
    AccountIconInfo("restaurant",             Icons.Outlined.Restaurant),
    AccountIconInfo("local_cafe",             Icons.Outlined.LocalCafe),
    AccountIconInfo("local_bar",              Icons.Outlined.LocalBar),
    AccountIconInfo("fastfood",               Icons.Outlined.Fastfood),
    AccountIconInfo("cake",                   Icons.Outlined.Cake),
    AccountIconInfo("lunch_dining",           Icons.Outlined.LunchDining),
    AccountIconInfo("bakery_dining",          Icons.Outlined.BakeryDining),
    AccountIconInfo("local_pizza",            Icons.Outlined.LocalPizza),
    AccountIconInfo("set_meal",               Icons.Outlined.SetMeal),
    AccountIconInfo("ramen_dining",           Icons.Outlined.RamenDining),
    // Shopping
    AccountIconInfo("shopping_bag",           Icons.Outlined.ShoppingBag),
    AccountIconInfo("shopping_basket",        Icons.Outlined.ShoppingBasket),
    AccountIconInfo("local_grocery_store",    Icons.Outlined.LocalGroceryStore),
    AccountIconInfo("dry_cleaning",           Icons.Outlined.DryCleaning),
    AccountIconInfo("checkroom",              Icons.Outlined.Checkroom),
    AccountIconInfo("storefront",             Icons.Outlined.Storefront),
    AccountIconInfo("local_offer",            Icons.Outlined.LocalOffer),
    AccountIconInfo("discount",               Icons.Outlined.Discount),
    AccountIconInfo("style",                  Icons.Outlined.Style),
    AccountIconInfo("diamond",                Icons.Outlined.Diamond),
    // Home
    AccountIconInfo("home",                   Icons.Outlined.Home),
    AccountIconInfo("apartment",              Icons.Outlined.Apartment),
    AccountIconInfo("cottage",                Icons.Outlined.Cottage),
    AccountIconInfo("weekend",                Icons.Outlined.Weekend),
    AccountIconInfo("build",                  Icons.Outlined.Build),
    AccountIconInfo("plumbing",               Icons.Outlined.Plumbing),
    AccountIconInfo("electrical_services",    Icons.Outlined.ElectricalServices),
    AccountIconInfo("cleaning_services",      Icons.Outlined.CleaningServices),
    AccountIconInfo("chair",                  Icons.Outlined.Chair),
    AccountIconInfo("bathtub",                Icons.Outlined.Bathtub),
    // Health & Fitness
    AccountIconInfo("local_hospital",         Icons.Outlined.LocalHospital),
    AccountIconInfo("medical_services",       Icons.Outlined.MedicalServices),
    AccountIconInfo("healing",                Icons.Outlined.Healing),
    AccountIconInfo("favorite_border",        Icons.Outlined.FavoriteBorder),
    AccountIconInfo("fitness_center",         Icons.Outlined.FitnessCenter),
    AccountIconInfo("spa",                    Icons.Outlined.Spa),
    AccountIconInfo("local_pharmacy",         Icons.Outlined.LocalPharmacy),
    AccountIconInfo("psychology",             Icons.Outlined.Psychology),
    AccountIconInfo("self_improvement",       Icons.Outlined.SelfImprovement),
    AccountIconInfo("sports",                 Icons.Outlined.Sports),
    // Entertainment
    AccountIconInfo("sports_esports",         Icons.Outlined.SportsEsports),
    AccountIconInfo("movie",                  Icons.Outlined.Movie),
    AccountIconInfo("music_note",             Icons.Outlined.MusicNote),
    AccountIconInfo("theater_comedy",         Icons.Outlined.TheaterComedy),
    AccountIconInfo("celebration",            Icons.Outlined.Celebration),
    AccountIconInfo("casino",                 Icons.Outlined.Casino),
    AccountIconInfo("videogame_asset",        Icons.Outlined.VideogameAsset),
    AccountIconInfo("live_tv",                Icons.Outlined.LiveTv),
    AccountIconInfo("headphones",             Icons.Outlined.Headphones),
    AccountIconInfo("sports_soccer",          Icons.Outlined.SportsSoccer),
    // Education
    AccountIconInfo("school",                 Icons.Outlined.School),
    AccountIconInfo("book",                   Icons.Outlined.Book),
    AccountIconInfo("science",                Icons.Outlined.Science),
    AccountIconInfo("laptop",                 Icons.Outlined.Laptop),
    AccountIconInfo("computer",               Icons.Outlined.Computer),
    AccountIconInfo("menu_book",              Icons.AutoMirrored.Outlined.MenuBook),
    AccountIconInfo("create",                 Icons.Outlined.Create),
    AccountIconInfo("history_edu",            Icons.Outlined.HistoryEdu),
    AccountIconInfo("calculate",              Icons.Outlined.Calculate),
    // Travel
    AccountIconInfo("beach_access",           Icons.Outlined.BeachAccess),
    AccountIconInfo("luggage",                Icons.Outlined.Luggage),
    AccountIconInfo("hotel",                  Icons.Outlined.Hotel),
    AccountIconInfo("location_on",            Icons.Outlined.LocationOn),
    AccountIconInfo("map",                    Icons.Outlined.Map),
    AccountIconInfo("explore",                Icons.Outlined.Explore),
    AccountIconInfo("landscape",              Icons.Outlined.Landscape),
    AccountIconInfo("anchor",                 Icons.Outlined.Anchor),
    AccountIconInfo("directions_boat",        Icons.Outlined.DirectionsBoat),
    AccountIconInfo("flight_takeoff",         Icons.Outlined.FlightTakeoff),
    // People & Work
    AccountIconInfo("person",                 Icons.Outlined.Person),
    AccountIconInfo("people",                 Icons.Outlined.People),
    AccountIconInfo("family_restroom",        Icons.Outlined.FamilyRestroom),
    AccountIconInfo("child_care",             Icons.Outlined.ChildCare),
    AccountIconInfo("work",                   Icons.Outlined.Work),
    AccountIconInfo("business_center",        Icons.Outlined.BusinessCenter),
    AccountIconInfo("business",               Icons.Outlined.Business),
    AccountIconInfo("handshake",              Icons.Outlined.Handshake),
    AccountIconInfo("engineering",            Icons.Outlined.Engineering),
    // Nature & Animals
    AccountIconInfo("park",                   Icons.Outlined.Park),
    AccountIconInfo("eco",                    Icons.Outlined.Eco),
    AccountIconInfo("water_drop",             Icons.Outlined.WaterDrop),
    AccountIconInfo("wb_sunny",               Icons.Outlined.WbSunny),
    AccountIconInfo("pets",                   Icons.Outlined.Pets),
    AccountIconInfo("forest",                 Icons.Outlined.Forest),
    AccountIconInfo("yard",                   Icons.Outlined.Yard),
    AccountIconInfo("grain",                  Icons.Outlined.Grain),
    // Misc
    AccountIconInfo("star_border",            Icons.Outlined.StarBorder),
    AccountIconInfo("emoji_events",           Icons.Outlined.EmojiEvents),
    AccountIconInfo("volunteer_activism",     Icons.Outlined.VolunteerActivism),
    AccountIconInfo("local_florist",          Icons.Outlined.LocalFlorist),
    AccountIconInfo("card_membership",        Icons.Outlined.CardMembership),
    AccountIconInfo("subscriptions",          Icons.Outlined.Subscriptions),
    AccountIconInfo("bookmark_border",        Icons.Outlined.BookmarkBorder),
    AccountIconInfo("info",                   Icons.Outlined.Info),
)

fun accountIconFromKey(key: String): ImageVector =
    ACCOUNT_ICONS.find { it.key == key }?.icon ?: Icons.Outlined.AccountBalanceWallet

// ── Color palette ─────────────────────────────────────────────────────────────

val ACCOUNT_COLORS_PALETTE: List<String> = listOf(
    // Pinks
    "#F48FB1", "#F06292", "#EC407A", "#E91E63", "#C2185B",
    "#FF80AB", "#FF4081", "#F50057", "#C51162", "#AD1457",
    // Reds
    "#EF9A9A", "#E57373", "#EF5350", "#F44336", "#E53935",
    "#D32F2F", "#C62828", "#B71C1C", "#FF1744", "#FF5252",
    // Purples
    "#CE93D8", "#BA68C8", "#AB47BC", "#9C27B0", "#7B1FA2",
    "#6A1B9A", "#4A148C", "#EA80FC", "#E040FB", "#D500F9",
    "#E1BEE7", "#D1C4E9", "#B39DDB", "#9575CD", "#7E57C2",
    "#673AB7", "#5E35B1", "#512DA8", "#4527A0", "#311B92",
    // Indigos
    "#C5CAE9", "#9FA8DA", "#7986CB", "#5C6BC0", "#3F51B5",
    "#3949AB", "#303F9F", "#283593", "#1A237E", "#8C9EFF",
    // Blues
    "#BBDEFB", "#90CAF9", "#64B5F6", "#42A5F5", "#2196F3",
    "#1E88E5", "#1976D2", "#1565C0", "#0D47A1", "#82B1FF",
    "#448AFF", "#2979FF", "#2962FF", "#80D8FF", "#40C4FF",
    "#00B0FF", "#0091EA", "#0288D1", "#0277BD", "#01579B",
    // Teals / Cyans
    "#80DEEA", "#4DD0E1", "#26C6DA", "#00BCD4", "#00ACC1",
    "#00838F", "#006064", "#84FFFF", "#18FFFF", "#00E5FF",
    "#00B8D4", "#80CBC4", "#4DB6AC", "#26A69A", "#009688",
    "#00897B", "#00796B", "#00695C", "#004D40", "#1DE9B6",
    // Greens
    "#A5D6A7", "#81C784", "#66BB6A", "#4CAF50", "#43A047",
    "#388E3C", "#2E7D32", "#1B5E20", "#69F0AE", "#00E676",
    "#00C853", "#CCFF90", "#B2FF59", "#76FF03", "#64DD17",
    "#8BC34A", "#7CB342", "#558B2F", "#33691E", "#C6E050",
    // Limes / Yellow-Greens
    "#F0F4C3", "#E6EE9C", "#DCE775", "#D4E157", "#CDDC39",
    "#C0CA33", "#AFB42B", "#9E9D24", "#F4FF81", "#EEFF41",
    // Yellows
    "#FFF9C4", "#FFF176", "#FFEE58", "#FFEB3B", "#FDD835",
    "#F9A825", "#F57F17", "#FFFF00", "#FFD600", "#FFAB00",
    // Ambers
    "#FFE082", "#FFD54F", "#FFCA28", "#FFC107", "#FFB300",
    "#FFA000", "#FF8F00", "#FF6F00", "#FFE57F", "#FFD740",
    // Oranges
    "#FFCC80", "#FFA726", "#FF9800", "#FB8C00", "#F57C00",
    "#EF6C00", "#E65100", "#FF6D00", "#FF9100", "#FF3D00",
    // Deep Oranges
    "#FF8A65", "#FF7043", "#F4511E", "#E64A19", "#D84315",
    "#BF360C", "#FF6E40", "#FF3D00", "#DD2C00", "#BF360C",
    // Browns
    "#EFEBE9", "#D7CCC8", "#BCAAA4", "#A1887F", "#8D6E63",
    "#795548", "#6D4C41", "#5D4037", "#4E342E", "#3E2723",
    // Grays
    "#FAFAFA", "#F5F5F5", "#EEEEEE", "#E0E0E0", "#BDBDBD",
    "#9E9E9E", "#757575", "#616161", "#424242", "#212121",
    // Blue-Grays
    "#ECEFF1", "#CFD8DC", "#B0BEC5", "#90A4AE", "#78909C",
    "#607D8B", "#546E7A", "#455A64", "#37474F", "#263238",
)

// ── Full-screen picker ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconColorPickerScreen(
    initialIconKey:  String,
    initialColorHex: String,
    title:           String = stringResource(R.string.acc_icon_title),
    onResult:        (iconKey: String, colorHex: String) -> Unit,
    onDismiss:       () -> Unit
) {
    var selectedIcon  by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialIconKey) }
    var selectedColor by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialColorHex) }
    var tab           by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }

    val previewColor = remember(selectedColor) {
        parseColorHex(selectedColor, FallbackAccountColor)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {

                // ── Top bar ──────────────────────────────────────────────────
                Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, stringResource(R.string.acc_close))
                        }
                        Text(
                            title,
                            modifier   = Modifier.weight(1f).padding(horizontal = 8.dp),
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Button(
                            onClick        = { onResult(selectedIcon, selectedColor) },
                            shape          = RoundedCornerShape(50),
                            contentPadding = PaddingValues(horizontal = Spacing.xl, vertical = 10.dp)
                        ) {
                            Text(stringResource(R.string.common_done), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // ── Preview ──────────────────────────────────────────────────
                Box(
                    modifier         = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xl, bottom = Spacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier         = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(previewColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            accountIconFromKey(selectedIcon), null,
                            tint     = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // ── Tabs ─────────────────────────────────────────────────────
                TabRow(
                    selectedTabIndex = tab,
                    containerColor   = MaterialTheme.colorScheme.surface,
                    contentColor     = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = tab == 0,
                        onClick  = { tab = 0 },
                        icon     = { Icon(Icons.Outlined.StarBorder, null, modifier = Modifier.size(20.dp)) },
                        text     = {
                            Text(
                                stringResource(R.string.cat_icon),
                                fontWeight = if (tab == 0) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = tab == 1,
                        onClick  = { tab = 1 },
                        icon     = { Icon(Icons.Outlined.Palette, null, modifier = Modifier.size(20.dp)) },
                        text     = {
                            Text(
                                stringResource(R.string.cat_color),
                                fontWeight = if (tab == 1) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    )
                }

                // ── Content ──────────────────────────────────────────────────
                when (tab) {
                    0 -> IconGrid(
                        selectedKey = selectedIcon,
                        onSelect    = { selectedIcon = it }
                    )
                    1 -> ColorGrid(
                        selectedHex = selectedColor,
                        onSelect    = { selectedColor = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun IconGrid(selectedKey: String, onSelect: (String) -> Unit) {
    LazyVerticalGrid(
        columns             = GridCells.Fixed(5),
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(Spacing.md),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
    ) {
        items(ACCOUNT_ICONS, key = { it.key }) { info ->
            val isSelected = info.key == selectedKey
            Box(
                modifier         = Modifier
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable { onSelect(info.key) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    info.icon, null,
                    tint     = if (isSelected) Color.White
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(OneMoneyTheme.dimens.standardIconSize)
                )
            }
        }
    }
}

@Composable
private fun ColorGrid(selectedHex: String, onSelect: (String) -> Unit) {
    LazyVerticalGrid(
        columns             = GridCells.Fixed(5),
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(Spacing.md),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
    ) {
        items(ACCOUNT_COLORS_PALETTE) { hex ->
            val color      = parseColorHex(hex, Color.Gray)
            val isSelected = hex.equals(selectedHex, ignoreCase = true)
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isSelected) Modifier.border(OneMoneyTheme.dimens.selectionRingWidth, Color.White, CircleShape)
                        else Modifier
                    )
                    .clickable { onSelect(hex) }
            )
        }
    }
}
