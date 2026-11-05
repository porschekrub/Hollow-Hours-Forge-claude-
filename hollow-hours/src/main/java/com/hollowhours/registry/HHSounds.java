package com.hollowhours.registry;

import com.hollowhours.HollowHours;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.HashMap;
import java.util.Map;

/**
 * Every sound of the mod. Names follow the Bedrock add-on ("ghost.growl", "ghost.scare.giggle" ...) so the ported
 * logic reads the same; {@link #get(String)} also understands the vanilla Bedrock names the add-on used
 * ("mob.ghast.scream", "random.door_open" ...) and maps them to Java sounds.
 */
public final class HHSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, HollowHours.MODID);
    private static final Map<String, RegistryObject<SoundEvent>> BY_NAME = new HashMap<>();

    public static final String[] NAMES = {
            // music + ghost presence
            "ghost.music.ambient1", "ghost.music.ambient2", "ghost.music.spawn", "ghost.music.chase",
            "ghost.g1.spawn", "ghost.g1.chase", "ghost.g2.spawn", "ghost.g2.chase", "ghost.g3.spawn", "ghost.g3.chase",
            "ghost.g4.spawn", "ghost.g4.chase", "ghost.g5.spawn", "ghost.g5.chase",
            "ghost.snap", "ghost.creak", "ghost.growl", "ghost.jumpscare",
            // doors / windows / boards
            "ghost.knock.wood", "ghost.knock.metal", "ghost.knock.glass", "ghost.bang.wood", "ghost.bang.metal", "ghost.bang.stone",
            "ghost.glass.crack", "ghost.glass.shatter", "ghost.burst", "ghost.repair", "ghost.scratch", "ghost.breath",
            "ghost.closet.open", "ghost.closet.close", "ghost.closet.yank", "ghost.sniff", "ghost.gasp", "ghost.breath_hold",
            "ghost.hammer", "ghost.board.place", "ghost.board.pry", "ghost.board.snap",
            // flashlight
            "ghost.flashlight.on", "ghost.flashlight.off", "ghost.flashlight.click", "ghost.flashlight.reload",
            "ghost.flashlight.flicker", "ghost.flashlight.dead",
            // voices
            "ghost.voice.g1.s", "ghost.voice.g1.m", "ghost.voice.g1.l", "ghost.voice.g1.rage",
            "ghost.voice.g2.s", "ghost.voice.g2.m", "ghost.voice.g2.l", "ghost.voice.g2.rage",
            "ghost.voice.g3.s", "ghost.voice.g3.m", "ghost.voice.g3.l", "ghost.voice.g3.rage",
            "ghost.voice.g4.s", "ghost.voice.g4.m", "ghost.voice.g4.l", "ghost.voice.g4.rage",
            "ghost.voice.g5.s", "ghost.voice.g5.m", "ghost.voice.g5.l", "ghost.voice.g5.rage",
            // stamina
            "ghost.pant", "ghost.pant.heavy", "ghost.dash",
            // scare sounds (indoor events)
            "ghost.scare.giggle", "ghost.scare.musicbox", "ghost.scare.scream", "ghost.scare.glass", "ghost.scare.roof",
            "ghost.scare.whisper", "ghost.scare.wetbreath", "ghost.scare.wallknock", "ghost.scare.bell", "ghost.scare.sob",
            "ghost.scare.chains", "ghost.scare.creak",
            // stalkers
            "ghost.stalker.scare1", "ghost.stalker.scare2", "ghost.stalker.scare3", "ghost.stalker.hit",
            // horror layers
            "ghost.horror.sting", "ghost.horror.choir", "ghost.horror.whisper", "ghost.horror.drone", "ghost.horror.heart",
            // ===== new in the Java edition: hardcore jumpscares + kill cinematics
            "ghost.scream.g1", "ghost.scream.g2", "ghost.scream.g3", "ghost.scream.g4", "ghost.scream.g5",
            "ghost.scream.stalker1", "ghost.scream.stalker2", "ghost.scream.stalker3",
            "ghost.jumpscare.hit", "ghost.jumpscare.static", "ghost.jumpscare.ring", "ghost.jumpscare.boom",
            "ghost.kill.grab", "ghost.kill.punch", "ghost.kill.crunch", "ghost.kill.slam", "ghost.kill.tear", "ghost.kill.choke",
            "ghost.kill.bite", "ghost.kill.burn", "ghost.kill.drown", "ghost.kill.neck", "ghost.kill.stab", "ghost.kill.stomp",
            "ghost.kill.splat", "ghost.kill.final",
            "ghost.betray.whisper", "ghost.betray.tick", "ghost.betray.laugh", "ghost.betray.sold",
            "ghost.heart.slow", "ghost.flatline",
            // the phone
            "phone.notify", "phone.eas", "phone.click", "phone.charge", "phone.siren", "phone.game",
            "phone.voice.th.0", "phone.voice.th.1", "phone.voice.th.2", "phone.voice.th.3", "phone.voice.th.4", "phone.voice.th.5",
            "phone.voice.en.0", "phone.voice.en.1", "phone.voice.en.2", "phone.voice.en.3", "phone.voice.en.4", "phone.voice.en.5",
            // the rescue
            "rescue.rotor",
            // fighting back
            "gun.pistol", "gun.rifle", "gun.shotgun", "gun.sniper", "gun.grenade", "gun.reload", "gun.cock", "gun.empty",
            // the last chapter
            "ghost.music.boss", "ghost.music.credits", "boss.cry", "boss.slam", "boss.scream", "boss.rise", "boss.defeat", "boss.release", "boss.windup",
            "boss.sweep", "boss.grab", "boss.tear", "boss.hurt", "rift.hum", "ending.boom",
    };

    static {
        for (String n : NAMES) {
            BY_NAME.put(n, SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(HollowHours.id(n))));
        }
    }

    /** Mod sound by name, or a vanilla sound for Bedrock-style names; never null. */
    public static SoundEvent get(String name) {
        RegistryObject<SoundEvent> ro = BY_NAME.get(name);
        if (ro != null && ro.isPresent()) return ro.get();
        SoundEvent v = vanilla(name);
        return v != null ? v : SoundEvents.AMBIENT_CAVE.value();
    }

    public static boolean has(String name) {
        return BY_NAME.containsKey(name);
    }

    private static SoundEvent vanilla(String name) {
        return switch (name) {
            case "mob.ghast.scream" -> SoundEvents.GHAST_SCREAM;
            case "mob.ghast.moan" -> SoundEvents.GHAST_AMBIENT;
            case "mob.warden.heartbeat" -> SoundEvents.WARDEN_HEARTBEAT;
            case "mob.warden.nearby_close" -> SoundEvents.WARDEN_NEARBY_CLOSE;
            case "random.door_open" -> SoundEvents.WOODEN_DOOR_OPEN;
            case "random.door_close" -> SoundEvents.WOODEN_DOOR_CLOSE;
            case "mob.creeper.say" -> SoundEvents.CREEPER_PRIMED;
            case "mob.zombie.say" -> SoundEvents.ZOMBIE_AMBIENT;
            case "mob.skeleton.say" -> SoundEvents.SKELETON_AMBIENT;
            case "mob.villager.idle" -> SoundEvents.VILLAGER_AMBIENT;
            case "random.chestopen" -> SoundEvents.CHEST_OPEN;
            case "dig.wood" -> SoundEvents.WOOD_BREAK;
            case "game.player.hurt" -> SoundEvents.PLAYER_HURT;
            case "ambient.cave" -> SoundEvents.AMBIENT_CAVE.value();
            case "ambient.weather.thunder" -> SoundEvents.LIGHTNING_BOLT_THUNDER;
            case "step.stone" -> SoundEvents.STONE_STEP;
            case "step.gravel" -> SoundEvents.GRAVEL_STEP;
            case "step.grass" -> SoundEvents.GRASS_STEP;
            case "mob.zombie.step" -> SoundEvents.ZOMBIE_STEP;
            case "mob.spider.step" -> SoundEvents.SPIDER_STEP;
            case "mob.skeleton.step" -> SoundEvents.SKELETON_STEP;
            case "mob.witch.ambient" -> SoundEvents.WITCH_CELEBRATE;
            case "mob.endermen.portal" -> SoundEvents.ENDERMAN_TELEPORT;
            case "mob.blaze.shoot" -> SoundEvents.BLAZE_SHOOT;
            case "mob.blaze.breathe" -> SoundEvents.BLAZE_AMBIENT;
            case "mob.ravager.roar" -> SoundEvents.RAVAGER_ROAR;
            case "fire.fire" -> SoundEvents.FIRE_AMBIENT;
            case "bubble.up" -> SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT;
            case "liquid.water" -> SoundEvents.WATER_AMBIENT;
            case "random.fizz" -> SoundEvents.FIRE_EXTINGUISH;
            case "random.click" -> SoundEvents.UI_BUTTON_CLICK.value();
            case "random.break" -> SoundEvents.ITEM_BREAK;
            case "random.glass" -> SoundEvents.GLASS_BREAK;
            case "random.explode" -> SoundEvents.GENERIC_EXPLODE;
            case "random.anvil_land" -> SoundEvents.ANVIL_LAND;
            case "dig.gravel" -> SoundEvents.GRAVEL_BREAK;
            case "dig.cloth" -> SoundEvents.WOOL_PLACE;
            default -> null;
        };
    }

    private HHSounds() {}
}
