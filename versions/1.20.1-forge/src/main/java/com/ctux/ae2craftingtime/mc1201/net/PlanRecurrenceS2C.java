package com.ctux.ae2craftingtime.mc1201.net;
import appeng.menu.me.crafting.CraftConfirmMenu; import com.ctux.ae2craftingtime.core.PlanRecurrenceChunk; import com.ctux.ae2craftingtime.mc1201.RecurrentPlanMenu; import java.util.BitSet; import java.util.function.Supplier; import net.minecraft.client.Minecraft; import net.minecraft.network.FriendlyByteBuf; import net.minecraftforge.network.NetworkEvent;
public record PlanRecurrenceS2C(PlanRecurrenceChunk chunk){
 public static PlanRecurrenceS2C of(int c,long r,int e,int o,int n,BitSet b){var m=new byte[32];var x=b.toByteArray();System.arraycopy(x,0,m,0,x.length);return new PlanRecurrenceS2C(new PlanRecurrenceChunk(c,r,e,o,n,m));}
 public static void encode(PlanRecurrenceS2C p,FriendlyByteBuf b){com.ctux.ae2craftingtime.mc1201.net.PlanRecurrenceCodec.write(b,p.chunk);}
 public static PlanRecurrenceS2C decode(FriendlyByteBuf b){return new PlanRecurrenceS2C(com.ctux.ae2craftingtime.mc1201.net.PlanRecurrenceCodec.read(b));}
 private static byte[] readMask(FriendlyByteBuf b){var m=new byte[32];b.readBytes(m);return m;}
 public static void handle(PlanRecurrenceS2C p,Supplier<NetworkEvent.Context> s){var c=s.get();c.enqueueWork(com.ctux.ae2craftingtime.mc1201.ClientConnectionSession.guard(c.getNetworkManager(), ()->{com.ctux.ae2craftingtime.mc1201.PlanRecurrenceClient.receive(p.chunk);}));c.setPacketHandled(true);}
}
