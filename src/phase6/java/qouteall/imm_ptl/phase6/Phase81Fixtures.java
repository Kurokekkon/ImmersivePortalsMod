package qouteall.imm_ptl.phase6;

import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.custom_portal_gen.PortalGenInfo;
import qouteall.imm_ptl.core.portal.nether_portal.*;
import java.util.*;

final class Phase81Fixtures {
    static void command(MinecraftServer s,String command){s.getCommands().performPrefixedCommand(s.createCommandSourceStack(),command);}
    static void create(MinecraftServer server,String mode){
        for(var world:server.getAllLevels()){var old=new ArrayList<Entity>();world.getAllEntities().forEach(e->{if(e instanceof Portal)old.add(e);});old.forEach(Entity::discard);}
        command(server,"forceload add -16 -16 64 16");
        command(server,"fill -8 200 -12 64 200 8 minecraft:glowstone");
        command(server,"fill -8 201 -12 64 208 8 minecraft:air");
        command(server,"fill 24 196 -8 48 212 -8 minecraft:emerald_block");
        if(mode.equals("obsidian")){
            ServerLevel destination=server.getLevel(Level.NETHER);
            command(server,"execute in minecraft:the_nether run forceload add 9992 9992 10008 10008");
            command(server,"execute in minecraft:the_nether run fill 9995 100 9994 10006 108 10008 minecraft:obsidian");
            command(server,"execute in minecraft:the_nether run fill 9996 101 9995 10005 107 10007 minecraft:air");
            for(int x=9996;x<=10005;x++)for(int y=101;y<=107;y++)destination.setBlockAndUpdate(new BlockPos(x,y,9995),((x+y)%2==0?Blocks.GOLD_BLOCK:Blocks.DIAMOND_BLOCK).defaultBlockState());
            BlockPortalShape from=shape(0,201,-2),to=shape(10000,101,10000);
            from.frameAreaWithCorner.forEach(p->server.overworld().setBlockAndUpdate(p,Blocks.OBSIDIAN.defaultBlockState()));
            to.frameAreaWithCorner.forEach(p->destination.setBlockAndUpdate(p,Blocks.OBSIDIAN.defaultBlockState()));
            var info=new PortalGenInfo(Level.OVERWORLD,Level.NETHER,from,to);
            info.generatePlaceholderBlocks();
            var generated=info.generateBiWayBiFacedPortal(NetherPortalEntity.ENTITY_TYPE);
            Control.write("phase81-geometry.json",Map.of("construction","native PortalGenInfo obsidian frame + normal four-entity bi-way bi-faced generation; destination deliberately displaced","entities",Arrays.stream(generated).map(p->Map.of("uuid",p.getUUID().toString(),"world",p.level().dimension().identifier().toString(),"origin",p.getOriginPos().toString(),"normal",p.getNormal().toString(),"width",p.getWidth(),"height",p.getHeight())).toList()));
        }else if(mode.equals("adjacent")){
            for(double x:new double[]{-.5,1.5}){
                var p=Portal.ENTITY_TYPE.create(server.overworld(),EntitySpawnReason.COMMAND);
                p.setOriginPos(new Vec3(x,202.5,-2.5));p.setDestinationDimension(Level.OVERWORLD);p.setDestination(new Vec3(x+32,202.5,-2.5));
                p.setOrientationAndSize(new Vec3(1,0,0),new Vec3(0,1,0),2,4);server.overworld().addFreshEntity(p);
            }
        }else throw new IllegalArgumentException("Bad Phase 8.1 fixture");
        command(server,"gamemode creative Phase6A");command(server,"execute in minecraft:overworld run tp Phase6A 0.5 201 2.5 180 0");
    }
    private static BlockPortalShape shape(int x,int y,int z){Set<BlockPos> area=new HashSet<>();for(int dx=0;dx<2;dx++)for(int dy=0;dy<3;dy++)area.add(new BlockPos(x+dx,y+dy,z));return new BlockPortalShape(area,Direction.Axis.Z);}
}
