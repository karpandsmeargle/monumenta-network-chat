package com.playmonumenta.networkchat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MMLog;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.BooleanArgument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// A channel for server announcements
public class ChannelAnnouncement extends Channel implements ChannelPermissionNode {
	public static final String CHANNEL_CLASS_ID = "announcement";

	private final UUID mId;
	private Instant mLastUpdate;
	private String mName;
	private @Nullable TextColor mMessageColor = null;
	private ChannelSettings mDefaultSettings;
	private ChannelAccess mDefaultAccess;
	private final Map<UUID, ChannelAccess> mPlayerAccess;
	private boolean mAutoJoin = true;
	private @Nullable String mChannelPermission = null;

	public ChannelAnnouncement(String name) {
		this(UUID.randomUUID(), Instant.now(), name);
	}

	private ChannelAnnouncement(UUID channelId, Instant lastUpdate, String name) {
		mId = channelId;
		mLastUpdate = lastUpdate;
		mName = name;

		mDefaultSettings = new ChannelSettings();
		mDefaultSettings.messagesPlaySound(true);
		mDefaultSettings.addSound(Sound.ENTITY_PLAYER_LEVELUP, 1, 0.5f);

		mDefaultAccess = new ChannelAccess();
		mPlayerAccess = new HashMap<>();
	}


	protected static Channel fromJsonInternal(JsonObject channelJson) throws Exception {
		String channelClassId = channelJson.getAsJsonPrimitive("type").getAsString();
		if (channelClassId == null || !channelClassId.equals(CHANNEL_CLASS_ID)) {
			throw new Exception("Cannot create ChannelAnnouncement from channel ID " + channelClassId);
		}
		String uuidString = channelJson.getAsJsonPrimitive("uuid").getAsString();
		UUID channelId = UUID.fromString(uuidString);
		Instant lastUpdate = Instant.now();
		JsonElement lastUpdateJson = channelJson.get("lastUpdate");
		if (lastUpdateJson != null) {
			lastUpdate = Instant.ofEpochMilli(lastUpdateJson.getAsLong());
		}
		String name = channelJson.getAsJsonPrimitive("name").getAsString();

		ChannelAnnouncement channel = new ChannelAnnouncement(channelId, lastUpdate, name);

		JsonPrimitive messageColorJson = channelJson.getAsJsonPrimitive("messageColor");
		if (messageColorJson != null && messageColorJson.isString()) {
			String messageColorString = messageColorJson.getAsString();
			try {
				channel.mMessageColor = MessagingUtils.colorFromString(messageColorString);
			} catch (Exception e) {
				MMLog.warning("Caught exception getting mMessageColor from json: " + e.getMessage());
			}
		}

		JsonObject defaultSettingsJson = channelJson.getAsJsonObject("defaultSettings");
		if (defaultSettingsJson != null) {
			channel.mDefaultSettings = ChannelSettings.fromJson(defaultSettingsJson);
		}

		JsonObject defaultAccessJson = channelJson.getAsJsonObject("defaultAccess");
		if (defaultAccessJson == null) {
			defaultAccessJson = channelJson.getAsJsonObject("defaultPerms");
		}
		if (defaultAccessJson != null) {
			channel.mDefaultAccess = ChannelAccess.fromJson(defaultAccessJson);
		}

		JsonObject allPlayerAccessJson = channelJson.getAsJsonObject("playerAccess");
		if (allPlayerAccessJson == null) {
			allPlayerAccessJson = channelJson.getAsJsonObject("playerPerms");
		}
		if (allPlayerAccessJson != null) {
			for (Map.Entry<String, JsonElement> playerPermEntry : allPlayerAccessJson.entrySet()) {
				UUID playerId;
				JsonObject playerAccessJson;
				try {
					playerId = UUID.fromString(playerPermEntry.getKey());
					playerAccessJson = playerPermEntry.getValue().getAsJsonObject();
				} catch (Exception e) {
					MMLog.warning("Caught exception getting ChannelAccess from json: " + e.getMessage());
					continue;
				}
				ChannelAccess playerAccess = ChannelAccess.fromJson(playerAccessJson);
				channel.mPlayerAccess.put(playerId, playerAccess);
			}
		}

		JsonPrimitive autoJoinJson = channelJson.getAsJsonPrimitive("autoJoin");
		if (autoJoinJson != null && autoJoinJson.isBoolean()) {
			channel.mAutoJoin = autoJoinJson.getAsBoolean();
		}

		JsonPrimitive channelPermissionJson = channelJson.getAsJsonPrimitive("channelPermission");
		if (channelPermissionJson != null && channelPermissionJson.isString()) {
			channel.mChannelPermission = channelPermissionJson.getAsString();
		}

		return channel;
	}

