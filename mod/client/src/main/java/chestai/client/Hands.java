package chestai.client;

import java.lang.reflect.Field;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import chestai.core.Pilot;

/**
 * Presses the keys of one tick as a keyboard and a mouse would have by
 * the time the game reads them in that tick (the event at the start of
 * the tick comes before the game reads its keys and works out what the
 * crosshair is on).
 */
final class Hands {
    /** 1.8 keeps the left button from clicking for half a second after a click into the air; the simulator does not. */
    private static final Field MISS_DELAY = Names.field(Minecraft.class, "leftClickCounter", "field_71429_W");

    private final Minecraft mc;
    private double carryX, carryY;
    private boolean useDown;

    Hands(Minecraft mc) {
        this.mc = mc;
    }

    /** What a mouse count turns the view by (times 0.15 degrees), at the sensitivity that is set. */
    static float mouseFactor(GameSettings gs) {
        float f = gs.mouseSensitivity * 0.6F + 0.2F;
        return f * f * f * 8.0F;
    }

    private static void key(KeyBinding k, boolean down) {
        KeyBinding.setKeyBindState(k.getKeyCode(), down);
    }

    void press(Pilot.Keys k, Layout lay) {
        GameSettings gs = mc.gameSettings;
        key(gs.keyBindForward, k.forward > 0);
        key(gs.keyBindBack, k.forward < 0);
        key(gs.keyBindLeft, k.strafe > 0);
        key(gs.keyBindRight, k.strafe < 0);
        key(gs.keyBindJump, k.jump);
        key(gs.keyBindSprint, k.sprint);

        // The view, in whole mouse counts.
        float f1 = mouseFactor(gs);
        double x = k.dyaw / (0.15 * f1) + carryX, y = k.dpitch / (0.15 * f1) + carryY;
        long nx = Math.round(x), ny = Math.round(y);
        carryX = x - nx;
        carryY = y - ny;
        if (nx != 0 || ny != 0) {
            mc.thePlayer.setAngles(nx * f1, -(ny * f1));
        }

        // The hand.
        int real = lay.real[k.slot];
        if (real >= 0 && mc.thePlayer.inventory.currentItem != real) {
            mc.thePlayer.inventory.currentItem = real;
        }
        if (k.throwIt && real >= 0) {
            ItemStack s = mc.thePlayer.inventory.mainInventory[real];
            if (s != null) {
                mc.playerController.sendUseItem(mc.thePlayer, mc.theWorld, s);
            }
        }
        if (k.attack) {
            Names.setInt(MISS_DELAY, mc, 0);
            KeyBinding.onTick(gs.keyBindAttack.getKeyCode());
        }
        boolean hold = k.useHeld;
        if ((k.claim || (hold && !useDown))) {
            KeyBinding.onTick(gs.keyBindUseItem.getKeyCode());
        }
        key(gs.keyBindUseItem, hold);
        useDown = hold;
    }

    void release() {
        KeyBinding.unPressAllKeys();
        useDown = false;
    }
}
