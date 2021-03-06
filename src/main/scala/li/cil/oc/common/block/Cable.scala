package li.cil.oc.common.block

import java.util

import codechicken.lib.vec.Cuboid6
import codechicken.multipart.{JNormalOcclusion, NormalOcclusionTest, TFacePart, TileMultipart}
import cpw.mods.fml.relauncher.{Side, SideOnly}
import li.cil.oc.Settings
import li.cil.oc.api.network.{Environment, SidedEnvironment}
import li.cil.oc.common.multipart.CablePart
import li.cil.oc.common.tileentity
import li.cil.oc.util.Tooltip
import li.cil.oc.util.mods.Mods
import net.minecraft.client.renderer.texture.IconRegister
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.{AxisAlignedBB, Icon}
import net.minecraft.world.{IBlockAccess, World}
import net.minecraftforge.common.ForgeDirection

class Cable(val parent: SpecialDelegator) extends SpecialDelegate {
  val unlocalizedName = "Cable"

  private var icon: Icon = _

  // ----------------------------------------------------------------------- //

  override def tooltipLines(stack: ItemStack, player: EntityPlayer, tooltip: util.List[String], advanced: Boolean) {
    tooltip.addAll(Tooltip.get(unlocalizedName))
  }

  @SideOnly(Side.CLIENT)
  override def icon(side: ForgeDirection) = Some(icon)

  @SideOnly(Side.CLIENT)
  override def registerIcons(iconRegister: IconRegister) {
    super.registerIcons(iconRegister)
    icon = iconRegister.registerIcon(Settings.resourceDomain + ":generic_top")
  }

  // ----------------------------------------------------------------------- //

  override def hasTileEntity = true

  override def createTileEntity(world: World) = Some(new tileentity.Cable)

  // ----------------------------------------------------------------------- //

  override def isNormalCube(world: World, x: Int, y: Int, z: Int) = false

  override def isSolid(world: IBlockAccess, x: Int, y: Int, z: Int, side: ForgeDirection) = false

  override def opacity(world: World, x: Int, y: Int, z: Int) = 0

  override def shouldSideBeRendered(world: IBlockAccess, x: Int, y: Int, z: Int, side: ForgeDirection) = false

  // ----------------------------------------------------------------------- //

  override def neighborBlockChanged(world: World, x: Int, y: Int, z: Int, blockId: Int) {
    world.markBlockForRenderUpdate(x, y, z)
    super.neighborBlockChanged(world, x, y, z, blockId)
  }

  override def updateBounds(world: IBlockAccess, x: Int, y: Int, z: Int) {
    parent.setBlockBounds(Cable.bounds(world, x, y, z))
  }
}

object Cable {
  val cachedBounds = {
    // 6 directions = 6 bits = 11111111b >> 2 = 0xFF >> 2
    (0 to 0xFF >> 2).map(mask => {
      val bounds = AxisAlignedBB.getBoundingBox(-0.125, -0.125, -0.125, 0.125, 0.125, 0.125)
      for (side <- ForgeDirection.VALID_DIRECTIONS) {
        if ((side.flag & mask) != 0) {
          if (side.offsetX < 0) bounds.minX += side.offsetX * 0.375
          else bounds.maxX += side.offsetX * 0.375
          if (side.offsetY < 0) bounds.minY += side.offsetY * 0.375
          else bounds.maxY += side.offsetY * 0.375
          if (side.offsetZ < 0) bounds.minZ += side.offsetZ * 0.375
          else bounds.maxZ += side.offsetZ * 0.375
        }
      }
      bounds.setBounds(
        bounds.minX + 0.5, bounds.minY + 0.5, bounds.minZ + 0.5,
        bounds.maxX + 0.5, bounds.maxY + 0.5, bounds.maxZ + 0.5)
    }).toArray
  }

  def neighbors(world: IBlockAccess, x: Int, y: Int, z: Int) = {
    var result = 0
    val tileEntity = world.getBlockTileEntity(x, y, z)
    for (side <- ForgeDirection.VALID_DIRECTIONS) {
      val (tx, ty, tz) = (x + side.offsetX, y + side.offsetY, z + side.offsetZ)
      if (!world.isAirBlock(tx, ty, tz)) {
        val neighborTileEntity = world.getBlockTileEntity(tx, ty, tz)
        val neighborHasNode = hasNetworkNode(neighborTileEntity, side.getOpposite)
        val canConnect = !Mods.ForgeMultipart.isAvailable ||
          (canConnectFromSide(tileEntity, side) && canConnectFromSide(neighborTileEntity, side.getOpposite))
        val canConnectIM = canConnectFromSideIM(tileEntity, side) && canConnectFromSideIM(neighborTileEntity, side.getOpposite)
        if (neighborHasNode && canConnect && canConnectIM) {
          result |= side.flag
        }
      }
    }
    result
  }

  def bounds(world: IBlockAccess, x: Int, y: Int, z: Int) = Cable.cachedBounds(Cable.neighbors(world, x, y, z)).copy()

  private def hasNetworkNode(tileEntity: TileEntity, side: ForgeDirection) =
    tileEntity match {
      case robot: tileentity.RobotProxy => false
      case host: SidedEnvironment =>
        if (host.getWorldObj.isRemote) host.canConnect(side)
        else host.sidedNode(side) != null
      case host: Environment => true
      case host if Mods.ForgeMultipart.isAvailable => hasMultiPartNode(tileEntity)
      case _ => false
    }

  private def hasMultiPartNode(tileEntity: TileEntity) =
    tileEntity match {
      case host: TileMultipart => host.partList.exists(_.isInstanceOf[CablePart])
      case _ => false
    }

  private def canConnectFromSide(tileEntity: TileEntity, side: ForgeDirection) =
    tileEntity match {
      case host: TileMultipart =>
        host.partList.forall {
          case part: JNormalOcclusion if !part.isInstanceOf[CablePart] =>
            import scala.collection.convert.WrapAsScala._
            val ownBounds = Iterable(new Cuboid6(cachedBounds(side.flag)))
            val otherBounds = part.getOcclusionBoxes
            NormalOcclusionTest(ownBounds, otherBounds)
          case part: TFacePart => !part.solid(side.ordinal) || (part.getSlotMask & codechicken.multipart.PartMap.face(side.ordinal).mask) == 0
          case _ => true
        }
      case _ => true
    }

  private def canConnectFromSideIM(tileEntity: TileEntity, side: ForgeDirection) =
    tileEntity match {
      case cable: tileentity.Cable => cable.ImmibisMicroblocks_isSideOpen(side.ordinal)
      case _ => true
    }
}