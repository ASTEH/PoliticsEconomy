package ru.zela.politicseconomy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import ru.zela.politicseconomy.country.CountryDirection;
import ru.zela.politicseconomy.country.CountrySettingsService;
import ru.zela.politicseconomy.country.GovernmentType;
import ru.zela.politicseconomy.country.ReligionType;
import ru.zela.politicseconomy.country.CountryPolicyManager;
import ru.zela.politicseconomy.country.CountryPolicyBonusService;
import ru.zela.politicseconomy.country.CountryPopulationService;
import ru.zela.politicseconomy.country.CountryDirectionManager;
import ru.zela.politicseconomy.country.CountryDirectionBonusService;
import ru.zela.politicseconomy.country.CountryDirectionProfile;
import ru.zela.politicseconomy.country.CountryDevelopmentService;
import ru.zela.politicseconomy.country.CountryWorkforceService;
import ru.zela.politicseconomy.country.CountryWorkplaceService;
import ru.zela.politicseconomy.country.WorkforceSector;
import ru.zela.politicseconomy.economy.ResourceExtractionCategory;
import ru.zela.politicseconomy.economy.NationalMaterialConsumptionService;
import ru.zela.politicseconomy.economy.PopulationDemandCatalog;
import ru.zela.politicseconomy.economy.PopulationMarketService;
import ru.zela.politicseconomy.economy.NationalMaterialDemandService;
import ru.zela.politicseconomy.economy.NationalMaterialInventoryService;
import ru.zela.politicseconomy.economy.NationalMaterialLedgerSavedData;
import ru.zela.politicseconomy.economy.TaxBlockShopService;
import ru.zela.politicseconomy.economyui.EconomyMenu;
import ru.zela.politicseconomy.integration.PoliticsModIntegration;
import ru.zela.politicseconomy.infrastructure.InfrastructureManager;
import ru.zela.politicseconomy.infrastructure.MaintenanceLedgerSavedData;
import ru.zela.politicseconomy.infrastructure.MaintenanceService;
import ru.zela.politicseconomy.recipe.RecipeAnalysis;
import ru.zela.politicseconomy.recipe.RecipeAnalyzer;
import ru.zela.politicseconomy.trade.TradeSavedData;
import ru.zela.politicseconomy.trade.TradeService;

import java.util.Optional;
import java.util.Map;

