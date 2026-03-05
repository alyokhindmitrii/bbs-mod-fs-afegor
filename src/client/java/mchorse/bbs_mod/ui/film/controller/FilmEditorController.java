package mchorse.bbs_mod.ui.film.controller;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.FilmControllerContext;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.ui.ValueMotionPath;
import mchorse.bbs_mod.settings.values.ui.ValueOnionSkin;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.graphics.Draw;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import mchorse.bbs_mod.settings.values.core.ValueGroup;

import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.film.replays.PerLimbService;

import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collections;

import java.util.List;
import java.util.Map;

public class FilmEditorController extends BaseFilmController
{
    public UIFilmController controller;

    private int lastTick;
    private List<String> lastSelectedBones = new ArrayList<>(Collections.singletonList(null));

    public FilmEditorController(Film film, UIFilmController controller)
    {
        super(film);

        this.controller = controller;
    }

    private BaseValue findValueRecursively(ValueGroup group, String id)
    {
        if (group.get(id) != null)
        {
            return group.get(id);
        }
        
        for (BaseValue value : group.getAll())
        {
            if (value.getId().equals(id))
            {
                return value;
            }
            
            if (value instanceof ValueGroup childGroup)
            {
                BaseValue found = this.findValueRecursively(childGroup, id);
                
                if (found != null)
                {
                    return found;
                }
            }
        }
        
        return null;
    }

    private String getBoneName(String path)
    {
        int lastSlash = path.lastIndexOf('/');
        int lastDot = path.lastIndexOf('.');
        int index = Math.max(lastSlash, lastDot);

        return index >= 0 ? path.substring(index + 1) : path;
    }

    private Set<Keyframe> getAllSelectedKeyframes()
    {
        Set<Keyframe> selected = new HashSet<>();

        if (this.controller.panel.replayEditor.keyframeEditor != null && this.controller.panel.replayEditor.keyframeEditor.view != null)
        {
            for (UIKeyframeSheet sheet : this.controller.panel.replayEditor.keyframeEditor.view.getGraph().getSheets())
            {
                selected.addAll(sheet.selection.getSelected());
            }
        }

        return selected;
    }

    private List<String> getSelectedBones()
    {
        Set<String> bones = new HashSet<>();
        
        if (this.controller.panel.replayEditor.keyframeEditor != null && this.controller.panel.replayEditor.keyframeEditor.view != null)
        {
            for (UIKeyframeSheet sheet : this.controller.panel.replayEditor.keyframeEditor.view.getGraph().getSheets())
            {
                if (sheet.selection.hasAny())
                {
                    if (PerLimbService.isPoseBoneChannel(sheet.id))
                    {
                        PerLimbService.PoseBonePath path = PerLimbService.parsePoseBonePath(sheet.id);
                        
                        if (path != null)
                        {
                            bones.add(path.bone());
                        }
                    }
                    else
                    {
                        bones.add(null);
                    }
                }
            }
        }
        
        return new ArrayList<>(bones);
    }

    private void collectKeyframesRecursively(BaseValue value, Set<Integer> keyframes, Set<Integer> selectedKeyframes, Set<Keyframe> allSelected)
    {
        if (value instanceof KeyframeChannel channel)
        {
            for (Object object : channel.getKeyframes())
            {
                if (object instanceof Keyframe frame)
                {
                    keyframes.add((int) frame.getTick());

                    if (allSelected.contains(frame))
                    {
                        selectedKeyframes.add((int) frame.getTick());
                    }
                }
            }
        }
        else if (value instanceof ValueGroup group)
        {
            for (BaseValue child : group.getAll())
            {
                this.collectKeyframesRecursively(child, keyframes, selectedKeyframes, allSelected);
            }
        }
    }

    @Override
    public Map<String, Integer> getActors()
    {
        return this.controller.getActors();
    }

    @Override
    public int getTick()
    {
        return this.controller.panel.getCursor();
    }

    @Override
    protected void updateEntities(int ticks)
    {
        ticks = this.getTick() + (this.controller.panel.getRunner().isRunning() ? 1 : 0);

        super.updateEntities(ticks);

        this.lastTick = ticks;
    }

    @Override
    protected void updateEntityAndForm(IEntity entity, int tick)
    {
        boolean isPlaying = this.controller.isPlaying();
        boolean isActor = !(entity instanceof MCEntity);

        if (isPlaying && isActor)
        {
            super.updateEntityAndForm(entity, tick);
        }
    }

