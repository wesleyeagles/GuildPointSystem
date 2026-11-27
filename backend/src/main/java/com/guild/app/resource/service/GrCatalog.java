package com.guild.app.resource.service;
import java.util.Map;
public final class GrCatalog {
    private GrCatalog() {}
    public static final int MAP = 55;
    // Wire categories 5/6/7 are GR4/5/6; category 4 remains Tower Rift Metal.
    public record Mob(String name, int category) {}
    public static final Map<Integer, Mob> MOBS = Map.ofEntries(
        Map.entry(66581, new Mob("[gr4](typeA) Mech. Warrior Exec", 5)),
        Map.entry(46116, new Mob("[gr4](typeB) Mech. Devastator", 5)),
        Map.entry(42155, new Mob("[gr4](typeC) Mech. Tower", 5)),
        Map.entry(25764, new Mob("[gr5](typeA) Mech. Chainsaw", 6)),
        Map.entry(81700, new Mob("[gr5](typeB) Mech. Tank", 6)),
        Map.entry(20388, new Mob("[gr5](typeC) Mech. Sword Defend", 6)),
        Map.entry(71449, new Mob("[gr6](typeA) Mech. Black Sign", 7)),
        Map.entry(84772, new Mob("[gr6](typeB) Mech. Bazooka", 7)),
        Map.entry(18955, new Mob("[gr6](typeC) Mech. Chip", 7)),
        Map.entry(627202, new Mob("[gr7] Mech. Commander", 8)));
    public static boolean valid(String name) { return MOBS.values().stream().anyMatch(m -> m.name().equals(name)); }
}
