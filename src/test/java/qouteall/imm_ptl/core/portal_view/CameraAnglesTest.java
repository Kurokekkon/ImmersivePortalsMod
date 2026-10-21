package qouteall.imm_ptl.core.portal_view;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import qouteall.q_misc_util.my_util.DQuaternion;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CameraAnglesTest {
    @ParameterizedTest
    @CsvSource({"0,180", "25,123", "-35,-75", "70,0"})
    void preservesUnrolledLook(double pitch, double yaw) {
        var result=CameraAngles.fromRotation(DQuaternion.getCameraRotation(pitch,yaw),42);
        assertEquals(pitch,result.getA(),1e-6);
        assertEquals(0,Math.IEEEremainder(result.getB()-yaw,360),1e-6);
    }

    @Test
    void quarterRollDoesNotReverseForwardDirection() {
        var rotation=DQuaternion.getCameraRotation(0,180).hamiltonProduct(
            DQuaternion.rotationByDegrees(new Vec3(0,0,1),-90));
        var result=CameraAngles.fromRotation(rotation,180);
        assertEquals(0,result.getA(),1e-6);
        assertEquals(0,Math.IEEEremainder(result.getB()-180,360),1e-6);
    }

    @Test
    void verticalLookKeepsYawInsteadOfUsingFloatingPointNoise() {
        var result=CameraAngles.fromRotation(DQuaternion.getCameraRotation(90,123),123);
        assertEquals(90,result.getA(),1e-6);
        assertEquals(123,result.getB(),1e-6);
    }
}
