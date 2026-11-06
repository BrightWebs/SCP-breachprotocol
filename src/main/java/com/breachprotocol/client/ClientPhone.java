package com.breachprotocol.client;

import net.minecraft.client.Minecraft;

/** Client-only entry point, kept separate so server code never loads screen classes. */
public final class ClientPhone {
    private ClientPhone() {}

    public static void open() {
        Minecraft.getInstance().setScreen(new PhoneScreen());
    }
}
