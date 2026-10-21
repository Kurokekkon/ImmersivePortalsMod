package qouteall.imm_ptl.core.mixin.client.portal_view;

import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Share immutable resource data, never camera-dependent cloud geometry or GPU buffers. */
@Mixin(CloudRenderer.class)
public interface CloudRendererAccessor {
    @Accessor("texture") CloudRenderer.TextureData ip_getTexture();
    @Accessor("texture") void ip_setTexture(CloudRenderer.TextureData texture);
}
