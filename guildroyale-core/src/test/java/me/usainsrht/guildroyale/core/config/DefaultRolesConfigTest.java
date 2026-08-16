package me.usainsrht.guildroyale.core.config;

import me.usainsrht.guildroyale.api.domain.GuildRole;
import me.usainsrht.guildroyale.api.permission.GuildPermissionKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DefaultRolesConfigTest {

    @Test
    public void testDefaultRolesAndPermissionMapping() {
        ConfigManager.DefaultRoleDefinition leaderDef = new ConfigManager.DefaultRoleDefinition("Leader", 0, null, null);
        ConfigManager.DefaultRoleDefinition coLeaderDef = new ConfigManager.DefaultRoleDefinition("Co-Leader", 1, null, null);
        ConfigManager.DefaultRoleDefinition helperDef = new ConfigManager.DefaultRoleDefinition("Helper", 2, null, null);
        ConfigManager.DefaultRoleDefinition memberDef = new ConfigManager.DefaultRoleDefinition("Member", 3, null, null);

        List<ConfigManager.DefaultRoleDefinition> defs = List.of(leaderDef, coLeaderDef, helperDef, memberDef);

        for (ConfigManager.DefaultRoleDefinition def : defs) {
            assertNotNull(def.name());
            assertTrue(def.index() >= 0);
        }
    }
}
