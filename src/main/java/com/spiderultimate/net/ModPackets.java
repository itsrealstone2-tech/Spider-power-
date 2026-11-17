package com.spiderultimate.net;

import com.spiderultimate.common.PoseData;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * All network payloads. Client to server packets are only ever treated as requests: the server re-checks gear,
 * energy, cooldowns, range and line of sight. Server to client packets are state broadcasts.
 */
public final class ModPackets {
	private ModPackets() {
	}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String path) {
		return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("spiderultimate", path));
	}

	// ============================================================== client -> server

	/** Fire the web shooter. (tx,ty,tz) is where the client's ray hit; entity is the hit entity id or -1. */
	public record WebFire(int mode, boolean left, int slot, double ox, double oy, double oz,
						  double tx, double ty, double tz, int entity) implements CustomPacketPayload {
		public static final Type<WebFire> TYPE = typeOf("web_fire");
		public static final StreamCodec<RegistryFriendlyByteBuf, WebFire> CODEC = StreamCodec.of((b, p) -> p.write(b), WebFire::read);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		void write(FriendlyByteBuf b) {
			b.writeByte(mode);
			b.writeBoolean(left);
			b.writeByte(slot);
			b.writeDouble(ox);
			b.writeDouble(oy);
			b.writeDouble(oz);
			b.writeDouble(tx);
			b.writeDouble(ty);
			b.writeDouble(tz);
			b.writeInt(entity);
		}

		static WebFire read(FriendlyByteBuf b) {
			return new WebFire(b.readByte(), b.readBoolean(), b.readByte(), b.readDouble(), b.readDouble(), b.readDouble(),
				b.readDouble(), b.readDouble(), b.readDouble(), b.readInt());
		}
	}

	/** Release the ropes in the slot mask (bit 0 = slot 0, bit 1 = slot 1). */
	public record Release(int slotMask, boolean jump) implements CustomPacketPayload {
		public static final Type<Release> TYPE = typeOf("release");
		public static final StreamCodec<RegistryFriendlyByteBuf, Release> CODEC = StreamCodec.of((b, p) -> p.write(b), Release::read);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		void write(FriendlyByteBuf b) {
			b.writeByte(slotMask);
			b.writeBoolean(jump);
		}

		static Release read(FriendlyByteBuf b) {
			return new Release(b.readByte(), b.readBoolean());
		}
	}

	/** Use a special ability. (dx,dy,dz) is the aim direction, (ax,ay,az) an optional target point. */
	public record AbilityUse(int ability, double dx, double dy, double dz,
							 double ax, double ay, double az) implements CustomPacketPayload {
		public static final Type<AbilityUse> TYPE = typeOf("ability_use");
		public static final StreamCodec<RegistryFriendlyByteBuf, AbilityUse> CODEC = StreamCodec.of((b, p) -> p.write(b), AbilityUse::read);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		void write(FriendlyByteBuf b) {
			b.writeByte(ability);
			b.writeDouble(dx);
			b.writeDouble(dy);
			b.writeDouble(dz);
			b.writeDouble(ax);
			b.writeDouble(ay);
			b.writeDouble(az);
		}

		static AbilityUse read(FriendlyByteBuf b) {
			return new AbilityUse(b.readByte(), b.readDouble(), b.readDouble(), b.readDouble(),
				b.readDouble(), b.readDouble(), b.readDouble());
		}
	}

	/** The owning client's cosmetic movement state (crawl normal, glide velocity, flags ...). */
	public record PoseUpdate(PoseData pose) implements CustomPacketPayload {
		public static final Type<PoseUpdate> TYPE = typeOf("pose_update");
		public static final StreamCodec<RegistryFriendlyByteBuf, PoseUpdate> CODEC =
			StreamCodec.of((b, p) -> p.pose.write(b), b -> new PoseUpdate(PoseData.read(b)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	// ============================================================== server -> client

	/** Another (or the own) player's movement state plus the anchors the server validated. */
	public record PoseBroadcast(int entityId, PoseData pose) implements CustomPacketPayload {
		public static final Type<PoseBroadcast> TYPE = typeOf("pose_broadcast");
		public static final StreamCodec<RegistryFriendlyByteBuf, PoseBroadcast> CODEC =
			StreamCodec.of((b, p) -> {
				b.writeInt(p.entityId);
				p.pose.write(b);
			}, b -> new PoseBroadcast(b.readInt(), PoseData.read(b)));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** A web was shot: lets everybody animate the arm and draw the flying web. */
	public record WebShot(int entityId, boolean left, int mode, double tx, double ty, double tz) implements CustomPacketPayload {
		public static final Type<WebShot> TYPE = typeOf("web_shot");
		public static final StreamCodec<RegistryFriendlyByteBuf, WebShot> CODEC = StreamCodec.of((b, p) -> p.write(b), WebShot::read);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		void write(FriendlyByteBuf b) {
			b.writeInt(entityId);
			b.writeBoolean(left);
			b.writeByte(mode);
			b.writeDouble(tx);
			b.writeDouble(ty);
			b.writeDouble(tz);
		}

		static WebShot read(FriendlyByteBuf b) {
			return new WebShot(b.readInt(), b.readBoolean(), b.readByte(), b.readDouble(), b.readDouble(), b.readDouble());
		}
	}

	/** Authoritative energy and cooldowns of the receiving player. cooldowns[0] = web, [1..] = abilities. */
	public record Stats(float energy, float maxEnergy, int[] cooldowns) implements CustomPacketPayload {
		public static final Type<Stats> TYPE = typeOf("stats");
		public static final StreamCodec<RegistryFriendlyByteBuf, Stats> CODEC = StreamCodec.of((b, p) -> p.write(b), Stats::read);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}

		void write(FriendlyByteBuf b) {
			b.writeFloat(energy);
			b.writeFloat(maxEnergy);
			b.writeByte(cooldowns.length);
			for (int c : cooldowns) {
				b.writeVarInt(c);
			}
		}

		static Stats read(FriendlyByteBuf b) {
			float e = b.readFloat();
			float m = b.readFloat();
			int n = Math.max(0, Math.min(32, b.readByte()));
			int[] cds = new int[n];
			for (int i = 0; i < n; i++) {
				cds[i] = b.readVarInt();
			}
			return new Stats(e, m, cds);
		}
	}

	/** The server refused an action. The client releases the given rope slots. */
	public record Deny(int reason, int slotMask) implements CustomPacketPayload {
		public static final int NO_GEAR = 0;
		public static final int NO_ENERGY = 1;
		public static final int COOLDOWN = 2;
		public static final int INVALID_TARGET = 3;
		public static final int TOO_FAST = 4;
		public static final int DISABLED = 5;

		public static final Type<Deny> TYPE = typeOf("deny");
		public static final StreamCodec<RegistryFriendlyByteBuf, Deny> CODEC = StreamCodec.of((b, p) -> {
			b.writeByte(p.reason);
			b.writeByte(p.slotMask);
		}, b -> new Deny(b.readByte(), b.readByte()));

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Registers every payload type for both directions (must run on client and server). */
	public static void register() {
		PayloadTypeRegistry.playC2S().register(WebFire.TYPE, WebFire.CODEC);
		PayloadTypeRegistry.playC2S().register(Release.TYPE, Release.CODEC);
		PayloadTypeRegistry.playC2S().register(AbilityUse.TYPE, AbilityUse.CODEC);
		PayloadTypeRegistry.playC2S().register(PoseUpdate.TYPE, PoseUpdate.CODEC);
		PayloadTypeRegistry.playS2C().register(PoseBroadcast.TYPE, PoseBroadcast.CODEC);
		PayloadTypeRegistry.playS2C().register(WebShot.TYPE, WebShot.CODEC);
		PayloadTypeRegistry.playS2C().register(Stats.TYPE, Stats.CODEC);
		PayloadTypeRegistry.playS2C().register(Deny.TYPE, Deny.CODEC);
	}
}