public final class PoliticsEconomyCommands {
    private PoliticsEconomyCommands() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("pe")
                .then(Commands.literal("info")
                    .executes(context -> showInfo(context.getSource())))
                .then(Commands.literal("profile")
                    .executes(context -> showProfile(context.getSource())))
                .then(Commands.literal("direction")
                    .executes(context -> showDirection(context.getSource()))
                    .then(Commands.argument("direction", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (CountryDirection direction : CountryDirection.values()) {
                                builder.suggest(direction.commandName());
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> setDirection(
                            context.getSource(),
                            StringArgumentType.getString(context, "direction")
                        ))))
                .then(Commands.literal("government")
                    .executes(context -> showGovernment(context.getSource()))
                    .then(Commands.argument("government", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (GovernmentType type : GovernmentType.values()) builder.suggest(type.commandName());
                            return builder.buildFuture();
                        })
                        .executes(context -> setGovernment(context.getSource(), StringArgumentType.getString(context, "government")))))
                .then(Commands.literal("religion")
                    .executes(context -> showReligion(context.getSource()))
                    .then(Commands.argument("religion", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (ReligionType type : ReligionType.values()) builder.suggest(type.commandName());
                            return builder.buildFuture();
                        })
                        .executes(context -> setReligion(context.getSource(), StringArgumentType.getString(context, "religion")))))
                .then(Commands.literal("shop")
                    .executes(context -> showTaxShop(context.getSource()))
                    .then(Commands.literal("buy")
                        .then(Commands.literal("tax_block")
                            .executes(context -> buyTaxBlocks(context.getSource(), 1))
                            .then(Commands.argument("amount", IntegerArgumentType.integer(1, TaxBlockShopService.maxBatch()))
                                .executes(context -> buyTaxBlocks(
                                    context.getSource(),
                                    IntegerArgumentType.getInteger(context, "amount")
                                )))))
                )
                .then(Commands.literal("market")
                    .executes(context -> showPopulationMarket(context.getSource()))
                    .then(Commands.literal("wallet")
                        .executes(context -> showMarketWallet(context.getSource())))
                    .then(Commands.literal("sell")
                        .then(Commands.argument("item", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                for (PopulationDemandCatalog.Good good : PopulationDemandCatalog.goods()) {
                                    builder.suggest(good.itemId());
                                }
                                return builder.buildFuture();
                            })
                            .then(Commands.argument("amount", IntegerArgumentType.integer(1, 4096))
                                .executes(context -> sellToPopulation(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "item"),
                                    IntegerArgumentType.getInteger(context, "amount")
                                )))))
                )
                .then(Commands.literal("infrastructure")
                    .executes(context -> showInfrastructure(context.getSource(), false))
                    .then(Commands.literal("country")
                        .executes(context -> showInfrastructure(context.getSource(), true))))
                .then(Commands.literal("economy")
                    .executes(context -> openEconomy(context.getSource()))
                    .then(Commands.literal("info")
                        .executes(context -> showEconomy(context.getSource())))
                    .then(Commands.literal("run")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> runEconomyNow(context.getSource())))
                    .then(Commands.literal("extraction")
                        .executes(context -> showExtractionTax(context.getSource())))
                    .then(Commands.literal("trade")
                        .executes(context -> showTradeFee(context.getSource())))
                    .then(Commands.literal("create")
                        .executes(context -> showCreateProduction(context.getSource())))
                    .then(Commands.literal("diesel")
                        .executes(context -> showDieselEfficiency(context.getSource())))
                    .then(Commands.literal("workforce")
                        .executes(context -> showWorkforce(context.getSource()))
                        .then(Commands.argument("sector", StringArgumentType.word())
                            .suggests((context, builder) -> {
                                for (WorkforceSector sector : WorkforceSector.values()) {
                                    builder.suggest(sector.commandName());
                                }
                                return builder.buildFuture();
                            })
                            .then(Commands.argument("delta", IntegerArgumentType.integer(-25, 25))
                                .executes(context -> setWorkforce(
                                    context.getSource(),
                                    StringArgumentType.getString(context, "sector"),
                                    IntegerArgumentType.getInteger(context, "delta")
                                )))))
                    .then(Commands.literal("deposit")
                        .executes(context -> depositInventory(context.getSource(), null))
                        .then(Commands.literal("all")
                            .executes(context -> depositInventory(context.getSource(), null)))
                        .then(Commands.argument("item", StringArgumentType.word())
                            .suggests((context, builder) -> suggestMaterialItems(context.getSource(), builder))
                            .executes(context -> depositInventory(
                                context.getSource(),
                                StringArgumentType.getString(context, "item")
                            )))
                    )
                    .then(Commands.literal("materials")
                        .executes(context -> showMaterials(context.getSource())))
                    .then(Commands.literal("development")
                        .executes(context -> showDevelopment(context.getSource()))
                        .then(Commands.literal("upgrade")
                            .executes(context -> upgradeDevelopment(context.getSource())))
                    )
                    .then(Commands.literal("stockpile")
                        .executes(context -> showStockpile(context.getSource()))
                        .then(Commands.literal("add")
                            .requires(source -> source.hasPermission(2))
                            .then(Commands.argument("item", StringArgumentType.word())
                                .suggests((context, builder) -> suggestMaterialItems(context.getSource(), builder))
                                .then(Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                    .executes(context -> addStockpile(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "item"),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "amount")
                                    )))))
                        .then(Commands.literal("set")
                            .requires(source -> source.hasPermission(2))
                            .then(Commands.argument("item", StringArgumentType.word())
                                .suggests((context, builder) -> suggestMaterialItems(context.getSource(), builder))
                                .then(Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                    .executes(context -> setStockpile(
                                        context.getSource(),
                                        StringArgumentType.getString(context, "item"),
                                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "amount")
                                    ))))))
                    .then(Commands.literal("recipe")
                        .then(Commands.argument("item", StringArgumentType.string())
                            .executes(context -> showRecipe(
                                context.getSource(),
                                StringArgumentType.getString(context, "item")
                            )))
                    )
                )
        );
    }



    
    private static int showTrade(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            return sendTrade(source, TradeService.listPlayerTrade(player));
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }
    }

    private static int tradeTerminalSet(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            return sendTrade(source, TradeService.setTerminal(player));
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }
    }

    private static int tradeTerminalShow(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            String country = PoliticsModIntegration.playerCountry(player).map(Country::getName).orElse(null);
            if (country == null) {
                source.sendFailure(Component.literal("Ты не состоишь ни в одной стране."));
                return 0;
            }
            TradeSavedData.Terminal terminal = TradeService.terminal(player.getServer(), country);
            if (terminal == null) {
                source.sendFailure(Component.literal("Торговый терминал ещё не назначен."));
                return 0;
            }
            source.sendSuccess(
                () -> Component.literal("Торговый терминал: " + ChunkPos.of(terminal.pos())),
                false
            );
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Не удалось получить терминал."));
            return 0;
        }
    }

    private static int tradeOrderCreate(CommandSourceStack source, String item, int amount, int maxPrice) {
        try {
            return sendTrade(source, TradeService.createOrder(
                source.getPlayerOrException(), item, amount, maxPrice
            ));
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }
    }

    private static int tradeOrderAccept(CommandSourceStack source, int id, int unitPrice) {
        try {
            return sendTrade(source, TradeService.acceptOrder(
                source.getPlayerOrException(), id, unitPrice
            ));
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }
    }

    private static int tradeOrderCancel(CommandSourceStack source, int id) {
        try {
            return sendTrade(source, TradeService.cancelOrder(
                source.getPlayerOrException(), id
            ));
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }
    }

    private static int tradeOrders(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            String country = PoliticsModIntegration.playerCountry(player).map(Country::getName).orElse(null);
            if (country == null) {
                source.sendFailure(Component.literal("Ты не состоишь ни в одной стране."));
                return 0;
            }

            source.sendSuccess(() -> Component.literal("=== Заказы государства " + country + " ===")
                .withStyle(ChatFormatting.GOLD), false);

            for (TradeSavedData.Order order : TradeService.countryOrders(
                player.getServer(), country
            )) {
                source.sendSuccess(() -> Component.literal(
                    "#" + order.id()
                        + " | " + order.itemId()
                        + " | " + order.remaining() + "/" + order.quantity()
                        + " | $" + order.maxUnitPrice() + "/шт"
                        + " | продавец: " + (order.sellerCountry() == null ? "не найден" : order.sellerCountry())
                        + " | статус: " + order.status()
                        + " | резерв $" + order.reservedFunds()
                ).withStyle(ChatFormatting.AQUA), false);
            }

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Не удалось получить заказы."));
            return 0;
        }
    }

    private static int tradeShipmentDispatch(CommandSourceStack source, int orderId, int amount) {
        try {
            return sendTrade(source, TradeService.dispatchShipment(
                source.getPlayerOrException(), orderId, amount
            ));
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }
    }

    private static int tradeShipmentHaul(CommandSourceStack source, int shipmentId) {
        try {
            return sendTrade(source, TradeService.acceptLogistics(
                source.getPlayerOrException(), shipmentId
            ));
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }
    }

    private static int tradeShipments(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            source.sendSuccess(() -> Component.literal("=== Доступные грузы ===")
                .withStyle(ChatFormatting.GOLD), false);

            for (TradeSavedData.Shipment shipment : TradeService.waitingShipments(player.getServer())) {
                source.sendSuccess(() -> Component.literal(
                    "#" + shipment.id()
                        + " | заказ #" + shipment.orderId()
                        + " | " + shipment.quantity() + " " + shipment.itemId()
                        + " | " + shipment.sellerCountry()
                        + " -> " + shipment.buyerCountry()
                ).withStyle(ChatFormatting.YELLOW), false);
            }

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Не удалось получить грузы."));
            return 0;
        }
    }

    private static int sendTrade(CommandSourceStack source, TradeService.TradeResult result) {
        source.sendSuccess(
            () -> Component.literal(result.message()).withStyle(
                result.success() ? ChatFormatting.GREEN : ChatFormatting.RED
            ),
            false
        );
        return result.success() ? 1 : 0;
    }

    private static int showPopulationMarket(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }

        Country country = PoliticsModIntegration.playerCountry(player).orElse(null);
        if (country == null) {
            source.sendFailure(Component.literal("Ты не состоишь ни в одной стране."));
            return 0;
        }

        int population = CountryPopulationService.population(player.getServer(), country.getName());
        long wallet = PopulationMarketService.wallet(player.getServer(), player.getUUID());

        source.sendSuccess(
            () -> Component.literal("=== Внутренний рынок: " + country.getName() + " ===")
                .withStyle(ChatFormatting.GOLD),
            false
        );
        source.sendSuccess(
            () -> Component.literal("Население: " + population + " | твой баланс рынка: $" + wallet)
                .withStyle(ChatFormatting.AQUA),
            false
        );
        source.sendSuccess(
            () -> Component.literal(
                "Продажа: /pe market sell <item_id> <amount> | население покупает только востребованные товары."
            ).withStyle(ChatFormatting.GRAY),
            false
        );

        var lines = PopulationMarketService.demandLines(
            player.getServer(),
            country.getName()
        );

        for (PopulationMarketService.DemandLine line : lines) {
            String name = PopulationDemandCatalog.shortName(line.itemId());
            String text = name
                + " | нужно " + line.baseDemand()
                + " | осталось " + line.remaining()
                + " | $" + line.pricePerUnit()
                + "/шт"
                + " | своё " + line.sold()
                + " | импорт " + line.imported();

            source.sendSuccess(
                () -> Component.literal(text)
                    .withStyle(line.remaining() > 0
                        ? ChatFormatting.YELLOW
                        : ChatFormatting.GREEN),
                false
            );
        }

        return 1;
    }

    private static int showMarketWallet(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }

        long wallet = PopulationMarketService.wallet(
            player.getServer(),
            player.getUUID()
        );
        source.sendSuccess(
            () -> Component.literal("Баланс внутреннего рынка: $" + wallet)
                .withStyle(ChatFormatting.GOLD),
            false
        );
        return 1;
    }

    private static int sellToPopulation(
        CommandSourceStack source,
        String item,
        int amount
    ) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }

        PopulationMarketService.SellResult result =
            PopulationMarketService.sellToPopulation(player, item, amount);

        source.sendSuccess(
            () -> Component.literal(result.message())
                .withStyle(result.success()
                    ? ChatFormatting.GREEN
                    : ChatFormatting.RED),
            true
        );
        return result.success() ? 1 : 0;
    }

    private static int showTaxShop(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }

        Optional<Country> countryOptional = PoliticsModIntegration.playerCountry(player);
        if (countryOptional.isEmpty()) {
            source.sendFailure(Component.literal("Сначала вступи в государство."));
            return 0;
        }

        Country country = countryOptional.get();
        long nextPrice = TaxBlockShopService.priceForNextTaxBlock(country);

        source.sendSuccess(
            () -> Component.literal("=== Государственный магазин ===")
                .withStyle(ChatFormatting.GOLD),
            false
        );
        source.sendSuccess(
            () -> Component.literal(
                "Tax Block уже установлено: " + country.taxBlocks.size()
                    + " | следующая единица: $" + nextPrice
            ).withStyle(ChatFormatting.YELLOW),
            false
        );
        source.sendSuccess(
            () -> Component.literal(
                "Купить: /pe shop buy tax_block [1-" + TaxBlockShopService.maxBatch() + "]"
            ).withStyle(ChatFormatting.GRAY),
            false
        );
        source.sendSuccess(
            () -> Component.literal(
                "Цена растёт с количеством налоговых блоков в стране."
            ).withStyle(ChatFormatting.DARK_GRAY),
            false
        );
        return 1;
    }

    private static int buyTaxBlocks(CommandSourceStack source, int amount) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }

        TaxBlockShopService.PurchaseResult result =
            TaxBlockShopService.buy(player, amount);

        if (result.success()) {
            source.sendSuccess(
                () -> Component.literal(result.message())
                    .withStyle(ChatFormatting.GREEN),
                true
            );
            return 1;
        }

        source.sendFailure(Component.literal(result.message()));
        return 0;
    }

    private static int showCreateProduction(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }

        String country = PoliticsModIntegration.countryNameAt(player);
        if (country == null) {
            source.sendFailure(Component.literal("Под тобой нет территории государства."));
            return 0;
        }

        double multiplier = ru.zela.politicseconomy.integration.CreateProductionService.effectiveMultiplier(
            player.serverLevel(), player.blockPosition()
        );
        double modifier = (multiplier - 1.0D) * 100.0D;

        source.sendSuccess(
            () -> Component.literal("=== Create производство ===").withStyle(ChatFormatting.GOLD),
            false
        );
        source.sendSuccess(
            () -> Component.literal("Страна: " + country).withStyle(ChatFormatting.AQUA),
            false
        );
        source.sendSuccess(
            () -> Component.literal(String.format(java.util.Locale.ROOT,
                "Модификатор результатов Basin: %+.0f%%", modifier))
                .withStyle(modifier >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED),
            false
        );
        source.sendSuccess(
            () -> Component.literal(String.format(java.util.Locale.ROOT,
                "Множитель: %.2fx", multiplier)).withStyle(ChatFormatting.YELLOW),
            false
        );
        source.sendSuccess(
            () -> Component.literal("Жидкостные результаты пока не изменяются.")
                .withStyle(ChatFormatting.GRAY),
            false
        );
        return 1;
    }

    private static int showWorkforce(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }

        Optional<Country> playerCountry = PoliticsModIntegration.playerCountry(player);
        if (playerCountry.isEmpty()) {
            source.sendFailure(Component.literal("Ты не состоишь ни в одной стране."));
            return 0;
        }

        String country = playerCountry.get().getName();
        int available = CountryWorkforceService.workingPopulation(player.getServer(), country);
        int employed = CountryWorkforceService.employedPopulation(player.getServer(), country);
        int unemployed = CountryWorkforceService.unemployedPopulation(player.getServer(), country);
        int workplaces = CountryWorkforceService.workplaceCapacity(player.getServer(), country);
        var allocation = CountryWorkforceService.allocation(player.getServer(), country);
        var workplaceSnapshot = CountryWorkplaceService.snapshot(player.getServer(), country);

        source.sendSuccess(() -> Component.literal(
            "=== Рабочая сила: " + country + " ===").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(
            "Доступно: " + available + " | занято: " + employed
                + " | без места: " + unemployed
                + " | рабочих мест: " + workplaces
        ).withStyle(unemployed > 0 ? ChatFormatting.YELLOW : ChatFormatting.GREEN), false);

        for (WorkforceSector sector : WorkforceSector.values()) {
            int share = allocation.getOrDefault(sector, 0);
            int workers = CountryWorkforceService.sectorWorkers(player.getServer(), country, sector);
            int slots = workplaceSnapshot.workplaceSlots().getOrDefault(sector, 0);
            int blocks = workplaceSnapshot.workplaceCounts().getOrDefault(sector, 0);
            double bonus = CountryWorkforceService.sectorBonusPercent(player.getServer(), country, sector);
            source.sendSuccess(() -> Component.literal(
                sector.displayName() + ": " + share + "% | " + workers + "/" + slots
                    + " работников | блоков " + blocks + " | бонус "
                    + CountryWorkforceService.formatBonus(bonus)
            ).withStyle(bonus > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        }

        return 1;
    }

    private static int setWorkforce(
        CommandSourceStack source,
        String sectorRaw,
        int delta
    ) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }

        WorkforceSector sector = WorkforceSector.fromCommandName(sectorRaw);
        if (sector == null) {
            source.sendFailure(Component.literal("Неизвестный сектор рабочей силы."));
            return 0;
        }

        CountryWorkforceService.Result result = CountryWorkforceService.apply(
            player,
            sector.commandName() + ":" + delta,
            player.isCreative() && player.hasPermissions(2)
        );
        source.sendSuccess(
            () -> Component.literal(result.message())
                .withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED),
            true
        );
        return result.success() ? 1 : 0;
    }

    private static int showDieselEfficiency(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }

        Optional<Country> playerCountry = PoliticsModIntegration.playerCountry(player);
        if (playerCountry.isEmpty()) {
            source.sendFailure(Component.literal("Ты не состоишь ни в одной стране."));
            return 0;
        }

        Country country = playerCountry.get();
        CountryDirectionProfile profile = CountryDirectionBonusService.profile(
            player.getServer(), country.getName()
        );
        if (profile == null) {
            source.sendFailure(Component.literal("У страны ещё не выбрано направление."));
            return 0;
        }

        double modifier = profile.dieselFuelConsumption();
        double multiplier = Math.max(0.0D, 1.0D + modifier / 100.0D);

        source.sendSuccess(
            () -> Component.literal("=== Дизельная эффективность ===").withStyle(ChatFormatting.GOLD),
            false
        );
        source.sendSuccess(
            () -> Component.literal("Страна: " + country.getName()).withStyle(ChatFormatting.AQUA),
            false
        );
        source.sendSuccess(
            () -> Component.literal(String.format(java.util.Locale.ROOT,
                "Расход топлива: %+.0f%% | множитель %.2fx", modifier, multiplier))
                .withStyle(modifier < 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY),
            false
        );
        source.sendSuccess(
            () -> Component.literal(String.format(java.util.Locale.ROOT,
                "Текущий расход: %.0f%% от базового", multiplier * 100.0D))
                .withStyle(ChatFormatting.YELLOW),
            false
        );
        return 1;
    }

    private static int showTradeFee(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }

        var manager = PoliticsModIntegration.manager(player.serverLevel());
        if (manager == null) {
            source.sendFailure(Component.literal("PoliticsMod недоступен."));
            return 0;
        }
        String country = manager.getPlayerCountry(player.getUUID());
        if (country == null) {
            source.sendFailure(Component.literal("Ты не состоишь ни в одной стране."));
            return 0;
        }

        int fee = CountryDirectionBonusService.effectiveTradeFeePercent(
            player.getServer(), country);
        source.sendSuccess(
            () -> Component.literal("=== Торговая комиссия ===").withStyle(ChatFormatting.GOLD),
            false
        );
        source.sendSuccess(
            () -> Component.literal("Страна: " + country).withStyle(ChatFormatting.AQUA),
            false
        );
        source.sendSuccess(
            () -> Component.literal("Эффективная комиссия продавца: " + fee + "%")
                .withStyle(ChatFormatting.YELLOW),
            false
        );
        source.sendSuccess(
            () -> Component.literal("Для союзника: 0%").withStyle(ChatFormatting.GREEN),
            false
        );
        return 1;
    }

    private static int showInfrastructure(CommandSourceStack source, boolean country) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }

        if (!country) {
            ChunkPos chunk = player.chunkPosition();
            var counts = InfrastructureManager.getCurrentChunkCounts(player);
            int total = counts.values().stream().mapToInt(Integer::intValue).sum();

            source.sendSuccess(
                () -> Component.literal("=== Инфраструктура чанка ===")
                    .withStyle(ChatFormatting.GOLD),
                false
            );
            source.sendSuccess(
                () -> Component.literal("Chunk: " + chunk.x + ", " + chunk.z),
                false
            );
            source.sendSuccess(
                () -> Component.literal("Поставлено игроками и отслеживается: " + total)
                    .withStyle(ChatFormatting.AQUA),
                false
            );

            if (counts.isEmpty()) {
                source.sendSuccess(
                    () -> Component.literal("Здесь пока ничего не отслеживается.")
                        .withStyle(ChatFormatting.GRAY),
                    false
                );
                return 1;
            }

            counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(10)
                .forEach(entry -> source.sendSuccess(
                    () -> Component.literal(entry.getKey() + " × " + entry.getValue()),
                    false
                ));
            return 1;
        }

        var stats = InfrastructureManager.getCountryStats(player);
        if (stats.countryName().isBlank()) {
            source.sendFailure(Component.literal("Ты не состоишь ни в одной стране."));
            return 0;
        }

        source.sendSuccess(
            () -> Component.literal("=== Инфраструктура страны ===")
                .withStyle(ChatFormatting.GOLD),
            false
        );
        source.sendSuccess(
            () -> Component.literal("Страна: " + stats.countryName())
                .withStyle(ChatFormatting.AQUA),
            false
        );
        source.sendSuccess(
            () -> Component.literal("Отслеживаемых блоков: " + stats.totalBlocks())
                .withStyle(ChatFormatting.GREEN),
            false
        );
        source.sendSuccess(
            () -> Component.literal(String.format(java.util.Locale.ROOT,
                "Базовое содержание: %.2f ед./цикл", stats.baseMaintenance()))
                .withStyle(ChatFormatting.YELLOW),
            false
        );
        source.sendSuccess(
            () -> Component.literal(String.format(java.util.Locale.ROOT,
                "Содержание с учётом направления: %.2f ед./цикл", stats.adjustedMaintenance()))
                .withStyle(ChatFormatting.RED),
            false
        );

        if (!stats.categories().isEmpty()) {
            source.sendSuccess(
                () -> Component.literal("Категории:").withStyle(ChatFormatting.GOLD),
                false
            );
            stats.categories().entrySet().stream()
                .sorted(Map.Entry.comparingByValue((a, b) -> Integer.compare(b, a)))
                .forEach(entry -> source.sendSuccess(
                    () -> Component.literal(entry.getKey().displayName() + " × " + entry.getValue()
                        + String.format(java.util.Locale.ROOT, " (%.2f ед./блок)",
                            ru.zela.politicseconomy.infrastructure.MaintenanceCalculator.categoryCostPerBlock(entry.getKey()))),
                    false
                ));
        }

        source.sendSuccess(
            () -> Component.literal("Первые 15 типов блоков:").withStyle(ChatFormatting.GOLD),
            false
        );

        stats.blocks().entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(15)
            .forEach(entry -> source.sendSuccess(
                () -> {
                    var info = ru.zela.politicseconomy.infrastructure.InfrastructureClassifier.classify(entry.getKey());
                    double adjusted = info.baseMaintenance() *
                        ru.zela.politicseconomy.infrastructure.MaintenanceCalculator.directionMultiplier(
                            player.getServer(), stats.countryName(), info.category()) * entry.getValue();
                    return Component.literal(String.format(java.util.Locale.ROOT,
                        "%s × %d | %s | %.2f ед./цикл",
                        entry.getKey(), entry.getValue(), info.category().displayName(), adjusted));
                },
                false
            ));

        return 1;
    }

    private static int showRecipe(CommandSourceStack source, String itemId) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }

        RecipeAnalysis analysis = RecipeAnalyzer.findCraftingRecipe(player.serverLevel(), itemId).orElse(null);
        if (analysis == null) {
            analysis = RecipeAnalyzer.findCraftingRecipeForBlock(player.serverLevel(), itemId).orElse(null);
        }

        if (analysis == null) {
            source.sendFailure(Component.literal(
                "Рецепт не найден. Пример: /pe recipe minecraft:chest"
            ));
            return 0;
        }

        final RecipeAnalysis result = analysis;
        source.sendSuccess(
            () -> Component.literal("=== Анализ рецепта ===").withStyle(ChatFormatting.GOLD),
            false
        );
        source.sendSuccess(
            () -> Component.literal("Результат: " + result.resultItemId() + " × " + result.resultCount())
                .withStyle(ChatFormatting.AQUA),
            false
        );
        source.sendSuccess(
            () -> Component.literal("Рецепт: " + result.recipeId())
                .withStyle(ChatFormatting.GRAY),
            false
        );

        if (result.ingredients().isEmpty()) {
            source.sendSuccess(
                () -> Component.literal("Ингредиенты: отсутствуют / специальный рецепт")
                    .withStyle(ChatFormatting.YELLOW),
                false
            );
            return 1;
        }

        source.sendSuccess(
            () -> Component.literal("Ингредиенты:").withStyle(ChatFormatting.GOLD),
            false
        );
        int index = 1;
        for (var ingredient : result.ingredients()) {
            final int slot = index++;
            final String text = slot + ". " + ingredient.displayName();
            source.sendSuccess(() -> Component.literal(text), false);
        }

        source.sendSuccess(
            () -> Component.literal("Это пока только анализ. Ресурсы ещё НЕ списываются автоматически.")
                .withStyle(ChatFormatting.DARK_GRAY),
            false
        );
        return 1;
    }

    private static int showExtractionTax(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }

        String territoryCountry = PoliticsModIntegration.countryNameAt(player);
        if (territoryCountry == null) {
            source.sendFailure(Component.literal("Под тобой нет территории государства."));
            return 0;
        }

        CountryDirectionProfile profile = CountryDirectionBonusService.profile(
            player.getServer(), territoryCountry
        );
        if (profile == null) {
            source.sendFailure(Component.literal("У этого государства ещё не выбрано направление."));
            return 0;
        }

        source.sendSuccess(
            () -> Component.literal("=== Добыча ресурсов ===").withStyle(ChatFormatting.GOLD),
            false
        );
        source.sendSuccess(
            () -> Component.literal("Государство: " + territoryCountry).withStyle(ChatFormatting.AQUA),
            false
        );

        for (ResourceExtractionCategory category : ResourceExtractionCategory.values()) {
            double production = profile.extractionProduction(category);
            double loss = profile.extractionLoss(category);
            double multiplier = profile.extractionMultiplier(category);
            double netPercent = (multiplier - 1.0D) * 100.0D;
            source.sendSuccess(
                () -> Component.literal(String.format(java.util.Locale.ROOT,
                    "%s: производство %+,.1f%% | потери %.1f%% | итог %+,.2f%%",
                    category.displayName(), production, loss, netPercent))
                    .withStyle(netPercent < 0.0 ? ChatFormatting.RED
                        : (netPercent > 0.0 ? ChatFormatting.GREEN : ChatFormatting.YELLOW)),
                false
            );
        }

        source.sendSuccess(
            () -> Component.literal("Потерянные предметы не переходят государству — они удаляются из финального дропа.")
                .withStyle(ChatFormatting.GRAY),
            false
        );
        return 1;
    }

    private static int openEconomy(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            EconomyMenu.open(player);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }
    }

    private static int showEconomy(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }

        Optional<Country> playerCountry = PoliticsModIntegration.playerCountry(player);
        if (playerCountry.isEmpty()) {
            source.sendFailure(Component.literal("Ты не состоишь ни в одной стране."));
            return 0;
        }

        Country country = playerCountry.get();
        String countryName = country.getName();
        var stats = InfrastructureManager.getCountryStats(player.getServer(), countryName);
        MaintenanceLedgerSavedData ledger = MaintenanceService.getLedger(player.getServer());
        double debt = ledger.getDebt(countryName);
        double pending = ledger.getPending(countryName);
        double projectedNextCharge = Math.floor(debt + pending + stats.adjustedMaintenance() + 1.0e-9);

        source.sendSuccess(
            () -> Component.literal("=== Экономика страны ===").withStyle(ChatFormatting.GOLD),
            false
        );
        source.sendSuccess(
            () -> Component.literal("Казна: $" + country.balance).withStyle(ChatFormatting.AQUA),
            false
        );
        source.sendSuccess(
            () -> Component.literal(String.format(java.util.Locale.ROOT,
                "Содержание инфраструктуры: %.2f $/цикл", stats.adjustedMaintenance()))
                .withStyle(ChatFormatting.YELLOW),
            false
        );
        source.sendSuccess(
            () -> Component.literal(String.format(java.util.Locale.ROOT,
                "Накопившийся долг: %.2f $", debt))
                .withStyle(debt > 0.0 ? ChatFormatting.RED : ChatFormatting.GREEN),
            false
        );
        source.sendSuccess(
            () -> Component.literal(String.format(java.util.Locale.ROOT,
                "Дробная сумма до следующего списания: %.2f $", pending))
                .withStyle(ChatFormatting.GRAY),
            false
        );
        source.sendSuccess(
            () -> Component.literal("Ожидаемое списание при следующем цикле: $" + (int) projectedNextCharge)
                .withStyle(ChatFormatting.LIGHT_PURPLE),
            false
        );
        source.sendSuccess(
            () -> Component.literal("Для ручного теста OP может использовать: /pe economy run")
                .withStyle(ChatFormatting.DARK_GRAY),
            false
        );
        return 1;
    }

    private static int runEconomyNow(CommandSourceStack source) {
        MaintenanceService.chargeNow(source.getServer());
        NationalMaterialConsumptionService.chargeNow(source.getServer());

        Optional<Country> playerCountry = Optional.empty();
        try {
            ServerPlayer player = source.getPlayerOrException();
            playerCountry = PoliticsModIntegration.playerCountry(player);
        } catch (Exception ignored) {
        }

        if (playerCountry.isPresent()) {
            String countryName = playerCountry.get().getName();
            NationalMaterialLedgerSavedData ledger = NationalMaterialConsumptionService.getLedger(source.getServer());
            int debt = ledger.totalDebt(countryName);
            source.sendSuccess(
                () -> Component.literal("Материальный долг страны после цикла: " + debt)
                    .withStyle(debt > 0 ? ChatFormatting.RED : ChatFormatting.GREEN),
                true
            );
        }

        source.sendSuccess(
            () -> Component.literal("Экономический цикл принудительно выполнен: деньги и конкретные материалы списаны.")
                .withStyle(ChatFormatting.GREEN),
            true
        );
        return 1;
    }

    private static int depositInventory(CommandSourceStack source, String itemId) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }

        if (PoliticsModIntegration.playerCountry(player).isEmpty()) {
            source.sendFailure(Component.literal("Ты не состоишь ни в одной стране."));
            return 0;
        }
        NationalMaterialInventoryService.DepositResult result =
            NationalMaterialInventoryService.deposit(player, itemId);
        if (result.itemCount() <= 0) {
            source.sendFailure(Component.literal(
                "В инвентаре не найдено материалов, которые сейчас требуются инфраструктуре страны."
            ));
            return 0;
        }
        NationalMaterialInventoryService.tellResult(player, result);
        return 1;
    }

    private static int showMaterials(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }

        Optional<Country> playerCountry = PoliticsModIntegration.playerCountry(player);
        if (playerCountry.isEmpty()) {
            source.sendFailure(Component.literal("Ты не состоишь ни в одной стране."));
            return 0;
        }

        String countryName = playerCountry.get().getName();
        NationalMaterialLedgerSavedData ledger = NationalMaterialConsumptionService.getLedger(player.getServer());
        ledger.initializeCountry(countryName);
        var demand = NationalMaterialDemandService.calculate(player.serverLevel(), countryName);

        source.sendSuccess(() -> Component.literal("=== Материалы экономики ===").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal(
            "Отслеживается строительных блоков: " + demand.trackedInfrastructureBlocks()), false);
        source.sendSuccess(() -> Component.literal(
            String.format(java.util.Locale.ROOT,
                "Постоянное потребление материалов: %.2f ед./цикл",
                demand.materials().stream().mapToDouble(NationalMaterialDemandService.MaterialDemand::perCycleConsumption).sum())
        ).withStyle(ChatFormatting.YELLOW), false);

        if (demand.materials().isEmpty()) {
            source.sendSuccess(() -> Component.literal(
                "Нет строительных блоков с обычным crafting-рецептом. Специальные/Create-рецепты подключим следующим этапом."
            ).withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        demand.materials().stream().limit(32).forEach(material -> {
            int stock = material.acceptedItemIds().stream()
                .mapToInt(itemId -> ledger.getStockpile(countryName, itemId))
                .sum();
            int debt = ledger.getDebt(countryName, material.key());
            String name = NationalMaterialDemandService.displayName(player.serverLevel(), material.acceptedItemIds());
            source.sendSuccess(() -> Component.literal(String.format(
                java.util.Locale.ROOT,
                "%s | содержание %.2f | расход %.2f/цикл | склад %d | долг %d | блоков %d",
                name, material.materialContent(), material.perCycleConsumption(), stock, debt, material.contributingBlocks()
            )).withStyle(debt > 0 ? ChatFormatting.RED : ChatFormatting.AQUA), false);
        });
        return 1;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> suggestMaterialItems(
        CommandSourceStack source,
        com.mojang.brigadier.suggestion.SuggestionsBuilder builder
    ) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            Optional<Country> country = PoliticsModIntegration.playerCountry(player);
            if (country.isEmpty()) {
                return builder.buildFuture();
            }
            var demand = NationalMaterialDemandService.calculate(player.serverLevel(), country.get().getName());
            demand.materials().stream()
                .flatMap(material -> material.acceptedItemIds().stream())
                .distinct()
                .sorted()
                .forEach(builder::suggest);
        } catch (Exception ignored) {
            // Command suggestions are best-effort only.
        }
        return builder.buildFuture();
    }

    private static int showDevelopment(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }
        Country country = PoliticsModIntegration.playerCountry(player).orElse(null);
        if (country == null) {
            source.sendFailure(Component.literal("Ты не состоишь ни в одной стране."));
            return 0;
        }
        String countryName = country.getName();
        CountryDirection direction = CountryDirectionManager.getDirection(player.getServer(), countryName);
        if (direction == null) {
            source.sendFailure(Component.literal("У страны ещё не выбрано направление."));
            return 0;
        }

        int level = CountryDevelopmentService.level(player.getServer(), countryName);
        int points = CountryDevelopmentService.points(player.getServer(), countryName);
        int next = CountryDevelopmentService.nextThreshold(player.getServer(), countryName);

        source.sendSuccess(() -> Component.literal("=== Развитие экономики ===").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("Страна: " + countryName).withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal("Уровень: " + level + "/5").withStyle(ChatFormatting.GREEN), false);
        source.sendSuccess(() -> Component.literal("Очки развития: " + points + (level < 5 ? " / " + next : " (максимум)"))
            .withStyle(ChatFormatting.YELLOW), false);
        source.sendSuccess(() -> Component.literal("Текущий бонус: " +
            CountryDevelopmentService.currentPerk(direction, level)).withStyle(ChatFormatting.WHITE), false);
        if (level < 5) {
            source.sendSuccess(() -> Component.literal("Следующий бонус: " +
                CountryDevelopmentService.nextPerk(direction, level)).withStyle(ChatFormatting.LIGHT_PURPLE), false);
            source.sendSuccess(() -> Component.literal("Для повышения уровня нужны очки и отсутствие ресурсного долга."), false);
        }
        return 1;
    }

    private static int upgradeDevelopment(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }
        Country country = PoliticsModIntegration.playerCountry(player).orElse(null);
        if (country == null) {
            source.sendFailure(Component.literal("Ты не состоишь ни в одной стране."));
            return 0;
        }
        if (!source.hasPermission(2) && PoliticsModIntegration.role(player, country) != CountryRole.LEADER) {
            source.sendFailure(Component.literal("Повысить уровень может только лидер страны."));
            return 0;
        }

        String countryName = country.getName();
        CountryDirection direction = CountryDirectionManager.getDirection(player.getServer(), countryName);
        if (direction == null) {
            source.sendFailure(Component.literal("У страны ещё не выбрано направление."));
            return 0;
        }

        NationalMaterialLedgerSavedData ledger = NationalMaterialConsumptionService.getLedger(player.getServer());
        if (ledger.hasAnyDebt(countryName)) {
            source.sendFailure(Component.literal("Нельзя повышать уровень при материальном долге."));
            return 0;
        }
        int oldLevel = CountryDevelopmentService.level(player.getServer(), countryName);
        if (oldLevel >= 5) {
            source.sendFailure(Component.literal("Экономика уже достигла максимального уровня."));
            return 0;
        }
        if (!CountryDevelopmentService.canUpgrade(player.getServer(), countryName)) {
            int next = CountryDevelopmentService.nextThreshold(player.getServer(), countryName);
            int current = CountryDevelopmentService.points(player.getServer(), countryName);
            source.sendFailure(Component.literal("Недостаточно очков развития: " + current + "/" + next));
            return 0;
        }

        CountryDevelopmentService.upgrade(player.getServer(), countryName);
        int newLevel = CountryDevelopmentService.level(player.getServer(), countryName);
        source.sendSuccess(() -> Component.literal("Экономика развита до уровня " + newLevel + ": " +
            CountryDevelopmentService.currentPerk(direction, newLevel)).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int showStockpile(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }
        Optional<Country> playerCountry = PoliticsModIntegration.playerCountry(player);
        if (playerCountry.isEmpty()) {
            source.sendFailure(Component.literal("Ты не состоишь ни в одной стране."));
            return 0;
        }

        String countryName = playerCountry.get().getName();
        NationalMaterialLedgerSavedData ledger = NationalMaterialConsumptionService.getLedger(player.getServer());
        ledger.initializeCountry(countryName);
        source.sendSuccess(() -> Component.literal("=== Государственный склад ===").withStyle(ChatFormatting.GOLD), false);

        Map<String, Integer> stock = ledger.getStockpile(countryName);
        if (stock.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Склад пуст."), false);
        } else {
            stock.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    String name = NationalMaterialDemandService.itemDisplayName(player.serverLevel(), entry.getKey());
                    source.sendSuccess(() -> Component.literal(name + " (" + entry.getKey() + "): " + entry.getValue())
                        .withStyle(ChatFormatting.AQUA), false);
                });
        }

        if (ledger.hasAnyDebt(countryName)) {
            source.sendSuccess(() -> Component.literal("Материальный долг: " + ledger.totalDebt(countryName))
                .withStyle(ChatFormatting.RED), false);
        }
        return 1;
    }

    private static int addStockpile(CommandSourceStack source, String rawItem, int amount) {
        return mutateStockpile(source, rawItem, amount, false);
    }

    private static int setStockpile(CommandSourceStack source, String rawItem, int amount) {
        return mutateStockpile(source, rawItem, amount, true);
    }

    private static int mutateStockpile(CommandSourceStack source, String rawItem, int amount, boolean set) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }
        Optional<Country> playerCountry = PoliticsModIntegration.playerCountry(player);
        if (playerCountry.isEmpty()) {
            source.sendFailure(Component.literal("Ты не состоишь ни в одной стране."));
            return 0;
        }

        ResourceLocation itemKey;
        try {
            itemKey = ResourceLocation.parse(rawItem);
        } catch (IllegalArgumentException e) {
            source.sendFailure(Component.literal("Неверный item id: " + rawItem));
            return 0;
        }
        if (BuiltInRegistries.ITEM.getOptional(itemKey).isEmpty()) {
            source.sendFailure(Component.literal("Предмет не найден: " + rawItem));
            return 0;
        }

        String countryName = playerCountry.get().getName();
        NationalMaterialLedgerSavedData ledger = NationalMaterialConsumptionService.getLedger(player.getServer());
        ledger.initializeCountry(countryName);
        if (set) {
            ledger.setStockpile(countryName, rawItem, amount);
        } else {
            ledger.addStockpile(countryName, rawItem, amount);
        }
        int value = ledger.getStockpile(countryName, rawItem);
        String name = NationalMaterialDemandService.itemDisplayName(player.serverLevel(), rawItem);
        source.sendSuccess(() -> Component.literal(
            (set ? "Склад " : "Добавлено ") + name + ": " + value
        ).withStyle(ChatFormatting.YELLOW), true);
        return 1;
    }

    private static int showInfo(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }
        ChunkPos chunk = player.chunkPosition();

        source.sendSuccess(
            () -> Component.literal("=== Politics Economy ===")
                .withStyle(ChatFormatting.GOLD),
            false
        );

        source.sendSuccess(
            () -> Component.literal("Chunk: " + chunk.x + ", " + chunk.z),
            false
        );

        String territoryCountry = PoliticsModIntegration.countryNameAt(player);
        String city = PoliticsModIntegration.cityAt(player);

        if (territoryCountry == null) {
            source.sendSuccess(
                () -> Component.literal("Territory: unclaimed")
                    .withStyle(ChatFormatting.GRAY),
                false
            );
        } else {
            String territory = "Territory country: " + territoryCountry;
            if (city != null && !city.isBlank()) {
                territory += " | City: " + city;
            }
            final String territoryText = territory;
            source.sendSuccess(
                () -> Component.literal(territoryText)
                    .withStyle(ChatFormatting.GREEN),
                false
            );
        }

        Optional<Country> playerCountry = PoliticsModIntegration.playerCountry(player);
        if (playerCountry.isEmpty()) {
            source.sendSuccess(
                () -> Component.literal("Your country: none")
                    .withStyle(ChatFormatting.GRAY),
                false
            );
            return 1;
        }

        Country country = playerCountry.get();
        CountryRole role = PoliticsModIntegration.role(player, country);
        String countryName = country.getName();
        int balance = country.balance;
        CountryDirection direction = CountryDirectionManager.getDirection(
            player.getServer(),
            countryName
        );

        source.sendSuccess(
            () -> Component.literal("Your country: " + countryName)
                .withStyle(ChatFormatting.AQUA),
            false
        );
        source.sendSuccess(
            () -> Component.literal("Role: " + role.name()),
            false
        );
        source.sendSuccess(
            () -> Component.literal("Treasury: $" + balance),
            false
        );
        source.sendSuccess(
            () -> Component.literal("Direction: " + directionText(direction))
                .withStyle(ChatFormatting.LIGHT_PURPLE),
            false
        );

        return 1;
    }

    private static int showProfile(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }

        Optional<Country> playerCountry = PoliticsModIntegration.playerCountry(player);
        if (playerCountry.isEmpty()) {
            source.sendFailure(Component.literal("Ты не состоишь ни в одной стране."));
            return 0;
        }

        Country country = playerCountry.get();
        CountryDirection direction = CountryDirectionManager.getDirection(
            player.getServer(), country.getName()
        );
        if (direction == null) {
            source.sendFailure(Component.literal("У страны ещё не выбрано направление."));
            return 0;
        }

        CountryDirectionProfile profile = CountryDirectionBonusService.profile(
            player.getServer(), country.getName()
        );

        source.sendSuccess(
            () -> Component.literal("=== Профиль: " + direction.displayName() + " ===")
                .withStyle(ChatFormatting.GOLD),
            false
        );
        sendModifier(source, "Промышленность", profile.industrialProduction());
        sendModifier(source, "Сырьё", profile.resourceProduction());
        sendModifier(source, "Сельское хозяйство", profile.agriculturalProduction());
        sendModifier(source, "Военное производство", profile.militaryProduction());
        sendModifier(source, "Доход от торговли", profile.tradeIncome());
        sendModifier(source, "Комиссия торговли", profile.tradeFee());
        sendModifier(source, "Содержание промышленности", profile.industrialMaintenance());
        sendModifier(source, "Расход топлива дизельных двигателей", profile.dieselFuelConsumption());
        sendModifier(source, "Содержание ресурсной инфраструктуры", profile.resourceMaintenance());
        sendModifier(source, "Содержание транспорта", profile.transportMaintenance());
        sendModifier(source, "Потребление еды", profile.foodConsumption());
        sendModifier(source, "Рост населения", profile.populationGrowth());
        sendModifier(source, "Стоимость сложной промышленности", profile.advancedIndustryCost());
        sendModifier(source, "Потери добычи: руды", -profile.extractionLoss(ResourceExtractionCategory.ORE));
        sendModifier(source, "Потери добычи: древесина", -profile.extractionLoss(ResourceExtractionCategory.WOOD));
        sendModifier(source, "Потери добычи: сельхозресурсы", -profile.extractionLoss(ResourceExtractionCategory.AGRICULTURE));
        sendModifier(source, "Потери добычи: топливо", -profile.extractionLoss(ResourceExtractionCategory.FUEL));
        sendModifier(source, "Потери добычи: прочее сырьё", -profile.extractionLoss(ResourceExtractionCategory.RAW_MATERIAL));
        var policy = CountryPolicyBonusService.profile(player.getServer(), country.getName());
        GovernmentType government = CountryPolicyManager.getGovernment(player.getServer(), country.getName());
        ReligionType religion = CountryPolicyManager.getReligion(player.getServer(), country.getName());
        source.sendSuccess(() -> Component.literal("Политика: " + (government == null ? "не выбрана" : government.displayName()) +
            " • " + (religion == null ? "не выбрана" : religion.displayName())).withStyle(ChatFormatting.GOLD), false);
        sendModifier(source, "Политика: промышленность", policy.industrialProduction());
        sendModifier(source, "Политика: сырьё", policy.resourceProduction());
        sendModifier(source, "Политика: сельское хозяйство", policy.agriculturalProduction());
        sendModifier(source, "Политика: военное производство", policy.militaryProduction());
        sendModifier(source, "Политика: торговая комиссия", policy.tradeFee());
        sendModifier(source, "Рабочая сила (население + политика)", CountryPolicyBonusService.workforcePercent(player.getServer(), country.getName()));

        return 1;
    }

    private static void sendModifier(CommandSourceStack source, String label, double percent) {
        String text = label + ": " + formatPercent(percent);
        ChatFormatting color = percent > 0 ? ChatFormatting.GREEN : percent < 0 ? ChatFormatting.RED : ChatFormatting.GRAY;
        source.sendSuccess(() -> Component.literal(text).withStyle(color), false);
    }

    private static String formatPercent(double percent) {
        if (percent > 0) {
            return "+" + trimPercent(percent) + "%";
        }
        return trimPercent(percent) + "%";
    }

    private static String trimPercent(double percent) {
        if (percent == Math.rint(percent)) {
            return Integer.toString((int) percent);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", percent);
    }

    private static int showGovernment(CommandSourceStack source) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку.")); return 0;
        }
        Country country = PoliticsModIntegration.playerCountry(player).orElse(null);
        if (country == null) { source.sendFailure(Component.literal("Ты не состоишь ни в одной стране.")); return 0; }
        GovernmentType current = CountryPolicyManager.getGovernment(player.getServer(), country.getName());
        source.sendSuccess(() -> Component.literal("Форма правления: " + (current == null ? "не выбрана" : current.displayName())).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int setGovernment(CommandSourceStack source, String raw) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку.")); return 0;
        }
        Country country = PoliticsModIntegration.playerCountry(player).orElse(null);
        if (country == null) { source.sendFailure(Component.literal("Сначала вступи или создай страну.")); return 0; }
        CountryRole role = PoliticsModIntegration.role(player, country);
        if (!player.isCreative() && role != CountryRole.LEADER) {
            source.sendFailure(Component.literal("Изменять форму правления может только лидер страны."));
            return 0;
        }
        GovernmentType value = GovernmentType.fromCommandName(raw);
        if (value == null) { source.sendFailure(Component.literal("Используй: democracy, communism, monarchy или fascism.")); return 0; }
        CountrySettingsService.Result result = CountrySettingsService.apply(player, "government", raw);
        source.sendSuccess(() -> Component.literal(result.message())
            .withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        return result.success() ? 1 : 0;
    }

    private static int showReligion(CommandSourceStack source) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку.")); return 0;
        }
        Country country = PoliticsModIntegration.playerCountry(player).orElse(null);
        if (country == null) { source.sendFailure(Component.literal("Ты не состоишь ни в одной стране.")); return 0; }
        ReligionType current = CountryPolicyManager.getReligion(player.getServer(), country.getName());
        source.sendSuccess(() -> Component.literal("Религия: " + (current == null ? "не выбрана" : current.displayName())).withStyle(ChatFormatting.AQUA), false);
        return 1;
    }

    private static int setReligion(CommandSourceStack source, String raw) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку.")); return 0;
        }
        Country country = PoliticsModIntegration.playerCountry(player).orElse(null);
        if (country == null) { source.sendFailure(Component.literal("Сначала вступи или создай страну.")); return 0; }
        CountryRole role = PoliticsModIntegration.role(player, country);
        if (!player.isCreative() && role != CountryRole.LEADER) {
            source.sendFailure(Component.literal("Изменять религию может только лидер страны."));
            return 0;
        }
        ReligionType value = ReligionType.fromCommandName(raw);
        if (value == null) { source.sendFailure(Component.literal("Используй: secular, christianity, islam, buddhism или judaism.")); return 0; }
        CountrySettingsService.Result result = CountrySettingsService.apply(player, "religion", raw);
        source.sendSuccess(() -> Component.literal(result.message())
            .withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        return result.success() ? 1 : 0;
    }

    private static int showDirection(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }

        Optional<Country> playerCountry = PoliticsModIntegration.playerCountry(player);
        if (playerCountry.isEmpty()) {
            source.sendFailure(Component.literal("Ты не состоишь ни в одной стране."));
            return 0;
        }

        Country country = playerCountry.get();
        CountryDirection direction = CountryDirectionManager.getDirection(
            player.getServer(),
            country.getName()
        );

        if (direction == null) {
            source.sendSuccess(
                () -> Component.literal("У страны ещё не выбрано направление.")
                    .withStyle(ChatFormatting.YELLOW),
                false
            );
            source.sendSuccess(
                () -> Component.literal("Доступно: industrial, resource, trade"),
                false
            );
        } else {
            source.sendSuccess(
                () -> Component.literal("Направление страны: " + direction.displayName())
                    .withStyle(ChatFormatting.LIGHT_PURPLE),
                false
            );
        }

        return 1;
    }

    private static int setDirection(CommandSourceStack source, String rawDirection) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception e) {
            source.sendFailure(Component.literal("Эта команда доступна только игроку."));
            return 0;
        }

        Country country = PoliticsModIntegration.playerCountry(player).orElse(null);
        if (country == null) {
            source.sendFailure(Component.literal("Сначала вступи или создай страну."));
            return 0;
        }

        CountryRole role = PoliticsModIntegration.role(player, country);
        if (!player.isCreative() && role != CountryRole.LEADER) {
            source.sendFailure(Component.literal("Выбрать направление может только лидер страны."));
            return 0;
        }

        CountryDirection direction = CountryDirection.fromCommandName(rawDirection);
        if (direction == null) {
            source.sendFailure(Component.literal("Неизвестное направление. Используй: industrial, resource или trade."));
            return 0;
        }

        CountrySettingsService.Result result = CountrySettingsService.apply(player, "direction", rawDirection);
        source.sendSuccess(() -> Component.literal(result.message())
            .withStyle(result.success() ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        return result.success() ? 1 : 0;
    }

    private static String directionText(CountryDirection direction) {
        return direction == null ? "не выбрано" : direction.displayName();
    }
}
