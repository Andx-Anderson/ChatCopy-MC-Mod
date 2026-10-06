package io.github.andxanderson.chatcopy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Mouse;

import java.util.List;

@Mod(modid = "chatcopy", useMetadata = true, clientSideOnly = true)
public class ChatCopy {

    private static final String PREFIX = "Copied: ";
    private static final Logger LOG = LogManager.getLogger("chatcopy");

    private final Minecraft mc = Minecraft.getMinecraft();
    private long lastEvent;

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.gui instanceof GuiChat) || Mouse.getEventButton() != 1 || !Mouse.getEventButtonState()) return;
        // Some clients post this event twice per click; handle each mouse event once.
        if (Mouse.getEventNanoseconds() == lastEvent) return;
        lastEvent = Mouse.getEventNanoseconds();

        int mouseX = Mouse.getEventX(), mouseY = Mouse.getEventY();
        GuiNewChat chat = mc.ingameGUI.getChatGUI();

        // Ask the game what's under the cursor. Clients that move or restyle chat
        // (Feather/Dawn) patch this method, so it stays correct where our own maths wouldn't.
        IChatComponent hit = chat.getChatComponent(mouseX, mouseY);
        if (hit == null) return;
        ChatLine line = lineContaining(chat, hit);
        if (line == null) {
            LOG.info("No chat line matched component '{}'", hit.getUnformattedText());
            return;
        }

        String plain = clean(line.getChatComponent().getUnformattedText());
        if (plain.startsWith(PREFIX)) return;

        String text = GuiScreen.isShiftKeyDown()
                ? fullMessage(chat, line)
                : wordAt(line, chatX(chat, mouseX, mouseY));
        if (text.isEmpty()) return;

        GuiScreen.setClipboardString(text);
        mc.thePlayer.addChatMessage(new ChatComponentText(PREFIX + text)
                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY)));
        event.setCanceled(true);
    }

    // Iterating a component yields copies, so getChatComponent's result is never the
    // same object as anything in the line; match on contents instead.
    @SuppressWarnings("unchecked")
    private ChatLine lineContaining(GuiNewChat chat, IChatComponent hit) {
        String hitText = hit.getUnformattedText();
        for (List<ChatLine> lines : new List[]{usedLines(chat), drawnLines(chat)}) {
            if (lines == null) continue;
            for (ChatLine line : lines) {
                for (IChatComponent part : line.getChatComponent()) {
                    if (part.equals(hit) || (!hitText.isEmpty() && part.getUnformattedText().equals(hitText))) return line;
                }
            }
        }
        return null;
    }

    private static List<ChatLine> drawnLines(GuiNewChat chat) {
        return ReflectionHelper.getPrivateValue(GuiNewChat.class, chat, "drawnChatLines", "field_146253_i");
    }

    // Feather/Dawn chat tabs render a filtered copy of drawnChatLines from this method.
    @SuppressWarnings("unchecked")
    private static List<ChatLine> usedLines(GuiNewChat chat) {
        try {
            return (List<ChatLine>) chat.getClass().getMethod("getUsedTrimmedMessages").invoke(chat);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    // Chat x in text pixels. The line's left edge is wherever getChatComponent first
    // returns something on this row, which accounts for any offset the client adds.
    private int chatX(GuiNewChat chat, int mouseX, int mouseY) {
        int lo = 0, hi = mouseX;
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (chat.getChatComponent(mid, mouseY) != null) hi = mid; else lo = mid + 1;
        }
        float pixelsPerUnit = new ScaledResolution(mc).getScaleFactor() * chat.getChatScale();
        return (int) ((mouseX - lo) / pixelsPerUnit);
    }

    // Walks the line char by char, measuring rendered width, to find the word under x.
    private String wordAt(ChatLine line, int x) {
        FontRenderer font = mc.fontRendererObj;
        StringBuilder plain = new StringBuilder();
        int hit = -1;
        int width = 0;

        for (IChatComponent part : line.getChatComponent()) {
            String style = part.getChatStyle().getFormattingCode();
            String raw = part.getUnformattedTextForChat();
            int start = width;
            for (int i = 0; i < raw.length(); i++) {
                if (raw.charAt(i) == '§') { i++; continue; } // inline colour/bold code, zero width
                int next = start + font.getStringWidth(style + raw.substring(0, i + 1));
                if (hit < 0 && x >= width && x < next) hit = plain.length();
                plain.append(raw.charAt(i));
                width = next;
            }
        }
        if (hit < 0 || Character.isWhitespace(plain.charAt(hit))) return "";

        int start = hit, end = hit;
        while (start > 0 && !Character.isWhitespace(plain.charAt(start - 1))) start--;
        while (end < plain.length() && !Character.isWhitespace(plain.charAt(end))) end++;
        String word = plain.substring(start, end);

        // "Steve:" -> "Steve", "<Steve>" -> "Steve", "[MVP+]" -> "MVP".
        String trimmed = word.replaceAll("^[^A-Za-z0-9_]+|[^A-Za-z0-9_]+$", "");
        return trimmed.isEmpty() ? word : trimmed;
    }

    // A wrapped message is several drawn lines; find the original message it came from.
    private String fullMessage(GuiNewChat chat, ChatLine drawnLine) {
        List<ChatLine> messages = ReflectionHelper.getPrivateValue(GuiNewChat.class, chat, "chatLines", "field_146252_h");
        String part = clean(drawnLine.getChatComponent().getUnformattedText());
        for (ChatLine message : messages) {
            if (message.getUpdatedCounter() != drawnLine.getUpdatedCounter()) continue;
            String full = clean(message.getChatComponent().getUnformattedText());
            if (full.contains(part)) return full;
        }
        return part;
    }

    private static String clean(String s) {
        return EnumChatFormatting.getTextWithoutFormattingCodes(s).replaceAll("\\s+", " ").trim();
    }
}
