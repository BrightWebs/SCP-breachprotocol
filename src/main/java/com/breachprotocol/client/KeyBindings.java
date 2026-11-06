package com.breachprotocol.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class KeyBindings {
    public static final String CATEGORY = "key.categories.breachprotocol";
    public static final KeyMapping CLOSE_EYES = new KeyMapping("key.breachprotocol.close_eyes",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);

    private KeyBindings() {}
}
