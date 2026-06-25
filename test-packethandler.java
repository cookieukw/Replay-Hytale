import com.hypixel.hytale.server.core.io.PacketHandler;
public class testpackethandler {
    public static void main(String[] args) {
        for(java.lang.reflect.Method m : PacketHandler.class.getMethods()) {
            System.out.println(m.getName());
        }
    }
}
