package me.usainsrht.guildroyale.core.message;

import net.kyori.adventure.sound.Sound;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void testSingleStringSoundParsing() {
        // Sound name only
        Message.SoundEntry entry1 = Message.SoundEntry.of("entity.player.levelup");
        assertEquals("entity.player.levelup", entry1.sound());
        assertEquals(1.0f, entry1.volume());
        assertEquals(1.0f, entry1.pitch());
        assertEquals(Sound.Source.MASTER, entry1.source());

        // Sound name + volume
        Message.SoundEntry entry2 = Message.SoundEntry.of("entity.player.levelup, 0.5");
        assertEquals("entity.player.levelup", entry2.sound());
        assertEquals(0.5f, entry2.volume());
        assertEquals(1.0f, entry2.pitch());
        assertEquals(Sound.Source.MASTER, entry2.source());

        // Sound name + volume + pitch
        Message.SoundEntry entry3 = Message.SoundEntry.of("entity.player.levelup, 1.5, 2.0");
        assertEquals("entity.player.levelup", entry3.sound());
        assertEquals(1.5f, entry3.volume());
        assertEquals(2.0f, entry3.pitch());
        assertEquals(Sound.Source.MASTER, entry3.source());

        // Sound name + volume + pitch + source
        Message.SoundEntry entry4 = Message.SoundEntry.of("entity.player.levelup,1,2,master");
        assertEquals("entity.player.levelup", entry4.sound());
        assertEquals(1.0f, entry4.volume());
        assertEquals(2.0f, entry4.pitch());
        assertEquals(Sound.Source.MASTER, entry4.source());

        // Custom source (e.g. player)
        Message.SoundEntry entry5 = Message.SoundEntry.of("entity.player.levelup, 0.8, 1.2, player");
        assertEquals("entity.player.levelup", entry5.sound());
        assertEquals(0.8f, entry5.volume());
        assertEquals(1.2f, entry5.pitch());
        assertEquals(Sound.Source.PLAYER, entry5.source());
    }

    @Test
    void testMessageParseMapWithCommaSeparatedSound() {
        Map<String, Object> map = Map.of(
                "chat", List.of("Hello world!"),
                "sound", "entity.player.levelup,1,2,master"
        );
        Message message = Message.parse(map);
        assertNotNull(message.sounds());
        assertEquals(1, message.sounds().size());

        Message.SoundEntry sound = message.sounds().get(0);
        assertEquals("entity.player.levelup", sound.sound());
        assertEquals(1.0f, sound.volume());
        assertEquals(2.0f, sound.pitch());
        assertEquals(Sound.Source.MASTER, sound.source());
    }

    @Test
    void testMessageParseMapWithSoundList() {
        Map<String, Object> map = Map.of(
                "sound", List.of(
                        "entity.player.levelup,1,2,master",
                        "entity.experience_orb.pickup,0.5,1.5,player"
                )
        );
        Message message = Message.parse(map);
        assertNotNull(message.sounds());
        assertEquals(2, message.sounds().size());

        Message.SoundEntry s1 = message.sounds().get(0);
        assertEquals("entity.player.levelup", s1.sound());
        assertEquals(1.0f, s1.volume());
        assertEquals(2.0f, s1.pitch());
        assertEquals(Sound.Source.MASTER, s1.source());

        Message.SoundEntry s2 = message.sounds().get(1);
        assertEquals("entity.experience_orb.pickup", s2.sound());
        assertEquals(0.5f, s2.volume());
        assertEquals(1.5f, s2.pitch());
        assertEquals(Sound.Source.PLAYER, s2.source());
    }

    @Test
    void testSoundKeyWithUnderscore() {
        java.util.List<Sound> playedSounds = new java.util.ArrayList<>();
        net.kyori.adventure.audience.Audience audience = new net.kyori.adventure.audience.Audience() {
            @Override
            public void playSound(Sound sound) {
                playedSounds.add(sound);
            }
        };

        Message message = Message.parse(Map.of("sound", "entity.ender_dragon.death"));
        message.send(audience, null);

        assertEquals(1, playedSounds.size());
        assertEquals("minecraft", playedSounds.get(0).name().namespace());
        assertEquals("entity.ender_dragon.death", playedSounds.get(0).name().value());
    }
}
