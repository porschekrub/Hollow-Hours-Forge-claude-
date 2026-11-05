package net.minecraftforge.fml.common;

import net.minecraftforge.api.distmarker.Dist;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Fabric shim: the annotations stay for the shared code; ports/pp.py lists the subscriber classes. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Mod {
    String value();

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface EventBusSubscriber {
        String modid() default "";

        Bus bus() default Bus.FORGE;

        Dist[] value() default {Dist.CLIENT, Dist.DEDICATED_SERVER};

        enum Bus { FORGE, MOD }
    }
}
