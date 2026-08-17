package me.usainsrht.guildroyale.core.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class LevelAdvantagesTest {

    @Test
    public void testDynamicLevelAdvantagesCalculation() {
        String yaml = """
            xp:
              base: 1000
              multiplier: 1.5
              level-cap: 10
              perks:
                2:
                  - "Special Level 2 Reward"
            features:
              members:
                base: 10
                per-level: 2
                max: 50
              roles:
                base: 4
                per-level: 1
                max: 15
              icon:
                unlock-level: 1
              shortname:
                unlock-level: 2
              badge:
                unlock-level: 5
              storage:
                unlock-level: 1
                slots-per-level: 9
                max-slots: 135
              bank:
                unlock-level: 2
            """;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new StringReader(yaml));

        // Test member limits
        int baseMembers = cfg.getInt("features.members.base", 10);
        int perLevelMembers = cfg.getInt("features.members.per-level", 2);
        int maxMembers = cfg.getInt("features.members.max", 50);

        assertEquals(10, baseMembers + (1 - 1) * perLevelMembers);
        assertEquals(12, baseMembers + (2 - 1) * perLevelMembers);
        assertEquals(18, baseMembers + (5 - 1) * perLevelMembers);

        // Test role limits
        int baseRoles = cfg.getInt("features.roles.base", 4);
        int perLevelRoles = cfg.getInt("features.roles.per-level", 1);
        assertEquals(4, baseRoles + (1 - 1) * perLevelRoles);
        assertEquals(5, baseRoles + (2 - 1) * perLevelRoles);

        // Test custom perks
        List<String> perksLv2 = cfg.getStringList("xp.perks.2");
        assertEquals(1, perksLv2.size());
        assertEquals("Special Level 2 Reward", perksLv2.get(0));
    }
}
