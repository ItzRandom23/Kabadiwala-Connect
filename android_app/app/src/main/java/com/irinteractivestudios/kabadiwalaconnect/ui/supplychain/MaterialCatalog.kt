package com.irinteractivestudios.kabadiwalaconnect.ui.supplychain

/** Human language used in the capture flows; backend keys stay unchanged. */
data class FriendlyMaterial(
    val key: String,
    val title: String,
    val examples: String,
    val hazardous: Boolean = false
)

val friendlyMaterials = listOf(
    FriendlyMaterial("PLASTIC", "Plastic bottles & containers", "Bottles, cans, tubs, packaging"),
    FriendlyMaterial("CABLE", "Wires & cables", "Charging wires, extension cords, copper wire"),
    FriendlyMaterial("COPPER", "Copper metal & pipes", "Copper wire, pipes, utensils"),
    FriendlyMaterial("PCB", "Circuit boards & computer parts", "Green boards, chips, electronic parts", hazardous = true),
    FriendlyMaterial("BATTERY", "Batteries & cells", "Phone batteries, inverter batteries, cells", hazardous = true),
    FriendlyMaterial("MOTOR", "Motors, fans & pumps", "Old motors, fans, pumps, compressors"),
    FriendlyMaterial("MAGNET", "Magnets", "Speaker or motor magnets"),
    FriendlyMaterial("CRT", "Old TV / monitor (thick glass)", "Box-style TV or computer monitor", hazardous = true),
    FriendlyMaterial("LCD_PANEL", "Flat TV / monitor screen", "LCD, LED or flat-panel display"),
    FriendlyMaterial("OTHER", "Other scrap / not sure", "Choose this if none of the above fits")
)

fun friendlyMaterial(key: String): FriendlyMaterial = friendlyMaterials.firstOrNull { it.key == key } ?: friendlyMaterials.last()
