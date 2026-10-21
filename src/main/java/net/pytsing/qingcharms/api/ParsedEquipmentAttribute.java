package net.pytsing.qingcharms.api;

/**
 * @deprecated QingCharms does not parse equipment Lore or expose calculated
 * attributes. Use {@link EquipmentSnapshot#getItems()} and let ILS read the
 * complete item Lore.
 */
@Deprecated
public final class ParsedEquipmentAttribute {

    private ParsedEquipmentAttribute() {
    }
}