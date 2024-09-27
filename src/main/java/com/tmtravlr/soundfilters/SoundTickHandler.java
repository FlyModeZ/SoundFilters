package com.tmtravlr.soundfilters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import com.tmtravlr.soundfilters.SoundFiltersMod.BlockMeta;

import paulscode.sound.Source;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * This class handles the tick events and updates the filter parameters for each
 * source playing.
 * 
 * @author Rebeca Rey
 * @Date 2014
 */
public class SoundTickHandler {
	private Minecraft mc = Minecraft.getMinecraft();
	private static int profileTickCountdown = 13;
	private static float prevDecayFactor = 0.0F;
	private static float prevRoomFactor = 0.0F;
	private static float prevSkyFactor = 0.0F;
	public static float targetLowPassGain = 1.0F;
	public static float targetLowPassGainHF = 1.0F;
	public static float baseLowPassGain = 1.0F;
	public static float baseLowPassGainHF = 1.0F;
	public static boolean waterSound = false;
	public static boolean lavaSound = false;

	// Comparator for the ComparablePositions
	public static Comparator<ComparablePosition> CPcomparator = new Comparator<ComparablePosition>() {
		public int compare(SoundTickHandler.ComparablePosition first, SoundTickHandler.ComparablePosition second) {
			return first.compareTo(second);
		}
	};

	public static ConcurrentSkipListMap<ComparablePosition, DoubleWithTimeout> sourceOcclusionMap = new ConcurrentSkipListMap(CPcomparator);

	/**
	 * Represents a x, y, z position which has a comparator and a few methods
	 * that make comparing it with another ComparablePositions very fast. Used
	 * for fast-access sets/maps.
	 * 
	 * @author Rebeca Rey
	 * @Date 2014
	 */
	public static class ComparablePosition implements Comparable {
		float x;
		float y;
		float z;

		ComparablePosition(float xToSet, float yToSet, float zToSet) {
			this.x = xToSet;
			this.y = yToSet;
			this.z = zToSet;
		}

		// Return the int floor of x
		public int x() {
			return (int) Math.floor(x);
		}

		// Return the int floor of y
		public int y() {
			return (int) Math.floor(y);
		}

		// Return the int floor of z
		public int z() {
			return (int) Math.floor(z);
		}

		public boolean equals(Object object) {
			if (!(object instanceof ComparablePosition)) {
				return false;
			}
			ComparablePosition toCompare = (ComparablePosition) object;

			return toCompare.compareTo(this) == 0;
		}

		public int compareTo(Object object) {
			if (!(object instanceof ComparablePosition)) {
				return 0;
			}
			ComparablePosition toCompare = (ComparablePosition) object;

			if (toCompare.x - this.x > 0.1) {
				return 1;
			} else if (toCompare.x - this.x < -0.1) {
				return -1;
			} else if (toCompare.y - this.y > 0.1) {
				return 1;
			} else if (toCompare.y - this.y < -0.1) {
				return -1;
			} else if (toCompare.z - this.z > 0.1) {
				return 1;
			} else if (toCompare.z - this.z < -0.1) {
				return -1;
			} else {
				return 0;
			}
		}

		@Override
		public String toString() {
			return "[" + x + ", " + y + ", " + z + "]";
		}
	}

	/**
	 * Represents two values for use in the occlusion: the sound source and
	 * occlusion amount. Also has a timeout variables, which the source should
	 * be constantly setting to 10. When the source stops setting it to 10, the
	 * sound is assumed to have finished playing.
	 * 
	 * @author Rebeca Rey
	 * @Date 2014
	 */
	public static class DoubleWithTimeout {
		public Source source;
		public double amount;
		public int timeout;

		DoubleWithTimeout() {
			this(null, 0.0D, 10);
		}

		DoubleWithTimeout(Source sToSet, double dToSet, int iToSet) {
			this.source = sToSet;
			this.amount = dToSet;
			this.timeout = iToSet;
		}
	}