    @Override
    protected void applyReplay(Replay replay, int ticks, IEntity entity)
    {
        List<String> groups = this.controller.getRecordingGroups();
        boolean isPlaying = this.controller.isPlaying();
        boolean isActor = !(entity instanceof MCEntity);

        if (entity != this.controller.getControlled() || (this.controller.isRecording() && this.controller.getRecordingCountdown() <= 0 && groups != null))
        {
            replay.keyframes.apply(ticks, entity, entity == this.controller.getControlled() ? groups : null);
            replay.applyClientActions(ticks, entity, this.film);
        }

        if (entity == this.controller.getControlled() && this.controller.isRecording() && this.controller.panel.getRunner().isRunning())
        {
            replay.keyframes.record(this.controller.panel.getCursor(), entity, groups);
        }

        ticks = this.getTick() + (this.controller.panel.getRunner().isRunning() ? 1 : 0);

        /* Special pausing logic */
        if (!isPlaying && isActor)
        {
            entity.setPrevX(entity.getX());
            entity.setPrevY(entity.getY());
            entity.setPrevZ(entity.getZ());
            entity.setPrevYaw(entity.getYaw());
            entity.setPrevHeadYaw(entity.getHeadYaw());
            entity.setPrevBodyYaw(entity.getBodyYaw());
            entity.setPrevPitch(entity.getPitch());

            int diff = Math.abs(this.lastTick - ticks);

            while (diff > 0)
            {
                entity.update();

                if (entity.getForm() != null)
                {
                    entity.getForm().update(entity);
                }

                diff -= 1;
            }
        }
    }

    @Override
    protected float getTransition(IEntity entity, float transition)
    {
        boolean current = this.isCurrent(entity) && this.controller.isControlling();
        float delta = !this.controller.isPlaying() && !current ? 0F : transition;

        return delta;
    }

    @Override
    protected boolean canUpdate(int i, Replay replay, IEntity entity, UpdateMode updateMode)
    {
        return super.canUpdate(i, replay, entity, updateMode)
            || this.controller.getPovMode() != UIFilmController.CAMERA_MODE_FIRST_PERSON
            || !this.isCurrent(entity)
            || !this.controller.orbit.enabled;
    }

    @Override
    protected void renderEntity(WorldRenderContext context, Replay replay, IEntity entity)
    {
        boolean current = this.isCurrent(entity);

        if (!(this.controller.getPovMode() == UIFilmController.CAMERA_MODE_FIRST_PERSON && current))
        {
            super.renderEntity(context, replay, entity);
        }

        boolean isPlaying = this.controller.isPlaying();
        int ticks = replay.getTick(this.getTick());
        ValueOnionSkin onionSkin = this.controller.getOnionSkin();
        BaseValue value = replay.properties.get(onionSkin.group.get());

        if (value == null)
        {
            value = replay.properties.get("pose");
        }

        if (value instanceof KeyframeChannel<?> pose && entity instanceof StubEntity)
        {
            boolean canRender = onionSkin.enabled.get();

            if (!onionSkin.all.get())
            {
                canRender = canRender && current;
            }

            if (canRender)
            {
                KeyframeSegment<?> segment = pose.findSegment(ticks);

                if (segment != null)
                {
                    this.renderOnion(replay, pose.getKeyframes().indexOf(segment.a), -1, pose, onionSkin.preColor.get(), onionSkin.preFrames.get(), context, isPlaying, entity);
                    this.renderOnion(replay, pose.getKeyframes().indexOf(segment.b), 1, pose, onionSkin.postColor.get(), onionSkin.postFrames.get(), context, isPlaying, entity);

                    replay.keyframes.apply(ticks, entity);
                    float tick = ticks + this.getTransition(entity, context.tickDelta());
                    Form form = entity.getForm();
                    replay.properties.applyProperties(form, tick);

                    if (!isPlaying)
                    {
                        entity.setPrevX(entity.getX());
                        entity.setPrevY(entity.getY());
                        entity.setPrevZ(entity.getZ());
                        entity.setPrevYaw(entity.getYaw());
                        entity.setPrevHeadYaw(entity.getHeadYaw());
                        entity.setPrevBodyYaw(entity.getBodyYaw());
                        entity.setPrevPitch(entity.getPitch());
                    }
                }
            }
        }
        
        ValueMotionPath motionPath = this.controller.getMotionPath();
        
        if (motionPath.enabled.get())
        {
            List<String> selectedBones = this.getSelectedBones();
            
            if (!selectedBones.isEmpty())
            {
                this.lastSelectedBones = selectedBones;
            }
            
            boolean mode = motionPath.mode.get();
            // In Specific mode, we don't care about 'current' selected bone for rendering,
            // we just check if there are ANY bones selected in the list.
            // But we should only render for the CURRENT entity being edited.
            
            boolean render = false;
            
            if (current)
            {
                if (mode)
                {
                    // Render even if lastSelectedBone is null (root)
                    render = true;
                }
                else
                {
                    render = !motionPath.bones.get().isEmpty();
                }
            }
            
            if (render)
            {
                this.renderMotionPath(context, replay, entity, motionPath);
            }
        }
    }

