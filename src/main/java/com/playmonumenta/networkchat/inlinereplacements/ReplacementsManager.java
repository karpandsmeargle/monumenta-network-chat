package com.playmonumenta.networkchat.inlinereplacements;

import com.playmonumenta.networkchat.utils.MMLog;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;


public class ReplacementsManager {
	public List<InlineReplacement> mReplacements = new ArrayList<>();

	public ReplacementsManager() {
		mReplacements.add(new ItemHoverReplacement());
		mReplacements.add(new DiceRollReplacement());
		mReplacements.add(new EquipmentHoverReplacement());
	}

	public Component run(CommandSender sender, Component input) {
		Component result = input;
		for (InlineReplacement replacement : mReplacements) {
			if (replacement.check(sender, MessagingUtils.plainText(input))) {
				MMLog.info("Replacement found for " + replacement.mName + "in " + input);
				result = replacement.replace(sender, result);
			} else {
				MMLog.fine("No replacement found for " + replacement.mName + "in " + input);
			}
		}
		return result;
	}
}
