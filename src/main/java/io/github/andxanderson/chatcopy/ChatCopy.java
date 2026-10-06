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
import org.lwjgl.input.Mouse;

import java.util.List;

@Mod(modid = "chatcopy", useMetadata = true, clientSideOnly = true)
public class ChatCopy {

    private final Minecraft mc = Minecraft.getMinecraft();

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!(event.gui instanceof GuiChat) || Mouse.getEventButton() != 1 || !Mouse.getEventButtonState()) return;

        GuiNewChat chat = mc.ingameGUI.getChatGUI();
        ChatLine line = lineAt(chat, Mouse.getX(), Mouse.getY());
        if (line == null) return;

        String text = GuiScreen.isShiftKeyDown() ? fullMessage(chat, line) : wordAt(line, mouseChatX(chat));
        if (text.isEmpty()) return;

        GuiScreen.setClipboardString(text);
        mc.thePlayer.addChatMessage(new ChatComponentText("Copied: " + text)
                .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GRAY)));
        event.setCanceled(true);
    }

    // Same hit-test as GuiNewChat.getChatComponent, but returns the whole drawn line.
    private ChatLine lineAt(GuiNewChat chat, int mouseX, int mouseY) {
        List<ChatLine> drawn = ReflectionHelper.getPrivateValue(GuiNewChat.class, chat, "drawnChatLines", "field_146253_i");
        int scrollPos = ReflectionHelper.getPrivateValue(GuiNewChat.class, chat, "scrollPos", "field_146250_j");

        int scale = new ScaledResolution(mc).getScaleFactor();
        float chatScale = chat.getChatScale();
        int x = (int) Math.floor((mouseX / scale - 3) / chatScale);
        int y = (int) Math.floor((mouseY / scale - 27) / chatScale);
        if (x < 0 || y < 0) return null;

        int visible = Math.min(chat.getLineCount(), drawn.size());
        int fontHeight = mc.fontRendererObj.FONT_HEIGHT;
        if (x > Math.floor(chat.getChatWidth() / chatScale) || y >= fontHeight * visible + visible) return null;

        int index = y / fontHeight + scrollPos;
        return index >= 0 && index < drawn.size() ? drawn.get(index) : null;
    }

    private int mouseChatX(GuiNewChat chat) {
        int scale = new ScaledResolution(mc).getScaleFactor();
        return (int) Math.floor((Mouse.getX() / scale - 3) / chat.getChatScale());
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

        // "Steve:" -> "Steve", "[MVP+]" stays as-is.
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
