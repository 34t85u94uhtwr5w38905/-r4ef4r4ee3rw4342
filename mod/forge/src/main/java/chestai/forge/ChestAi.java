package chestai.forge;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import chestai.client.Session;
import chestai.client.Settings;
import chestai.core.Net;

/**
 * The mod as Forge loads it: a key (J) that hands the keys of the game to
 * the trained chest farmer and takes them back. The policy comes inside
 * the jar; a file policy.bin in config/chestai is used in its place, and
 * ajustes.txt there holds what the player sets.
 */
@Mod(modid = "chestai", name = "Chest AI", version = "1.0", clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public final class ChestAi {
    private final KeyBinding key = new KeyBinding("Chest AI: prender y apagar", Keyboard.KEY_J, "Chest AI");
    private File dir = new File("config", "chestai");
    private Session session;
    private final SelfTest selfTest = Boolean.getBoolean("chestai.selftest") ? new SelfTest(this) : null;

    boolean on() {
        return session != null && session.on();
    }

    Session session() {
        return session;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        dir = new File(e.getModConfigurationDirectory(), "chestai");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        ClientRegistry.registerKeyBinding(key);
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
    }

    private Net net() throws IOException {
        File own = new File(dir, "policy.bin");
        InputStream in = own.isFile() ? new FileInputStream(own) : ChestAi.class.getResourceAsStream("/chestai/policy.bin");
        if (in == null) {
            throw new IOException("no hay politica en el jar");
        }
        try {
            return Net.load(new BufferedInputStream(in));
        } finally {
            in.close();
        }
    }

    private static void say(Minecraft mc, String line) {
        if (line != null && mc.ingameGUI != null) {
            mc.ingameGUI.getChatGUI().printChatMessage(new ChatComponentText(line));
        }
    }

    void flip(Minecraft mc) throws IOException {
        if (session != null && session.on()) {
            session.stop();
            say(mc, "Chest AI apagado.");
            return;
        }
        Net net = net();
        session = new Session(mc, net, dir);
        String line = session.start(Settings.load(new File(dir, "ajustes.txt")));
        if (session.on()) {
            line += String.format(Locale.ROOT, " Entrenado con %d millones de muestras.", net.samples / 1000000L);
        }
        say(mc, line);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        try {
            if (selfTest != null) {
                selfTest.step(mc);
            }
            boolean pressed = false;
            while (key.isPressed()) {
                pressed = true;
            }
            if (pressed) {
                flip(mc);
            }
            if (session != null) {
                say(mc, session.tickStart());
            }
        } catch (Throwable t) {
            // Whatever goes wrong, the player gets the keys back.
            t.printStackTrace();
            try {
                if (session != null) {
                    session.stop();
                }
            } catch (Throwable again) {
                again.printStackTrace();
            }
            session = null;
            say(mc, "Chest AI apagado por un error: " + t);
        }
    }
}