    private void renderMotionPath(WorldRenderContext context, Replay replay, IEntity entity, ValueMotionPath motionPath)
    {
        int tick = replay.getTick(this.getTick());
        double pre = motionPath.preTicks.get();
        double post = motionPath.postTicks.get();
        double step = motionPath.getStep();
        int preColor = motionPath.preColor.get();
        int postColor = motionPath.postColor.get();
        float lineWidth = motionPath.lineWidth.get();
        float keyframeSize = motionPath.keyframeSize.get();
        
        Set<Keyframe> selectedKeyframes = this.getAllSelectedKeyframes();
        List<String> bonesToRender = this.getBonesToRender(motionPath);

        if (bonesToRender.isEmpty())
        {
            return;
        }

        MatrixStack stack = context.matrixStack();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        /* Setup Render State */
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();

        /* Pre-calculate camera vectors */
        Quaternionf rotation = context.camera().getRotation();
        Vector3f right = new Vector3f(1, 0, 0).rotate(rotation);
        Vector3f up = new Vector3f(0, 1, 0).rotate(rotation);
        double camX = context.camera().getPos().x;
        double camY = context.camera().getPos().y;
        double camZ = context.camera().getPos().z;

        if (pre > 0)
        {
            this.renderPathOptimized(context, replay, entity, tick, -pre, -step, preColor, lineWidth, keyframeSize, selectedKeyframes, bonesToRender, buffer, stack, right, up, camX, camY, camZ);
        }
        
        if (post > 0)
        {
            this.renderPathOptimized(context, replay, entity, tick, post, step, postColor, lineWidth, keyframeSize, selectedKeyframes, bonesToRender, buffer, stack, right, up, camX, camY, camZ);
        }
        
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();

        /* Restore entity state */
        replay.keyframes.apply(tick, entity);
        float t = tick + this.getTransition(entity, context.tickDelta());
        Form form = entity.getForm();
        replay.properties.applyProperties(form, t);
    }

    private List<String> getBonesToRender(ValueMotionPath motionPath)
    {
        boolean mode = motionPath.mode.get();
        
        if (mode) {
            if (!this.lastSelectedBones.isEmpty())
            {
                return this.lastSelectedBones;
            }
            else
            {
                return Collections.emptyList();
            }
        } else {
            return new ArrayList<>(motionPath.bones.get());
        }
    }

