package mchorse.bbs_mod.settings.values.ui;

import mchorse.bbs_mod.settings.values.core.ValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueDouble;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.colors.Colors;

public class ValueMotionPath extends ValueGroup
{
    public final ValueBoolean enabled = new ValueBoolean("enabled", false);
    public final ValueBoolean mode = new ValueBoolean("mode", true); // true = selected, false = specific
    
    public final ValueDouble preTicks = new ValueDouble("pre_ticks", 10D, 0D, 1000D);
    public final ValueDouble postTicks = new ValueDouble("post_ticks", 10D, 0D, 1000D);
    
    public final ValueInt preColor = new ValueInt("pre_color", Colors.NEGATIVE | Colors.A75);
    public final ValueInt postColor = new ValueInt("post_color", Colors.POSITIVE | Colors.A75);
    
    /* 0 = 1.0, 1 = 0.5, 2 = 0.1 */
    public final ValueInt quality = new ValueInt("quality", 1, 0, 2);
    public final ValueFloat lineWidth = new ValueFloat("line_width", 2F, 0.1F, 10F);
    public final ValueFloat keyframeSize = new ValueFloat("keyframe_size", 0.05F, 0.01F, 1F);
    
    public final ValueStringKeys bones = new ValueStringKeys("bones");

    public ValueMotionPath(String id)
    {
        super(id);

        this.add(this.enabled);
        this.add(this.mode);
        this.add(this.preTicks);
        this.add(this.postTicks);
        this.add(this.preColor);
        this.add(this.postColor);
        this.add(this.quality);
        this.add(this.lineWidth);
        this.add(this.keyframeSize);
        this.add(this.bones);
    }
    
    public double getStep()
    {
        int q = this.quality.get();
        
        if (q == 2) return 0.1D;
        if (q == 1) return 0.5D;
        
        return 1D;
    }
}
