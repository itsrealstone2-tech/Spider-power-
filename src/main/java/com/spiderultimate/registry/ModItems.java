package com.spiderultimate.registry;

import com.spiderultimate.config.SpiderConfig;
import com.spiderultimate.item.WebShooterItem;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;

import java.util.Map;
import java.util.function.Function;

/** Items, armor material and the creative tab. */
public final class ModItems {
	public static final String MOD_ID = "spiderultimate";
	private static final int BASE_DURABILITY = 22;

	public static final ResourceKey<EquipmentAsset> SUIT_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID,
		Identifier.fromNamespaceAndPath(MOD_ID, "spider_suit"));
	public static final TagKey<Item> REPAIRS_SUIT = TagKey.create(BuiltInRegistries.ITEM.key(),
		Identifier.fromNamespaceAndPath(MOD_ID, "repairs_spider_suit"));

	/** Built after the config is loaded, so the protection values are configurable. */
	public static final ArmorMaterial SUIT_MATERIAL = new ArmorMaterial(
		BASE_DURABILITY,
		Map.of(
			ArmorType.HELMET, (int) Math.round(SpiderConfig.get().suitHelmetArmor),
			ArmorType.CHESTPLATE, (int) Math.round(SpiderConfig.get().suitChestplateArmor),
			ArmorType.LEGGINGS, (int) Math.round(SpiderConfig.get().suitLeggingsArmor),
			ArmorType.BOOTS, (int) Math.round(SpiderConfig.get().suitBootsArmor)
		),
		14,
		SoundEvents.ARMOR_EQUIP_LEATHER,
		(float) SpiderConfig.get().suitToughness,
		0.0F,
		REPAIRS_SUIT,
		SUIT_ASSET
	);

	public static final Item WEB_SHOOTER = register("web_shooter", WebShooterItem::new,
		new Item.Properties().stacksTo(1));

	public static final Item SPIDER_SUIT_HELMET = register("spider_suit_helmet", Item::new,
		new Item.Properties().humanoidArmor(SUIT_MATERIAL, ArmorType.HELMET)
			.durability(ArmorType.HELMET.getDurability(BASE_DURABILITY)));
	public static final Item SPIDER_SUIT_CHESTPLATE = register("spider_suit_chestplate", Item::new,
		new Item.Properties().humanoidArmor(SUIT_MATERIAL, ArmorType.CHESTPLATE)
			.durability(ArmorType.CHESTPLATE.getDurability(BASE_DURABILITY)));
	public static final Item SPIDER_SUIT_LEGGINGS = register("spider_suit_leggings", Item::new,
		new Item.Properties().humanoidArmor(SUIT_MATERIAL, ArmorType.LEGGINGS)
			.durability(ArmorType.LEGGINGS.getDurability(BASE_DURABILITY)));
	public static final Item SPIDER_SUIT_BOOTS = register("spider_suit_boots", Item::new,
		new Item.Properties().humanoidArmor(SUIT_MATERIAL, ArmorType.BOOTS)
			.durability(ArmorType.BOOTS.getDurability(BASE_DURABILITY)));

	public static final ResourceKey<CreativeModeTab> TAB_KEY = ResourceKey.create(
		BuiltInRegistries.CREATIVE_MODE_TAB.key(), Identifier.fromNamespaceAndPath(MOD_ID, "spider_ultimate"));

	public static final CreativeModeTab TAB = FabricItemGroup.builder()
		.icon(() -> new ItemStack(ModItems.WEB_SHOOTER))
		.title(Component.translatable("itemGroup.spiderultimate"))
		.displayItems((params, output) -> {
			output.accept(ModItems.WEB_SHOOTER);
			output.accept(ModItems.SPIDER_SUIT_HELMET);
			output.accept(ModItems.SPIDER_SUIT_CHESTPLATE);
			output.accept(ModItems.SPIDER_SUIT_LEGGINGS);
			output.accept(ModItems.SPIDER_SUIT_BOOTS);
		})
		.build();

	private ModItems() {
	}

	private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties props) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(MOD_ID, name));
		T item = factory.apply(props.setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, item);
		return item;
	}

	public static void init() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB_KEY, TAB);
	}
}