	@Override
	public JsonObject toJson() {
		JsonObject allPlayerAccessJson = new JsonObject();
		for (Map.Entry<UUID, ChannelAccess> playerPermEntry : mPlayerAccess.entrySet()) {
			UUID channelId = playerPermEntry.getKey();
			ChannelAccess channelAccess = playerPermEntry.getValue();
			if (!channelAccess.isDefault()) {
				allPlayerAccessJson.add(channelId.toString(), channelAccess.toJson());
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("type", CHANNEL_CLASS_ID);
		result.addProperty("uuid", mId.toString());
		result.addProperty("lastUpdate", mLastUpdate.toEpochMilli());
		result.addProperty("name", mName);
		if (mMessageColor != null) {
			result.addProperty("messageColor", MessagingUtils.colorToString(mMessageColor));
		}
		result.addProperty("autoJoin", mAutoJoin);
		if (mChannelPermission != null) {
			result.addProperty("channelPermission", mChannelPermission);
		}
		result.add("defaultSettings", mDefaultSettings.toJson());
		result.add("defaultAccess", mDefaultAccess.toJson());
		result.add("playerAccess", allPlayerAccessJson);
		return result;
	}

	public static void registerNewChannelCommands(String[] baseCommands, List<Argument<?>> prefixArguments) {
		List<Argument<?>> arguments;

		for (String baseCommand : baseCommands) {
			arguments = new ArrayList<>(prefixArguments);
			// last element of prefixArguments is channel ID
			arguments.add(new MultiLiteralArgument(CHANNEL_CLASS_ID));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.new.announcement")) {
						throw CommandUtils.fail(sender, "You do not have permission to create announcement channels.");
					}

					String channelName = (String)args[prefixArguments.size() - 1];
					ChannelAnnouncement newChannel = null;

					// Ignore [prefixArguments.size()], which is just the channel class ID.
					try {
						newChannel = new ChannelAnnouncement(channelName);
					} catch (Exception e) {
						throw CommandUtils.fail(sender, "Could not create new channel " + channelName + ": Could not connect to RabbitMQ.");
					}
					// Throws an exception if the channel already exists, failing the command.
					ChannelManager.registerNewChannel(sender, newChannel);
				})
				.register();

			arguments.add(new BooleanArgument("Auto Join"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.new.announcement")) {
						throw CommandUtils.fail(sender, "You do not have permission to create announcement channels.");
					}

					String channelName = (String)args[prefixArguments.size() - 1];
					ChannelAnnouncement newChannel = null;

					// Ignore [prefixArguments.size()], which is just the channel class ID.
					try {
						newChannel = new ChannelAnnouncement(channelName);
						newChannel.mAutoJoin = (boolean)args[prefixArguments.size() + 1];
					} catch (Exception e) {
						throw CommandUtils.fail(sender, "Could not create new channel " + channelName + ": Could not connect to RabbitMQ.");
					}
					// Throws an exception if the channel already exists, failing the command.
					ChannelManager.registerNewChannel(sender, newChannel);
				})
				.register();

