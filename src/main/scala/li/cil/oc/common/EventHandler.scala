package li.cil.oc.common

import java.util
import java.util.logging.Level

import codechicken.multipart.TMultiPart
import cpw.mods.fml.common._
import cpw.mods.fml.common.network.{IConnectionHandler, Player}
import ic2.api.energy.event.{EnergyTileLoadEvent, EnergyTileUnloadEvent}
import li.cil.oc._
import li.cil.oc.api.Network
import li.cil.oc.client.renderer.PetRenderer
import li.cil.oc.client.{PacketSender => ClientPacketSender}
import li.cil.oc.common.tileentity.traits.power
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import li.cil.oc.util.LuaStateFactory
import li.cil.oc.util.mods.{Mods, ProjectRed}
import net.minecraft.client.Minecraft
import net.minecraft.entity.player.{EntityPlayer, EntityPlayerMP}
import net.minecraft.inventory.IInventory
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.{NetHandler, Packet1Login}
import net.minecraft.network.{INetworkManager, NetLoginHandler}
import net.minecraft.server.MinecraftServer
import net.minecraft.tileentity.TileEntity
import net.minecraftforge.common.MinecraftForge

import scala.collection.mutable

object EventHandler extends ITickHandler with IConnectionHandler with ICraftingHandler {
  val pending = mutable.Buffer.empty[() => Unit]

  def schedule(tileEntity: TileEntity) =
    if (FMLCommonHandler.instance.getEffectiveSide.isServer) pending.synchronized {
      pending += (() => Network.joinOrCreateNetwork(tileEntity))
    }

  @Optional.Method(modid = "ForgeMultipart")
  def schedule(part: TMultiPart) =
    if (FMLCommonHandler.instance.getEffectiveSide.isServer) pending.synchronized {
      pending += (() => Network.joinOrCreateNetwork(part.tile))
    }

  @Optional.Method(modid = "IC2")
  def scheduleIC2Add(tileEntity: power.IndustrialCraft2) = pending.synchronized {
    pending += (() => if (!tileEntity.addedToPowerGrid && !tileEntity.isInvalid) {
      MinecraftForge.EVENT_BUS.post(new EnergyTileLoadEvent(tileEntity))
      tileEntity.addedToPowerGrid = true
    })
  }

  @Optional.Method(modid = "IC2")
  def scheduleIC2Remove(tileEntity: power.IndustrialCraft2) = pending.synchronized {
    pending += (() => if (tileEntity.addedToPowerGrid) {
      MinecraftForge.EVENT_BUS.post(new EnergyTileUnloadEvent(tileEntity))
      tileEntity.addedToPowerGrid = false
    })
  }

  override def getLabel = "OpenComputers Network Initialization Ticker"

  override def ticks() = util.EnumSet.of(TickType.SERVER)

  override def tickStart(`type`: util.EnumSet[TickType], tickData: AnyRef*) {
    pending.synchronized {
      val adds = pending.toArray
      pending.clear()
      adds
    } foreach (callback => {
      try callback() catch {
        case t: Throwable => OpenComputers.log.log(Level.WARNING, "Error in scheduled tick action.", t)
      }
    })
  }

  override def tickEnd(`type`: util.EnumSet[TickType], tickData: AnyRef*) = {}

  def playerLoggedIn(player: Player, netHandler: NetHandler, manager: INetworkManager) {
    if (netHandler.isServerHandler) player match {
      case p: EntityPlayerMP =>
        if (!LuaStateFactory.isAvailable) {
          p.sendChatToPlayer(Localization.Chat.WarningLuaFallback)
        }
        if (Mods.ProjectRed.isAvailable && !ProjectRed.isAPIAvailable) {
          p.sendChatToPlayer(Localization.Chat.WarningProjectRed)
        }
        if (!Settings.get.pureIgnorePower && Settings.get.ignorePower) {
          p.sendChatToPlayer(Localization.Chat.WarningPower)
        }
        OpenComputers.tampered match {
          case Some(event) => p.sendChatToPlayer(Localization.Chat.WarningFingerprint(event))
          case _ =>
        }
        ServerPacketSender.sendPetVisibility(None, Some(p))
        // Do update check in local games and for OPs.
        if (!MinecraftServer.getServer.isDedicatedServer || MinecraftServer.getServer.getConfigurationManager.isPlayerOpped(p.getCommandSenderName)) {
          UpdateCheck.checkForPlayer(p)
        }
      case _ =>
    }
  }

  def connectionReceived(netHandler: NetLoginHandler, manager: INetworkManager) = null

  def connectionOpened(netClientHandler: NetHandler, server: String, port: Int, manager: INetworkManager) {
  }

  def connectionOpened(netClientHandler: NetHandler, server: MinecraftServer, manager: INetworkManager) {
  }

  def connectionClosed(manager: INetworkManager) {
  }

  def clientLoggedIn(clientHandler: NetHandler, manager: INetworkManager, login: Packet1Login) {
    PetRenderer.hidden.clear()
    if (Settings.get.hideOwnPet) {
      PetRenderer.hidden += Minecraft.getMinecraft.thePlayer.getCommandSenderName
    }
    ClientPacketSender.sendPetVisibility()
  }

  lazy val navigationUpgrade = api.Items.get("navigationUpgrade")

  override def onCrafting(player: EntityPlayer, craftedStack: ItemStack, inventory: IInventory) = {
    if (api.Items.get(craftedStack) == navigationUpgrade) {
      Option(api.Driver.driverFor(craftedStack)).foreach(driver =>
        for (i <- 0 until inventory.getSizeInventory) {
          val stack = inventory.getStackInSlot(i)
          if (stack != null && api.Items.get(stack) == navigationUpgrade) {
            // Restore the map currently used in the upgrade.
            val nbt = driver.dataTag(stack)
            val map = ItemStack.loadItemStackFromNBT(nbt.getCompoundTag(Settings.namespace + "map"))
            if (!player.inventory.addItemStackToInventory(map)) {
              player.dropPlayerItemWithRandomChoice(map, false)
            }
          }
        })
    }
  }

  override def onSmelting(player: EntityPlayer, item: ItemStack) {}
}
