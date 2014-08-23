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

package com.sk89q.worldguard.bukkit.listener;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.sk89q.worldguard.bukkit.util.Events;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;

import java.util.concurrent.TimeUnit;

class BlockEntityEventDebounce {

    private final Cache<Key, Entry> cache;

    BlockEntityEventDebounce(int debounceTime) {
        cache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(debounceTime, TimeUnit.MILLISECONDS)
                .concurrencyLevel(2)
                .build(new CacheLoader<Key, Entry>() {
                    @Override
                    public Entry load(Key key) throws Exception {
                        return new Entry();
                    }
                });
    }

    public <T extends Event & Cancellable> void debounce(Block block, Entity entity, Cancellable originalEvent, T firedEvent) {
        Key key = new Key(block, entity);
        Entry entry = cache.getUnchecked(key);
        if (entry.cancelled != null) {
            if (entry.cancelled) {
                originalEvent.setCancelled(true);
            }
        } else {
            boolean cancelled = Events.fireAndTestCancel(firedEvent);
            if (cancelled) {
                originalEvent.setCancelled(true);
            }
            entry.cancelled = cancelled;
        }
    }

    private static class Key {
        private final Block block;
        private final Material blockMaterial;
        private final Entity entity;

        private Key(Block block, Entity entity) {
            this.block = block;
            this.blockMaterial = block.getType();
            this.entity = entity;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Key key = (Key) o;

            if (!block.equals(key.block)) return false;
            if (blockMaterial != key.blockMaterial) return false;
            if (!entity.equals(key.entity)) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = block.hashCode();
            result = 31 * result + blockMaterial.hashCode();
            result = 31 * result + entity.hashCode();
            return result;
        }
    }

    private static class Entry {
        private Boolean cancelled;
    }

}
