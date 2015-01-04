package bothack.bot;

import java.util.Map;
import java.util.Set;

import bothack.bot.dungeon.ILevel;
import bothack.bot.items.IItem;

/** Immutable representation of the game state at some point. */
public interface IGame {
	/** Returns the last snapshot of the virtual terminal screen. */
	IFrame frame();
	/** Returns the player representation. */
	IPlayer player();
	/** True if the player can safely pray (95% confidence) */
	Boolean canPray();
	/** True if the player is capable of engraving and is not currently on the plane of air or water. */
	Boolean canEngrave();
	/** Returns the slot-item pair of a levitation item currently in use (or null) */
	Map.Entry<Character,IItem> haveLevitationItemOn();
	/** Returns the set of monster types and classes that have been genocided in the game. */
	Set<String> genocided();
	/** Returns the current dungeon level. */
	ILevel currentLevel();
	/** Estimate of the sum of weight of all carried items. */
	Long weightSum();
	/** How much gold the player is carrying in total (including bagged gold). */
	Long gold();
	/** How much gold the player is carrying in main inventory. */
	Long goldAvailable();
	/**
	 * Corpse freshness tracker – checks if the corpse at given position is likely
	 * good to eat. May return false even for safe corpses if there is any
	 * uncertainity.
	 */
	Boolean isCorpseFresh(IPosition pos, IItem corpse);
	/** Return the number of game turns passed since the bot started. */
	Long turn();
	/** Return the number of actions performed since the bot started. */
	Long actionTurn();
	/** Return the current game score. */
	Long score();
}
