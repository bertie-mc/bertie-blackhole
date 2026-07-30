package com.berlord.bertieblackhole.command;

import com.berlord.bertieblackhole.BlackHoleState;
import com.berlord.bertieblackhole.StatefulBlackHole;
import com.berlord.bertieblackhole.config.BbhConfig;
import com.berlord.bertieblackhole.config.CounterDef;
import com.berlord.bertieblackhole.config.ExchangeDef;
import com.berlord.bertieblackhole.config.LevelDef;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;

/** {@code /bertieblackhole reload} and {@code /bertieblackhole info} - there is no GUI. */
public final class BbhCommand {

    private static final int SEARCH_RADIUS = 8;

    private BbhCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bertieblackhole")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("reload").executes(BbhCommand::reload))
                .then(Commands.literal("info").executes(BbhCommand::info)));
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        try {
            BbhConfig config = BbhConfig.reload();
            context.getSource().sendSuccess(() -> Component.literal(
                    "Reloaded " + BbhConfig.FILE_NAME + " - " + config.levels().size() + " level(s)"), true);
            return 1;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Reload failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos origin = BlockPos.containing(source.getPosition());

        BlockPos foundPos = null;
        StatefulBlackHole found = null;
        double best = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
                origin.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS))) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof StatefulBlackHole hole) {
                double distance = pos.distSqr(origin);
                if (distance < best) {
                    best = distance;
                    found = hole;
                    foundPos = pos.immutable();
                }
            }
        }

        if (found == null) {
            source.sendFailure(Component.literal("No black hole within " + SEARCH_RADIUS + " blocks."));
            return 0;
        }
        report(source, foundPos, found.bbh$state());
        return 1;
    }

    private static void report(CommandSourceStack source, BlockPos pos, BlackHoleState state) {
        BbhConfig config = BbhConfig.get();
        int level = state.level();

        send(source, "Black hole at " + pos.toShortString() + " - level " + level, ChatFormatting.GOLD);

        LevelDef next = config.nextLevelDef(level);
        if (next == null) {
            send(source, "  at the highest configured level", ChatFormatting.GRAY);
        } else {
            for (CounterDef counter : next.requires().values()) {
                int current = state.counters().getOrDefault(counter.id(), 0);
                send(source, "  counter " + counter.id() + ": " + current + " / " + counter.max(),
                        current >= counter.max() ? ChatFormatting.GREEN : ChatFormatting.GRAY);
            }
        }

        for (ExchangeDef exchange : config.exchangesFor(level)) {
            int banked = state.buffers().getOrDefault(exchange.id(), 0);
            send(source, "  exchange " + exchange.id() + ": " + banked + " / " + exchange.count()
                    + " banked -> " + exchange.outputCount() + "x " + exchange.output(), ChatFormatting.GRAY);
        }

        if (state.pendingExchange() != null) {
            send(source, "  converting " + state.pendingExchange() + " in " + state.outputTimer() + " ticks",
                    ChatFormatting.AQUA);
        }
    }

    private static void send(CommandSourceStack source, String text, @Nullable ChatFormatting colour) {
        source.sendSuccess(() -> Component.literal(text).withStyle(colour == null ? ChatFormatting.WHITE : colour), false);
    }
}
