package qouteall.imm_ptl.phase6;

import com.google.gson.*;
import java.nio.file.*;

public final class Control {
    static final Path ROOT=Path.of("E:/ImmersivePortalsMod/build/phase6-inspection");
    static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    public static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    public static JsonObject read(String name){
        try {var p=ROOT.resolve(name);return Files.exists(p)?JsonParser.parseString(Files.readString(p)).getAsJsonObject():null;}
        catch(java.io.IOException|JsonParseException busy){return null;}
    }
    public static void write(String name,Object value){
        try {
            Path p=ROOT.resolve(name),t=ROOT.resolve(name+".tmp");Files.writeString(t,JSON.toJson(value));
            for(int retry=0;;retry++)try{Files.move(t,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);break;}
            catch(java.nio.file.AccessDeniedException busy){if(retry>=20)throw busy;Thread.sleep(2);}
        }catch(Exception e){throw new IllegalStateException("Test evidence write "+name,e);}
    }
}
