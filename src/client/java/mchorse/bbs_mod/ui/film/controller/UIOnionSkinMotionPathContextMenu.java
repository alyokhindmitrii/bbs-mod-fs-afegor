package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.ui.ValueMotionPath;
import mchorse.bbs_mod.settings.values.ui.ValueOnionSkin;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanels;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.film.replays.overlays.UIKeyframeSheetFilterOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UIConstants;
import java.util.Set;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIListOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UIOnionSkinMotionPathContextMenu extends UIContextMenu
{
    /* Onion Skin */
    public UIIcon enable;
    public UIIcon all;
    public UIIcon group;
    public UITrackpad preFrames;
    public UIColor preColor;
    public UITrackpad postFrames;
    public UIColor postColor;

    /* Motion Path */
    public UIIcon mpEnable;
    public UIIcon mpMode;
    public UIIcon mpBones;
    public UITrackpad mpPreTicks;
    public UIColor mpPreColor;
    public UITrackpad mpPostTicks;
    public UIColor mpPostColor;
    public UITrackpad mpQuality;
    public UITrackpad mpLineWidth;
    public UITrackpad mpKeyframeSize;

    private UIElement column;

    private UIFilmPanel panel;
    private ValueOnionSkin onionSkin;
    private ValueMotionPath motionPath;

    public UIOnionSkinMotionPathContextMenu(UIFilmPanel panel, ValueOnionSkin onionSkin, ValueMotionPath motionPath)
    {
        this.panel = panel;
        this.onionSkin = onionSkin;
        this.motionPath = motionPath;

        /* Onion Skin Widgets */
        this.enable = new UIIcon(Icons.VISIBLE, (b) -> this.onionSkin.enabled.set(!this.onionSkin.enabled.get()));
        this.enable.tooltip(UIKeys.FILM_CONTROLLER_ONION_SKIN_TITLE);
        this.preFrames = new UITrackpad((v) -> this.onionSkin.preFrames.set(v.intValue()));
        this.preFrames.limit(0, 10, true).setValue(this.onionSkin.preFrames.get());
        this.preColor = new UIColor((c) -> this.onionSkin.preColor.set(c));
        this.preColor.withAlpha().setColor(this.onionSkin.preColor.get());
        this.postFrames = new UITrackpad((v) -> this.onionSkin.postFrames.set(v.intValue()));
        this.postFrames.limit(0, 10, true).setValue(this.onionSkin.postFrames.get());
        this.postColor = new UIColor((c) -> this.onionSkin.postColor.set(c));
        this.postColor.withAlpha().setColor(this.onionSkin.postColor.get());
        this.all = new UIIcon(Icons.POSE, (b) -> this.onionSkin.all.set(!this.onionSkin.all.get()));
        this.all.tooltip(UIKeys.FILM_CONTROLLER_ONION_SKIN_ALL_DESCRIPTION);
        this.group = new UIIcon(Icons.MORE, (b) ->
        {
            this.getContext().replaceContextMenu((menu) ->
            {
                Replay replay = this.panel.replayEditor.getReplay();

                if (replay == null)
                {
                    return;
                }

                for (String property : replay.properties.properties.keySet())
                {
                    menu.action(Icons.FOLDER, IKey.constant(property), this.onionSkin.group.get().equals(property), () ->
                    {
                        this.onionSkin.group.set(property);
                        this.group.tooltip(UIKeys.FILM_CONTROLLER_ONION_SKIN_GROUP.format(property));
                    });
                }
            });
        });
        this.group.tooltip(UIKeys.FILM_CONTROLLER_ONION_SKIN_GROUP.format(this.onionSkin.group.get()));

        /* Motion Path Widgets */
        this.mpEnable = new UIIcon(Icons.FRUSTUM, (b) -> this.motionPath.enabled.toggle());
        this.mpEnable.tooltip(UIKeys.FILM_CONTROLLER_MOTION_PATH_TITLE);
        
        this.mpMode = new UIIcon(Icons.REFRESH, (b) -> this.motionPath.mode.toggle());
        this.mpMode.tooltip(UIKeys.FILM_CONTROLLER_MOTION_PATH_MODE);

        this.mpBones = new UIIcon(Icons.LIMB, (b) ->
        {
            Replay replay = this.panel.replayEditor.getReplay();

            if (replay == null)
            {
                return;
            }

            Set<String> bones = this.motionPath.bones.get();
            UIOverlayPanel overlayPanel = new UIOverlayPanel(UIKeys.FILM_CONTROLLER_MOTION_PATH_BONES);
            UIScrollView scrollView = UI.scrollView(4, 6);

            scrollView.full(overlayPanel.content);
            overlayPanel.content.add(scrollView);

            for (String key : replay.properties.properties.keySet())
            {
                UIToggle toggle = new UIToggle(IKey.constant(key), (button) ->
                {
                    if (bones.contains(key))
                    {
                        bones.remove(key);
                    }
                    else
                    {
                        bones.add(key);
                    }
                });

                toggle.h(UIConstants.CONTROL_HEIGHT);
                toggle.setValue(bones.contains(key));
                scrollView.add(toggle);
            }

            UIOverlay.addOverlay(this.getContext(), overlayPanel, 200, 0.5F);
        });
        this.mpBones.tooltip(UIKeys.FILM_CONTROLLER_MOTION_PATH_BONES);

        this.mpPreTicks = new UITrackpad((v) -> this.motionPath.preTicks.set(v));
        this.mpPreTicks.limit(0, 100).setValue(this.motionPath.preTicks.get());
        
        this.mpPreColor = new UIColor((c) -> this.motionPath.preColor.set(c));
        this.mpPreColor.withAlpha().setColor(this.motionPath.preColor.get());

        this.mpPostTicks = new UITrackpad((v) -> this.motionPath.postTicks.set(v));
        this.mpPostTicks.limit(0, 100).setValue(this.motionPath.postTicks.get());

        this.mpPostColor = new UIColor((c) -> this.motionPath.postColor.set(c));
        this.mpPostColor.withAlpha().setColor(this.motionPath.postColor.get());
        
        this.mpQuality = new UITrackpad((v) -> this.motionPath.quality.set(v.intValue()));
        this.mpQuality.limit(0, 2, true).setValue(this.motionPath.quality.get());
        this.mpQuality.tooltip(UIKeys.FILM_CONTROLLER_MOTION_PATH_QUALITY);

        this.mpLineWidth = new UITrackpad((v) -> this.motionPath.lineWidth.set(v.floatValue()));
        this.mpLineWidth.limit(0.1, 10).setValue(this.motionPath.lineWidth.get());
        this.mpLineWidth.tooltip(UIKeys.FILM_CONTROLLER_MOTION_PATH_LINE_WIDTH);

        this.mpKeyframeSize = new UITrackpad((v) -> this.motionPath.keyframeSize.set(v.floatValue()));
        this.mpKeyframeSize.limit(0.01, 1).setValue(this.motionPath.keyframeSize.get());
        this.mpKeyframeSize.tooltip(UIKeys.FILM_CONTROLLER_MOTION_PATH_KEYFRAME_SIZE);

        /* Layout */
        UIElement onionRow = UI.row(this.enable, this.all, this.group);
        UIElement motionRow = UI.row(this.mpEnable, this.mpMode, this.mpBones);

        this.column = UI.column(5, 10,
            UI.label(UIKeys.FILM_CONTROLLER_ONION_SKIN_TITLE).background(),
            onionRow,
            UI.row(this.preFrames, this.preColor).tooltip(UIKeys.FILM_CONTROLLER_ONION_SKIN_PREV),
            UI.row(this.postFrames, this.postColor).tooltip(UIKeys.FILM_CONTROLLER_ONION_SKIN_NEXT),
            
            UI.label(UIKeys.FILM_CONTROLLER_MOTION_PATH_TITLE).background().marginTop(10),
            motionRow,
            UI.row(this.mpPreTicks, this.mpPreColor).tooltip(UIKeys.FILM_CONTROLLER_MOTION_PATH_PRE_TICKS),
            UI.row(this.mpPostTicks, this.mpPostColor).tooltip(UIKeys.FILM_CONTROLLER_MOTION_PATH_POST_TICKS),
            this.mpQuality,
            UI.row(this.mpLineWidth, this.mpKeyframeSize)
        );
        
        this.column.relative(this).w(160);

        this.add(this.column);
        this.column.resize();
    }

    @Override
    public boolean isEmpty()
    {
        return false;
    }

    @Override
    public void setMouse(UIContext context)
    {
        this.xy(context.mouseX(), context.mouseY())
            .wh(this.column.area.w, this.column.area.h)
            .bounds(context.menu.overlay, 5);
    }

    @Override
    protected void renderBackground(UIContext context)
    {
        super.renderBackground(context);

        /* Onion Skin Highlights */
        if (this.onionSkin.enabled.get()) UIDashboardPanels.renderHighlight(context.batcher, this.enable.area);
        if (this.onionSkin.all.get()) UIDashboardPanels.renderHighlight(context.batcher, this.all.area);
        
        /* Motion Path Highlights */
        if (this.motionPath.enabled.get()) UIDashboardPanels.renderHighlight(context.batcher, this.mpEnable.area);
        if (this.motionPath.mode.get()) UIDashboardPanels.renderHighlight(context.batcher, this.mpMode.area);
    }
}
