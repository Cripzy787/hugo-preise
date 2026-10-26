package de.hugoprices;

import de.hugoprices.logic.Mode;
import de.hugoprices.logic.ModeDetector;
import de.hugoprices.logic.MoneyFormat;
import de.hugoprices.logic.PriceBook;
import de.hugoprices.logic.PriceExtractor;
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
 * Liest Preise aus dem Auktionshaus- und Order-Menü mit und zeigt sie im Tooltip an:
 * AH = niedrigster gesehener Preis, Order = höchster gesehener Preis.
 */
public final class HugoPricesClient implements ClientModInitializer {
    public static final PriceBook BOOK = new PriceBook();
    private static Settings settings = new Settings();

    @Override
    public void onInitializeClient() {
        settings = Settings.load(FabricLoader.getInstance().getConfigDir().resolve("hugoprices.properties"));

        // Menü offen: alle paar Ticks die Einträge lesen.
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
        String key = keyOf(stack);
        long now = System.currentTimeMillis();
        long maxAge = settings.maxAgeMinutes * 60_000L;
        String cur = settings.currencySymbol;
        if (settings.showAh) {
            BOOK.get(Mode.AH, key, now, maxAge).ifPresent(e -> lines.add(Component
                    .literal("AH: " + MoneyFormat.format(e.price(), cur) + " (" + MoneyFormat.age(now - e.seenAtMillis()) + ")")
                    .withStyle(ChatFormatting.GOLD)));
        }
        if (settings.showOrder) {
            BOOK.get(Mode.ORDER, key, now, maxAge).ifPresent(e -> lines.add(Component
                    .literal("Order: " + MoneyFormat.format(e.price(), cur) + " (" + MoneyFormat.age(now - e.seenAtMillis()) + ")")
                    .withStyle(ChatFormatting.AQUA)));
        }
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

    /** Schlüssel = Item-ID + sortierte Verzauberungen (gespeicherte bei Büchern inklusive). */
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
