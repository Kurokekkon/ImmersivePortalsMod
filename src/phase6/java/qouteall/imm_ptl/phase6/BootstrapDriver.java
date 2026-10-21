package qouteall.imm_ptl.phase6;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Drives AutoModpack's real review callbacks only in marked disposable profiles. */
public class BootstrapDriver implements ClientModInitializer {
    private String role;
    private long sequence;
    public static void sync(String role)throws Exception {
        BootstrapDriver driver=new BootstrapDriver();driver.role=role;driver.installReview();
        Thread worker=new Thread(driver::install,"phase6-real-pack-sync");worker.setDaemon(true);worker.start();
    }
    public void onInitializeClient(){
        Control.check(Files.exists(Path.of(".phase6-client")),"Unmarked bootstrap profile");
        role=System.getProperty("imm_ptl.phase6.role","A");
        var old=Control.read("client"+role+"-command.json");sequence=old==null?0:old.get("seq").getAsLong();
        Thread poller=new Thread(()->{
            for(;;)try{Thread.sleep(500);Minecraft.getInstance().execute(this::tick);}catch(InterruptedException done){return;}
        },"phase6-bootstrap-control");poller.setDaemon(true);poller.start();
    }
    private void tick(){
        var mc=Minecraft.getInstance();
        Control.write("client"+role+"-state.json",Map.of("pid",ProcessHandle.current().pid(),"bootstrap",true,
            "screen",mc.gui.screen()==null?"none":mc.gui.screen().getClass().getName(),"connected",mc.player!=null));
        var c=Control.read("client"+role+"-command.json");
        if(c==null||c.get("seq").getAsLong()<=sequence)return;
        sequence=c.get("seq").getAsLong();
        try{
            switch(c.get("action").getAsString()){
                case "install" -> {installReview();Thread worker=new Thread(this::install,"phase6-real-pack-install");worker.setDaemon(true);worker.start();}
                case "exit" -> mc.stop();
                default -> throw new IllegalArgumentException("Unsupported bootstrap action");
            }
            Control.write("client"+role+"-response.json",Map.of("seq",sequence,"status","PASS"));
        }catch(Throwable e){Control.write("client"+role+"-response.json",Map.of("seq",sequence,"status","FAIL","error",e.toString()));e.printStackTrace();}
    }
    private static Class<?> type(String name)throws Exception{return Class.forName("pl.skidam.automodpack_core."+name);}
    private static Object call(Object target,String method)throws Exception{return target.getClass().getMethod(method).invoke(target);}
    private void installReview()throws Exception{
        Class<?> manager=type("screen.ScreenManager"),service=type("screen.ScreenService");
        var field=manager.getDeclaredField("instance");field.setAccessible(true);Object original=field.get(null);
        Object proxy=Proxy.newProxyInstance(service.getClassLoader(),new Class<?>[]{service},(self,method,args)->{
            if(method.getName().equals("welcome")||method.getName().equals("preview")){
                Object payload=args[0];String origin=call(payload,"origin").toString();
                Control.check(origin.equals("127.0.0.1:25681"),"Unexpected review origin");
                var unknown=(List<?>)call(payload,"unverifiedJarPaths");
                var authorized=Objects.requireNonNull(Control.read("authorized-candidate.json"),"Missing authorized candidate");
                for(Object path:unknown)Control.check(path.toString().endsWith(authorized.get("file").getAsString()),"Unapproved unknown jar: "+path);
                // The exact jar was explicitly authorized by the user and hash-checked at source/distribution.
                Control.write("client"+role+"-review.json",Map.of("origin",origin,"unknownJars",unknown,"review",method.getName(),"authorizedCandidateSha256",authorized.get("sha256").getAsString()));
                if(method.getName().equals("welcome")){
                    Object actions=call(payload,"actions");((Runnable)call(actions,"startConfirmedUpdate")).run();return null;
                }
                ((Runnable)call(payload,"continueAction")).run();return true;
            }
            return method.invoke(original,args);
        });
        manager.getMethod("install",service).invoke(null,proxy);
    }
    private void install(){
        try{
            Class<?> storageType=type("update.ClientStorage");
            Object storage=storageType.getMethod("open",Path.class).invoke(null,Path.of("").toAbsolutePath());
            String id=call(storage,"selectedModpackId").toString();
            Class<?> sessionType=type("client.StoredModpackConnection");
            Object session=sessionType.getMethod("open",storageType,String.class,boolean.class).invoke(null,storage,id,false);
            Object fields=call(session,"advertisedFields");
            Class<?> platform=type("modpack.group.ClientPlatform"),target=type("modpack.group.SelectedModpackTarget");
            Object selected=target.getMethod("prepareDefault",fields.getClass(),platform).invoke(null,fields,platform.getMethod("current").invoke(null));
            Object updater=sessionType.getMethod("newUpdater",target,storageType).invoke(session,selected,storage);
            Object result=updater.getClass().getMethod("processModpackUpdate",boolean.class).invoke(updater,true);
            Control.write("client"+role+"-install-call.json",Map.of("result",result.toString()));
        }catch(Throwable e){Control.write("client"+role+"-install-error.json",Map.of("error",e.toString()));e.printStackTrace();}
    }
}
