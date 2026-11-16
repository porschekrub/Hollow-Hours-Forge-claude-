package com.helperbuddy.ai;

import com.helperbuddy.config.HBConfig;

/** Which AI the helpers think with (the config of the world). */
public final class AiUse {
    private AiUse() {}

    public static boolean isOllama() {
        return HBConfig.AI_ENABLED.get() && HBConfig.AI_PROVIDER.get().trim().equalsIgnoreCase("ollama");
    }

    /** From now on the helpers think with this Ollama model. */
    public static void ollama(String model) {
        HBConfig.AI_PROVIDER.set("ollama");
        HBConfig.AI_MODEL.set(model);
        HBConfig.AI_URL.set("");
        HBConfig.AI_ENABLED.set(true);
        HBConfig.SPEC.save();
        com.helperbuddy.talk.Talk.aiChanged();
    }
}
