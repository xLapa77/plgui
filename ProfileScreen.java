package pl.profilegui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class ProfileScreen extends Screen {
    private static final int PW = 330, PH = 214;
    private static final long SEARCH_DELAY_MS = 1500L;
    private static final int MAX_LEN = 16;
    private static final int SNOW_COUNT = 30;

    private enum State { IDLE, SEARCHING, FOUND, NOT_FOUND }

    private static final class Flake {
        float x, y, vy, drift, phase;
        int size, alpha;
    }

    private final List<Flake> flakes = new ArrayList<>();
    private final Random rnd = new Random();
    private final StringBuilder input = new StringBuilder();

    private boolean inputFocused = true;
    private long openedAt, lastEdit, lastFrame, headRevealAt, toastUntil, errorUntil;
    private String resolvedFor = "";
    private PlayerListEntry target;
    private String kickName = "";
    private boolean kicking;
    private String toast = "";

    // layout (liczone w layout())
    private int px, py, inX, inY, inW, inH, kx, ky, kw, kh, ux, uy, uw;

    public ProfileScreen() {
        super(Text.literal("Profil gracza"));
        this.openedAt = System.currentTimeMillis();
        this.lastFrame = this.openedAt;
    }

    @Override
    protected void init() {
        flakes.clear();
        for (int i = 0; i < SNOW_COUNT; i++) {
            Flake f = new Flake();
            respawn(f, true);
            flakes.add(f);
        }
    }

    private void respawn(Flake f, boolean anywhere) {
        f.x = rnd.nextFloat() * Math.max(1, width);
        f.y = anywhere ? rnd.nextFloat() * Math.max(1, height) : -4;
        f.vy = 12f + rnd.nextFloat() * 16f;       // wolno
        f.drift = 3f + rnd.nextFloat() * 7f;
        f.phase = rnd.nextFloat() * 6.28f;
        f.size = 1 + rnd.nextInt(3);
        f.alpha = 120 + rnd.nextInt(120);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ---------------------------------------------------------------- logika

    private State state() {
        String n = input.toString();
        if (n.isEmpty()) return State.IDLE;
        if (!n.equals(resolvedFor)) return State.SEARCHING;
        return target != null ? State.FOUND : State.NOT_FOUND;
    }

    @Override
    public void tick() {
        String n = input.toString();
        if (n.isEmpty()) {
            resolvedFor = "";
            target = null;
            return;
        }
        if (!n.equals(resolvedFor) && System.currentTimeMillis() - lastEdit >= SEARCH_DELAY_MS) {
            resolve(n);
        }
    }

    private void resolve(String name) {
        resolvedFor = name;
        target = null;
        try {
            ClientPlayNetworkHandler nh = client == null ? null : client.getNetworkHandler();
            if (nh != null) {
                target = nh.getPlayerListEntry(name);
            }
        } catch (Exception ignored) {
            target = null;
        }
        if (target != null) headRevealAt = System.currentTimeMillis();
    }

    private void edited() {
        lastEdit = System.currentTimeMillis();
        kicking = false;
    }

    private void onKick() {
        String name = input.toString();
        if (name.isEmpty()) {
            errorUntil = System.currentTimeMillis() + 2000;
            return;
        }
        if (!name.equals(resolvedFor)) resolve(name);
        kickName = target != null ? target.getProfile().getName() : name;
        kicking = true;
        if (target != null) {
            ProfileGuiClient.startGlow(target.getProfile().getId(), ProfileGuiClient.GLOW_MS);
        }
    }

    private void click(float pitch, float volume) {
        try {
            if (client != null) {
                client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), pitch, volume));
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        layout();
        click(1.0f, 1.0f); // kazde klikniecie = dzwiek
        if (button == 0) {
            inputFocused = in(mx, my, inX, inY, inW, inH);
            if (in(mx, my, kx, ky, kw, kh)) {
                onKick();
            } else if (client != null && client.player != null && in(mx, my, ux, uy - 1, uw, 10)) {
                client.keyboard.setClipboard(client.player.getUuid().toString());
                toast = "Skopiowano UUID!";
                toastUntil = System.currentTimeMillis() + 1500;
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (inputFocused) {
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (hasControlDown()) input.setLength(0);
                else if (input.length() > 0) input.deleteCharAt(input.length() - 1);
                click(1.5f, 0.4f);
                edited();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_V && hasControlDown() && client != null) {
                String clip = client.keyboard.getClipboard();
                if (clip != null) {
                    for (char c : clip.toCharArray()) {
                        if (valid(c) && input.length() < MAX_LEN) input.append(c);
                    }
                    edited();
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                lastEdit = 0; // szukaj od razu
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (!inputFocused) return false;
        if (System.currentTimeMillis() - openedAt < 150) return true; // ignoruj "i" z otwarcia
        if (valid(chr) && input.length() < MAX_LEN) {
            input.append(chr);
            click(1.4f, 0.4f);
            edited();
        }
        return true;
    }

    private static boolean valid(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
    }

    // ---------------------------------------------------------------- render

    private void layout() {
        long age = System.currentTimeMillis() - openedAt;
        float t = Math.min(1f, age / 280f);
        float e = 1f - (1f - t) * (1f - t) * (1f - t);
        px = (width - PW) / 2;
        py = (height - PH) / 2 + Math.round((1f - e) * 16f);
        inX = px + 48;
        inY = py + 88;
        inW = PW - 48 - 14;
        inH = 26;
        kx = px + 14;
        ky = py + 150;
        kw = PW - 28;
        kh = 30;
        ux = px + 58;
        uy = py + 42;
        uw = 200;
    }

    private static void panel(DrawContext c, int x, int y, int w, int h, int color) {
        c.fill(x + 1, y, x + w - 1, y + h, color);
        c.fill(x, y + 1, x + w, y + h - 1, color);
    }

    private Text bold(String s) {
        return Text.literal(s).formatted(Formatting.BOLD);
    }

    private void scaledText(DrawContext c, Text t, int x, int y, float s, int color) {
        c.getMatrices().push();
        c.getMatrices().translate(x, y, 0);
        c.getMatrices().scale(s, s, 1f);
        c.drawText(textRenderer, t, 0, 0, color, true);
        c.getMatrices().pop();
    }

    @Override
    public void renderBackground(DrawContext c, int mx, int my, float delta) {
        c.fillGradient(0, 0, width, height, 0xF0080D18, 0xF0172641);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        super.render(c, mx, my, delta);
        layout();
        long now = System.currentTimeMillis();
        State st = state();

        // poswiata
        for (int i = 4; i >= 1; i--) {
            int a = 10 * (5 - i);
            c.fill(px - i * 2, py - i * 2, px + PW + i * 2, py + PH + i * 2, (a << 24) | 0x4FC3F7);
        }
        panel(c, px - 1, py - 1, PW + 2, PH + 2, 0xFF2E4A73);
        panel(c, px, py, PW, PH, 0xF0111A2B);
        c.fill(px + 2, py, px + PW - 2, py + 2, 0xFF4FC3F7);

        // ---- naglowek: skin + nick + UUID (lewy gorny rog)
        String myName = "Gracz";
        String myUuid = "";
        SkinTextures mySkin = null;
        if (client != null && client.player != null) {
            myName = client.player.getGameProfile().getName();
            myUuid = client.player.getUuid().toString();
            mySkin = client.player.getSkinTextures();
        } else if (client != null) {
            myName = client.getSession().getUsername();
        }
        c.fill(px + 13, py + 13, px + 53, py + 53, 0xFF3A5F8F);
        c.fill(px + 14, py + 14, px + 52, py + 52, 0xFF0E1522);
        if (mySkin != null) PlayerSkinDrawer.draw(c, mySkin, px + 15, py + 15, 36);

        c.drawText(textRenderer, "PROFIL GRACZA", px + 58, py + 13, 0xFF6FA8DC, false);
        scaledText(c, bold(myName), px + 58, py + 24, 1.4f, 0xFFFFFFFF);
        boolean hoverUuid = in(mx, my, ux, uy - 1, uw, 10);
        c.drawText(textRenderer, "UUID: " + myUuid, ux, uy, hoverUuid ? 0xFFB3E5FC : 0xFF8FA3BF, false);
        if (hoverUuid) {
            c.fill(ux, uy + 9, ux + textRenderer.getWidth("UUID: " + myUuid), uy + 10, 0xFF4FC3F7);
        }
        String hint = now < toastUntil ? toast : "[ESC] zamknij";
        int hintColor = now < toastUntil ? 0xFF81C784 : 0xFF5C6F8C;
        c.drawText(textRenderer, hint, px + PW - 14 - textRenderer.getWidth(hint), py + 13, hintColor, false);

        c.fill(px + 14, py + 64, px + PW - 14, py + 65, 0xFF26385A);

        // ---- sekcja wyrzucania
        c.drawText(textRenderer, bold("WYRZUĆ GRACZA"), px + 14, py + 74, 0xFFE57373, false);

        // slot na glowke
        int border = switch (st) {
            case FOUND -> 0xFF66BB6A;
            case NOT_FOUND -> 0xFFE57373;
            case SEARCHING -> 0xFFFFD54F;
            default -> 0xFF3A5F8F;
        };
        c.fill(px + 14, py + 88, px + 40, py + 114, border);
        c.fill(px + 15, py + 89, px + 39, py + 113, 0xFF0E1522);
        if (st == State.FOUND && target != null) {
            float t = Math.min(1f, (now - headRevealAt) / 300f);
            float e = 1f - (1f - t) * (1f - t);
            int size = Math.max(1, Math.round(22 * e));
            int off = (22 - size) / 2;
            PlayerSkinDrawer.draw(c, target.getSkinTextures(), px + 16 + off, py + 90 + off, size);
        } else if (st == State.SEARCHING) {
            int cx = px + 27, cy = py + 101;
            for (int i = 0; i < 8; i++) {
                double ang = i * Math.PI / 4.0;
                int dx = (int) Math.round(Math.cos(ang) * 7);
                int dy = (int) Math.round(Math.sin(ang) * 7);
                int k = (int) ((now / 90 + 8 - i) % 8);
                int a = 255 - k * 28;
                c.fill(cx + dx - 1, cy + dy - 1, cx + dx + 1, cy + dy + 1, (a << 24) | 0xFFD54F);
            }
        } else {
            c.drawCenteredTextWithShadow(textRenderer, Text.literal("?"), px + 27, py + 97, 0xFF5C6F8C);
        }

        // pole tekstowe
        int boxBorder = inputFocused ? 0xFF4FC3F7 : 0xFF2A3A55;
        c.fill(inX, inY, inX + inW, inY + inH, boxBorder);
        c.fill(inX + 1, inY + 1, inX + inW - 1, inY + inH - 1, 0xFF0B111D);
        String txt = input.toString();
        if (txt.isEmpty()) {
            c.drawText(textRenderer, "Wpisz nick gracza...", inX + 8, inY + 9, 0xFF4F5F78, false);
        } else {
            c.drawText(textRenderer, txt, inX + 8, inY + 9, 0xFFFFFFFF, true);
        }
        if (inputFocused && (now / 500) % 2 == 0) {
            int cxp = inX + 8 + textRenderer.getWidth(txt);
            c.fill(cxp, inY + 7, cxp + 1, inY + 18, 0xFFFFFFFF);
        }
        String cnt = input.length() + "/" + MAX_LEN;
        c.drawText(textRenderer, cnt, inX + inW - 6 - textRenderer.getWidth(cnt), inY + 9, 0xFF4F5F78, false);

        // status wyszukiwania
        int sy = py + 121;
        String dots = ".".repeat((int) ((now / 400) % 4));
        switch (st) {
            case IDLE -> c.drawText(textRenderer, "Wpisz nick — skin pojawi się po chwili", px + 14, sy, 0xFF5C6F8C, false);
            case SEARCHING -> c.drawText(textRenderer, "Szukam gracza" + dots, px + 14, sy, 0xFFFFD54F, false);
            case FOUND -> c.drawText(textRenderer, "Znaleziono: " + target.getProfile().getName(), px + 14, sy, 0xFF81C784, false);
            case NOT_FOUND -> c.drawText(textRenderer, "Nie znaleziono gracza online", px + 14, sy, 0xFFE57373, false);
        }

        // przycisk kick
        boolean enabled = input.length() > 0;
        boolean hover = enabled && in(mx, my, kx, ky, kw, kh);
        int top, bot, bd;
        if (!enabled) { top = 0xFF4A4A55; bot = 0xFF38383F; bd = 0xFF2A2A30; }
        else if (hover) { top = 0xFFEF5350; bot = 0xFFC62828; bd = 0xFFFF8A80; }
        else { top = 0xFFC62828; bot = 0xFF8E1B1B; bd = 0xFF5E1212; }
        c.fill(kx, ky, kx + kw, ky + kh, bd);
        c.fillGradient(kx + 1, ky + 1, kx + kw - 1, ky + kh - 1, top, bot);
        c.drawCenteredTextWithShadow(textRenderer, bold("WYRZUĆ"), kx + kw / 2, ky + 11, enabled ? 0xFFFFFFFF : 0xFF8A8A94);

        // komunikat pod przyciskiem
        int my2 = py + 186;
        if (now < errorUntil) {
            c.drawText(textRenderer, "Najpierw wpisz nick gracza!", px + 14, my2, 0xFFE57373, false);
        } else if (kicking) {
            c.drawText(textRenderer, "Przygotowanie do wyrzucenia gracza z serwera" + dots, px + 14, my2, 0xFFFFB74D, false);
            UUID id = target != null ? target.getProfile().getId() : null;
            long rem = ProfileGuiClient.glowRemaining(id);
            String line = "Gracz: " + kickName;
            if (rem > 0) line += "  •  podświetlenie: " + ((rem + 999) / 1000) + "s";
            else if (id == null) line += "  •  brak gracza online";
            c.drawText(textRenderer, line, px + 14, my2 + 11, 0xFF8FA3BF, false);
        }

        // snieg
        float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
        lastFrame = now;
        for (Flake f : flakes) {
            f.y += f.vy * dt;
            f.x += (float) Math.sin(f.phase + now / 1200.0) * f.drift * dt;
            if (f.y > height + 4) respawn(f, false);
            int ix = Math.round(f.x), iy = Math.round(f.y);
            int col = (f.alpha << 24) | 0xFFFFFF;
            if (f.size == 3) {
                c.fill(ix + 1, iy, ix + 2, iy + 3, col);
                c.fill(ix, iy + 1, ix + 3, iy + 2, col);
            } else {
                c.fill(ix, iy, ix + f.size, iy + f.size, col);
            }
        }
    }
}
