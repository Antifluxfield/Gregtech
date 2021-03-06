package gregtech.common.blocks.modelfactories;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import gregtech.api.model.AbstractBlockModelFactory;
import gregtech.api.model.ResourcePackHook;
import gregtech.api.unification.material.MaterialIconType;
import gregtech.api.unification.material.type.Material;
import gregtech.common.blocks.BlockFrame;
import net.minecraft.block.Block;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;

@SideOnly(Side.CLIENT)
public class BlockFrameFactory extends AbstractBlockModelFactory {

    private static final String VARIANT_DEFINITION =
            "\"$MATERIAL$\": {\n" +
                "        \"textures\": {\n" +
                "          \"side\": \"$TEXTURE$\",\n" +
                "          \"top\": \"$TEXTURE_TOP$\",\n" +
                "          \"particle\": \"$TEXTURE$\"\n" +
                "        }\n" +
                "      }";

    private static final Joiner COMMA_JOINER = Joiner.on(',');

    public static void init() {
        BlockFrameFactory factory = new BlockFrameFactory();
        ResourcePackHook.addResourcePackFileHook(factory);
    }

    private BlockFrameFactory() {
        super("frame_block", "frame_");
    }

    @Override
    protected String fillSample(Block block, String blockStateSample) {
        ImmutableList<Material> allowedValues = ((BlockFrame) block).variantProperty.getAllowedValues();
        ArrayList<String> variants = new ArrayList<>();
        for(Material material : allowedValues) {
            variants.add(VARIANT_DEFINITION
                    .replace("$MATERIAL$", material.toString())
                    .replace("$TEXTURE$", MaterialIconType.frameSide.getBlockPath(material.materialIconSet).toString())
                    .replace("$TEXTURE_TOP$", MaterialIconType.frameTop.getBlockPath(material.materialIconSet).toString())
            );
        }
        return blockStateSample.replace("$VARIANTS$", COMMA_JOINER.join(variants));
    }


}
