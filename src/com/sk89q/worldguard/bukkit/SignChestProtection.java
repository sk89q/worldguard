// $Id$
/*
 * WorldGuard
 * Copyright (C) 2010 sk89q <http://www.sk89q.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.sk89q.worldguard.bukkit;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;

/**
 * Utility class for sign chest protection.
 * 
 * @author sk89q
 */
public class SignChestProtection {
    
    public boolean isProtected(Block block, Player player) {
        if (isChest(block.getType())) {
            Block below = block.getRelative(0, -1, 0);
            return isProtectedSignAround(below, player);
        } else if (block.getType() == Material.SIGN_POST) {
            return isProtectedSignAndChestBinary(block, player);
        } else {
            Block above = block.getRelative(0, 1, 0);
            Boolean res = isProtectedSign(above, player);
            if (res != null) return res;
            return false;
        }
    }
    
    private boolean isProtectedSignAround(Block searchBlock, Player player) {
        Block side;
        Boolean res;
        
        side = searchBlock;
        res = isProtectedSign(side, player);
        if (res != null) return res;
        
        side = searchBlock.getRelative(-1, 0, 0);
        res = isProtectedSignAndChest(side, player);
        if (res != null) return res;
        
        side = searchBlock.getRelative(1, 0, 0);
        res = isProtectedSignAndChest(side, player);
        if (res != null) return res;
        
        side = searchBlock.getRelative(0, 0, -1);
        res = isProtectedSignAndChest(side, player);
        if (res != null) return res;
        
        side = searchBlock.getRelative(0, 0, 1);
        res = isProtectedSignAndChest(side, player);
        if (res != null) return res;
        
        return false;
    }
    
    private Boolean isProtectedSign(Sign sign, Player player) {
        if (sign.getLine(0).equalsIgnoreCase("[Lock]")) {
            if (player == null) { // No player, no access
                return true;
            }
            
            String name = player.getName();
            if (name.equalsIgnoreCase(sign.getLine(1).trim())
                    || name.equalsIgnoreCase(sign.getLine(2).trim())
                    || name.equalsIgnoreCase(sign.getLine(3).trim())) {
                return false;
            }
            
            // No access!
            return true;
        }
        
        return null;
    }
    
    private Boolean isProtectedSign(Block block, Player player) {
        BlockState state = block.getState();
        if (state == null || !(state instanceof Sign)) {
            return null;
        }
        return isProtectedSign((Sign) state, player);
    }
    
    private Boolean isProtectedSignAndChest(Block block, Player player) {
        if (!isChest(block.getRelative(0, 1, 0).getType())) {
            return null;
        }
        return isProtectedSign(block, player);
    }
    
    private boolean isProtectedSignAndChestBinary(Block block, Player player) {
        Boolean res = isProtectedSignAndChest(block, player);
        if (res == null || res == false) {
            return false;
        }
        return true;
    }
    
    private boolean isChest(Material material) {
        return material == Material.CHEST
                || material == Material.DISPENSER
                || material == Material.FURNACE
                || material == Material.BURNING_FURNACE;
    }
}
