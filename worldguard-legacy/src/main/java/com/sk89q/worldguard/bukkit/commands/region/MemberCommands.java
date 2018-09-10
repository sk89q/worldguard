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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.sk89q.minecraft.util.commands.Command;
import com.sk89q.minecraft.util.commands.CommandContext;
import com.sk89q.minecraft.util.commands.CommandException;
import com.sk89q.minecraft.util.commands.CommandPermissionsException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.BukkitWorldConfiguration;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.bukkit.commands.AsyncCommandHelper;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.util.DomainInputResolver;
import com.sk89q.worldguard.protection.util.DomainInputResolver.UserLocatorPolicy;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MemberCommands extends RegionCommandsBase {

    private final WorldGuardPlugin plugin;

    public MemberCommands(WorldGuardPlugin plugin) {
        this.plugin = plugin;
    }

    @Command(aliases = {"addmember", "addmember", "addmem", "am"},
            usage = "<id> <игроки...>",
            flags = "nw:",
            desc = "Добавить участника в регион",
            min = 2)
    public void addMember(CommandContext args, CommandSender sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        String id = args.getString(0);
        RegionManager manager = checkRegionManager(plugin, BukkitAdapter.adapt(world));
        ProtectedRegion region = checkExistingRegion(manager, id, true);

        id = region.getId();

        // Check permissions
        if (!getPermissionModel(plugin.wrapCommandSender(sender)).mayAddMembers(region)) {
            throw new CommandPermissionsException();
        }

        // Resolve members asynchronously
        DomainInputResolver resolver = new DomainInputResolver(
                WorldGuard.getInstance().getProfileService(), args.getParsedPaddedSlice(1, 0));
        resolver.setLocatorPolicy(args.hasFlag('n') ? UserLocatorPolicy.NAME_ONLY : UserLocatorPolicy.UUID_ONLY);

        // Then add it to the members
        ListenableFuture<DefaultDomain> future = Futures.transform(
                WorldGuard.getInstance().getExecutorService().submit(resolver),
                resolver.createAddAllFunction(region.getMembers()));

        AsyncCommandHelper.wrap(future, plugin, sender)
                .formatUsing(region.getId(), world.getName())
                .registerWithSupervisor("Добавление участника в регион '%s' на '%s'")
                .sendMessageAfterDelay("(Пожалуйста, подождите...)")
                .thenRespondWith("В регион '%s' добавлен новый участник.", "Не удалось добавить нового участника");
    }

    @Command(aliases = {"addowner", "addowner", "ao"},
            usage = "<id> <игроки...>",
            flags = "nw:",
            desc = "Добавить владельца в регион",
            min = 2)
    public void addOwner(CommandContext args, CommandSender sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(world);

        Player player = null;
        LocalPlayer localPlayer = null;
        if (sender instanceof Player) {
            player = (Player) sender;
            localPlayer = plugin.wrapPlayer(player);
        }

        String id = args.getString(0);

        RegionManager manager = checkRegionManager(plugin, weWorld);
        ProtectedRegion region = checkExistingRegion(manager, id, true);

        id = region.getId();

        Boolean flag = region.getFlag(Flags.BUYABLE);
        DefaultDomain owners = region.getOwners();

        if (localPlayer != null) {
            if (flag != null && flag && owners != null && owners.size() == 0) {
                // TODO: Move this to an event
                if (!plugin.hasPermission(player, "worldguard.region.unlimited")) {
                    int maxRegionCount = ((BukkitWorldConfiguration) WorldGuard.getInstance().getPlatform().getGlobalStateManager().get(weWorld)).getMaxRegionCount(player);
                    if (maxRegionCount >= 0 && manager.getRegionCountOfPlayer(localPlayer)
                            >= maxRegionCount) {
                        throw new CommandException("Вы владеете максимально допустимым количеством регионов.");
                    }
                }
                plugin.checkPermission(sender, "worldguard.region.addowner.unclaimed." + id.toLowerCase());
            } else {
                // Check permissions
                if (!getPermissionModel(localPlayer).mayAddOwners(region)) {
                    throw new CommandPermissionsException();
                }
            }
        }

        // Resolve owners asynchronously
        DomainInputResolver resolver = new DomainInputResolver(
                WorldGuard.getInstance().getProfileService(), args.getParsedPaddedSlice(1, 0));
        resolver.setLocatorPolicy(args.hasFlag('n') ? UserLocatorPolicy.NAME_ONLY : UserLocatorPolicy.UUID_ONLY);

        // Then add it to the owners
        ListenableFuture<DefaultDomain> future = Futures.transform(
                WorldGuard.getInstance().getExecutorService().submit(resolver),
                resolver.createAddAllFunction(region.getOwners()));

        AsyncCommandHelper.wrap(future, plugin, sender)
                .formatUsing(region.getId(), world.getName())
                .registerWithSupervisor("Добавление владельца в регион '%s' на '%s'")
                .sendMessageAfterDelay("(Пожалуйста, подождите...)")
                .thenRespondWith("В регион '%s' добавлен новый владелец.", "Не удалось добавить нового владельца");
    }

    @Command(aliases = {"removemember", "remmember", "removemem", "remmem", "rm"},
            usage = "<id> <игроки...>",
            flags = "naw:",
            desc = "Удалить участника из региона",
            min = 1)
    public void removeMember(CommandContext args, CommandSender sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        String id = args.getString(0);
        RegionManager manager = checkRegionManager(plugin, BukkitAdapter.adapt(world));
        ProtectedRegion region = checkExistingRegion(manager, id, true);

        // Check permissions
        if (!getPermissionModel(plugin.wrapCommandSender(sender)).mayRemoveMembers(region)) {
            throw new CommandPermissionsException();
        }

        ListenableFuture<?> future;

        if (args.hasFlag('a')) {
            region.getMembers().removeAll();

            future = Futures.immediateFuture(null);
        } else {
            if (args.argsLength() < 2) {
                throw new CommandException("Перечислите имена игроков, чтобы удалить или используйте -a, чтобы удалить всех.");
            }

            // Resolve members asynchronously
            DomainInputResolver resolver = new DomainInputResolver(
                    WorldGuard.getInstance().getProfileService(), args.getParsedPaddedSlice(1, 0));
            resolver.setLocatorPolicy(args.hasFlag('n') ? UserLocatorPolicy.NAME_ONLY : UserLocatorPolicy.UUID_AND_NAME);

            // Then remove it from the members
            future = Futures.transform(
                    WorldGuard.getInstance().getExecutorService().submit(resolver),
                    resolver.createRemoveAllFunction(region.getMembers()));
        }

        AsyncCommandHelper.wrap(future, plugin, sender)
                .formatUsing(region.getId(), world.getName())
                .registerWithSupervisor("Удаление участника из региона '%s' на '%s'")
                .sendMessageAfterDelay("(Пожалуйста, подождите...)")
                .thenRespondWith("Из региона '%s' удален участник.", "Не удалось удалить участника");
    }

    @Command(aliases = {"removeowner", "remowner", "ro"},
            usage = "<id> <игроки...>",
            flags = "naw:",
            desc = "Удалить владельца из региона",
            min = 1)
    public void removeOwner(CommandContext args, CommandSender sender) throws CommandException {
        warnAboutSaveFailures(sender);

        World world = checkWorld(args, sender, 'w'); // Get the world
        String id = args.getString(0);
        RegionManager manager = checkRegionManager(plugin, BukkitAdapter.adapt(world));
        ProtectedRegion region = checkExistingRegion(manager, id, true);

        // Check permissions
        if (!getPermissionModel(plugin.wrapCommandSender(sender)).mayRemoveOwners(region)) {
            throw new CommandPermissionsException();
        }

        ListenableFuture<?> future;

        if (args.hasFlag('a')) {
            region.getOwners().removeAll();

            future = Futures.immediateFuture(null);
        } else {
            if (args.argsLength() < 2) {
                throw new CommandException("Перечислите имена игроков, чтобы удалить или используйте -a, чтобы удалить всех.");
            }

            // Resolve owners asynchronously
            DomainInputResolver resolver = new DomainInputResolver(
                    WorldGuard.getInstance().getProfileService(), args.getParsedPaddedSlice(1, 0));
            resolver.setLocatorPolicy(args.hasFlag('n') ? UserLocatorPolicy.NAME_ONLY : UserLocatorPolicy.UUID_AND_NAME);

            // Then remove it from the owners
            future = Futures.transform(
                    WorldGuard.getInstance().getExecutorService().submit(resolver),
                    resolver.createRemoveAllFunction(region.getOwners()));
        }

        AsyncCommandHelper.wrap(future, plugin, sender)
                .formatUsing(region.getId(), world.getName())
                .registerWithSupervisor("Удаление владельца из региона '%s' на '%s'")
                .sendMessageAfterDelay("(Пожалуйста, подождите...)")
                .thenRespondWith("Из региона '%s' удален владелец.", "Не удалось удалить владельца");
    }
}
