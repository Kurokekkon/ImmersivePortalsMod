import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Read-only NBT/region inspection of the disposable, validated restore point. */
class Phase6SnapshotCheck {
    static Object tag(DataInputStream in,int type)throws IOException {
        return switch(type){
            case 1->in.readByte();case 2->in.readShort();case 3->in.readInt();case 4->in.readLong();
            case 5->in.readFloat();case 6->in.readDouble();
            case 7->{byte[] a=new byte[in.readInt()];in.readFully(a);yield a;}
            case 8->in.readUTF();
            case 9->{int t=in.readUnsignedByte(),n=in.readInt();List<Object> a=new ArrayList<>();for(int i=0;i<n;i++)a.add(tag(in,t));yield a;}
            case 10->{Map<String,Object> m=new TreeMap<>();int t;while((t=in.readUnsignedByte())!=0){String key=in.readUTF();m.put(key,tag(in,t));}yield m;}
            case 11->{int[] a=new int[in.readInt()];for(int i=0;i<a.length;i++)a[i]=in.readInt();yield a;}
            case 12->{long[] a=new long[in.readInt()];for(int i=0;i<a.length;i++)a[i]=in.readLong();yield a;}
            default->throw new IOException("Unexpected NBT tag "+type);
        };
    }
    static Object root(InputStream stream)throws IOException {
        try(var in=new DataInputStream(stream)){int type=in.readUnsignedByte();in.readUTF();return tag(in,type);}
    }
    static int portals;
    static void find(Object value,String location){
        if(value instanceof Map<?,?> map){
            if("immersive_portals:portal".equals(map.get("id"))){
                if(!map.containsKey("UUID")||!map.containsKey("Pos"))throw new IllegalStateException("Incomplete portal NBT");
                portals++;System.out.println("Portal entity: "+location+" pos="+map.get("Pos")+" keys="+map.keySet());
            }
            for(Object nested:map.values())find(nested,location);
        }else if(value instanceof List<?> list)for(Object nested:list)find(nested,location);
    }
    static void region(Path path)throws IOException {
        try(var file=new RandomAccessFile(path.toFile(),"r")){
            int[] locations=new int[1024];for(int i=0;i<1024;i++)locations[i]=file.readInt();
            for(int location:locations){
                if(location==0)continue;
                file.seek((long)(location>>>8)*4096);int length=file.readInt();int compression=file.readUnsignedByte();
                if(length<2||length>file.length())throw new IOException("Invalid region record");
                if((compression&128)!=0)throw new IOException("External record requires separate inspection");
                byte[] data=new byte[length-1];file.readFully(data);InputStream input=new ByteArrayInputStream(data);
                input=switch(compression){case 1->new GZIPInputStream(input);case 2->new InflaterInputStream(input);case 3->input;default->throw new IOException("Unknown region compression");};
                find(root(input),path.toString());
            }
        }
    }
    public static void main(String[] args)throws Exception {
        Path snapshot=Path.of(args[0]);if(!Files.exists(snapshot.resolve("restorepoint-manifest.json")))throw new IllegalArgumentException("Not a restore point");
        Path world=snapshot.resolve("world");
        for(String item:List.of("level.dat","players/data","players/advancements","dimensions/minecraft/overworld/region","dimensions/minecraft/the_nether/region","dimensions/chronodawn/chronodawn/region")){
            if(!Files.exists(world.resolve(item)))throw new IllegalStateException("Missing "+item);
        }
        Object metadata=root(new GZIPInputStream(Files.newInputStream(world.resolve("level.dat"))));
        if(!(metadata instanceof Map<?,?>))throw new IllegalStateException("Invalid level metadata");
        int globals=0;
        try(var paths=Files.walk(world)){
            for(Path path:paths.filter(Files::isRegularFile).toList()){
                if(path.getFileName().toString().equals("global_portal.dat")){root(new GZIPInputStream(Files.newInputStream(path)));globals++;System.out.println("Global portal SavedData: "+world.relativize(path));}
                if(path.getParent().getFileName().toString().equals("entities")&&path.toString().endsWith(".mca"))region(path);
            }
        }
        if(portals!=2||globals<2)throw new IllegalStateException("Expected two fixture portals and SavedData across dimensions, found "+portals+" / "+globals);
        System.out.println("PASS: two complete portal entities, "+globals+" readable global portal SavedData files, player/advancement/dimension data present.");
    }
}
