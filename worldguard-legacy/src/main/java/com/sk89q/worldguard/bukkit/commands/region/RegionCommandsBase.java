/*
 * WorldGuard, a suite of tools for Minecraft
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldGuard team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldguard.bukkit.commands.region;

import com.google.common.base.Joiner;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.worldedit.BlockVector;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Polygonal2DRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.selector.CuboidRegionSelector;
import com.sk89q.worldedit.regions.selector.Polygonal2DRegionSelector;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.internal.permission.RegionPermissionModel;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.FlagContext;
import com.sk89q.worldguard.protection.flags.InvalidFlagFormat;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.stream.Collectors;

class RegionCommandsBase {

    protected RegionCommandsBase() {
    }

    /**
     * Get the permission model to lookup permissions.
     *
     * @param sender the sender
     * @return the permission model
     */
    protected static RegionPermissionModel getPermissionModel(Actor sender) {
        return new RegionPermissionModel(sender);
    }

    /**
     * Gets the world from the given flag, or falling back to the the current player
     * if the sender is a player, otherwise reporting an error.
     *
     * @param args the arguments
     * @param sender the sender
     * @param flag the flag (such as 'w')
     * @return a world
     * @throws CommandException on error
     */
    protected static World checkWorld(CommandContext args, CommandSender sender, char flag) throws CommandException {
        if (args.hasFlag(flag)) {
            return WorldGuardPlugin.inst().matchWorld(sender, args.getFlag(flag));
        } else {
            if (sender instanceof Player) {
                return WorldGuardPlugin.inst().checkPlayer(sender).getWorld();
            } else {
                throw new CommandException("Пожалуйста, укажите " + "название мира: -" + flag + " world_name.");
            }
        }
    }

    /**
     * Validate a region ID.
     *
     * @param id the id
     * @param allowGlobal whether __global__ is allowed
     * @return the id given
     * @throws CommandException thrown on an error
     */
    protected static String checkRegionId(String id, boolean allowGlobal) throws CommandException {
        if (!ProtectedRegion.isValidId(id)) {
            throw new CommandException(
                    "Название региона '" + id + "' содержит запрещенные символы.");
        }

        if (!allowGlobal && id.equalsIgnoreCase("__global__")) { // Sorry, no global
            throw new CommandException(
                    "Вы не можете использовать глобальный регион.");
        }

        return id;
    }

    /**
     * Get a protected region by a given name, otherwise throw a
     * {@link CommandException}.
     *
     * <p>This also validates the region ID.</p>
     *
     * @param regionManager the region manager
     * @param id the name to search
     * @param allowGlobal true to allow selecting __global__
     * @throws CommandException thrown if no region is found by the given name
     */
    protected static ProtectedRegion checkExistingRegion(RegionManager regionManager, String id, boolean allowGlobal) throws CommandException {
        // Validate the id
        checkRegionId(id, allowGlobal);

        ProtectedRegion region = regionManager.getRegion(id);

        // No region found!
        if (region == null) {
            // But we want a __global__, so let's create one
            if (id.equalsIgnoreCase("__global__")) {
                region = new GlobalProtectedRegion(id);
                regionManager.addRegion(region);
                return region;
            }

            throw new CommandException(
                    "Регион '" + id + "' не найден.");
        }

        return region;
    }


    /**
     * Get the region at the player's location, if possible.
     *
     * <p>If the player is standing in several regions, an error will be raised
     * and a list of regions will be provided.</p>
     *
     * @param regionManager the region manager
     * @param player the player
     * @return a region
     * @throws CommandException thrown if no region was found
     */
    protected static ProtectedRegion checkRegionStandingIn(RegionManager regionManager, LocalPlayer player) throws CommandException {
        return checkRegionStandingIn(regionManager, player, false);
    }

    /**
     * Get the region at the player's location, if possible.
     *
     * <p>If the player is standing in several regions, an error will be raised
     * and a list of regions will be provided.</p>
     *
     * <p>If the player is not standing in any regions, the global region will
     * returned if allowGlobal is true and it exists.</p>
     *
     * @param regionManager the region manager
     * @param player the player
     * @param allowGlobal whether to search for a global region if no others are found
     * @return a region
     * @throws CommandException thrown if no region was found
     */
    protected static ProtectedRegion checkRegionStandingIn(RegionManager regionManager, LocalPlayer player, boolean allowGlobal) throws CommandException {
        ApplicableRegionSet set = regionManager.getApplicableRegions(player.getLocation().toVector());

        if (set.size() == 0) {
            if (allowGlobal) {
                ProtectedRegion global = checkExistingRegion(regionManager, "__global__", true);
                player.printDebug("Вы не состоите ни в одном из " +
                        "регионов. Используем глобальный регион.");
                return global;
            }
            throw new CommandException(
                    "Вы не состоите ни в одном из регионов." +
                            "Укажите ID региона если хотите указать какой-то конкретный регион.");
        } else if (set.size() > 1) {
            StringBuilder builder = new StringBuilder();
            boolean first = true;

            for (ProtectedRegion region : set) {
                if (!first) {
                    builder.append(", ");
                }
                first = false;
                builder.append(region.getId());
            }

            throw new CommandException(
                    "Вы находитесь в нескольких регионах (пожалуйста, выберите один).\nВы в: " + builder.toString());
        }

        return set.iterator().next();
    }

    /**
     * Get a WorldEdit selection for a player, or emit an exception if there is none
     * available.
     *
     * @param player the player
     * @return the selection
     * @throws CommandException thrown on an error
     */
    protected static Region checkSelection(Player player) throws CommandException {
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        try {
            return WorldEdit.getInstance().getSessionManager().get(localPlayer).getRegionSelector(localPlayer.getWorld()).getRegion();
        } catch (IncompleteRegionException e) {
            throw new CommandException(
                    "Вы не выделили область для привата региона.");
        }
    }

    /**
     * Check that a region with the given ID does not already exist.
     *
     * @param manager the manager
     * @param id the ID
     * @throws CommandException thrown if the ID already exists
     */
    protected static void checkRegionDoesNotExist(RegionManager manager, String id, boolean mayRedefine) throws CommandException {
        if (manager.hasRegion(id)) {
            throw new CommandException("Регион с таким именем уже существует. Пожалуйста, выберите другое имя." +
                    (mayRedefine ? " Для изменения позиции используйте /region redefine " + id + "." : ""));
        }
    }

    /**
     * Check that the given region manager is not null.
     *
     * @param plugin the plugin
     * @param world the world
     * @throws CommandException thrown if the manager is null
     */
    protected static RegionManager checkRegionManager(WorldGuardPlugin plugin, com.sk89q.worldedit.world.World world) throws CommandException {
        if (!WorldGuard.getInstance().getPlatform().getGlobalStateManager().get(world).useRegions) {
            throw new CommandException("Регионы отключены в данном мире. " +
                    "Они могут быть включены для каждого мира в конфигурационных файлах WorldGuard. " +
                    "Однако, возможно, вам придется перезагрузить сервер после этого.");
        }

        RegionManager manager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(world);
        if (manager == null) {
            throw new CommandException("Не удалось загрузить регион для данного мира. " +
                    "Пожалуйста, сообщите администратору.");
        }
        return manager;
    }

    /**
     * Create a {@link ProtectedRegion} from the player's selection.
     *
     * @param player the player
     * @param id the ID of the new region
     * @return a new region
     * @throws CommandException thrown on an error
     */
    protected static ProtectedRegion checkRegionFromSelection(Player player, String id) throws CommandException {
        Region selection = checkSelection(player);

        // Detect the type of region from WorldEdit
        if (selection instanceof Polygonal2DRegion) {
            Polygonal2DRegion polySel = (Polygonal2DRegion) selection;
            int minY = polySel.getMinimumPoint().getBlockY();
            int maxY = polySel.getMaximumPoint().getBlockY();
            return new ProtectedPolygonalRegion(id, polySel.getPoints(), minY, maxY);
        } else if (selection instanceof CuboidRegion) {
            BlockVector min = selection.getMinimumPoint().toBlockVector();
            BlockVector max = selection.getMaximumPoint().toBlockVector();
            return new ProtectedCuboidRegion(id, min, max);
        } else {
            throw new CommandException("Извините, только кубоиды и полигоны могут быть регионами в WorldGuard.");
        }
    }

    /**
     * Warn the region saving is failing.
     *
     * @param sender the sender to send the message to
     */
    protected static void warnAboutSaveFailures(CommandSender sender) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        Set<RegionManager> failures = container.getSaveFailures();

        if (failures.size() > 0) {
            String failingList = Joiner.on(", ").join(failures.stream().map(regionManager -> "'" + regionManager.getName() + "'").collect(Collectors.toList()));

            sender.sendMessage(ChatColor.GOLD +
                    "(Предупреждение: Не удается сохранить регион в этом мире: " + failingList + ". " +
                    "Пожалуйста, сообщите администратору.)");
        }
    }

    /**
     * Warn the sender if the dimensions of the given region are worrying.
     *
     * @param sender the sender to send the message to
     * @param region the region
     */
    protected static void warnAboutDimensions(Actor sender, ProtectedRegion region) {
        int height = region.getMaximumPoint().getBlockY() - region.getMinimumPoint().getBlockY();
        if (height <= 2) {
<<<<<<< HEAD
            sender.sendMessage(ChatColor.GRAY + "(Внимание: Высота региона составляет " + (height + 1) + " блок(ов).)");
=======
            sender.printDebug("(Warning: The height of the region was " + (height + 1) + " block(s).)");
>>>>>>> 8e819f7a823e29fca68fca5f88d575ee7663aa90
        }
    }

    /**
     * Inform a new user about automatic protection.
     *
     * @param sender the sender to send the message to
     * @param manager the region manager
     * @param region the region
     */
    protected static void informNewUser(CommandSender sender, RegionManager manager, ProtectedRegion region) {
        if (manager.getRegions().size() <= 2) {
            sender.sendMessage(ChatColor.GRAY +
                    "(Теперь этот регион защищен от изменения другими игроками. " +
                    "Не хотите этого? Используйте " +
                    ChatColor.AQUA + "/rg flag " + region.getId() + " passthrough allow" +
                    ChatColor.GRAY + ")");
        }
    }

    /**
     * Set a player's selection to a given region.
     *
     * @param player the player
     * @param region the region
     * @throws CommandException thrown on a command error
     */
    protected static void setPlayerSelection(Player player, ProtectedRegion region) throws CommandException {
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);

        LocalSession session = WorldEdit.getInstance().getSessionManager().get(localPlayer);

        // Set selection
        if (region instanceof ProtectedCuboidRegion) {
            ProtectedCuboidRegion cuboid = (ProtectedCuboidRegion) region;
            Vector pt1 = cuboid.getMinimumPoint();
            Vector pt2 = cuboid.getMaximumPoint();
<<<<<<< HEAD
            CuboidSelection selection = new CuboidSelection(world, pt1, pt2);
            worldEdit.setSelection(player, selection);
            player.sendMessage(ChatColor.YELLOW + "Регион выбран как кубоид.");
=======

            session.setRegionSelector(localPlayer.getWorld(), new CuboidRegionSelector(localPlayer.getWorld(), pt1, pt2));
            player.sendMessage(ChatColor.YELLOW + "Region selected as a cuboid.");
>>>>>>> 8e819f7a823e29fca68fca5f88d575ee7663aa90

        } else if (region instanceof ProtectedPolygonalRegion) {
            ProtectedPolygonalRegion poly2d = (ProtectedPolygonalRegion) region;
            Polygonal2DRegionSelector selector = new Polygonal2DRegionSelector(
                    localPlayer.getWorld(), poly2d.getPoints(),
                    poly2d.getMinimumPoint().getBlockY(),
                    poly2d.getMaximumPoint().getBlockY() );
<<<<<<< HEAD
            worldEdit.setSelection(player, selection);
            player.sendMessage(ChatColor.YELLOW + "Регион выбран как полигон.");
=======
            session.setRegionSelector(localPlayer.getWorld(), selector);
            player.sendMessage(ChatColor.YELLOW + "Region selected as a polygon.");
>>>>>>> 8e819f7a823e29fca68fca5f88d575ee7663aa90

        } else if (region instanceof GlobalProtectedRegion) {
            throw new CommandException(
                    "Нельзя выбрать глобальный регион. " +
                            "Это позволит охватить весь мир!");

        } else {
            throw new CommandException("Неизвестный тип региона: " +
                    region.getClass().getCanonicalName());
        }
    }

    /**
     * Utility method to set a flag.
     *
     * @param region the region
     * @param flag the flag
     * @param sender the sender
     * @param value the value
     * @throws InvalidFlagFormat thrown if the value is invalid
     */
    protected static <V> void setFlag(ProtectedRegion region, Flag<V> flag, Actor sender, String value) throws InvalidFlagFormat {
        region.setFlag(flag, flag.parseInput(FlagContext.create().setSender(sender).setInput(value).setObject("region", region).build()));
    }

}