    private void renderPathOptimized(WorldRenderContext context, Replay replay, IEntity entity, int currentTick, double duration, double step, int color, float lineWidth, float keyframeSize, Set<Keyframe> selectedKeyframes, List<String> bonesToRender, BufferBuilder buffer, MatrixStack stack, Vector3f right, Vector3f up, double camX, double camY, double camZ)
    {
        Colors.COLOR.set(color, true);
        float r = Colors.COLOR.r;
        float g = Colors.COLOR.g;
        float b = Colors.COLOR.b;
        float a = Colors.COLOR.a;

        int steps = (int) (Math.abs(duration) / Math.abs(step));
        
        /* Data structures to hold path points for each bone */
        /* Map<BoneName, List<Point>> */
        Map<String, List<Vector3f>> bonePaths = new java.util.HashMap<>();
        
        for (String bone : bonesToRender)
        {
            bonePaths.put(bone, new ArrayList<>(steps + 1));
        }

        /* 1. Loop through Time (The Optimization) */
        for (int i = 0; i <= steps; i++)
        {
            double offset = i * step;
            float time = (float) (currentTick + offset);
            
            /* Heavy operation: Apply pose to entity ONCE per tick */
            replay.keyframes.apply((int) time, entity);
            Form form = entity.getForm();
            replay.properties.applyProperties(form, time);
            
            /* Collect matrices ONCE per tick */
            Form root = FormUtils.getRoot(form);
            MatrixCache map = FormUtilsClient.getRenderer(root).collectMatrices(entity, 0F);
            Matrix4f baseMatrix = getMatrixForRenderWithRotation(entity, 0, 0, 0, 0F);

            /* 2. Collect points for ALL bones from this single pose */
            for (String boneName : bonesToRender)
            {
                Vector3f pos = new Vector3f();
                
                if (boneName != null)
                {
                    String simpleBoneName = this.getBoneName(boneName);
                    
                    if (map.has(simpleBoneName))
                    {
                         Matrix4f matrix = map.get(simpleBoneName).matrix();
                         
                         if (matrix != null)
                         {
                             Matrix4f worldMatrix = new Matrix4f(baseMatrix);
                             worldMatrix.mul(matrix);
                             worldMatrix.getTranslation(pos);
                         }
                         else
                         {
                             pos.set((float) entity.getX(), (float) entity.getY(), (float) entity.getZ());
                         }
                    }
                    else
                    {
                         // If bone not found in this frame, fallback to root or skip? 
                         // Fallback to root to avoid gaps, or last known position?
                         pos.set((float) entity.getX(), (float) entity.getY(), (float) entity.getZ());
                    }
                }
                else
                {
                    pos.set((float) entity.getX(), (float) entity.getY(), (float) entity.getZ());
                }
                
                /* Relative to camera immediately */
                pos.sub((float) camX, (float) camY, (float) camZ);
                bonePaths.get(boneName).add(pos);
            }
        }

        /* 3. Render Ribbons (Batch Draw) */
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Matrix4f matrix = stack.peek().getPositionMatrix();
        float thickness = lineWidth * 0.05F;

        for (String boneName : bonesToRender)
        {
            List<Vector3f> points = bonePaths.get(boneName);
            if (points.size() < 2) continue;

            List<Vector3f> normals = new ArrayList<>(points.size());

            /* Calculate normals */
            for (int i = 0; i < points.size(); i++)
            {
                Vector3f p = points.get(i);
                Vector3f t = new Vector3f();
                
                if (i == 0) t.set(points.get(1)).sub(p);
                else if (i == points.size() - 1) t.set(p).sub(points.get(i - 1));
                else t.set(points.get(i + 1)).sub(points.get(i - 1));
                
                Vector3f view = new Vector3f(p); 
                Vector3f normal = new Vector3f();
                t.cross(view, normal);
                
                if (normal.lengthSquared() > 0.000001F) normal.normalize().mul(thickness / 2F);
                else normal.set(0, thickness / 2F, 0);
                
                normals.add(normal);
            }

            /* Draw Ribbon */
            for (int i = 0; i < points.size() - 1; i++)
            {
                Vector3f p1 = points.get(i);
                Vector3f p2 = points.get(i + 1);
                Vector3f n1 = normals.get(i);
                Vector3f n2 = normals.get(i + 1);
                
                float progress = (float) i / steps;
                float fade = 1F - progress;
                float alpha = a * (fade * fade);
                
                buffer.vertex(matrix, p1.x - n1.x, p1.y - n1.y, p1.z - n1.z).color(r, g, b, alpha).next();
                buffer.vertex(matrix, p1.x + n1.x, p1.y + n1.y, p1.z + n1.z).color(r, g, b, alpha).next();
                buffer.vertex(matrix, p2.x + n2.x, p2.y + n2.y, p2.z + n2.z).color(r, g, b, alpha).next();
                buffer.vertex(matrix, p2.x - n2.x, p2.y - n2.y, p2.z - n2.z).color(r, g, b, alpha).next();
            }
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        /* 4. Render Keyframes (Batch Draw) */
        RenderSystem.disableDepthTest();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

        for (String boneName : bonesToRender)
        {
             this.renderKeyframesForBoneOptimized(replay, currentTick, duration, boneName, keyframeSize, buffer, stack, right, up, selectedKeyframes, camX, camY, camZ);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
    }

    private void renderKeyframesForBoneOptimized(Replay replay, int currentTick, double duration, String boneName, float keyframeSize, BufferBuilder buffer, MatrixStack stack, Vector3f right, Vector3f up, Set<Keyframe> selectedKeyframes, double camX, double camY, double camZ)
    {
        /* This part is tricky to optimize fully without caching because keyframes are sparse.
           But we can at least avoid re-calculating if multiple keyframes land on the same tick,
           though usually keyframes are distinct ticks.
           
           For now, we keep the original logic for keyframes but ensure it uses the provided buffer/matrices.
           Keyframe rendering is much lighter than path interpolation (points vs continuous curve).
        */
        
        /* Check if bone exists */
        // Note: We don't have the entity in this method easily available for matrix check without passing it, 
        // but we can assume if we got here, we want to try rendering.
        
        Set<Integer> keyframes = new HashSet<>();
        Set<Integer> selectedTicks = new HashSet<>();
        
        /* Collect keyframes logic (Same as before) */
        if (boneName != null)
        {
            if (replay.keyframes.get(boneName) != null)
            {
                this.collectKeyframesRecursively(replay.keyframes.get(boneName), keyframes, selectedTicks, selectedKeyframes);
            }
            
             BaseValue value = this.findValueRecursively(replay.properties, boneName);
             if (value == null)
             {
                 String simpleName = boneName;
                 int lastDot = simpleName.lastIndexOf('.');
                 if (lastDot >= 0) simpleName = simpleName.substring(lastDot + 1);
                 
                 for (BaseValue v : replay.properties.getAll())
                 {
                     if (v.getId().equals(simpleName) || v.getId().endsWith("." + simpleName))
                     {
                         value = v;
                         break;
                     }
                 }
                 if (value == null) value = this.findValueRecursively(replay.properties, simpleName);
             }

             if (value != null) this.collectKeyframesRecursively(value, keyframes, selectedTicks, selectedKeyframes);
         }
         else
         {
             this.collectKeyframesRecursively(replay.keyframes.x, keyframes, selectedTicks, selectedKeyframes);
             this.collectKeyframesRecursively(replay.keyframes.y, keyframes, selectedTicks, selectedKeyframes);
             this.collectKeyframesRecursively(replay.keyframes.z, keyframes, selectedTicks, selectedKeyframes);
         }
         
         if (keyframes.isEmpty()) return;
        
        double min = Math.min(currentTick, currentTick + duration);
        double max = Math.max(currentTick, currentTick + duration);
        if (min > max) { double temp = min; min = max; max = temp; }
        min -= 0.1;
        max += 0.1;
        
        /* We need the entity to calculate position for keyframes. 
           Wait, in the optimized loop we don't have the entity in the correct pose anymore.
           We must retrieve it.
           
           Ideally, we should have collected keyframe positions during the main loop!
           But keyframes might not align with 'step' (e.g. step=0.5, keyframe at 10.25).
           
           So for keyframes, we unfortunately still need to apply poses.
           HOWEVER, keyframes are much fewer than path steps.
        */
        
        IEntity entity = this.controller.getCurrentEntity(); // Safe to access here? Yes.
        Vector3f pos = new Vector3f();

        for (int tick : keyframes)
        {
            if (tick >= min && tick <= max)
            {
                replay.keyframes.apply(tick, entity);
                Form form = entity.getForm();
                replay.properties.applyProperties(form, tick);
                
                if (boneName != null)
                {
                    String simpleBoneName = this.getBoneName(boneName);
                    Form root = FormUtils.getRoot(form);
                    MatrixCache map = FormUtilsClient.getRenderer(root).collectMatrices(entity, 0F);
                    
                    if (map.has(simpleBoneName))
                    {
                         Matrix4f matrix = map.get(simpleBoneName).matrix();
                         if (matrix != null)
                         {
                             Matrix4f worldMatrix = getMatrixForRenderWithRotation(entity, 0, 0, 0, 0F);
                             worldMatrix.mul(matrix);
                             worldMatrix.getTranslation(pos);
                         }
                         else pos.set((float) entity.getX(), (float) entity.getY(), (float) entity.getZ());
                    }
                    else pos.set((float) entity.getX(), (float) entity.getY(), (float) entity.getZ());
                }
                else
                {
                    pos.set((float) entity.getX(), (float) entity.getY(), (float) entity.getZ());
                }
                
                if (selectedTicks.contains(tick))
                {
                    Colors.COLOR.set(Colors.ORANGE, true);
                    this.renderBillboardPoint(buffer, stack, (float) (pos.x - camX), (float) (pos.y - camY), (float) (pos.z - camZ), keyframeSize, Colors.COLOR.r, Colors.COLOR.g, Colors.COLOR.b, 1F, right, up);
                }
                else
                {
                    this.renderBillboardPoint(buffer, stack, (float) (pos.x - camX), (float) (pos.y - camY), (float) (pos.z - camZ), keyframeSize, 0, 0, 0, 1F, right, up);
                }
            }
        }
    }

    private void renderRibbonForBone(WorldRenderContext context, Replay replay, IEntity entity, int currentTick, double duration, double step, float r, float g, float b, float a, int steps, String boneName, BufferBuilder buffer, MatrixStack stack)
    {
        /* Check if bone exists in the model to avoid rendering garbage for non-bone properties */
        if (boneName != null)
        {
            String simpleBoneName = this.getBoneName(boneName);
            Form form = entity.getForm();
            Form root = FormUtils.getRoot(form);
            
            MatrixCache map = FormUtilsClient.getRenderer(root).collectMatrices(entity, 0F);
            
            if (!map.has(simpleBoneName))
            {
                return;
            }
        }
        
        Vector3f pos = new Vector3f();
        List<Vector3f> points = new java.util.ArrayList<>();
        
        double camX = context.camera().getPos().x;
        double camY = context.camera().getPos().y;
        double camZ = context.camera().getPos().z;
        float thickness = this.controller.getMotionPath().lineWidth.get() * 0.05F;
        
        for (int i = 0; i <= steps; i++)
        {
            double offset = i * step;
            float time = (float) (currentTick + offset);
            
            replay.keyframes.apply((int) time, entity);
            Form form = entity.getForm();
            replay.properties.applyProperties(form, time);
            
            /* Calculate pivot position */
            if (boneName != null)
            {
                String simpleBoneName = this.getBoneName(boneName);
                Form root = FormUtils.getRoot(form);
                MatrixCache map = FormUtilsClient.getRenderer(root).collectMatrices(entity, 0F);
                
                if (map.has(simpleBoneName))
                {
                     Matrix4f matrix = map.get(simpleBoneName).matrix();
                     
                     if (matrix != null)
                     {
                         Matrix4f worldMatrix = getMatrixForRenderWithRotation(entity, 0, 0, 0, 0F);
                         worldMatrix.mul(matrix);
                         worldMatrix.getTranslation(pos);
                     }
                     else
                     {
                         pos.set((float) entity.getX(), (float) entity.getY(), (float) entity.getZ());
                     }
                }
                else
                {
                     pos.set((float) entity.getX(), (float) entity.getY(), (float) entity.getZ());
                }
            }
            else
            {
                pos.set((float) entity.getX(), (float) entity.getY(), (float) entity.getZ());
            }
            
            points.add(new Vector3f((float) (pos.x - camX), (float) (pos.y - camY), (float) (pos.z - camZ)));
        }
        
        if (points.size() < 2)
        {
            return;
        }
        
        /* Calculate ribbon vertices */
        List<Vector3f> normals = new java.util.ArrayList<>();
        
        for (int i = 0; i < points.size(); i++)
        {
            Vector3f p = points.get(i);
            Vector3f t = new Vector3f();
            
            /* Calculate tangent */
            if (i == 0)
            {
                t.set(points.get(1)).sub(p);
            }
            else if (i == points.size() - 1)
            {
                t.set(p).sub(points.get(i - 1));
            }
            else
            {
                t.set(points.get(i + 1)).sub(points.get(i - 1));
            }
            
            /* Calculate normal (perpendicular to view and tangent) */
            Vector3f view = new Vector3f(p); // View vector relative to camera
            Vector3f normal = new Vector3f();
            
            t.cross(view, normal);
            
            if (normal.lengthSquared() > 0.000001F)
            {
                normal.normalize().mul(thickness / 2F);
            }
            else
            {
                normal.set(0, thickness / 2F, 0); // Fallback
            }
            
            normals.add(normal);
        }
        
        /* Render continuous ribbon */
        Matrix4f matrix = stack.peek().getPositionMatrix();
        
        for (int i = 0; i < points.size() - 1; i++)
        {
            Vector3f p1 = points.get(i);
            Vector3f p2 = points.get(i + 1);
            Vector3f n1 = normals.get(i);
            Vector3f n2 = normals.get(i + 1);
            
            float progress = (float) i / steps;
            float fade = 1F - progress;
            float alpha = a * (fade * fade); // Quadratic fade
            
            /* Draw quad connecting segments */
            buffer.vertex(matrix, p1.x - n1.x, p1.y - n1.y, p1.z - n1.z).color(r, g, b, alpha).next();
            buffer.vertex(matrix, p1.x + n1.x, p1.y + n1.y, p1.z + n1.z).color(r, g, b, alpha).next();
            buffer.vertex(matrix, p2.x + n2.x, p2.y + n2.y, p2.z + n2.z).color(r, g, b, alpha).next();
            buffer.vertex(matrix, p2.x - n2.x, p2.y - n2.y, p2.z - n2.z).color(r, g, b, alpha).next();
        }
    }

    private void renderKeyframesForBone(WorldRenderContext context, Replay replay, IEntity entity, int currentTick, double duration, String boneName, float keyframeSize, BufferBuilder buffer, MatrixStack stack, Vector3f right, Vector3f up, Set<Keyframe> selectedKeyframes)
    {
        /* Check if bone exists */
        if (boneName != null)
        {
            String simpleBoneName = this.getBoneName(boneName);
            Form form = entity.getForm();
            Form root = FormUtils.getRoot(form);
            MatrixCache map = FormUtilsClient.getRenderer(root).collectMatrices(entity, 0F);
            
            if (!map.has(simpleBoneName)) return;
        }
        
        Set<Integer> keyframes = new HashSet<>();
        Set<Integer> selectedTicks = new HashSet<>();
        
        /* Collect keyframes */
        if (boneName != null)
        {
            /* Check root keyframes */
            if (replay.keyframes.get(boneName) != null)
            {
                this.collectKeyframesRecursively(replay.keyframes.get(boneName), keyframes, selectedTicks, selectedKeyframes);
            }
            
            /* Check property keyframes */
             /* Search recursively because 'boneName' might be nested (e.g. 'head' inside 'pose') */
             BaseValue value = this.findValueRecursively(replay.properties, boneName);
             
             if (value == null)
             {
                 String simpleName = boneName;
                 int lastDot = simpleName.lastIndexOf('.');
                 
                 if (lastDot >= 0)
                 {
                     simpleName = simpleName.substring(lastDot + 1);
                 }
                 
                 /* Try to find by suffix in all properties */
                 for (BaseValue v : replay.properties.getAll())
                 {
                     if (v.getId().equals(simpleName) || v.getId().endsWith("." + simpleName))
                     {
                         value = v;
                         break;
                     }
                 }
                 
                 if (value == null)
                 {
                     value = this.findValueRecursively(replay.properties, simpleName);
                 }
             }

             if (value != null)
             {
                 this.collectKeyframesRecursively(value, keyframes, selectedTicks, selectedKeyframes);
             }
         }
         else
         {
             this.collectKeyframesRecursively(replay.keyframes.x, keyframes, selectedTicks, selectedKeyframes);
             this.collectKeyframesRecursively(replay.keyframes.y, keyframes, selectedTicks, selectedKeyframes);
             this.collectKeyframesRecursively(replay.keyframes.z, keyframes, selectedTicks, selectedKeyframes);
         }
         
         if (keyframes.isEmpty()) return;
        
        double min = Math.min(currentTick, currentTick + duration);
        double max = Math.max(currentTick, currentTick + duration);
        
        /* Swap min/max if duration is negative (past) */
        if (min > max)
        {
            double temp = min;
            min = max;
            max = temp;
        }
        
        /* Expand range slightly to include keyframes exactly on the edge */
        min -= 0.1;
        max += 0.1;
        
        Vector3f pos = new Vector3f();
        double camX = context.camera().getPos().x;
        double camY = context.camera().getPos().y;
        double camZ = context.camera().getPos().z;
        
        for (int tick : keyframes)
        {
            if (tick >= min && tick <= max)
            {
                /* Temporarily apply keyframe tick state to entity */
                replay.keyframes.apply(tick, entity);
                Form form = entity.getForm();
                replay.properties.applyProperties(form, tick);
                
                /* Calculate pivot position */
                if (boneName != null)
                {
                    String simpleBoneName = this.getBoneName(boneName);
                    Form root = FormUtils.getRoot(form);
                    MatrixCache map = FormUtilsClient.getRenderer(root).collectMatrices(entity, 0F);
                    
                    if (map.has(simpleBoneName))
                    {
                         Matrix4f matrix = map.get(simpleBoneName).matrix();
                         
                         if (matrix != null)
                         {
                             Matrix4f worldMatrix = getMatrixForRenderWithRotation(entity, 0, 0, 0, 0F);
                             worldMatrix.mul(matrix);
                             worldMatrix.getTranslation(pos);
                         }
                         else
                         {
                             pos.set((float) entity.getX(), (float) entity.getY(), (float) entity.getZ());
                         }
                    }
                    else
                    {
                         pos.set((float) entity.getX(), (float) entity.getY(), (float) entity.getZ());
                    }
                }
                else
                {
                    pos.set((float) entity.getX(), (float) entity.getY(), (float) entity.getZ());
                }
                
                if (selectedTicks.contains(tick))
                {
                    Colors.COLOR.set(Colors.ORANGE, true);
                    this.renderBillboardPoint(buffer, stack, (float) (pos.x - camX), (float) (pos.y - camY), (float) (pos.z - camZ), keyframeSize, Colors.COLOR.r, Colors.COLOR.g, Colors.COLOR.b, 1F, right, up);
                }
                else
                {
                    this.renderBillboardPoint(buffer, stack, (float) (pos.x - camX), (float) (pos.y - camY), (float) (pos.z - camZ), keyframeSize, 0, 0, 0, 1F, right, up);
                }
            }
        }
        
        /* Restore entity state to current tick */
        replay.keyframes.apply(currentTick, entity);
        Form form = entity.getForm();
        replay.properties.applyProperties(form, currentTick);
    }
    
    private void renderBillboardLine(BufferBuilder buffer, MatrixStack stack, float x1, float y1, float z1, float x2, float y2, float z2, float thickness, float r, float g, float b, float a)
    {
        Vector3f view = new Vector3f(x1, y1, z1);
        Vector3f dir = new Vector3f(x2 - x1, y2 - y1, z2 - z1);
        Vector3f normal = new Vector3f();
        
        dir.cross(view, normal);
        
        if (normal.lengthSquared() < 0.000001F)
        {
            return;
        }
        
        normal.normalize().mul(thickness / 2F);
        
        Matrix4f matrix = stack.peek().getPositionMatrix();
        
        buffer.vertex(matrix, x1 - normal.x, y1 - normal.y, z1 - normal.z).color(r, g, b, a).next();
        buffer.vertex(matrix, x1 + normal.x, y1 + normal.y, z1 + normal.z).color(r, g, b, a).next();
        buffer.vertex(matrix, x2 + normal.x, y2 + normal.y, z2 + normal.z).color(r, g, b, a).next();
        buffer.vertex(matrix, x2 - normal.x, y2 - normal.y, z2 - normal.z).color(r, g, b, a).next();
    }
    
    private void renderBillboardPoint(BufferBuilder buffer, MatrixStack stack, float x, float y, float z, float size, float r, float g, float b, float a, Vector3f right, Vector3f up)
    {
        float s = size / 2F;
        Matrix4f matrix = stack.peek().getPositionMatrix();
        
        /* BL, BR, TR, TL */
        Vector3f v1 = new Vector3f(x, y, z).sub(right.x * s, right.y * s, right.z * s).sub(up.x * s, up.y * s, up.z * s);
        Vector3f v2 = new Vector3f(x, y, z).add(right.x * s, right.y * s, right.z * s).sub(up.x * s, up.y * s, up.z * s);
        Vector3f v3 = new Vector3f(x, y, z).add(right.x * s, right.y * s, right.z * s).add(up.x * s, up.y * s, up.z * s);
        Vector3f v4 = new Vector3f(x, y, z).sub(right.x * s, right.y * s, right.z * s).add(up.x * s, up.y * s, up.z * s);
        
        buffer.vertex(matrix, v1.x, v1.y, v1.z).color(r, g, b, a).next();
        buffer.vertex(matrix, v2.x, v2.y, v2.z).color(r, g, b, a).next();
        buffer.vertex(matrix, v3.x, v3.y, v3.z).color(r, g, b, a).next();
        buffer.vertex(matrix, v4.x, v4.y, v4.z).color(r, g, b, a).next();
    }

    private void renderOnion(Replay replay, int index, int direction, KeyframeChannel<?> pose, int color, int frames, WorldRenderContext context, boolean isPlaying, IEntity entity)
    {
        List<? extends Keyframe<?>> keyframes = pose.getKeyframes();
        float alpha = Colors.getA(color);

        for (; CollectionUtils.inRange(keyframes, index) && frames > 0; index += direction)
        {
            Keyframe<?> keyframe = keyframes.get(index);

            if (keyframe.getTick() == this.getTick())
            {
                continue;
            }

            int tick1 = (int) keyframe.getTick();
            replay.keyframes.apply(tick1, entity);
            float tick = (int) keyframe.getTick();
            Form form = entity.getForm();
            replay.properties.applyProperties(form, tick);

            BaseFilmController.renderEntity(FilmControllerContext.instance
                .setup(this.getEntities(), entity, replay, context)
                .color(Colors.setA(color, alpha))
                .transition(0F));

            frames -= 1;
            alpha *= alpha;
        }
    }

    @Override
    protected FilmControllerContext getFilmControllerContext(WorldRenderContext context, Replay replay, IEntity entity)
    {
        Pair<String, Boolean> bone = this.isCurrent(entity) && !this.controller.panel.recorder.isRecording() ? this.controller.getBone() : null;
        String aBone = bone == null ? null : bone.a;
        boolean local = bone != null && bone.b;
        String aBone2 = null;
        boolean local2 = false;

        if (replay.axesPreview.get())
        {
            aBone2 = replay.axesPreviewBone.get();
            local2 = true;
        }

        if (this.controller.panel.recorder.isRecording())
        {
            aBone = null;
            local = false;
            aBone2 = null;
            local2 = false;
        }

        return super.getFilmControllerContext(context, replay, entity)
            .transition(this.getTransition(entity, context.tickDelta()))
            .bone(aBone, local)
            .bone2(aBone2, local2);
    }

    private boolean isCurrent(IEntity entity)
    {
        return entity == this.controller.getCurrentEntity();
    }
}