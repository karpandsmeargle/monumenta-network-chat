package com.playmonumenta.networkchat.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

import com.google.gson.JsonElement;
import com.playmonumenta.networkchat.NetworkChatPlugin;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.RemotePlayerManager;

import me.clip.placeholderapi.PlaceholderAPI;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.Template;
import net.kyori.adventure.text.minimessage.transformation.TransformationType;
import net.kyori.adventure.text.minimessage.markdown.DiscordFlavor;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainComponentSerializer;

public class MessagingUtils {
	public static final GsonComponentSerializer GSON_SERIALIZER = GsonComponentSerializer.gson();
	public static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
	public static final PlainComponentSerializer PLAIN_SERIALIZER = PlainComponentSerializer.plain();
	private static final MiniMessage SENDER_FMT_MINIMESSAGE = MiniMessage.builder()
	    .removeDefaultTransformations()
	    .markdownFlavor(DiscordFlavor.get())
	    .transformation(TransformationType.COLOR)
	    .transformation(TransformationType.DECORATION)
	    .transformation(TransformationType.HOVER_EVENT)
	    .transformation(TransformationType.CLICK_EVENT)
	    .transformation(TransformationType.KEYBIND)
	    .transformation(TransformationType.TRANSLATABLE)
	    .transformation(TransformationType.INSERTION)
	    .transformation(TransformationType.FONT)
	    .transformation(TransformationType.GRADIENT)
	    .transformation(TransformationType.RAINBOW)
	    .transformation(TransformationType.RESET)
	    .build();

	public static String translatePlayerName(Player player, String message) {
		return (message.replaceAll("@S", player.getName()));
	}

	public static Component senderComponent(CommandSender sender) {
		if (sender instanceof Entity) {
			return entityComponent((Entity) sender);
		}
		return SENDER_FMT_MINIMESSAGE.parse(PlaceholderAPI.setPlaceholders(null, NetworkChatPlugin.messageFormat("sender")),
		    List.of(Template.of("sender_name", sender.getName())));
	}

	public static Component entityComponent(Entity entity) {
		return entityComponent(entity.getType().getKey(), entity.getUniqueId(), entity.customName());
	}

	public static Component entityComponent(NamespacedKey type, UUID id, Component name) {
		if (type.toString().equals("minecraft:player")) {
			return RemotePlayerManager.getPlayerComponent(id);
		}

		Component entityName = name;
		if (name == null) {
			entityName = Component.translatable("entity." + type.toString().replace(":", "."));
		}

		Team entityTeam = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(id.toString());
		TextColor color;
		Component teamPrefix;
		Component teamDisplayName;
		Component teamSuffix;
		if (entityTeam == null) {
			color = null;
			teamPrefix = Component.empty();
			teamDisplayName = Component.empty();
			teamSuffix = Component.empty();
		} else {
			try {
				color = entityTeam.color();
				teamPrefix = entityTeam.prefix();
				teamDisplayName = entityTeam.displayName();
				teamSuffix = entityTeam.suffix();
			} catch (Exception e) {
				color = null;
				teamPrefix = Component.empty();
				teamDisplayName = Component.translatable("minecraft:chat.square_brackets", Component.text(entityTeam.getName()));
				teamSuffix = Component.empty();
			}
		}

		return SENDER_FMT_MINIMESSAGE.parse(PlaceholderAPI.setPlaceholders(null, NetworkChatPlugin.messageFormat("entity")),
		    List.of(Template.of("entity_type", type.toString()),
		        Template.of("entity_uuid", (id == null) ? "" : id.toString()),
		        Template.of("entity_name", entityName),
		        Template.of("team_color", (color == null) ? "" : "<" + color.asHexString() + ">"),
		        Template.of("team_prefix", teamPrefix),
		        Template.of("team_displayname", teamDisplayName),
		        Template.of("team_suffix", teamSuffix)));
	}

	public static Component playerComponent(Player player) {
		Team playerTeam = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(player.getName());
		TextColor color;
		String colorMiniMessage = "";
		Component teamPrefix;
		Component teamDisplayName;
		Component teamSuffix;
		if (playerTeam == null) {
			color = null;
			teamPrefix = Component.empty();
			teamDisplayName = Component.empty();
			teamSuffix = Component.empty();
		} else {
			try {
				color = playerTeam.color();
				if (color != null) {
					colorMiniMessage = "<" + color.asHexString() + ">";
				}
				teamPrefix = playerTeam.prefix();
				teamDisplayName = playerTeam.displayName();
				teamSuffix = playerTeam.suffix();
			} catch (Exception e) {
				color = null;
				teamPrefix = Component.empty();
				teamDisplayName = Component.translatable("chat.square_brackets", Component.text(playerTeam.getName()));
				teamSuffix = Component.empty();
			}
		}

		Component profileMessage = PlayerStateManager.getPlayerState(player).profileMessageComponent();

		return SENDER_FMT_MINIMESSAGE.parse(PlaceholderAPI.setPlaceholders(player, NetworkChatPlugin.messageFormat("player")),
		    List.of(Template.of("team_color", colorMiniMessage),
		        Template.of("team_prefix", teamPrefix),
		        Template.of("team_displayname", teamDisplayName),
		        Template.of("team_suffix", teamSuffix),
		        Template.of("profile_message", profileMessage)));
	}

	public static void sendStackTrace(CommandSender sender, Exception e) {
		TextComponent formattedMessage;
		String errorMessage = e.getLocalizedMessage();
		if (errorMessage != null) {
			formattedMessage = LEGACY_SERIALIZER.deserialize(errorMessage);
		} else {
			formattedMessage = Component.text("An error occured without a set message. Hover for stack trace.");
		}
		formattedMessage.color(NamedTextColor.RED);

		// Get the first 300 characters of the stacktrace and send them to the player
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		String sStackTrace = sw.toString();
		sStackTrace = sStackTrace.substring(0, Math.min(sStackTrace.length(), 300));

		TextComponent textStackTrace = Component.text(sStackTrace.replace("\t", "  "), NamedTextColor.RED);
		formattedMessage.hoverEvent(textStackTrace);
		sender.sendMessage(formattedMessage);

		e.printStackTrace();
	}

	public static Component fromJson(JsonElement json) {
		return GSON_SERIALIZER.deserializeFromTree(json);
	}

	public static JsonElement toJson(Component component) {
		return GSON_SERIALIZER.serializeToTree(component);
	}

	public static String plainText(Component formattedText) {
		String legacyText = PLAIN_SERIALIZER.serialize(formattedText);
		return PLAIN_SERIALIZER.serialize(LEGACY_SERIALIZER.deserialize(legacyText));
	}

	public static TextColor colorFromString(String value) {
		if (value.startsWith("#")) {
			return TextColor.fromHexString(value);
		} else {
			return NamedTextColor.NAMES.value(value);
		}
	}

	public static String colorToString(TextColor color) {
		if (color instanceof NamedTextColor) {
			return color.toString();
		}
		return color.asHexString();
	}

	public static String colorToMiniMessage(TextColor color) {
		return "<" + colorToString(color) + ">";
	}
}