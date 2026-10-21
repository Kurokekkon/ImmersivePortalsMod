package qouteall.imm_ptl.core.portal_view;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.phys.Vec3;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.Tuple;

/** Extract Minecraft look angles from a world-to-camera rotation, including roll. */
@Environment(EnvType.CLIENT)
public final class CameraAngles {
    private CameraAngles() {}

    public static Tuple<Double, Double> fromRotation(DQuaternion rotation, double verticalYaw) {
        Vec3 forward = rotation.getConjugated().rotate(new Vec3(0, 0, -1));
        double horizontal = Math.hypot(forward.x, forward.z);
        double pitch = Math.toDegrees(Math.atan2(-forward.y, horizontal));
        double yaw = horizontal < 1e-8 ? verticalYaw : Math.toDegrees(Math.atan2(-forward.x, forward.z));
        return new Tuple<>(pitch, yaw);
    }
}
