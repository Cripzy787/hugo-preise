package de.hugoprices;

import de.hugoprices.logic.ItemKeyBuilder;
import de.hugoprices.logic.MarketClient;
import de.hugoprices.logic.Mode;
import de.hugoprices.logic.ModeDetector;
import de.hugoprices.logic.MoneyFormat;
import de.hugoprices.logic.PriceBook;
import de.hugoprices.logic.PriceExtractor;
import de.hugoprices.logic.Quote;
import de.hugoprices.logic.Settings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

/**
 * Zeigt im Tooltip eines verzauberten Items den niedrigsten Auktionspreis und den höchsten Order-Preis.
 * Quelle 1: Live-Daten von hugosmp-market.net (im Hintergrund abgerufen).
 * Quelle 2 (Ersatz): Preise, die im Auktionshaus-/Order-Menü im Spiel gesehen wurden.
 */
public final class HugoPricesClient implements ClientModInitializer {
    public static final PriceBook BOOK = new PriceBook();
    private static Settings settings = new Settings();
    private static MarketClient market;

    @Override
    public void onInitializeClient() {
        settings = Settings.load(FabricLoader.getInstance().getConfigDir().resolve("hugoprices.properties"));
        market = new MarketClient(settings);

        // Menü offen: alle paar Ticks die Einträge lesen (Ersatzquelle).
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container)) {
                return;
            }
            int[] counter = {0};
            ScreenEvents.afterTick(screen).register(s -> {
                if (counter[0]++ % settings.scanEveryTicks == 0) {
                    scan(container);
                }
            });
        });

        // Tooltip: Preise anhängen.
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> addPriceLines(stack, lines));
    }

    private static void scan(AbstractContainerScreen<?> screen) {
        Optional<Mode> mode = ModeDetector.detect(screen.getTitle().getString(), settings);
        if (mode.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        long maxAge = settings.maxAgeMinutes * 60_000L;
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container instanceof Inventory) {
                continue; // eigenes Inventar überspringen, nur die Menü-Einträge zählen
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            Optional<PriceExtractor.Result> found = PriceExtractor.extract(loreOf(stack), settings);
            if (found.isEmpty()) {
                continue;
            }
            boolean total = mode.get() == Mode.AH ? settings.ahPriceIsTotal : settings.orderPriceIsTotal;
            long price = found.get().price();
            if (total && !found.get().perPieceHint()) {
                price = PriceExtractor.perPiece(price, stack.getCount());
            }
            BOOK.observe(mode.get(), keyOf(stack), price, now, maxAge);
        }
    }

    private static void addPriceLines(ItemStack stack, List<Component> lines) {
        if (stack.isEmpty()) {
            return;
        }
        // Menü-Einträge mit eigener Preiszeile nicht noch einmal beschriften.
        if (PriceExtractor.extract(loreOf(stack), settings).isPresent()) {
            return;
        }
        long nowMs = System.currentTimeMillis();
        long maxAge = settings.maxAgeMinutes * 60_000L;
        String cur = settings.currencySymbol;

        MarketClient.Result web = null;
        boolean loading = false;
        String webError = null;
        String marketKey = settings.webEnabled ? marketKeyOf(stack) : null;
        if (marketKey != null) {
            MarketClient.Lookup lookup = market.lookup(marketKey);
            web = lookup.result();
            loading = lookup.loading() && web == null;
            webError = web == null ? lookup.error() : null;
        } else if (settings.webEnabled && isUnsupported(stack)) {
            lines.add(Component.literal("Preise: fuer diese Item-Art nicht verfuegbar").withStyle(ChatFormatting.GRAY));
        }
        String gameKey = keyOf(stack);

        if (settings.showAh) {
            Optional<Quote> q = web != null ? web.auction() : Optional.empty();
            if (q.isPresent()) {
                lines.add(Component.literal("AH: " + MoneyFormat.format(q.get().price(), cur)
                        + " (" + MoneyFormat.age(nowMs - q.get().seenAt().toEpochMilli()) + ")")
                        .withStyle(ChatFormatting.GOLD));
            } else {
                Optional<PriceBook.Entry> g = BOOK.get(Mode.AH, gameKey, nowMs, maxAge);
                if (g.isPresent()) {
                    lines.add(Component.literal("AH: " + MoneyFormat.format(g.get().price(), cur)
                            + " (im Spiel gesehen, " + MoneyFormat.age(nowMs - g.get().seenAtMillis()) + ")")
                            .withStyle(ChatFormatting.GOLD));
                } else if (web != null) {
                    lines.add(Component.literal("AH: kein aktuelles Angebot").withStyle(ChatFormatting.GRAY));
                }
            }
        }
        if (settings.showOrder) {
            Optional<Quote> q = web != null ? web.order() : Optional.empty();
            if (q.isPresent()) {
                lines.add(Component.literal("Order: " + MoneyFormat.format(q.get().price(), cur)
                        + " (" + MoneyFormat.age(nowMs - q.get().seenAt().toEpochMilli()) + ")")
                        .withStyle(ChatFormatting.AQUA));
            } else {
                Optional<PriceBook.Entry> g = BOOK.get(Mode.ORDER, gameKey, nowMs, maxAge);
                if (g.isPresent()) {
                    lines.add(Component.literal("Order: " + MoneyFormat.format(g.get().price(), cur)
                            + " (im Spiel gesehen, " + MoneyFormat.age(nowMs - g.get().seenAtMillis()) + ")")
                            .withStyle(ChatFormatting.AQUA));
                } else if (web != null) {
                    lines.add(Component.literal("Order: keine Order gefunden").withStyle(ChatFormatting.GRAY));
                }
            }
        }
        if (loading) {
            lines.add(Component.literal("Preise werden geladen ...").withStyle(ChatFormatting.DARK_GRAY));
        } else if (webError != null) {
            lines.add(Component.literal("Preise nicht abrufbar: " + webError).withStyle(ChatFormatting.RED));
        }
    }

    /** Items, deren Marktschlüssel mehr als die ID braucht (Tränke, Spawner, Köpfe ...): nicht abfragen. */
    private static boolean isUnsupported(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return id.endsWith("potion") || id.endsWith("tipped_arrow") || id.endsWith("spawner")
                || id.endsWith("_head") || id.endsWith("_skull") || id.endsWith("written_book")
                || id.endsWith("filled_map") || id.endsWith("firework_rocket") || id.endsWith("firework_star");
    }

    private static List<String> loreOf(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        List<String> out = new ArrayList<>();
        if (lore != null) {
            for (Component c : lore.lines()) {
                out.add(c.getString());
            }
        }
        return out;
    }

    /** Verzauberungen als "minecraft:sharpness=5" (bei Büchern die gespeicherten). */
    private static List<String> enchantsOf(ItemStack stack) {
        List<String> ench = new ArrayList<>();
        var direct = stack.get(DataComponents.ENCHANTMENTS);
        if (direct != null) {
            direct.entrySet().forEach(e -> ench.add(e.getKey().getRegisteredName() + "=" + e.getIntValue()));
        }
        var stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null) {
            stored.entrySet().forEach(e -> ench.add(e.getKey().getRegisteredName() + "=" + e.getIntValue()));
        }
        return ench;
    }

    /** Schlüssel für hugosmp-market.net, z. B. "enchanted_book/multishot:1". null = nicht abfragen. */
    private static String marketKeyOf(ItemStack stack) {
        if (isUnsupported(stack)) {
            return null;
        }
        List<String> ench = enchantsOf(stack);
        if (ench.isEmpty() && settings.onlyEnchanted) {
            return null;
        }
        return ItemKeyBuilder.build(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), ench,
                settings.enchantSeparator);
    }

    /** Schlüssel für die im Spiel gesehenen Preise: Item-ID + sortierte Verzauberungen. */
    private static String keyOf(ItemStack stack) {
        StringBuilder sb = new StringBuilder(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        List<String> ench = new ArrayList<>();
        var direct = stack.get(DataComponents.ENCHANTMENTS);
        if (direct != null) {
            direct.entrySet().forEach(e -> ench.add(e.getKey().getRegisteredName() + "=" + e.getIntValue()));
        }
        var stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null) {
            stored.entrySet().forEach(e -> ench.add("stored:" + e.getKey().getRegisteredName() + "=" + e.getIntValue()));
        }
        java.util.Collections.sort(ench);
        for (String e : ench) {
            sb.append('|').append(e);
        }
        return sb.toString();
    }
}