			arguments.add(new GreedyStringArgument("Channel Permission"));
			new CommandAPICommand(baseCommand)
				.withArguments(arguments)
				.executesNative((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.new.announcement")) {
						throw CommandUtils.fail(sender, "You do not have permission to create announcement channels.");
					}

					String channelName = (String)args[prefixArguments.size() - 1];
					ChannelAnnouncement newChannel = null;

					// Ignore [prefixArguments.size()], which is just the channel class ID.
					try {
						newChannel = new ChannelAnnouncement(channelName);
						newChannel.mAutoJoin = (boolean)args[prefixArguments.size() + 1];
						newChannel.mChannelPermission = (String)args[prefixArguments.size() + 2];
					} catch (Exception e) {
						throw CommandUtils.fail(sender, "Could not create new channel " + channelName + ": Could not connect to RabbitMQ.");
					}
					// Throws an exception if the channel already exists, failing the command.
					ChannelManager.registerNewChannel(sender, newChannel);
				})
				.register();
		}
	}

	@Override
	public String getClassId() {
		return CHANNEL_CLASS_ID;
	}

	@Override
	public UUID getUniqueId() {
		return mId;
	}

	@Override
	public void markModified() {
		mLastUpdate = Instant.now();
	}

	@Override
	public Instant lastModified() {
		return mLastUpdate;
	}

	@Override
	protected void setName(String name) throws WrapperCommandSyntaxException {
		mName = name;
	}

	@Override
	public String getName() {
		return mName;
	}

	@Override
	public @Nullable TextColor color() {
		return mMessageColor;
	}

	@Override
	public void color(CommandSender sender, @Nullable TextColor color) throws WrapperCommandSyntaxException {
		mMessageColor = color;
	}

	@Override
	public ChannelSettings channelSettings() {
		return mDefaultSettings;
	}

	@Override
	public ChannelAccess channelAccess() {
		return mDefaultAccess;
	}

	@Override
	public ChannelAccess playerAccess(UUID playerId) {
		ChannelAccess playerAccess = mPlayerAccess.get(playerId);
		if (playerAccess == null) {
			playerAccess = new ChannelAccess();
			mPlayerAccess.put(playerId, playerAccess);
		}
		return playerAccess;
	}

	@Override
	public void resetPlayerAccess(UUID playerId) {
		if (playerId == null) {
			return;
		}
		mPlayerAccess.remove(playerId);
	}

	@Override
	public boolean shouldAutoJoin(PlayerState state) {
		Player player = state.getPlayer();
		return mAutoJoin && player != null && mayListen(player);
	}

	@Override
	public boolean mayChat(CommandSender sender) {
		if (!CommandUtils.hasPermission(sender, "networkchat.say.announcement")) {
			return false;
		}
		if (mChannelPermission != null && !CommandUtils.hasPermission(sender, mChannelPermission)) {
			return false;
		}

		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player player)) {
			return true;
		} else {
			ChannelAccess playerAccess = mPlayerAccess.get(player.getUniqueId());
			if (playerAccess == null) {
				return mDefaultAccess.mayChat() != null && mDefaultAccess.mayChat();
			} else {
				return playerAccess.mayChat() != null && playerAccess.mayChat();
			}
		}
	}

	@Override
	public boolean mayListen(CommandSender sender) {
		if (!CommandUtils.hasPermission(sender, "networkchat.see.announcement")) {
			return false;
		}
		if (mChannelPermission != null && !CommandUtils.hasPermission(sender, mChannelPermission)) {
			return false;
		}

		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player player)) {
			return true;
		} else {
			UUID playerId = player.getUniqueId();

			ChannelAccess playerAccess = mPlayerAccess.get(playerId);
			if (playerAccess == null) {
				return mDefaultAccess.mayListen() == null || mDefaultAccess.mayListen();
			} else {
				return playerAccess.mayListen() == null || playerAccess.mayListen();
			}
		}
	}

	@Override
	public void sendMessage(CommandSender sender, String messageText) throws WrapperCommandSyntaxException {
		if (!CommandUtils.hasPermission(sender, "networkchat.say.announcement")) {
			throw CommandUtils.fail(sender, "You do not have permission to make announcements.");
		}
		if (mChannelPermission != null && !CommandUtils.hasPermission(sender, mChannelPermission)) {
			throw CommandUtils.fail(sender, "You do not have permission to talk in " + mName + ".");
		}

		if (!mayChat(sender)) {
			throw CommandUtils.fail(sender, "You do not have permission to chat in this channel.");
		}

		WrapperCommandSyntaxException notListeningEx = isListeningCheck(sender);
		if (notListeningEx != null) {
			throw notListeningEx;
		}

		if (messageText.contains("@")) {
			if (messageText.contains("@everyone") && !CommandUtils.hasPermission(sender, "networkchat.ping.everyone")) {
				throw CommandUtils.fail(sender, "You do not have permission to ping everyone in this channel.");
			} else if (!CommandUtils.hasPermission(sender, "networkchat.ping.player") && MessagingUtils.containsPlayerMention(messageText)) {
				throw CommandUtils.fail(sender, "You do not have permission to ping a player in this channel.");
			}
		}

		messageText = PlaceholderAPI.setPlaceholders(null, messageText);

		@Nullable Message message = Message.createMessage(this, MessageType.SYSTEM, sender, null, messageText);
		if (message == null) {
			return;
		}

		try {
			MessageManager.getInstance().broadcastMessage(message);
		} catch (Exception e) {
			throw CommandUtils.fail(sender, "Could not send message; RabbitMQ is not responding.");
		}
	}

	@Override
	public void distributeMessage(Message message) {
		showMessage(Bukkit.getConsoleSender(), message);
		for (Map.Entry<UUID, PlayerState> playerStateEntry : PlayerStateManager.getPlayerStates().entrySet()) {
			PlayerState state = playerStateEntry.getValue();
			Player player = state.getPlayer();
			if (player == null || !mayListen(player)) {
				continue;
			}

			if (state.isListening(this)) {
				// This accounts for players who have paused their chat
				state.receiveMessage(message);
			}
		}
	}

	@Override
	protected Component shownMessage(CommandSender recipient, Message message) {
		TextColor channelColor;
		if (mMessageColor != null) {
			channelColor = mMessageColor;
		} else {
			channelColor = NetworkChatPlugin.messageColor(CHANNEL_CLASS_ID);
		}
		String prefix = NetworkChatPlugin.messageFormat(CHANNEL_CLASS_ID);
		if (prefix == null) {
			prefix = "";
		}
		prefix = prefix
			.replace("<message_gui_cmd>", message.getGuiCommand())
			.replace("<channel_color>", MessagingUtils.colorToMiniMessage(channelColor)) + " ";

		return Component.empty()
			.append(MessagingUtils.SENDER_FMT_MINIMESSAGE.deserialize(prefix, Placeholder.unparsed("channel_name", mName)))
			.append(Component.empty().color(channelColor).append(message.getMessage()));
	}

	@Override
	protected void showMessage(CommandSender recipient, Message message) {
		recipient.sendMessage(Identity.nil(), shownMessage(recipient, message), message.getMessageType());
		if (recipient instanceof Player player) {
			@Nullable PlayerState playerState = PlayerStateManager.getPlayerState(player);
			if (playerState == null) {
				player.sendMessage(MessagingUtils.noChatState(player));
				return;
			}
			playerState.playMessageSound(message);
		}
	}

	@Override
	public @Nullable String getChannelPermission() {
		return mChannelPermission;
	}

	@Override
	public void setChannelPermission(@Nullable String newPerms) {
		mChannelPermission = newPerms;
	}
}
