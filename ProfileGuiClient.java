package pl.profilegui;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class ProfileGuiClient implements ClientModInitializer {
    public static final String MOD_ID = "profilegui";
    public static final long GLOW_MS = 20_000L;

    private static KeyBinding openKey;
    /** UUID gracza -> czas (ms) zakonczenia podswietlenia. Tylko watek klienta. */
    private static final Map<UUID, Long> GLOW = new HashMap<>();

    @Override
    public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.profilegui.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                "category.profilegui"));

        ClientTickEvents.END_CLIENT_TICK.register(ProfileGuiClient::onTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> GLOW.clear());
    }

    public static void startGlow(UUID uuid, long durationMs) {
        if (uuid != null) {
            GLOW.put(uuid, System.currentTimeMillis() + durationMs);
        }
    }

    /** Pozostale ms podswietlenia (0 jesli brak). */
    public static long glowRemaining(UUID uuid) {
        if (uuid == null) return 0;
        Long end = GLOW.get(uuid);
        if (end == null) return 0;
        return Math.max(0, end - System.currentTimeMillis());
    }

    private static void onTick(MinecraftClient client) {
        try {
            while (openKey.wasPressed()) {
                if (client.player != null && client.currentScreen == null) {
                    client.setScreen(new ProfileScreen());
                }
            }
            tickGlow(client);
        } catch (Exception ignored) {
            // nigdy nie crashujemy gry
        }
    }

    private static void tickGlow(MinecraftClient client) {
        if (GLOW.isEmpty()) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = GLOW.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> e = it.next();
            PlayerEntity p = client.world == null ? null : client.world.getPlayerByUuid(e.getKey());
            if (client.world == null || now >= e.getValue()) {
                if (p != null) p.setGlowing(false);
                it.remove();
            } else if (p != null) {
                p.setGlowing(true);
            }
        }
    }
}
