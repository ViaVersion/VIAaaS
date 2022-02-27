/*
 * Copyright (c) RK_01  2021.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT
 * OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * ===================================
 *
 * Copyright (c) VIAaaS contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.viaversion.aas.protocol.sharewareto1_14;

import com.viaversion.viaversion.api.protocol.packet.ClientboundPacketType;

public enum ClientboundPacketsShareware implements ClientboundPacketType {
	SPAWN_ENTITY, // 0x00
	SPAWN_EXPERIENCE_ORB, // 0x01
	SPAWN_GLOBAL_ENTITY, // 0x02
	SPAWN_MOB, // 0x03
	SPAWN_PAINTING, // 0x04
	SPAWN_PLAYER, // 0x05
	ENTITY_ANIMATION, // 0x06
	STATISTICS, // 0x07
	BLOCK_BREAK_ANIMATION, // 0x08
	BLOCK_ENTITY_DATA, // 0x09
	BLOCK_ACTION, // 0x0A
	BLOCK_CHANGE, // 0x0B
	BOSSBAR, // 0x0C
	SERVER_DIFFICULTY, // 0x0D
	CHAT_MESSAGE, // 0x0E
	MULTI_BLOCK_CHANGE, // 0x0F
	TAB_COMPLETE, // 0x10
	DECLARE_COMMANDS, // 0x11
	WINDOW_CONFIRMATION, // 0x12
	CLOSE_WINDOW, // 0x13
	OPEN_HORSE_WINDOW, // 0x14
	WINDOW_ITEMS, // 0x15
	WINDOW_PROPERTY, // 0x16
	SET_SLOT, // 0x17
	COOLDOWN, // 0x18
	PLUGIN_MESSAGE, // 0x19
	NAMED_SOUND, // 0x1A
	DISCONNECT, // 0x1B
	ENTITY_STATUS, // 0x1C
	NBT_QUERY, // 0x1D
	EXPLOSION, // 0x1E
	UNLOAD_CHUNK, // 0x1F
	GAME_EVENT, // 0x20
	KEEP_ALIVE, // 0x21
	CHUNK_DATA, // 0x22
	EFFECT, // 0x23
	SPAWN_PARTICLE, // 0x24
	JOIN_GAME, // 0x25
	MAP_DATA, // 0x26
	ENTITY_MOVEMENT, // 0x27
	ENTITY_POSITION, // 0x28
	ENTITY_POSITION_AND_ROTATION, // 0x29
	ENTITY_ROTATION, // 0x2A
	VEHICLE_MOVE, // 0x2B
	OPEN_BOOK, // 0x2C
	OPEN_SIGN_EDITOR, // 0x2D
	CRAFT_RECIPE_RESPONSE, // 0x2E
	PLAYER_ABILITIES, // 0x2F
	COMBAT_EVENT, // 0x30
	PLAYER_INFO, // 0x31
	FACE_PLAYER, // 0x32
	PLAYER_POSITION, // 0x33
	UNLOCK_RECIPES, // 0x34
	DESTROY_ENTITIES, // 0x35
	REMOVE_ENTITY_EFFECT, // 0x36
	RESOURCE_PACK, // 0x37
	RESPAWN, // 0x38
	ENTITY_HEAD_LOOK, // 0x39
	SELECT_ADVANCEMENTS_TAB, // 0x3A
	WORLD_BORDER, // 0x3B
	CAMERA, // 0x3C
	HELD_ITEM_CHANGE, // 0x3D
	DISPLAY_SCOREBOARD, // 0x3E
	ENTITY_METADATA, // 0x3F
	ATTACH_ENTITY, // 0x40
	ENTITY_VELOCITY, // 0x41
	ENTITY_EQUIPMENT, // 0x42
	SET_EXPERIENCE, // 0x43
	UPDATE_HEALTH, // 0x44
	SCOREBOARD_OBJECTIVE, // 0x45
	SET_PASSENGERS, // 0x46
	TEAMS, // 0x47
	UPDATE_SCORE, // 0x48
	SPAWN_POSITION, // 0x49
	TIME_UPDATE, // 0x4A
	TITLE, // 0x4B
	STOP_SOUND, // 0x4C
	SOUND, // 0x4D
	ENTITY_SOUND, // 0x4E
	TAB_LIST, // 0x4F
	COLLECT_ITEM, // 0x50
	ENTITY_TELEPORT, // 0x51
	ADVANCEMENTS, // 0x52
	ENTITY_PROPERTIES, // 0x53
	ENTITY_EFFECT, // 0x54
	DECLARE_RECIPES, // 0x55
	TAGS, // 0x56
	UPDATE_LIGHT, // 0x57
	OPEN_WINDOW, // 0x58
	TRADE_LIST, // 0x59
	UPDATE_VIEW_DISTANCE; // 0x5A

	@Override
	public int getId() {
		return this.ordinal();
	}

	@Override
	public String getName() {
		return this.name();
	}
}