	@SubscribeEvent
	public void tick(TickEvent.ClientTickEvent event) {
		if (event.phase == TickEvent.Phase.START) {
			if (this.mc != null && this.mc.theWorld != null && this.mc.thePlayer != null && !this.mc.isGamePaused()) {
				// Handle the low-pass inside of liquids				
				if (this.mc.thePlayer.isInsideOfMaterial(Material.water)) {
					if (!waterSound) {
						if (SoundFiltersMod.DEBUG) {
							SoundFiltersMod.logger.debug("[SoundFilters] Applying water sound low pass.");
						}

						targetLowPassGain = SoundFiltersMod.waterVolume;
						targetLowPassGainHF = SoundFiltersMod.waterLowPassAmount;
						lavaSound = false;
						waterSound = true;
					}
				} else if (waterSound) {
					if (SoundFiltersMod.DEBUG) {
						SoundFiltersMod.logger.debug("[SoundFilters] Stopping water sound low pass.");
					}

					targetLowPassGain = 1.0F;
					targetLowPassGainHF = 1.0F;
					waterSound = false;
				}

				if (this.mc.thePlayer.isInsideOfMaterial(Material.lava)) {
					if (!lavaSound) {
						if (SoundFiltersMod.DEBUG) {
							SoundFiltersMod.logger.debug("[SoundFilters] Applying lava sound low pass.");
						}

						targetLowPassGain = SoundFiltersMod.lavaVolume;
						targetLowPassGainHF = SoundFiltersMod.lavaLowPassAmount;
						lavaSound = true;
						waterSound = false;
					}
				} else if (lavaSound) {
					if (SoundFiltersMod.DEBUG) {
						SoundFiltersMod.logger.debug("[SoundFilters] Stopping lava sound low pass.");
					}

					targetLowPassGain = 1.0F;
					targetLowPassGainHF = 1.0F;
					lavaSound = false;
				}
				
				if (Math.abs(targetLowPassGain - baseLowPassGain) > 0.001F) {
					baseLowPassGain = (targetLowPassGain + baseLowPassGain) / 2;
				}
				
				if (Math.abs(targetLowPassGainHF - baseLowPassGainHF) > 0.001F) {
					baseLowPassGainHF = (targetLowPassGainHF + baseLowPassGainHF) / 2;
				}
			} else {
				// We must be in the menu; turn everything off:

				baseLowPassGain = 1.0F;
				baseLowPassGainHF = 1.0F;
				SoundFiltersMod.reverbFilter.decayTime = 0.1F;
				SoundFiltersMod.reverbFilter.reflectionsDelay = 0.0F;
				SoundFiltersMod.reverbFilter.lateReverbDelay = 0.0F;
				lavaSound = false;
				waterSound = false;
			}
		} else {
			ArrayList<ComparablePosition> toRemove = new ArrayList<ComparablePosition>();

			// Calculate sound occlusion for all the sources playing.
			if (SoundFiltersMod.doOcclusion) {
				for (ComparablePosition sourcePosition : sourceOcclusionMap.keySet()) {
					DoubleWithTimeout sourceAndAmount = (DoubleWithTimeout) sourceOcclusionMap.get(sourcePosition);
					if (sourceAndAmount != null) {
						if (sourceAndAmount.source != null && sourceAndAmount.source.position != null && sourceAndAmount.source.active()) {
							
							// The source can sometimes be modified in another thread, causing a rare null pointer exception here. It seems to happen when a lot of mods are installed.
							try {
								if (this.mc != null && this.mc.theWorld != null && this.mc.thePlayer != null) {
									Vec3 roomSize = new Vec3(this.mc.thePlayer.posX, this.mc.thePlayer.posY + (double) this.mc.thePlayer.getEyeHeight(), this.mc.thePlayer.posZ);
										sourceAndAmount.amount = (sourceAndAmount.amount * 3 + SoundFiltersMod.occlusionPercent * getSoundOcclusion(this.mc.theWorld, new Vec3((double) sourceAndAmount.source.position.x, (double) sourceAndAmount.source.position.y, (double) sourceAndAmount.source.position.z), roomSize)) / 4.0;
								} else {
									sourceAndAmount.amount = 0.0D;
								}
							} catch (NullPointerException e) {
								if (SoundFiltersMod.DEBUG) {
									SoundFiltersMod.logger.warn("Caught null pointer exception while updating sound occlusion. This happens sometimes because a sound is modified at the same time in another thread.");
								}
							}
						} else {
							toRemove.add(sourcePosition);
						}
					}
				}

				for (ComparablePosition positionToRemove : toRemove) {
					if (SoundFiltersMod.SUPER_DUPER_DEBUG)
						SoundFiltersMod.logger.debug("[Sound Filters] Removing " + positionToRemove + ", " + positionToRemove.hashCode() + ", " + profileTickCountdown);
					sourceOcclusionMap.remove(positionToRemove);
				}
			}

			// Create a profile of the reverb in the area.
			if (this.mc != null && this.mc.theWorld != null && this.mc.thePlayer != null && SoundFiltersMod.doReverb) {
				--profileTickCountdown;

				// Only run every 13 ticks.
				if (profileTickCountdown <= 0) {
					profileTickCountdown = 13;

					Random rand = new Random();
					TreeSet<ComparablePosition> visited = new TreeSet<ComparablePosition>(CPcomparator);
					ArrayList<IBlockState> blocksFound = new ArrayList<IBlockState>();

					LinkedList<ComparablePosition> toVisit = new LinkedList<ComparablePosition>();
					toVisit.add(new ComparablePosition((int) Math.floor(this.mc.thePlayer.posX), (int) Math.floor(this.mc.thePlayer.posY), (int) Math.floor(this.mc.thePlayer.posZ)));
					Block block;
					IBlockState state;

					// Flood fill through the area the player is in, with
					// maximum size of
					// SoundFiltersMod.profileSize (the size in the config file)
					int i;
					for (i = 0; i < SoundFiltersMod.profileSize && !toVisit.isEmpty(); i++) {
						ComparablePosition current = (ComparablePosition) toVisit.remove(rand.nextInt(toVisit.size()));
						visited.add(current);

						// Check for valid neighbours in 6 directions (valid
						// means a non-solid block that hasn't been visited)

						// South
						ComparablePosition pos = new ComparablePosition(current.x, current.y, current.z + 1);
						state = this.mc.theWorld.getBlockState(new BlockPos(pos.x(), pos.y(), pos.z()));
						block = state.getBlock();
						Material material = block.getMaterial();

						if (!material.blocksMovement()) {
							if (!visited.contains(pos) && !toVisit.contains(pos)) {
								toVisit.add(pos);
							}

							if (material != Material.air) {
								blocksFound.add(state);
							}
						} else {
							blocksFound.add(state);
						}

						// North
						pos = new ComparablePosition(current.x, current.y, current.z - 1);
						state = this.mc.theWorld.getBlockState(new BlockPos(pos.x(), pos.y(), pos.z()));
						block = state.getBlock();
						material = block.getMaterial();

						if (!material.blocksMovement()) {
							if (!visited.contains(pos) && !toVisit.contains(pos)) {
								toVisit.add(pos);
							}

							if (material != Material.air) {
								blocksFound.add(state);
							}
						} else {
							blocksFound.add(state);
						}

						// Up
						pos = new ComparablePosition(current.x, current.y + 1, current.z);
						state = this.mc.theWorld.getBlockState(new BlockPos(pos.x(), pos.y(), pos.z()));
						block = state.getBlock();
						material = block.getMaterial();

						if (!material.blocksMovement()) {
							if (!visited.contains(pos) && !toVisit.contains(pos)) {
								toVisit.add(pos);
							}

							if (material != Material.air) {
								blocksFound.add(state);
							}
						} else {
							blocksFound.add(state);
						}

						// Down
						pos = new ComparablePosition(current.x, current.y - 1, current.z);
						state = this.mc.theWorld.getBlockState(new BlockPos(pos.x(), pos.y(), pos.z()));
						block = state.getBlock();
						material = block.getMaterial();

						if (!material.blocksMovement()) {
							if (!visited.contains(pos) && !toVisit.contains(pos)) {
								toVisit.add(pos);
							}

							if (material != Material.air) {
								blocksFound.add(state);
							}
						} else {
							blocksFound.add(state);
						}

						// East
						pos = new ComparablePosition(current.x + 1, current.y, current.z);
						state = this.mc.theWorld.getBlockState(new BlockPos(pos.x(), pos.y(), pos.z()));
						block = state.getBlock();
						material = block.getMaterial();

						if (!material.blocksMovement()) {
							if (!visited.contains(pos) && !toVisit.contains(pos)) {
								toVisit.add(pos);
							}

							if (material != Material.air) {
								blocksFound.add(state);
							}
						} else {
							blocksFound.add(state);
						}

						// West
						pos = new ComparablePosition(current.x - 1, current.y, current.z);
						state = this.mc.theWorld.getBlockState(new BlockPos(pos.x(), pos.y(), pos.z()));
						block = state.getBlock();
						material = block.getMaterial();

						if (!material.blocksMovement()) {
							if (!visited.contains(pos) && !toVisit.contains(pos)) {
								toVisit.add(pos);
							}

							if (material != Material.air) {
								blocksFound.add(state);
							}
						} else {
							blocksFound.add(state);
						}
					}

					// Now we've gone through the whole room, or we hit the size
					// limit.
					// Figure out the reverb from the blocks found.

					int roomSize = visited.size();
					double highReverb = 0;
					double midReverb = 0;
					double lowReverb = 0;

					for (IBlockState s : blocksFound) {
						Block b = s.getBlock();

						// Check for custom reverb blocks
						BlockMeta blockInfo = new BlockMeta(b, 16);

						if (!SoundFiltersMod.customReverb.containsKey(blockInfo)) {
							blockInfo = new BlockMeta(b, b.getMetaFromState(s));
						}

						if (SoundFiltersMod.customReverb.containsKey(blockInfo)) {
							double factor = SoundFiltersMod.customReverb.get(blockInfo);

							lowReverb += factor >= 1.0 || factor < 0.0 ? 0.0 : 1.0 - factor;
							midReverb += factor >= 2.0 || factor <= 0.0 ? 0.0 : 1.0 - Math.abs(factor - 1.0);
							highReverb += factor <= 1.0 ? 0.0 : factor > 2.0 ? 1.0 : factor - 1.0;
						} else if (b.getMaterial() != Material.rock && b.getMaterial() != Material.glass && b.getMaterial() != Material.ice && b.getMaterial() != Material.iron) {
							if (b.getMaterial() != Material.cactus && b.getMaterial() != Material.cake && b.getMaterial() != Material.cloth && b.getMaterial() != Material.coral
									&& b.getMaterial() != Material.grass && b.getMaterial() != Material.leaves && b.getMaterial() != Material.carpet && b.getMaterial() != Material.plants
									&& b.getMaterial() != Material.gourd && b.getMaterial() != Material.snow && b.getMaterial() != Material.sponge && b.getMaterial() != Material.vine
									&& b.getMaterial() != Material.web) {
								// Generic materials that don't fall into either
								// catagory (wood, dirt, etc.)
								++midReverb;
							} else {
								// Reverb-absorbing materials (like wool and
								// plants)
								++lowReverb;
							}
						} else {
							// Materials that relfect sound (smooth and solid
							// like stone, glass, and ice)
							++highReverb;
						}

					}

					float skyFactor = 0.0F;

					if (SoundFiltersMod.doSkyChecks && roomSize == SoundFiltersMod.profileSize) {
						/*
						 * Check if you and blocks around you can see the sky in
						 * a pattern like so:
						 * 
						 * B B B
						 * 
						 * B P B
						 * 
						 * B B B
						 * 
						 * With distances of random from 5 to 10, and also above
						 * 5 for the B's
						 */

						int x = (int) Math.floor(mc.thePlayer.posX);
						int y = (int) Math.floor(mc.thePlayer.posY);
						int z = (int) Math.floor(mc.thePlayer.posZ);

						if (onlySkyAboveBlock(mc.theWorld, x, y, z))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x + rand.nextInt(5) + 5, y, z))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x - rand.nextInt(5) - 5, y, z))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x, y, z + rand.nextInt(5) + 5))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x, y, z - rand.nextInt(5) - 5))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x + rand.nextInt(5) + 5, y, z + rand.nextInt(5) + 5))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x - rand.nextInt(5) - 5, y, z + rand.nextInt(5) + 5))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x + rand.nextInt(5) + 5, y, z - rand.nextInt(5) - 5))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x - rand.nextInt(5) - 5, y, z - rand.nextInt(5) - 5))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x + rand.nextInt(5) + 5, y + 5, z))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x - rand.nextInt(5) - 5, y + 5, z))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x, y + 5, z + rand.nextInt(5) + 5))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x, y + 5, z - rand.nextInt(5) - 5))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x + rand.nextInt(5) + 5, y + 5, z + rand.nextInt(5) + 5))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x - rand.nextInt(5) - 5, y + 5, z + rand.nextInt(5) + 5))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x + rand.nextInt(5) + 5, y + 5, z - rand.nextInt(5) - 5))
							skyFactor++;
						if (onlySkyAboveBlock(mc.theWorld, x - rand.nextInt(5) - 5, y + 5, z - rand.nextInt(5) - 5))
							skyFactor++;
					}

					skyFactor = 1.0F - Math.min(skyFactor, 12F) / 12F;

					float decayFactor = 0.0F;
					float roomFactor = (float) roomSize / (float) SoundFiltersMod.profileSize;

					if (highReverb + midReverb + lowReverb > 0) {
						decayFactor += (float) (highReverb - lowReverb) / (float) (highReverb + midReverb + lowReverb);
					}

					if (decayFactor < 0.0F) {
						decayFactor = 0.0F;
					}

					if (decayFactor > 1.0F) {
						decayFactor = 1.0F;
					}

					if (SoundFiltersMod.SUPER_DUPER_DEBUG) {
						SoundFiltersMod.logger.debug("[Sound Filters] Reverb Profile - Room Size: " + roomSize + ", Looked at: " + i + ", Sky Factor: " + skyFactor + ", High, Mid, and Low Reverb: ("
								+ highReverb + ", " + midReverb + ", " + lowReverb + ")");
					}

					decayFactor = (decayFactor + prevDecayFactor) / 2.0F;
					roomFactor = (roomFactor + prevRoomFactor) / 2.0F;
					skyFactor = (skyFactor + prevSkyFactor) / 2.0F;

					prevDecayFactor = decayFactor;
					prevRoomFactor = roomFactor;
					prevSkyFactor = skyFactor;

					SoundFiltersMod.reverbFilter.decayTime = SoundFiltersMod.reverbPercent * 8.0F * decayFactor * roomFactor * skyFactor;

					if (SoundFiltersMod.reverbFilter.decayTime < 0.1F) {
						SoundFiltersMod.reverbFilter.decayTime = 0.1F;
					}

					SoundFiltersMod.reverbFilter.reflectionsGain = SoundFiltersMod.reverbPercent * (0.05F + (0.05F * roomFactor));
					SoundFiltersMod.reverbFilter.reflectionsDelay = 0.025F * roomFactor;
					SoundFiltersMod.reverbFilter.lateReverbGain = SoundFiltersMod.reverbPercent * (1.26F + (0.1F * roomFactor));
					SoundFiltersMod.reverbFilter.lateReverbDelay = 0.01F * roomFactor;
				}
			}
		}
	}

	private static boolean onlySkyAboveBlock(World world, int x, int y, int z) {
		for (int i = y; i < 256; i++) {
			IBlockState state = world.getBlockState(new BlockPos(x, i, z));
			Block block = state.getBlock();

			if (block.getMaterial().blocksMovement()) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Ray traces through the world from sound to listener, and returns the
	 * amount of occlusion the sound should have. Note that it ignores the block
	 * which the sound plays from inside (so sounds playing from inside blocks
	 * don't sound occluded).
	 * 
	 * @param world
	 *            The world
	 * @param sound
	 *            The position the sound plays from
	 * @param listener
	 *            The listener's position (the player)
	 * @return A double representing the amount of occlusion to apply to the
	 *         sound
	 */
	public static double getSoundOcclusion(World world, Vec3 sound, Vec3 listener) {
		double occludedPercent = 0.0D;

		// Fixes some funky things
		sound = sound.addVector(0.1, 0.1, 0.1);

		if (!Double.isNaN(sound.xCoord) && !Double.isNaN(sound.yCoord) && !Double.isNaN(sound.zCoord)) {
			if (!Double.isNaN(listener.xCoord) && !Double.isNaN(listener.yCoord) && !Double.isNaN(listener.zCoord)) {
				int listenerX = (int) Math.floor(listener.xCoord);
				int listenerY = (int) Math.floor(listener.yCoord);
				int listenerZ = (int) Math.floor(listener.zCoord);
				int soundX = (int) Math.floor(sound.xCoord);
				int soundY = (int) Math.floor(sound.yCoord);
				int soundZ = (int) Math.floor(sound.zCoord);
				int countDown = 200;

				while (countDown-- >= 0) {
					if (Double.isNaN(sound.xCoord) || Double.isNaN(sound.yCoord) || Double.isNaN(sound.zCoord)) {
						return occludedPercent;
					}

					if (soundX == listenerX && soundY == listenerY && soundZ == listenerZ) {
						return occludedPercent;
					}

					boolean shouldChangeX = true;
					boolean shouldChangeY = true;
					boolean shouldChangeZ = true;
					double newX = 999.0D;
					double newY = 999.0D;
					double newZ = 999.0D;

					if (listenerX == soundX) {
						shouldChangeX = false;
					}

					if (shouldChangeX) {
						newX = (double) soundX + (listenerX > soundX ? 1.0D : 0.0D);
					}

					if (listenerY == soundY) {
						shouldChangeY = false;
					}

					if (shouldChangeY) {
						newY = (double) soundY + (listenerY > soundY ? 1.0D : 0.0D);
					}

					if (listenerZ == soundZ) {
						shouldChangeZ = false;
					}

					if (shouldChangeZ) {
						newZ = (double) soundZ + (listenerZ > soundZ ? 1.0D : 0.0D);
					}

					double xPercentChange = 999.0D;
					double yPercentChange = 999.0D;
					double zPercentChange = 999.0D;
					double xDifference = listener.xCoord - sound.xCoord;
					double yDifference = listener.yCoord - sound.yCoord;
					double zDifference = listener.zCoord - sound.zCoord;

					if (shouldChangeX) {
						xPercentChange = (newX - sound.xCoord) / xDifference;
					}

					if (shouldChangeY) {
						yPercentChange = (newY - sound.yCoord) / yDifference;
					}

					if (shouldChangeZ) {
						zPercentChange = (newZ - sound.zCoord) / zDifference;
					}

					byte whichToChange;

					if (xPercentChange < yPercentChange && xPercentChange < zPercentChange) {
						if (listenerX > soundX) {
							whichToChange = 4;
						} else {
							whichToChange = 5;
						}

						sound = new Vec3(newX, sound.yCoord + yDifference * xPercentChange, sound.zCoord + zDifference * xPercentChange);
					} else if (yPercentChange < zPercentChange) {
						if (listenerY > soundY) {
							whichToChange = 0;
						} else {
							whichToChange = 1;
						}

						sound = new Vec3(sound.xCoord + xDifference * yPercentChange, newY, sound.zCoord + zDifference * yPercentChange);
					} else {
						if (listenerZ > soundZ) {
							whichToChange = 2;
						} else {
							whichToChange = 3;
						}

						sound = new Vec3(sound.xCoord + xDifference * zPercentChange, sound.yCoord + yDifference * zPercentChange, newZ);
					}

					Vec3 vec32 = new Vec3((int) Math.floor(sound.xCoord), Math.floor(sound.yCoord), Math.floor(sound.zCoord));
					soundX = (int) vec32.xCoord;

					if (whichToChange == 5) {
						--soundX;
						vec32 = vec32.addVector(1.0, 0.0, 0.0);
					}

					soundY = (int) vec32.yCoord;

					if (whichToChange == 1) {
						--soundY;
						vec32 = vec32.addVector(0.0, 1.0, 0.0);
					}

					soundZ = (int) vec32.zCoord;

					if (whichToChange == 3) {
						--soundZ;
						vec32 = vec32.addVector(0.0, 0.0, 1.0);
					}

					BlockPos pos = new BlockPos(soundX, soundY, soundZ);
					IBlockState state = world.getBlockState(pos);
					Block block = state.getBlock();
					int meta = block.getMetaFromState(state);
					Material material = block.getMaterial();
					
					BlockPos pos2 = new BlockPos(soundX, soundY, soundZ);
					IBlockState state2 = world.getBlockState(pos2);

					if (block != null && block != Blocks.air && block.getCollisionBoundingBox(world, pos2, state2) != null && block.canCollideCheck(state, false)) {
						MovingObjectPosition rayTrace = block.collisionRayTrace(world, pos, sound, listener);

						if (rayTrace != null) {
							// Check for custom occlusion blocks
							BlockMeta blockInfo = new BlockMeta(block, 16);

							if (!SoundFiltersMod.customOcclusion.containsKey(blockInfo)) {
								blockInfo = new BlockMeta(block, meta);
							}

							if (SoundFiltersMod.customOcclusion.containsKey(blockInfo)) {
								occludedPercent += SoundFiltersMod.customOcclusion.get(blockInfo) * 0.1D;
							} else if (occludedPercent < 0.7D) {
								occludedPercent += material.isOpaque() ? 0.1D : 0.05D;
							}

							if (occludedPercent > 0.98D) {
								return 0.98D;
							}
						}
					}
				}

				return occludedPercent;
			} else {
				return occludedPercent;
			}
		} else {
			return occludedPercent;
		}
	}
}
