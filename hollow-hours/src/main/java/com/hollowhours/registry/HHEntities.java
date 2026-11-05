package com.hollowhours.registry;

import com.hollowhours.HollowHours;
import com.hollowhours.entity.BoardsEntity;
import com.hollowhours.entity.ClosetEntity;
import com.hollowhours.entity.GhostEntity;
import com.hollowhours.entity.GhostKind;
import com.hollowhours.entity.HideSeatEntity;
import com.hollowhours.entity.StalkerEntity;
import com.hollowhours.entity.StalkerKind;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import java.util.function.Supplier;

import java.util.ArrayList;
import java.util.List;

public final class HHEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, HollowHours.MODID);

    public static final List<Supplier<EntityType<GhostEntity>>> GHOSTS = new ArrayList<>();
    public static final List<Supplier<EntityType<StalkerEntity>>> STALKERS = new ArrayList<>();

    static {
        for (GhostKind k : GhostKind.values()) {
            GHOSTS.add(ENTITIES.register(k.id(), () -> EntityType.Builder.<GhostEntity>of(GhostEntity::new, MobCategory.MONSTER)
                    .sized(k.width, k.height).fireImmune().clientTrackingRange(16).updateInterval(2)
                    .build(HollowHours.MODID + ":" + k.id())));
        }
        for (StalkerKind k : StalkerKind.values()) {
            STALKERS.add(ENTITIES.register(k.id(), () -> EntityType.Builder.<StalkerEntity>of(StalkerEntity::new, MobCategory.MONSTER)
                    .sized(k.width, k.height).fireImmune().clientTrackingRange(16).updateInterval(1)
                    .build(HollowHours.MODID + ":" + k.id())));
        }
    }

    public static final Supplier<EntityType<ClosetEntity>> CLOSET = ENTITIES.register("closet",
            () -> EntityType.Builder.<ClosetEntity>of(ClosetEntity::new, MobCategory.MISC).sized(1.0f, 2.2f).clientTrackingRange(10)
                    .build(HollowHours.MODID + ":closet"));
    public static final Supplier<EntityType<HideSeatEntity>> HIDE_SEAT = ENTITIES.register("hide_seat",
            () -> EntityType.Builder.<HideSeatEntity>of(HideSeatEntity::new, MobCategory.MISC).sized(0.4f, 0.2f).clientTrackingRange(10)
                    .noSummon().build(HollowHours.MODID + ":hide_seat"));
    public static final Supplier<EntityType<com.hollowhours.physics.FallingTreeEntity>> FALLING_TREE = ENTITIES.register("falling_tree",
            () -> EntityType.Builder.<com.hollowhours.physics.FallingTreeEntity>of(com.hollowhours.physics.FallingTreeEntity::new, MobCategory.MISC)
                    .sized(1.0f, 1.0f).clientTrackingRange(12).updateInterval(20).noSummon().build(HollowHours.MODID + ":falling_tree"));
    public static final Supplier<EntityType<com.hollowhours.fun.SwingSeatEntity>> SWING_SEAT = ENTITIES.register("swing_seat",
            () -> EntityType.Builder.<com.hollowhours.fun.SwingSeatEntity>of(com.hollowhours.fun.SwingSeatEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.3f).clientTrackingRange(10).updateInterval(20).noSummon().build(HollowHours.MODID + ":swing_seat"));
    public static final Supplier<EntityType<com.hollowhours.fun.BallEntity>> BALL = ENTITIES.register("ball",
            () -> EntityType.Builder.<com.hollowhours.fun.BallEntity>of(com.hollowhours.fun.BallEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(10).updateInterval(1).build(HollowHours.MODID + ":ball"));
    public static final Supplier<EntityType<com.hollowhours.grave.GraveEntity>> GRAVE = ENTITIES.register("grave",
            () -> EntityType.Builder.<com.hollowhours.grave.GraveEntity>of(com.hollowhours.grave.GraveEntity::new, MobCategory.MISC)
                    .sized(0.8f, 1.0f).fireImmune().clientTrackingRange(10).updateInterval(20).noSummon().build(HollowHours.MODID + ":grave"));
    public static final Supplier<EntityType<com.hollowhours.combat.Grenade.Thrown>> GRENADE = ENTITIES.register("grenade",
            () -> EntityType.Builder.<com.hollowhours.combat.Grenade.Thrown>of(com.hollowhours.combat.Grenade.Thrown::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).clientTrackingRange(8).updateInterval(2).build(HollowHours.MODID + ":grenade"));
    public static final Supplier<EntityType<com.hollowhours.endgame.WeepingEntity>> WEEPING = ENTITIES.register("weeping",
            () -> EntityType.Builder.<com.hollowhours.endgame.WeepingEntity>of(com.hollowhours.endgame.WeepingEntity::new, MobCategory.MONSTER)
                    .sized(3.6f, 6.4f).fireImmune().clientTrackingRange(16).updateInterval(1).build(HollowHours.MODID + ":weeping"));
    public static final Supplier<EntityType<com.hollowhours.endgame.RiftEntity>> RIFT = ENTITIES.register("rift",
            () -> EntityType.Builder.<com.hollowhours.endgame.RiftEntity>of(com.hollowhours.endgame.RiftEntity::new, MobCategory.MISC)
                    .sized(1.6f, 2.6f).fireImmune().clientTrackingRange(12).updateInterval(20).noSummon().build(HollowHours.MODID + ":rift"));
    public static final Supplier<EntityType<BoardsEntity>> BOARDS = ENTITIES.register("boards",
            () -> EntityType.Builder.<BoardsEntity>of(BoardsEntity::new, MobCategory.MISC).sized(0.3f, 0.3f).clientTrackingRange(10)
                    .updateInterval(20).noSummon().build(HollowHours.MODID + ":boards"));

    public static final Supplier<EntityType<com.hollowhours.rescue.HelicopterEntity>> HELICOPTER = ENTITIES.register("helicopter",
            () -> EntityType.Builder.<com.hollowhours.rescue.HelicopterEntity>of(com.hollowhours.rescue.HelicopterEntity::new, MobCategory.MISC)
                    .sized(5.0f, 4.5f).fireImmune().clientTrackingRange(16).updateInterval(1).noSummon().build(HollowHours.MODID + ":helicopter"));

    public static GhostKind kindOf(EntityType<?> type) {
        for (int i = 0; i < GHOSTS.size(); i++) if (GHOSTS.get(i).get() == type) return GhostKind.values()[i];
        return GhostKind.WAIL;
    }

    public static StalkerKind stalkerKindOf(EntityType<?> type) {
        for (int i = 0; i < STALKERS.size(); i++) if (STALKERS.get(i).get() == type) return StalkerKind.values()[i];
        return StalkerKind.LANKY;
    }

    public static EntityType<GhostEntity> ghost(GhostKind k) {
        return GHOSTS.get(k.ordinal()).get();
    }

    public static EntityType<StalkerEntity> stalker(StalkerKind k) {
        return STALKERS.get(k.ordinal()).get();
    }

    private HHEntities() {}
}
