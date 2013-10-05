package li.cil.oc.common.tileentity

import li.cil.oc.client.gui
import li.cil.oc.client.{PacketSender => ClientPacketSender}
import li.cil.oc.common.component.ScreenEnvironment
import li.cil.oc.server.{PacketSender => ServerPacketSender}
import net.minecraft.nbt.NBTTagCompound

class Screen extends Rotatable with ScreenEnvironment {
  var guiScreen: Option[gui.Screen] = None

  /** Read and reset to false from the tile entity renderer. */
  var hasChanged = false

  override def readFromNBT(nbt: NBTTagCompound) = {
    super.readFromNBT(nbt)
    load(nbt.getCompoundTag("data"))
  }

  override def writeToNBT(nbt: NBTTagCompound) = {
    super.writeToNBT(nbt)

    val dataNbt = new NBTTagCompound
    save(dataNbt)
    nbt.setCompoundTag("data", dataNbt)
  }

  override def validate() = {
    super.validate()
    if (worldObj.isRemote)
      ClientPacketSender.sendScreenBufferRequest(this)
  }

  // ----------------------------------------------------------------------- //
  // IScreenEnvironment
  // ----------------------------------------------------------------------- //

  override def onScreenResolutionChange(w: Int, h: Int) = {
    super.onScreenResolutionChange(w, h)
    if (worldObj.isRemote) {
      guiScreen.foreach(_.setSize(w, h))
      hasChanged = true
    }
    else {
      worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this)
      ServerPacketSender.sendScreenResolutionChange(this, w, h)
    }
  }

  override def onScreenSet(col: Int, row: Int, s: String) = {
    super.onScreenSet(col, row, s)
    if (worldObj.isRemote) {
      guiScreen.foreach(_.updateText())
      hasChanged = true
    }
    else {
      worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this)
      ServerPacketSender.sendScreenSet(this, col, row, s)
    }
  }

  override def onScreenFill(col: Int, row: Int, w: Int, h: Int, c: Char) = {
    super.onScreenFill(col, row, w, h, c)
    if (worldObj.isRemote) {
      guiScreen.foreach(_.updateText())
      hasChanged = true
    }
    else {
      worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this)
      ServerPacketSender.sendScreenFill(this, col, row, w, h, c)
    }
  }

  override def onScreenCopy(col: Int, row: Int, w: Int, h: Int, tx: Int, ty: Int) = {
    super.onScreenCopy(col, row, w, h, tx, ty)
    if (worldObj.isRemote) {
      guiScreen.foreach(_.updateText())
      hasChanged = true
    }
    else {
      worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this)
      ServerPacketSender.sendScreenCopy(this, col, row, w, h, tx, ty)
    }
  }
}